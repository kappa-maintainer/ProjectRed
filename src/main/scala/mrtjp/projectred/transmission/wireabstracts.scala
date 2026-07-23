package mrtjp.projectred.transmission

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.{CuboidRayTraceResult, IndexedCuboid6}
import codechicken.lib.render.CCRenderState
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.{Cuboid6, Rotation, Vector3}
import codechicken.microblock.handler.MicroblockProxy
import codechicken.microblock.{ISidedHollowConnect, ItemMicroPart, MicroMaterialRegistry}
import codechicken.multipart.*
import mrtjp.projectred.ProjectRedCore
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.*
import IWirePart.*
import mrtjp.projectred.transmission.WireDef.WireDef
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{BlockRenderLayer, EnumFacing, EnumHand, SoundCategory}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*

trait TWireCommons extends TMultiPart with TConnectableCommons with TPropagationCommons with TSwitchPacket with TNormalOcclusionPart with TFastRenderPart
{
    def preparePlacement(side:Int, meta:Int): Unit ={}

    override def onPartChanged(part:TMultiPart): Unit =
    {
        if !world.isRemote then {
            WirePropagator.logCalculation()

            if updateOutward() then {
                onMaskChanged()
                WirePropagator.propagateTo(this, FORCE)
            }
            else WirePropagator.propagateTo(this, RISING)
        }
    }

    override def onNeighborChanged(): Unit =
    {
        if !world.isRemote then {
            if dropIfCantStay() then return
            WirePropagator.logCalculation()
            if updateExternalConns() then {
                onMaskChanged()
                WirePropagator.propagateTo(this, FORCE)
            }
            else WirePropagator.propagateTo(this, RISING)
        }
    }

    override def onAdded(): Unit =
    {
        super.onAdded()
        if !world.isRemote then {
            if updateInward() then onMaskChanged()
            WirePropagator.propagateTo(this, RISING)
        }
    }

    override def onRemoved(): Unit =
    {
        super.onRemoved()
        if !world.isRemote then notifyAllExternals()
    }

    def sendConnUpdate(): Unit 

    override def onMaskChanged(): Unit =
    {
        sendConnUpdate()
    }

    def canStay:Boolean

    def dropIfCantStay() =
    {
        if !canStay then {
            drop()
            true
        }
        else false
    }

    def drop(): Unit =
    {
        TileMultipart.dropItem(getItem, world, Vector3.fromTileCenter(tile))
        tile.remPart(this)
    }

    def getItem:ItemStack

    def getWireType:WireDef

    def getThickness = getWireType.thickness

    override def getDrops = Seq(getItem).asJava

    override def pickItem(hit:CuboidRayTraceResult) = getItem

    override def onSignalUpdate(): Unit =
    {
        tile.markDirty()
    }

    override def diminishOnSide(side:Int) = true

    def debug(player:EntityPlayer) = false

    def test(player:EntityPlayer) = false

    override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, held:ItemStack, hand:EnumHand) =
    {
        //if (CommandDebug.WIRE_READING) debug(player) else
        if !held.isEmpty && held.getItem == ProjectRedCore.itemMultimeter then {
            held.damageItem(1, player)
            player.swingArm(hand)
            test(player)
        }
        else false
    }

    def renderHue = -1

    @SideOnly(Side.CLIENT)
    def getIcon = getWireType.wireSprites(0)

    @SideOnly(Side.CLIENT)
    override def renderStatic(pos:Vector3, layer:BlockRenderLayer, ccrs:CCRenderState) =
    {
        if layer == getRenderLayer && useStaticRenderer then {
            ccrs.setBrightness(world, this.pos)
            doStaticTessellation(pos, layer, ccrs)
            true
        }
        else false
    }

    @SideOnly(Side.CLIENT)
    override def renderFast(ccrs:CCRenderState, pos:Vector3, pass:Int, frame:Float): Unit =
    {
        doFastTessellation(pos, frame, pass, ccrs)
    }

    override def canRenderFast(pass: Int) = pass == 0 && !useStaticRenderer

    @SideOnly(Side.CLIENT)
    override def renderBreaking(pos:Vector3, texture:TextureAtlasSprite, ccrs:CCRenderState): Unit =
    {
        ccrs.reset()
        doBreakTessellation(pos, texture, ccrs)
    }

    @SideOnly(Side.CLIENT)
    def getRenderLayer = BlockRenderLayer.SOLID

    @SideOnly(Side.CLIENT)
    def doStaticTessellation(pos:Vector3, layer:BlockRenderLayer, ccrs:CCRenderState): Unit 
    @SideOnly(Side.CLIENT)
    def doFastTessellation(pos:Vector3, frame:Float, pass:Int, ccrs:CCRenderState): Unit 
    @SideOnly(Side.CLIENT)
    def doBreakTessellation(pos:Vector3, texture:TextureAtlasSprite, ccrs:CCRenderState): Unit 

    def useStaticRenderer = Configurator.staticWires
}

