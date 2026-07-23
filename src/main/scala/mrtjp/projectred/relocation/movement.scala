/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.relocation

import java.util.{ArrayList as JAList}

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.packet.PacketCustom
import mrtjp.core.math.MathLib
import mrtjp.core.world.WorldLib
import mrtjp.projectred.RelocationConfig
import mrtjp.projectred.api.{IConditionallyMovable, IMovementCallback, IMovementDescriptor, IRelocationAPI}
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.{BlockPos, ChunkPos}
import net.minecraft.world.{NextTickListEntry, World, WorldServer}
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*
import scala.collection.immutable.HashSet
import scala.collection.mutable.{HashMap as MHashMap, Set as MSet}
import scala.ref.WeakReference

object MovementManager
{
    val serverRelocations:MHashMap[Int, WorldStructs] = MHashMap()
    val clientRelocations:MHashMap[Int, WorldStructs] = MHashMap()

    def relocationMap(isClient:Boolean):MHashMap[Int, WorldStructs] =
        if isClient then clientRelocations else serverRelocations

    def isValidWorld(w:World) = w != null && w.provider != null

    def getWorldStructs(w:World):WorldStructs =
        relocationMap(w.isRemote).getOrElseUpdate(w.provider.getDimension, new WorldStructs)

    def getEnclosedStructure(w:World, pos:BlockPos):BlockStruct =
        getWorldStructs(w).structs.find(_.contains(pos)).orNull

    def getWorld(dim:Int, isClient:Boolean):World =
        if !isClient then DimensionManager.getWorld(dim)
        else getClientWorld(dim)

    @SideOnly(Side.CLIENT)
    private def getClientWorld(dim:Int):World =
    {
        val w = Minecraft.getMinecraft.world
        if w.provider.getDimension == dim then w else null
    }

    def isMoving(w:World, pos:BlockPos):Boolean = getWorldStructs(w).contains(pos)

    def isAdjacentToMoving(w:World, pos:BlockPos):Boolean = getWorldStructs(w).isAdjacentTo(pos)

    def writeDesc(w:World, chunks:Set[ChunkPos], out:MCDataOutput):Boolean =
    {
        var send = false
        for s <- getWorldStructs(w).structs if s.getChunks.exists(chunks.contains) do {
            send = true
            out.writeShort(s.id)
            s.writeDesc(out)
        }
        if send then out.writeShort(Short.MaxValue)
        send
    }

    def readDesc(w:World, in:MCDataInput):Unit =
    {
        var id = in.readUShort()
        while id != Short.MaxValue do {
            val struct = new BlockStruct
            struct.id = id
            struct.readDesc(in)
            addStructToWorld(w, struct)
            id = in.readUShort()
        }
    }

    def read(w:World, in:MCDataInput, key:Int):Unit = key match {
        case 1 =>
            val struct = new BlockStruct
            struct.id = in.readUShort()
            struct.readDesc(in)
            addStructToWorld(w, struct)
            for b <- struct.allBlocks do //rerender all moving blocks
                w.markBlockRangeForRenderUpdate(b, b)
        case 2 =>
            val id = in.readUShort()
            getWorldStructs(w).structs.find(_.id == id) match {
                case Some(struct) => clientCycleMove(w, struct)
                case None => throw new RuntimeException(s"DC: Moving structure with id $id was not found client-side.")
            }
        case _ => throw new RuntimeException(s"DC: Packet with ID $key was not handled. " +
                s"Skipped ${in.asInstanceOf[PacketCustom].array().length} bytes.")
    }

    private def sendStruct(w:World, struct:BlockStruct):Unit =
    {
        val out = RelocationSPH.getStream(w, struct.getChunks, 1)
        out.writeShort(struct.id)
        struct.writeDesc(out)
        RelocationSPH.forceSendData()
    }

    private def sendCycle(w:World, struct:BlockStruct):Unit =
    {
        RelocationSPH.getStream(w, struct.getChunks, 2).writeShort(struct.id)
        RelocationSPH.forceSendData()
    }

    private def addStructToWorld(w:World, b:BlockStruct):Unit =
    {
        getWorldStructs(w).addStruct(b)
        b.onAdded(w)
    }

