package mrtjp.projectred.transportation

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.{CuboidRayTraceResult, IndexedCuboid6}
import codechicken.lib.render.CCRenderState
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.{Cuboid6, Rotation, Vector3}
import codechicken.microblock.ISidedHollowConnect
import codechicken.multipart.*
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.ItemKey
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.*
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.util.{BlockRenderLayer, EnumFacing, ITickable}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*

abstract class SubcorePipePart extends TMultiPart with TCenterConnectable with TSwitchPacket with TNormalOcclusionPart with ISidedHollowConnect with TDynamicRenderPart
{
    var meta:Byte = 0

    def preparePlacement(side:Int, meta:Int): Unit =
    {
        this.meta = meta.asInstanceOf[Byte]
    }

    override def save(tag:NBTTagCompound): Unit =
    {
        tag.setInteger("connMap", connMap)
        tag.setByte("meta", meta)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        connMap = tag.getInteger("connMap")
        meta = tag.getByte("meta")
    }

    override def writeDesc(packet:MCDataOutput): Unit =
    {
        packet.writeByte(clientConnMap)
        packet.writeByte(meta)
    }

    override def readDesc(packet:MCDataInput): Unit =
    {
        connMap = packet.readUByte()
        meta = packet.readByte()
    }

    override def read(packet:MCDataInput, key:Int) = key match
    {
        case 1 =>
            connMap = packet.readUByte()
            tile.markRender()
        case _ =>
    }

    def clientConnMap = connMap&0x3F|connMap>>6&0x3F

    def sendConnUpdate(): Unit =
    {
        getWriteStreamOf(1).writeByte(clientConnMap)
    }

    override def discoverOpen(s:Int) = getInternal(s) match
    {
        case null => true
        case _ =>
            PipeBoxes.expandBounds = s
            val fits = tile.canReplacePart(this, this)
            PipeBoxes.expandBounds = -1
            fits
    }

    override def discoverInternal(s:Int) = false

    override def onPartChanged(part:TMultiPart): Unit =
    {
        if !world.isRemote then if updateOutward() then onMaskChanged()
    }

    override def onNeighborChanged(): Unit =
    {
        if !world.isRemote then if updateExternalConns() then onMaskChanged()
    }

    override def onAdded(): Unit =
    {
        super.onAdded()
        if !world.isRemote then if updateInward() then onMaskChanged()
    }

    override def onRemoved(): Unit =
    {
        super.onRemoved()
        if !world.isRemote then notifyAllExternals()
    }

    override def onMaskChanged(): Unit =
    {
        sendConnUpdate()
    }

    def getItem = getPipeType.makeStack
    def getPipeType = PipeDefs.fromMeta(meta)

    def getType = getPipeType.partname

    override def getStrength(player:EntityPlayer, hit:CuboidRayTraceResult) = 2/30f

    override def getDrops = Seq(getItem).asJava

    override def pickItem(hit:CuboidRayTraceResult) = getItem

    override def getHollowSize(side:Int) = 8

    override def getSubParts =
    {
        import mrtjp.projectred.transportation.PipeBoxes.*
        var boxes = Seq(new IndexedCuboid6(-1, oBounds(6)))
        for s <- 0 until 6 do if maskConnects(s) then boxes :+= new IndexedCuboid6(s, oBounds(s))
        boxes.asJava
    }

    override def getOcclusionBoxes =
    {
        import mrtjp.projectred.transportation.PipeBoxes.*
        if expandBounds >= 0 then Seq(oBounds(expandBounds)).asJava
        else Seq(oBounds(6)).asJava
    }

    override def getCollisionBoxes =
    {
        import mrtjp.projectred.transportation.PipeBoxes.*
        var boxes = Seq(oBounds(6))
        for s <- 0 until 6 do if maskConnects(s) then boxes :+= oBounds(s)
        boxes.asJava
    }

    @SideOnly(Side.CLIENT)
    override def renderBreaking(pos:Vector3, texture:TextureAtlasSprite, ccrs:CCRenderState): Unit =
    {
        RenderPipe.renderBreakingOverlay(texture, this, ccrs)
    }

    override def renderStatic(pos:Vector3, layer:BlockRenderLayer, ccrs:CCRenderState) =
    {
        if layer == BlockRenderLayer.CUTOUT then {
            ccrs.setBrightness(world, this.pos)
            doStaticTessellation(pos, ccrs)
            true
        }
        else false
    }