abstract class WirePart extends TMultiPart with TWireCommons with TFaceConnectable with TFacePropagation
{
    override def preparePlacement(side:Int, meta:Int): Unit =
    {
        setSide(side^1)
    }

    override def save(tag:NBTTagCompound): Unit =
    {
        tag.setInteger("connMap", connMap)
        tag.setByte("side", side.toByte)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        connMap = tag.getInteger("connMap")
        setSide(tag.getByte("side"))
    }

    override def writeDesc(packet:MCDataOutput): Unit =
    {
        packet.writeInt(connMap)
        packet.writeByte(orientation)
    }

    override def readDesc(packet:MCDataInput): Unit =
    {
        connMap = packet.readInt()
        orientation = packet.readByte()
    }

    override def read(packet:MCDataInput, key:Int) = key match
    {
        case 1 =>
            connMap = packet.readInt()
            if useStaticRenderer then tile.markRender()
        case _ => super.read(packet, key)
    }

    override def sendConnUpdate(): Unit =
    {
        getWriteStreamOf(1).writeInt(connMap)
    }

    override def canConnectCorner(r:Int) = true

    override def canStay = PRLib.canPlaceWireOnSide(world,
        pos.offset(EnumFacing.byIndex(side)), side^1)

    override def getItem = getWireType.makeStack

    override def setRenderFlag(part:IConnectable) = part match
    {
        case w:WirePart =>
            if w.getThickness == getThickness then side < w.side else w.getThickness > getThickness
        case _ => true
    }

    override def discoverOpen(r:Int) =
    {
        if tile.partMap(PartMap.edgeBetween(side, absoluteDir(r))) != null then false
        else getInternal(r) match {
            case w:WirePart => canConnectPart(w, r)
            case t:TMultiPart => false
            case null => true
        }
    }

    override def getType = getWireType.wireType

    override def getStrength(player:EntityPlayer, hit:CuboidRayTraceResult) = 2/30f

    override def getSubParts = Seq(new IndexedCuboid6(0, WireBoxes.sBounds(getThickness)(side))).asJava

    override def getOcclusionBoxes = Seq(WireBoxes.oBounds(getThickness)(side)).asJava

    override def redstoneConductionMap = 0xF

    override def solid(side:Int) = false

    @SideOnly(Side.CLIENT)
    override def doBreakTessellation(pos:Vector3, texture:TextureAtlasSprite, ccrs:CCRenderState): Unit =
    {
        RenderWire.renderBreakingOverlay(texture, this, ccrs)
    }
    @SideOnly(Side.CLIENT)
    override def doFastTessellation(pos:Vector3, frame:Float, pass:Int, ccrs:CCRenderState): Unit =
    {
        RenderWire.render(this, pos, ccrs)
    }
    @SideOnly(Side.CLIENT)
    override def doStaticTessellation(pos:Vector3, layer:BlockRenderLayer, ccrs:CCRenderState): Unit =
    {
        RenderWire.render(this, pos, ccrs)
    }
}

abstract class FramedWirePart extends TMultiPart with TWireCommons with TCenterConnectable with TCenterPropagation with ISidedHollowConnect
{
    var hasMaterial = false
    var material = 0

    override def save(tag:NBTTagCompound): Unit =
    {
        tag.setInteger("connMap", connMap)
        tag.setString("mat", MicroMaterialRegistry.materialName(material))
        tag.setBoolean("hasmat", hasMaterial)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        connMap = tag.getInteger("connMap")
        hasMaterial = tag.getBoolean("hasmat")
        material = MicroMaterialRegistry.materialID(tag.getString("mat"))
    }

    override def writeDesc(packet:MCDataOutput): Unit =
    {
        packet.writeByte(clientConnMap)
        packet.writeBoolean(hasMaterial)
        if hasMaterial then
            MicroMaterialRegistry.writeMaterialID(packet, material)
    }

    override def readDesc(packet:MCDataInput): Unit =
    {
        connMap = packet.readUByte()
        hasMaterial = packet.readBoolean()
        if hasMaterial then
            material = MicroMaterialRegistry.readMaterialID(packet)
    }

    override def read(packet:MCDataInput, key:Int) = key match
    {
        case 1 =>
            connMap = packet.readUByte()
            if useStaticRenderer then tile.markRender()
        case 2 =>
            hasMaterial = true
            material = MicroMaterialRegistry.readMaterialID(packet)
            if useStaticRenderer then tile.markRender()
        case 3 =>
            hasMaterial = false
            material = 0
            if useStaticRenderer then tile.markRender()
        case _ =>
    }

    def clientConnMap = connMap&0x3F|connMap>>6&0x3F

    override def sendConnUpdate(): Unit =
    {
        getWriteStreamOf(1).writeByte(clientConnMap)
    }

    def sendMatUpdate(): Unit =
    {
        if hasMaterial then MicroMaterialRegistry.writeMaterialID(getWriteStreamOf(2), material)
        else getWriteStreamOf(3)
    }

