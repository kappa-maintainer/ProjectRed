package mrtjp.projectred.illumination

import java.lang.{Boolean as JBool, Integer as JInt}
import java.util.{List as JList}

import codechicken.lib.block.property.unlisted.{UnlistedBooleanProperty, UnlistedIntegerProperty}
import codechicken.lib.render.particle.CustomParticleHandler
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.model.bakery.generation.IBakery
import codechicken.lib.model.bakery.{IBakeryProvider, ModelBakery, SimpleBlockRenderer}
import codechicken.lib.render.CCRenderState
import codechicken.lib.render.item.IItemRenderer
import codechicken.lib.util.TransformUtils
import codechicken.lib.vec.{Cuboid6, RedundantTransformation, Rotation}
import codechicken.lib.vec.uv.MultiIconTransformation
import mrtjp.core.block.{MTBlockTile, MultiTileBlock, TTileOrient}
import mrtjp.projectred.ProjectRedIllumination
import mrtjp.projectred.api.{IBundledEmitter, IBundledTile, IConnectable, IMaskedBundledTile}
import mrtjp.projectred.core.{RenderHalo, TConnectableInstTile}
import mrtjp.projectred.transmission.{APIImpl_Transmission, BundledCommons}
import net.minecraft.block.material.Material
import net.minecraft.block.state.{BlockStateContainer, IBlockState}
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.{BakedQuad, ItemCameraTransforms}
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.client.particle.ParticleManager
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.{EntityLivingBase}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.util.math.{BlockPos, RayTraceResult}
import net.minecraft.world.{IBlockAccess, World, WorldServer}
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.opengl.GL11

import scala.jdk.CollectionConverters.*

class BlockSmartLamp extends MultiTileBlock(Material.REDSTONE_LIGHT) with IBakeryProvider
:
    setHardness(0.5F)
    setCreativeTab(ProjectRedIllumination.tabLighting)

    override def isBlockNormalCube(state:IBlockState) = true
    override def isOpaqueCube(state:IBlockState) = true
    override def isFullCube(state:IBlockState) = true
    override def isFullBlock(state:IBlockState) = true

    override def createBlockState(): BlockStateContainer = new BlockStateContainer.Builder(this)
        .add(MultiTileBlock.TILE_INDEX)
        .add(SmartLampProperties.ON, SmartLampProperties.SIDE)
        .build()

    override def getExtendedState(state:IBlockState, world:IBlockAccess, pos:BlockPos): IBlockState =
        ModelBakery.handleExtendedState(state.asInstanceOf[IExtendedBlockState], world, pos)

    @SideOnly(Side.CLIENT)
    override def getBakery:IBakery = SmartLampRenderer

    override def addLandingEffects(state:IBlockState, world:WorldServer, pos:BlockPos, actualState:IBlockState, entity:EntityLivingBase, count:Int):Boolean =
        CustomParticleHandler.handleLandingEffects(world, pos, entity, count)

    @SideOnly(Side.CLIENT)
    override def addHitEffects(state:IBlockState, world:World, target:RayTraceResult, manager:ParticleManager):Boolean =
        CustomParticleHandler.handleHitEffects(state, world, target, manager)

    @SideOnly(Side.CLIENT)
    override def addDestroyEffects(world:World, pos:BlockPos, manager:ParticleManager):Boolean =
        CustomParticleHandler.handleDestroyEffects(world, pos, manager)

object SmartLampProperties:
    val ON = new UnlistedBooleanProperty("on")
    val SIDE = new UnlistedIntegerProperty("side")