    @SideOnly(Side.CLIENT)
    override def renderDynamic(pos:Vector3, pass:Int, frame:Float): Unit =
    {
        TextureUtils.bindBlockTexture()
        doDynamicTessellation(pos, frame, CCRenderState.instance())

    }

    override def canRenderDynamic(pass: Int) = pass == 0

    @SideOnly(Side.CLIENT)
    def getIcon(side:Int) = getPipeType.sprites(0)

    @SideOnly(Side.CLIENT)
    def doStaticTessellation(pos:Vector3, ccrs:CCRenderState): Unit =
    {
        RenderPipe.renderPipe(this, pos, ccrs)
    }

    @SideOnly(Side.CLIENT)
    def doDynamicTessellation(pos:Vector3, frame:Float, ccrs:CCRenderState): Unit ={}
}

object PipeBoxes
{
    var oBounds =
    {
        val boxes = new Array[Cuboid6](7)
        val w = 2/8D
        boxes(6) = new Cuboid6(0.5-w, 0.5-w, 0.5-w, 0.5+w, 0.5+w, 0.5+w)
        for s <- 0 until 6 do
            boxes(s) = new Cuboid6(0.5-w, 0, 0.5-w, 0.5+w, 0.5-w, 0.5+w).apply(Rotation.sideRotations(s).at(Vector3.center))
        boxes
    }
    var expandBounds = -1
}

trait TPipeTravelConditions
{
    /**
     * 00FT
     * T - can travel to
     * F - can come from
     */
    def getPathFlags(input:Int, output:Int) = 0x3

    def getPathWeight = 1

    def itemsExclude = true
    def filteredItems:Set[ItemKey] = Set.empty

    def colorExclude = true
    def filteredColors = 0

    def pathFilter:PathFilter = pathFilter(-1, -1)
    def pathFilter(inputDir:Int, outputDir:Int):PathFilter =
    {
        val f = new PathFilter
        if inputDir != -1 && outputDir != -1 then
            f.pathFlags = getPathFlags(inputDir, outputDir)

        f.filterExclude = itemsExclude
        f.itemFilter = filteredItems

        f.colorExclude = colorExclude
        f.colors = filteredColors
        f
    }
}

abstract class PayloadPipePart[T <: AbstractPipePayload] extends SubcorePipePart with TPipeTravelConditions with ITickable
{
    val itemFlow = new PayloadMovement[T]