    def tryStartMove(w:World, blocks:Set[BlockPos], moveDir:Int, speed:Double, c:IMovementCallback):Boolean =
    {
        if blocks.size > RelocationConfig.moveLimit then return false

        val map = MHashMap[(Int, Int), MSet[Int]]()
        for b <- blocks do
            map.getOrElseUpdate(MathLib.normal(b, moveDir), MSet.empty) += MathLib.basis(b, moveDir)

        val shift = if (moveDir & 1) == 1 then 1 else -1
        val rowB = Set.newBuilder[BlockRow]
        for normal <- map.keys do {
            val line = map(normal).toIndexedSeq
            val sline = if shift == 1 then line.sorted else line.sorted(using Ordering[Int].reverse)
            for (basis, size) <- MathLib.splitLine(sline, shift) do {
                val c = MathLib.rhrAxis(moveDir, normal, basis + shift)
                rowB += new BlockRow(c, EnumFacing.byIndex(moveDir), size)
            }
        }

        val rows = rowB.result()
        if rows.exists(!_.canMove(w)) then return false

        val struct = new BlockStruct
        struct.id = BlockStruct.claimID()
        struct.speed = speed
        struct.rows = rows
        struct.callback = WeakReference(c)
        addStructToWorld(w, struct)
        sendStruct(w, struct)

        for r <- rows do TileMovingRow.setBlockForRow(w, r)
        true
    }

    def onTick(isClient:Boolean): Unit =
    {
        val map = relocationMap(isClient)
        for (dim, ws) <- map do if ws.nonEmpty then {
            ws.pushAll()
            val world = getWorld(dim, isClient)
            if world != null then for bs <- ws.structs do for br <- bs.rows do
                br.pushEntities(world, bs.progress)
        }

        if !isClient then {
            val fin = map.map(pair => (pair._1, pair._2.removeFinished())).filter(_._2.nonEmpty)
            for (dim, b) <- fin do {
                val w = getWorld(dim, isClient = false)
                if w != null then for s <- b do {
                    cycleMove(w, s)
                    sendCycle(w, s)
                }
            }
        }
    }

    def onWorldUnload(w:World): Unit =
    {
        getWorldStructs(w).clear()
    }

    private def clientCycleMove(w:World, struct:BlockStruct): Unit =
    {
        getWorldStructs(w).removeStruct(struct)
        struct.rows.foreach(_.pushEntities(w, 1.0))
        cycleMove(w, struct)
    }

    private def cycleMove(w:World, struct:BlockStruct): Unit =
    {
        struct.doMove(w)
        struct.postMove(w)
        struct.endMove(w)

        rescheduleTicks(w, struct.preMoveBlocks, struct.allBlocks, struct.moveDir)

        val changes = MSet[BlockPos]()
        for r <- struct.rows do r.cacheChanges(w, changes)
        for bc <- changes do w.neighborChanged(bc, Blocks.AIR, bc) // FIXME: Update source block?

        if w.isRemote then for b <- struct.allBlocks do {
            Minecraft.getMinecraft.renderGlobal.markBlockRangeForRenderUpdate(
                b.getX, b.getY, b.getZ,
                b.getX, b.getY, b.getZ
            )
        }
    }

    private def rescheduleTicks(world:World, blocks:Set[BlockPos], allBlocks:Set[BlockPos], dir:EnumFacing): Unit =
    {
        world match {
            case world:WorldServer =>
                val hash = world.pendingTickListEntriesHashSet
                val tree = world.pendingTickListEntriesTreeSet
                val list = world.pendingTickListEntriesThisTick

                val isOptifine = world.getClass.getName == "WorldServerOF"
                val chunks = allBlocks.map(b => world.getChunk(b)).filter(_ != null)

                val scheduledTicks = chunks.flatMap(ch => world.getPendingBlockUpdates(ch, !isOptifine)
                        .asInstanceOf[JAList[NextTickListEntry]] match {
                    case null => HashSet[NextTickListEntry]()
                    case tList => tList.asScala.toSet
                })

                if isOptifine then for tick <- scheduledTicks do {
                    tree.remove(tick)
                    hash.remove(tick)
                    list.remove(tick)
                }

                for tick <- scheduledTicks do {
                    val bc = tick.position
                    if blocks(bc) then {
                        bc.offset(dir)
                        tick.position = bc
                    }
                }

                for tick <- scheduledTicks do if !hash.contains(tick) then {
                    hash.add(tick)
                    tree.add(tick)
                }
            case _ =>
        }
    }