    override def discoverOpen(s:Int) = getInternal(s) match
    {
        case null => true
        case w:WirePart if canConnectPart(w, s) => true
        case _ =>
            WireBoxes.expandBounds = s
            val fits = tile.canReplacePart(this, this)
            WireBoxes.expandBounds = -1
            fits
    }

    override def getType = getWireType.framedType

    override def canStay = true

    override def getStrength(player:EntityPlayer, hit:CuboidRayTraceResult) =
    {
        if hasMaterial then Math.min(1.25f/30f, MicroMaterialRegistry.getMaterial(material).getStrength(player))
        else 1.25f/30f
    }

    override def getItem = getWireType.makeFramedStack

    override def getDrops =
    {
        if hasMaterial then (super.getDrops.asScala ++ Iterable.single(ItemMicroPart.create(1, material))).asJava
        else super.getDrops
    }

    override def getSubParts = getCollisionBoxes.asScala.map(that => new IndexedCuboid6(0, that)).asJava

    override def getOcclusionBoxes =
    {
        import mrtjp.projectred.transmission.WireBoxes.*
        if expandBounds >= 0 then Seq(fOBounds(expandBounds)).asJava
        else Seq(fOBounds(6)).asJava
    }

    override def getCollisionBoxes =
    {
        import mrtjp.projectred.transmission.WireBoxes.*
        var b = Seq.newBuilder[Cuboid6].+=(fOBounds(6))
        for s <- 0 until 6 do if maskConnects(s) then b += fOBounds(s)
        b.result().asJava
    }

    override def getHollowSize(side:Int) = 8

    override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, held:ItemStack, hand:EnumHand):Boolean =
    {
        def dropMaterial(): Unit =
        {
            if hasMaterial && !player.capabilities.isCreativeMode then
                PRLib.dropTowardsPlayer(world, pos, ItemMicroPart.create(1, material), player)
        }

        if super.activate(player, hit, held, hand) then return true

        if held.isEmpty && player.isSneaking && hasMaterial then {
            if !world.isRemote then {
                dropMaterial()
                hasMaterial = false
                material = 0
                sendMatUpdate()
            }
            return true
        }

        if !held.isEmpty && held.getItem == MicroblockProxy.itemMicro && held.getItemDamage == 1 then {
            val newmatid = ItemMicroPart.getMaterialID(held)
            if !hasMaterial || newmatid != material then {
                if !world.isRemote then {
                    val newmat = MicroMaterialRegistry.getMaterial(newmatid)
                    if newmat == null || newmat.isTransparent then return false
                    else {
                        dropMaterial()
                        hasMaterial = true
                        material = newmatid
                        world.playSound(null, pos, newmat.getSound.getPlaceSound,
                            SoundCategory.BLOCKS, newmat.getSound.getVolume+1.0F/2.0F,
                            newmat.getSound.getPitch*0.8F)
                        sendMatUpdate()
                        if !player.capabilities.isCreativeMode then held.shrink(1)
                    }
                }
                return true
            }
        }

        false
    }

    @SideOnly(Side.CLIENT)
    override def getRenderLayer = BlockRenderLayer.CUTOUT

    @SideOnly(Side.CLIENT)
    override def doBreakTessellation(pos:Vector3, texture:TextureAtlasSprite, ccrs:CCRenderState): Unit =
    {
        RenderFramedWire.renderBreakingOverlay(texture, this, ccrs)
    }
    @SideOnly(Side.CLIENT)
    override def doFastTessellation(pos:Vector3, frame:Float, pass:Int, ccrs:CCRenderState): Unit =
    {
        RenderFramedWire.render(this, pos, ccrs)
    }
    @SideOnly(Side.CLIENT)
    override def doStaticTessellation(pos:Vector3, layer:BlockRenderLayer, ccrs:CCRenderState): Unit =
    {
        RenderFramedWire.render(this, pos, ccrs)
    }
}

object WireBoxes
{
    var sBounds = Array.ofDim[Cuboid6](3, 6)
    var oBounds = Array.ofDim[Cuboid6](3, 6)

    for t <- 0 until 3 do {
        val selection = new Cuboid6(0, 0, 0, 1, (t+2)/16D, 1).expand(-0.005)
        val occlusion = new Cuboid6(2/8D, 0, 2/8D, 6/8D, (t+2)/16D, 6/8D)
        for s <- 0 until 6 do {
            sBounds(t)(s) = selection.copy.apply(Rotation.sideRotations(s).at(Vector3.center))
            oBounds(t)(s) = occlusion.copy.apply(Rotation.sideRotations(s).at(Vector3.center))
        }
    }

    var fOBounds = {
        val boxes = new Array[Cuboid6](7)
        val w = 2/8D
        boxes(6) = new Cuboid6(0.5-w, 0.5-w, 0.5-w, 0.5+w, 0.5+w, 0.5+w)
        for s <- 0 until 6 do
            boxes(s) = new Cuboid6(0.5-w, 0, 0.5-w, 0.5+w, 0.5-w, 0.5+w).apply(Rotation.sideRotations(s).at(Vector3.center))
        boxes
    }
    var expandBounds = -1
}
