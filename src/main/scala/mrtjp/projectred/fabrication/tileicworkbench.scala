/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.block.property.unlisted.{UnlistedBooleanProperty, UnlistedIntegerProperty}
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.model.bakery.generation.IBakery
import codechicken.lib.model.bakery.{IBakeryProvider, ModelBakery, SimpleBlockRenderer}
import codechicken.lib.packet.PacketCustom
import codechicken.lib.vec.Rotation
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import mrtjp.core.block.*
import mrtjp.core.gui.NodeContainer
import mrtjp.core.world.WorldLib
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.api.IScrewdriver
import mrtjp.projectred.fabrication.ItemICBlueprint.*
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer.Builder
import net.minecraft.block.state.{BlockStateContainer, IBlockState}
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.property.IExtendedBlockState

import scala.collection.mutable.{Set as MSet}

class BlockICMachine(bakery:IBakery) extends MultiTileBlock(Material.ROCK) with IBakeryProvider
{
    setHardness(2)
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    override def createBlockState(): BlockStateContainer = new Builder(this).add(MultiTileBlock.TILE_INDEX)
            .add(BlockICMachine.UNLISTED_ROTATION_PROPERTY)
            .add(BlockICMachine.UNLISTED_SIDE_PROPERTY)
            .add(BlockICMachine.UNLISTED_HAS_BP_PROPERTY)
            .build()

    override def getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos) = ModelBakery.handleExtendedState(state.asInstanceOf[IExtendedBlockState], world, pos)

    override def getBakery = bakery
}

object BlockICMachine
{
    val UNLISTED_ROTATION_PROPERTY = new UnlistedIntegerProperty("rotation")
    val UNLISTED_SIDE_PROPERTY = new UnlistedIntegerProperty("side")
    val UNLISTED_HAS_BP_PROPERTY = new UnlistedBooleanProperty("hasBP")
}

abstract class TileICMachine extends MTBlockTile with TTileOrient
{
    override def getBlock = ProjectRedFabrication.icBlock

    override def onBlockPlaced(side:Int, player:EntityPlayer, stack:ItemStack): Unit =
    {
        setSide(0)
        setRotation(if doesRotate then (Rotation.getSidedRotation(player, side)+2)%4 else 0)
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeByte(orientation)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        orientation = in.readByte
    }

    override def save(tag:NBTTagCompound): Unit =
    {
        tag.setByte("rot", orientation)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        orientation = tag.getByte("rot")
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 =>
            orientation = in.readByte()
            markRender()
        case _ => super.read(in, key)
    }

    override def onBlockActivated(player:EntityPlayer, actside:Int):Boolean =
    {
        val held = player.getHeldItemMainhand
        if doesRotate && !held.isEmpty && held.getItem.isInstanceOf[IScrewdriver]
                && held.getItem.asInstanceOf[IScrewdriver].canUse(player, held) then
        {
            if world.isRemote then return true
            val old = rotation
            while { setRotation((rotation+1)%4) ; old != rotation && !isRotationAllowed(rotation)} do ()
            if old != rotation then sendOrientUpdate()
            world.notifyNeighborsRespectDebug(getPos, getBlock, false)
            onBlockRotated()
            held.getItem.asInstanceOf[IScrewdriver].damageScrewdriver(player, held)
            return true
        }
        false
    }

    def doesRotate = true

    def isRotationAllowed(rot:Int) = true

    def onBlockRotated(): Unit ={}

    def sendOrientUpdate(): Unit =
    {
        writeStream(1).writeByte(orientation).sendToChunk(this)
    }
}

class TileICWorkbench extends TileICMachine with TICTileEditorNetwork
{
    val editor = new ICTileMapEditor(this)