    def getConditionallyMovable(w:World, pos:BlockPos):IConditionallyMovable = {
        val b = w.getBlockState(pos).getBlock
        if b.isInstanceOf[IConditionallyMovable] then return b.asInstanceOf[IConditionallyMovable]

        val te = w.getTileEntity(pos)
        if te != null && te.isInstanceOf[IConditionallyMovable] then
            return te.asInstanceOf[IConditionallyMovable]

        if te != null && te.hasCapability(IRelocationAPI.CONDITIONALLY_MOVABLE_CAPABILITY, null) then
            return te.getCapability(IRelocationAPI.CONDITIONALLY_MOVABLE_CAPABILITY, null)

        null
    }
}

class WorldStructs
{
    var structs:Set[BlockStruct] = Set.empty

    def isEmpty:Boolean = structs.isEmpty

    def nonEmpty:Boolean = !isEmpty

    def contains(pos:BlockPos):Boolean = structs.exists(_.contains(pos))

    def isAdjacentTo(pos:BlockPos):Boolean = structs.exists(_.isAdjacentTo(pos))

    def structContaining(pos:BlockPos) = structs.find(_.contains(pos))

    def addStruct(b:BlockStruct):Unit =
    {
        structs += b
    }

    def pushAll():Unit =
    {
        structs.foreach(_.push())
    }

    def removeFinished():Set[BlockStruct] =
    {
        val finished = structs.filter(_.isFinished)
        structs = structs.filterNot(_.isFinished)
        finished
    }

    def removeStruct(s:BlockStruct):Unit =
    {
        structs -= s
    }

    def clear(): Unit =
    {
        structs = Set.empty
    }

    def getChunks:Set[ChunkPos] =
    {
        structs.flatMap(_.getChunks)
    }
}

object BlockStruct
{
    private var maxID = 0

    def claimID():Int =
    {
        if maxID < 32765 then maxID += 1 //little less than Short.MaxValue (reserved for terminator)
        else maxID = 0
        maxID
    }
}

class MoveDesc(b:WeakReference[BlockStruct]) extends IMovementDescriptor
{
    def this(b:BlockStruct) = this(new WeakReference(b))

    override def isMoving:Boolean = b match {
        case WeakReference(b:BlockStruct) => !b.isFinished
        case _ => false
    }

    override def getProgress:Double = b match {
        case WeakReference(b:BlockStruct) => b.progress
        case _ => -1
    }

    override def getSize:Int = b match {
        case WeakReference(b:BlockStruct) => b.allBlocks.size
        case _ => 0
    }
}

class BlockStruct
{
    var id:Int = -1
    var speed:Double = 1 / 16D
    var rows:Set[BlockRow] = Set.empty
    var callback:WeakReference[IMovementCallback] = WeakReference(null)

    var progress = 0.0D

    lazy val allBlocks:Set[BlockPos] = rows.flatMap(_.allBlocks)
    lazy val preMoveBlocks:Set[BlockPos] = rows.flatMap(_.preMoveBlocks)
    lazy val postMoveBlocks:Set[BlockPos] = rows.flatMap(_.postMoveBlocks)

    lazy val allAdjacentBlocks:Set[BlockPos] = allBlocks.flatMap(b => (0 until 6).map(s => b.offset(EnumFacing.byIndex(s))))

    def moveDir:EnumFacing = rows.head.moveDir

    def contains(pos:BlockPos):Boolean = rows.exists(_.contains(pos))

    def isAdjacentTo(pos:BlockPos):Boolean = allAdjacentBlocks.contains(pos)

    def push(): Unit =
    {
        progress = math.min(1.0, progress + speed)
    }

    def isFinished:Boolean = progress >= 1.0D

    def getChunks:Set[ChunkPos] =
    {
        val c = Set.newBuilder[ChunkPos]
        for b <- allBlocks do
            c += new ChunkPos(b.getX >> 4, b.getZ >> 4)
        c.result()
    }