    private implicit def payloadToT(p:AbstractPipePayload):T = p.asInstanceOf[T]

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        val nbttaglist = new NBTTagList
        for r <- itemFlow.it do
        {
            val payloadData = new NBTTagCompound
            nbttaglist.appendTag(payloadData)
            r.save(payloadData)
        }
        tag.setTag("itemFlow", nbttaglist)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        val nbttaglist = tag.getTagList("itemFlow", 10)
        for j <- 0 until nbttaglist.tagCount do
        {
            try
            {
                val payloadData = nbttaglist.getCompoundTagAt(j)
                val r = createNewPayload(AbstractPipePayload.claimID())
                r.bind(this)
                r.load(payloadData)
                if !r.isCorrupted then itemFlow.scheduleLoad(r)
            }
            catch {case t:Throwable =>}
        }
    }

    override def read(packet:MCDataInput, key:Int) = key match
    {
        case 4 => handleItemUpdatePacket(packet)
        case _ => super.read(packet, key)
    }

    override def update(): Unit =
    {
        pushItemFlow()
    }

    def pushItemFlow(): Unit =
    {
        itemFlow.executeLoad()
        itemFlow.exececuteRemove()
        for r <- itemFlow.it do if r.isCorrupted then itemFlow.scheduleRemoval(r)
        else {
            r.moveProgress(r.speed)
            if r.isEntering && hasReachedMiddle(r) then
            {
                r.isEntering = false
                if r.output == 6 then handleDrop(r)
                else centerReached(r)
            }
            else if !r.isEntering && hasReachedEnd(r) then
                if itemFlow.scheduleRemoval(r) then endReached(r)
        }
        itemFlow.exececuteRemove()
    }

    def handleDrop(r:T): Unit =
    {
        if itemFlow.scheduleRemoval(r) then if !world.isRemote then
        {
            r.preItemRemove()
            world.spawnEntity(r.getEntityForDrop(pos))
        }
    }

    def resolveDestination(r:T): Unit =
    {
        chooseRandomDestination(r)
    }

    def chooseRandomDestination(r:T): Unit =
    {
        chooseRandomDestination(r, 0)
    }

    def chooseRandomDestination(r:T, mask:Int): Unit =
    {
        var moves = Seq[Int]()
        for i <- 0 until 6 do
            if (connMap&1<<i) != 0 && i != (r.input^1) && (mask&1<<i) == 0 then moves :+= i
        if moves.isEmpty then r.output = r.input^1
        else r.output = moves(world.rand.nextInt(moves.size))
    }

    def endReached(r:T): Unit =
    {
        if !world.isRemote then
        {
            if !(maskConnects(r.output) && passPayload(r)) then
                if r.payload.stackSize > 0 then bounceStack(r)
        }
    }

    def passPayload(r:T):Boolean =
    {
        if passToInventory(r) then return true

        if passToNextPipe(r) then return true

        false
    }

    def passToNextPipe(r:T) =
    {
        getStraight(r.output) match
        {
            case pipe:PayloadPipePart[T] =>
                pipe.injectPayload(r, r.output)
                true
            case _ => false
        }
    }

    def passToInventory(r:T) =
    {
        val w = InvWrapper.wrap(world, posOfStraight(r.output), EnumFacing.VALUES(r.output^1))
        if w != null then
        {
            r.payload.stackSize -= w.injectItem(r.payload.key, r.payload.stackSize)
            r.payload.stackSize == 0
        }
        else false
    }

    def bounceStack(r:T): Unit =
    {
        itemFlow.unscheduleRemoval(r)
        r.isEntering = true
        r.input = r.output^1
        r.progress = 0
        resolveDestination(r)
        adjustSpeed(r)
        if !world.isRemote then sendItemUpdate(r)
    }

    def centerReached(r:T): Unit =
    {
        if !maskConnects(r.output) && !world.isRemote then
        {
            resolveDestination(r)
            sendItemUpdate(r)
        }
    }

    def adjustSpeed(r:T): Unit ={}

    protected def hasReachedMiddle(r:T) = r.progress >= 0.5F

    protected def hasReachedEnd(r:T) = r.progress >= 1.0F

    def injectPayload(r:T, in:Int): Unit =
    {
        if r.isCorrupted then return
        if itemFlow.delegate.contains(r) then return
        r.bind(this)
        r.reset()
        r.input = in
        itemFlow.add(r)

        adjustSpeed(r)
        if r.progress > 0.0F then r.progress = Math.max(0, r.progress-1.0F)

        if !world.isRemote then
        {
            resolveDestination(r)
            sendItemUpdate(r)
        }
    }

    override def onNeighborChanged(): Unit =
    {
        super.onNeighborChanged()
        val connCount = Integer.bitCount(connMap)

        if connCount == 0 then if !world.isRemote then for r <- itemFlow.it do if itemFlow.scheduleRemoval(r) then
        {
            r.preItemRemove()
            world.spawnEntity(r.getEntityForDrop(pos))
        }
    }

    override def onRemoved(): Unit =
    {
        super.onRemoved()
        if !world.isRemote then for r <- itemFlow.it do
        {
            r.preItemRemove()
            world.spawnEntity(r.getEntityForDrop(pos))
        }
    }

    def sendItemUpdate(r:T): Unit =
    {
        val out = getWriteStreamOf(4)
        out.writeShort(r.payloadID)
        r.writeDesc(out)
    }

    def handleItemUpdatePacket(packet:MCDataInput): Unit =
    {
        val id = packet.readShort()
        val r = itemFlow.getOrElseUpdate(id, _ => createNewPayload(id))
        r.readDesc(packet)
    }

    def createNewPayload(id:Int):T

    @SideOnly(Side.CLIENT)
    override def doDynamicTessellation(pos:Vector3, frame:Float, ccrs:CCRenderState): Unit =
    {
        super.doDynamicTessellation(pos, frame, ccrs)
        RenderPipe.renderItemFlow(this, pos, frame, ccrs)
    }

    override def canConnectPart(part:IConnectable, s:Int) = false
}