    var hasBP = false
    var watchers = MSet[EntityPlayer]()

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        val ictag = new NBTTagCompound
        editor.save(ictag)
        tag.setTag("ictag", ictag)
        tag.setBoolean("bp", hasBP)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        editor.load(tag.getCompoundTag("ictag"))
        hasBP = tag.getBoolean("bp")
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(hasBP)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        hasBP = in.readBoolean()
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 =>
            hasBP = in.readBoolean()
            markRender()
        case 2 => editor.readDesc(in)
        case 3 => readTileStream(in)
        case 4 => readICStream(in)
        case 5 =>
            if !hasBP then new ICTileMapEditor(null).readDesc(in)
            else {
                editor.readDesc(in)
                sendICDesc()
            }
        case 6 =>
            val name = in.readString()
            if hasBP then {
                editor.tileMapContainer.name = name
                sendICDesc()
            }
        case _ => super.read(in, key)
    }

    private def sendHasBPUpdate(): Unit =
    {
        writeStream(1).writeBoolean(hasBP).sendToChunk(this)
    }

    private def sendICDesc(): Unit ={ sendICDesc(watchers.toSeq*) }

    private def sendICDesc(players:EntityPlayer*): Unit =
    {
        if players.nonEmpty then
        {
            val out = writeStream(2)
            editor.writeDesc(out)
            for p <- players do
                out.sendToPlayer(p)
        }
    }

    def sendNewICToServer(ic:ICTileMapEditor): Unit =
    {
        val stream = writeStream(5)
        ic.writeDesc(stream)
        stream.sendToServer()
    }

    def sendICNameToServer(): Unit =
    {
        writeStream(6).writeString(editor.tileMapContainer.name).sendToServer()
    }

    override def getIC = editor
    override def getEditorWorld = world
    override def isRemote = world.isRemote
    override def markSave(): Unit ={markDirty()}

    override def createTileStream() = writeStream(3)
    override def createEditorStream() = writeStream(4)
    override def sendTileStream(out:PacketCustom): Unit =
    {
        watchers.foreach(out.sendToPlayer)
    }
    override def sendEditorStream(out:PacketCustom): Unit =
    {
        if world.isRemote then out.sendToServer()
        else watchers.foreach(out.sendToPlayer)
    }

    override def updateServer(): Unit =
    {
        super.updateServer()
        flushICStream()
        flushTileStream()
        editor.tick()
    }
    override def updateClient(): Unit =
    {
        super.updateClient()
        flushICStream() //ic stream is bi-directional
    }

    override def onBlockActivated(player:EntityPlayer, side:Int):Boolean =
    {
        if super.onBlockActivated(player, side) then return true
        if !world.isRemote then {
            import ItemICBlueprint.*
            val held = player.getHeldItemMainhand
            if !hasBP && !held.isEmpty && held.getItem.isInstanceOf[ItemICBlueprint] then {
                if hasICInside(held) then {
                    editor.clear()
                    loadTileMap(editor.tileMapContainer, held)
                    sendICDesc()
                }
                held.shrink(1)
                hasBP = true
                sendHasBPUpdate()
            }
            else if hasBP && player.isSneaking then {
                val stack = new ItemStack(ProjectRedFabrication.itemICBlueprint)
                if editor.nonEmpty then {
                    saveTileMap(editor.tileMapContainer, stack)
                    saveFlags(stack, editor.simEngineContainer.logger)
                    editor.clear()
                    sendICDesc()
                }
                val p = pos.offset(EnumFacing.values()(1))
                val item = new EntityItem(world, p.getX+0.5, p.getY+0.20, p.getZ+0.5, stack)
                item.setPickupDelay(10)
                item.motionX = 0
                item.motionY = 0.15
                item.motionZ = 0
                world.spawnEntity(item)
                hasBP = false
                sendHasBPUpdate()
            } else {
                val nc = new NodeContainer
                nc.startWatchDelegate = playerStartWatch
                nc.stopWatchDelegate = playerStopWatch
                GuiICWorkbench.open(player, nc, {p =>
                    p.writePos(pos)
                    editor.writeDesc(p)
                    p.writeLong(editor.simEngineContainer.simEngine.getRegVal[Long](SEIntegratedCircuit.REG_SYSTIME))
                })
            }
        }
        true
    }

    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()
        if hasBP then {
            val stack = new ItemStack(ProjectRedFabrication.itemICBlueprint)
            if editor.nonEmpty then {
                saveTileMap(editor.tileMapContainer, stack)
                saveFlags(stack, editor.simEngineContainer.logger)
            }
            WorldLib.dropItem(world, pos, stack)
        }
    }

    override def doesRotate = false

    def playerStartWatch(p:EntityPlayer): Unit =
    {
        watchers += p
    }

    def playerStopWatch(p:EntityPlayer): Unit =
    {
        watchers -= p
    }
}

object RenderICWorkbench extends SimpleBlockRenderer
{
    import java.lang.{Boolean as JBool}

    import BlockICMachine.*
    import org.apache.commons.lang3.tuple.Triple

    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var side1:TextureAtlasSprite = scala.compiletime.uninitialized
    var side2:TextureAtlasSprite = scala.compiletime.uninitialized
    var sidebp1:TextureAtlasSprite = scala.compiletime.uninitialized
    var sidebp2:TextureAtlasSprite = scala.compiletime.uninitialized
    var top:TextureAtlasSprite = scala.compiletime.uninitialized
    var topBP:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT:UVTransformation = scala.compiletime.uninitialized
    var iconTBP:UVTransformation = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState, world: IBlockAccess, pos:BlockPos):IExtendedBlockState = world.getTileEntity(pos) match {
        case t:TileICWorkbench =>
            state.withProperty(UNLISTED_HAS_BP_PROPERTY, t.hasBP.asInstanceOf[JBool])

        case _ => state
    }

    override def getWorldTransforms(state:IExtendedBlockState) =
    {
        val hasBP = state.getValue(UNLISTED_HAS_BP_PROPERTY)
        Triple.of(0, 0, if hasBP then iconTBP else iconT)
    }

    override def getItemTransforms(stack:ItemStack) = Triple.of(0, 0, iconT)

    override def shouldCull() = true

    override def registerIcons(reg:TextureMap): Unit =
    {
        def register(s:String) = reg.registerSprite(new ResourceLocation("projectred:blocks/fabrication/icworkbench/"+s))

        bottom = register("bottom")
        top = register("top")
        topBP = register("topbp")
        side1 = register("side1")
        side2 = register("side2")
        sidebp1 = register("sidebp1")
        sidebp2 = register("sidebp2")

        iconT = new MultiIconTransformation(bottom, top, side1, side1, side2, side2)
        iconTBP = new MultiIconTransformation(bottom, topBP, sidebp1, sidebp1, sidebp2, sidebp2)

        RenderICTileMap.registerIcons(reg)
    }
}