    def onAdded(w:World): Unit =
    {
        if !w.isRemote then callback match {
            case WeakReference(c) =>
                c.setDescriptor(new MoveDesc(this))
                c.onMovementStarted()
            case _ =>
        }
    }

    def doMove(w:World): Unit =
    {
        for r <- rows do r.doMove(w)
    }

    def postMove(w:World): Unit =
    {
        for r <- rows do r.postMove(w)
    }

    def endMove(w:World): Unit =
    {
        for r <- rows do r.endMove(w)
        if !w.isRemote then callback match {
            case WeakReference(c) => c.onMovementFinished()
            case _ =>
        }
    }

    override def equals(obj:Any):Boolean = obj match {
        case that:BlockStruct => id == that.id
        case _ => false
    }

    def writeDesc(out:MCDataOutput): Unit =
    {
        out.writeFloat(progress.toFloat)
        out.writeFloat(speed.toFloat)
        out.writeByte(rows.size)
        for r <- rows do {
            out.writePos(r.pos)
            out.writeByte(r.moveDir.getIndex)
            out.writeShort(r.size)
        }
    }

    def readDesc(in:MCDataInput): Unit =
    {
        progress = in.readFloat()
        speed = in.readFloat()
        val rb = Set.newBuilder[BlockRow]
        for _ <- 0 until in.readUByte() do
            rb += new BlockRow(in.readPos(), EnumFacing.byIndex(in.readUByte()), in.readShort())
        rows = rb.result()
    }
}

class BlockRow(val pos:BlockPos, val moveDir:EnumFacing, val size:Int)
{
    lazy val allBlocks:IndexedSeq[BlockPos] = 0 to size map { i => pos.offset(moveDir.getOpposite, i) }
    lazy val preMoveBlocks:IndexedSeq[BlockPos] = allBlocks drop 1
    lazy val postMoveBlocks:IndexedSeq[BlockPos] = allBlocks dropRight 1

    lazy val allAdjacent:IndexedSeq[BlockPos] = allBlocks.flatMap(b => (0 until 6).map(s =>
        b.offset(EnumFacing.byIndex(s)))).filterNot(allBlocks.contains)

    def contains(pos:BlockPos):Boolean =
    {
        import MathLib.{basis, normal, shift}

        import math.{max, min}

        if normal(this.pos, moveDir.getIndex) == normal(pos, moveDir.getIndex) then {
            val b1 = basis(this.pos, moveDir.getIndex)
            val b2 = b1 + size * shift(moveDir.getOpposite.getIndex)
            min(b1, b2) to max(b1, b2) contains basis(pos, moveDir.getOpposite.getIndex)
        }
        else false
    }

    def pushEntities(w:World, progress:Double): Unit =
    {
        WorldLib.uncheckedGetTileEntity(w, pos) match {
            case te:TileMovingRow => te.pushEntities(this, progress)
            case _ =>
        }
    }

    def canMove(w:World):Boolean =
    {
        if !MovingTileRegistry.canRunOverBlock(w, pos) then
            return false

        if !preMoveBlocks.forall { b =>
            val movable = MovementManager.getConditionallyMovable(w, b)
            movable == null || movable.isMovable(w, b)
        } then return false

        true
    }

    def doMove(w:World): Unit =
    {
        if pos.getY < 0 || pos.getY >= 256 then return

        w.removeTileEntity(pos)
        WorldLib.uncheckedSetBlock(w, pos, Blocks.AIR.getDefaultState) //Remove movement block

        for b <- preMoveBlocks do
            MovingTileRegistry.move(w, b, moveDir)
    }

    def postMove(w:World):Unit =
        for b <- postMoveBlocks do
            MovingTileRegistry.postMove(w, b)

    def endMove(w:World): Unit = {}

    def cacheChanges(w:World, changes:MSet[BlockPos]): Unit =
    {
        for i <- 0 to size do {
            val c = pos.offset(moveDir.getOpposite, i)
            changes += c
            for s <- EnumFacing.VALUES; s1 <- EnumFacing.VALUES if s1 != s.getOpposite do
                changes += c.offset(s).offset(s1)
        }
    }
}