object SmartLampRenderer extends SimpleBlockRenderer
:
    val glowBounds = Cuboid6.full.copy.expand(0.05D)

    private var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    private var topOff:TextureAtlasSprite = scala.compiletime.uninitialized
    private var topOn:TextureAtlasSprite = scala.compiletime.uninitialized
    private var sideOff:TextureAtlasSprite = scala.compiletime.uninitialized
    private var sideOn:TextureAtlasSprite = scala.compiletime.uninitialized
    private var offIcons:MultiIconTransformation = scala.compiletime.uninitialized
    private var onIcons:MultiIconTransformation = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState, world:IBlockAccess, pos:BlockPos): IExtendedBlockState =
        world.getTileEntity(pos) match
            case lamp:TileSmartLamp => state
                .withProperty(SmartLampProperties.ON, lamp.isOn.asInstanceOf[JBool])
                .withProperty(SmartLampProperties.SIDE, lamp.side.asInstanceOf[JInt])
            case _ => state

    override def getWorldTransforms(state:IExtendedBlockState) =
        val side = state.getValue(SmartLampProperties.SIDE)
        val on = state.getValue(SmartLampProperties.ON).asInstanceOf[Boolean]
        org.apache.commons.lang3.tuple.Triple.of(side, 0, if on then onIcons else offIcons)

    override def getItemTransforms(stack:ItemStack) =
        org.apache.commons.lang3.tuple.Triple.of(0, 0, onIcons)

    override def shouldCull() = true
    override def getParticleTexture(state:IExtendedBlockState) = bottom

    override def registerIcons(map:TextureMap): Unit =
        def icon(name:String) = map.registerSprite(new ResourceLocation(s"projectred:blocks/lighting/illumar_smart_lamp_$name"))
        bottom = icon("bottom")
        topOff = icon("top")
        topOn = icon("top_on")
        sideOff = icon("side")
        sideOn = icon("side_on")
        offIcons = new MultiIconTransformation(bottom, topOff, sideOff, sideOff, sideOff, sideOff)
        onIcons = new MultiIconTransformation(bottom, topOn, sideOn, sideOn, sideOn, sideOn)

class SmartLampHaloRenderer extends TileEntitySpecialRenderer[TileSmartLamp]
:
    override def render(tile:TileSmartLamp, x:Double, y:Double, z:Double, partialTicks:Float, destroyStage:Int, alpha:Float): Unit =
        if tile.isOn then RenderHalo.addMultiLight(tile.getPos, SmartLampRenderer.glowBounds, tile.signal)

object SmartLampHaloRenderer:
    val instance = new SmartLampHaloRenderer

object SmartLampItemRenderer extends IItemRenderer
:
    private val itemGlowBounds = Cuboid6.full.copy.expand(0.02D)
    private val itemSignal = new Array[Byte](16)
    private var lastAnimationTick = Long.MinValue

    override def isAmbientOcclusion = true
    override def isGui3d = true
    override def getTransforms = TransformUtils.DEFAULT_BLOCK

    override def renderItem(item:ItemStack, transformType:ItemCameraTransforms.TransformType): Unit =
        val ccrs = CCRenderState.instance()
        ccrs.reset()
        ccrs.pullLightmap()
        ccrs.startDrawing(GL11.GL_QUADS, DefaultVertexFormats.ITEM)

        val model = ModelBakery.getCachedItemModel(item)
        renderQuads(model.getQuads(null, null, 0), ccrs)
        for face <- EnumFacing.VALUES do renderQuads(model.getQuads(null, face, 0), ccrs)
        ccrs.draw()

        animateSignal()
        RenderHalo.renderInventoryMultiHalo(itemGlowBounds, itemSignal, new RedundantTransformation)

    private def animateSignal(): Unit =
        val tick = Option(Minecraft.getMinecraft.world).map(_.getTotalWorldTime).getOrElse(System.currentTimeMillis() / 50L)
        if tick == lastAnimationTick then return
        lastAnimationTick = tick
        val active = (math.sin(tick.toDouble / 200.0D) + 1.0D) / 2.0D * 15.0D
        val width = 1.5D
        for i <- itemSignal.indices do
            val difference = math.min(math.abs(active - i), width)
            itemSignal(i) = (255.0D * (1.0D - difference / width)).toByte

    private def renderQuads(quads:JList[BakedQuad], ccrs:CCRenderState): Unit =
        for quad <- quads.asScala do ccrs.getBuffer.addVertexData(quad.getVertexData)

class TileSmartLamp extends MTBlockTile with TTileOrient with TConnectableInstTile with IMaskedBundledTile
:
    val signal = new Array[Byte](16)

    override def getBlock = ProjectRedIllumination.blockSmartLamp

    def isOn = signal.exists(_ != 0)

    def lightLevel =
        var maximum = 0
        for value <- signal do maximum = math.max(maximum, value & 0xFF)
        maximum / 17

    override def getLightValue = lightLevel
    override def getBundledSignal(dir:Int):Array[Byte] = null

    override def canConnectBundled(s:Int) = s != (side ^ 1)

    override def getConnectionMask(s:Int) =
        if s == side then 0x1F
        else if s == (side ^ 1) then 0
        else 1 << Rotation.rotationTo(s ^ 1, side)

    override def canConnectPart(part:IConnectable, s:Int, edgeRot:Int) = part match
        case _:IBundledEmitter if s == side => true
        case _:IBundledEmitter if s != (side ^ 1) => edgeRot == Rotation.rotationTo(s ^ 1, side)
        case _ => false

    override def discoverStraightOverride(s:Int):Boolean =
        val target = posOfInternal.offset(EnumFacing.VALUES(s))
        world.getTileEntity(target) match
            case tile:IMaskedBundledTile => tile.canConnectBundled(s ^ 1) && (tile.getConnectionMask(s ^ 1) & 0x10) != 0
            case tile:IBundledTile => tile.canConnectBundled(s ^ 1)
            case _ => APIImpl_Transmission.canConnectBundled(world, target, EnumFacing.VALUES(s ^ 1))

    override def onBlockPlaced(clickedSide:Int, player:EntityPlayer, stack:ItemStack): Unit =
        setSide(clickedSide ^ 1)
        super.onBlockPlaced(clickedSide, player, stack)
        if !world.isRemote then checkSignal()

    override def onNeighborBlockChange(): Unit =
        super.onNeighborBlockChange()
        if !world.isRemote then checkSignal()

    private def checkSignal(): Unit =
        val next = calcBundledInput()
        if !BundledCommons.signalsEqual(signal, next) then
            Array.copy(next, 0, signal, 0, signal.length)
            markDirty()
            markDescUpdate()
            markLight()
            markRender()

    private def calcBundledInput():Array[Byte] =
        val result = new Array[Byte](16)
        for s <- 0 until 6 do
            if s != (side ^ 1) then
                if s == side then
                    if maskConnectsStraightCenter(s) then raise(result, centerSignal(s))
                    for r <- 0 until 4 do
                        if maskConnectsStraight(s, r) then raise(result, straightSignal(s, r))
                        else if maskConnectsCorner(s, r) then raise(result, cornerSignal(s, r))
                else
                    val r = Rotation.rotationTo(s ^ 1, side)
                    if maskConnectsStraight(s, r) then raise(result, straightSignal(s, r))
                    else if maskConnectsCorner(s, r) then raise(result, cornerSignal(s, r))
        result

    private def raise(result:Array[Byte], source:Array[Byte]): Unit =
        BundledCommons.raiseSignal(result, source)

    private def centerSignal(s:Int):Array[Byte] =
        getStraightCenter(s) match
            case emitter:IBundledEmitter => emitter.getBundledSignal(s ^ 1)
            case _ => tileSignal(posOfInternal.offset(EnumFacing.VALUES(s)), s ^ 1)

    private def straightSignal(s:Int, r:Int):Array[Byte] =
        getStraight(s, r) match
            case emitter:IBundledEmitter => emitter.getBundledSignal(rotFromStraight(s, r))
            case _ => tileSignal(posOfStraight(s), s ^ 1)

    private def cornerSignal(s:Int, r:Int):Array[Byte] =
        getCorner(s, r) match
            case emitter:IBundledEmitter => emitter.getBundledSignal(rotFromCorner(s, r))
            case _ => tileSignal(posOfCorner(s, r), Rotation.rotateSide(s ^ 1, r))

    private def tileSignal(target:BlockPos, dir:Int):Array[Byte] = world.getTileEntity(target) match
        case tile:IBundledTile => tile.getBundledSignal(dir)
        case _:TileEntity if APIImpl_Transmission.isValidInteractionFor(world, target, EnumFacing.VALUES(dir)) =>
            APIImpl_Transmission.getBundledSignal(world, target, EnumFacing.VALUES(dir))
        case _ => null

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setByte("orientation", orientation)
        tag.setByteArray("signal", signal)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        orientation = tag.getByte("orientation")
        val loaded = tag.getByteArray("signal")
        if loaded.length == signal.length then Array.copy(loaded, 0, signal, 0, signal.length)

    override def writeDesc(out:MCDataOutput): Unit =
        super.writeDesc(out)
        out.writeByte(orientation)
        for value <- signal do out.writeByte(value)

    override def readDesc(in:MCDataInput): Unit =
        super.readDesc(in)
        orientation = in.readByte()
        for i <- signal.indices do signal(i) = in.readByte()
        markLight()
        markRender()
