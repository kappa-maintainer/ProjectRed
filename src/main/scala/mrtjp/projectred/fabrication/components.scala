/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.math.MathHelper
import codechicken.lib.render.pipeline.{ColourMultiplier, IVertexOperation}
import codechicken.lib.render.{CCModel, CCRenderState}
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.*
import codechicken.lib.vec.uv.{IconTransformation, UVTransformation}
import mrtjp.core.vec.Size
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

object ICComponentStore
:
    val faceModels =
        val m = CCModel.quadModel(4)
        m.generateBlock(0, new Cuboid6(0, 0, 0, 1, 0, 1), ~1)
        m.computeNormals
        m.shrinkUVs(0.0005)
        Seq(m, m.backfacedCopy)

    var pref:TextureAtlasSprite = scala.compiletime.uninitialized
    var prefCorner:TextureAtlasSprite = scala.compiletime.uninitialized
    var prefEdge:TextureAtlasSprite = scala.compiletime.uninitialized

    var torchOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var torchOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var leverOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var leverOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var buttonOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var buttonOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized

    val redwireIcons = new Array[TextureAtlasSprite](16)
    val insulatedwireIcons = new Array[TextureAtlasSprite](16)
    val bundledwireIcons = new Array[TextureAtlasSprite](16)
    var bundledColourIcon:TextureAtlasSprite = scala.compiletime.uninitialized

    var ioBorder:TextureAtlasSprite = scala.compiletime.uninitialized
    var ioSig:TextureAtlasSprite = scala.compiletime.uninitialized
    var tLeverOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var tLeverOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var redChipOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var redChipOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var yellowChipOnIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var yellowChipOffIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var pointerIcon:TextureAtlasSprite = scala.compiletime.uninitialized

    var cellStandIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var nullCellWireBottomIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var nullCellWireTopIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var invertCellWireBottomIcon:TextureAtlasSprite = scala.compiletime.uninitialized

    def registerIcons(reg:TextureMap): Unit =
        def register(path:String) = reg.registerSprite(new ResourceLocation("projectred:blocks/fabrication/"+path))

        pref = register("prefboard")
        prefCorner = register("prefboard_corner")
        prefEdge = register("prefboard_edge")

        for i <- 0 until 16 do redwireIcons(i) = register("alloywire/conn"+i)
        for i <- 0 until 16 do insulatedwireIcons(i) = register("insulatedwire/conn"+i)
        for i <- 0 until 16 do bundledwireIcons(i) = register("bundledcable/conn"+i)
        bundledColourIcon = register("bundledcable/col")

        torchOffIcon = register("torch_off")
        torchOnIcon = register("torch_on")
        leverOffIcon = register("lever_off")
        leverOnIcon = register("lever_on")
        buttonOffIcon = register("button_off")
        buttonOnIcon = register("button_on")

        RenderGateTile.registerIcons(reg)

        for m <- WireModel.wireModels do m.icon = register("surface/"+m.iconPath)
        for m <- BaseComponentModel.baseModels do m.icon = register("surface/"+m.iconPath+"/base")

        ioBorder = register("io_freq")
        ioSig = register("io_sig")
        tLeverOffIcon = register("small_lever_off")
        tLeverOnIcon = register("small_lever_on")
        redChipOnIcon = register("redchip_on")
        redChipOffIcon = register("redchip_off")
        yellowChipOnIcon = register("yellowchip_on")
        yellowChipOffIcon = register("yellowchip_off")
        pointerIcon = register("pointer")

        cellStandIcon = register("cell_stand")
        nullCellWireBottomIcon = register("bottom_null_cell_wire")
        nullCellWireTopIcon = register("top_null_cell_wire")
        invertCellWireBottomIcon = register("bottom_invert_cell_wire")

    def prepairRender(ccrs:CCRenderState): Unit =
        TextureUtils.bindBlockTexture()
        ccrs.reset()
        ccrs.startDrawing(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR)
        ccrs.pullLightmap()

    def finishRender(ccrs:CCRenderState): Unit =
        ccrs.draw()

    def generateWireModels(name:String, count:Int) =
        val xs = Seq.newBuilder[WireModel]
        for i <- 0 until count do
            xs += new WireModel(name+"/"+name+"-"+i)
        xs.result()

    def orthoGridT(xSize:Double, ySize:Double) =
        new Scale(xSize, 1, -ySize) `with` new Rotation(0.5*MathHelper.pi, 1, 0, 0)

    def orthoPartT(x:Double, y:Double, xSize:Double, ySize:Double, boardSize:Size, xPoint:Int, yPoint:Int) =
        new TransformationList(
            new Scale(1.0/boardSize.width, 1, 1.0/boardSize.height),
            new Translation(new Vector3(xPoint, 0, yPoint).multiply(1.0/boardSize.width, 0, 1.0/boardSize.height)),
            orthoGridT(xSize, ySize),
            new Translation(x, y, 0)
        )

    def dynamicT(orient:Int) =
        if orient == 0 then new RedundantTransformation
        else new Scale(-1, 0, 1).at(Vector3.center)

    def dynamicIdx(orient:Int, ortho:Boolean) = orient^(if ortho then 1 else 0)

    def signalColour(signal:Byte) = (signal&0xFF)/2+60<<24|0xFF

import mrtjp.projectred.fabrication.ICComponentStore.*

abstract class ICComponentModel
:
    def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit 

abstract class SingleComponentModel(pos:Vector3 = Vector3.zero) extends ICComponentModel
:
    val models = faceModels.map(_.copy.apply(new Translation(-0.5, 0, -0.5)).apply(pos.copy.multiply(1/16D).translation))

    def getUVT:UVTransformation

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        models(dynamicIdx(orient, ortho)).render(ccrs, dynamicT(orient) `with` t, getUVT)

abstract class CenteredSingleComponentModel extends ICComponentModel
:
    def getUVT:UVTransformation

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        faceModels(dynamicIdx(orient, ortho)).render(ccrs, dynamicT(orient) `with` t, getUVT)

abstract class OnOffModel(pos:Vector3 = Vector3.zero) extends SingleComponentModel(pos)
:
    var on = false

    def getIcons:Seq[TextureAtlasSprite]

    override def getUVT = new IconTransformation(getIcons(if on then 1 else 0))

object BaseComponentModel
:
    var baseModels = Seq[BaseComponentModel]()

class BaseComponentModel(val iconPath:String) extends CenteredSingleComponentModel
:
    BaseComponentModel.baseModels :+= this

    var icon:TextureAtlasSprite = scala.compiletime.uninitialized
    override def getUVT = new IconTransformation(icon)

class RedstoneTorchModel(x:Double, z:Double) extends OnOffModel(new Vector3(x, 0, z))
:
    override def getIcons = Seq(torchOffIcon, torchOnIcon)

object WireModel
:
    var wireModels = Seq[WireModel]()

class WireModel(val iconPath:String) extends ICComponentModel
:
    WireModel.wireModels :+= this

    var on = false
    var disabled = false

    var icon:TextureAtlasSprite = scala.compiletime.uninitialized

    var onColour = 187<<24|0xFF
    var offColour = 60<<24|0xFF
    var disabledColour = EnumColour.GRAY.rgba

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        faceModels(dynamicIdx(orient, ortho)).render(ccrs, dynamicT(orient) `with` t, new IconTransformation(icon),
            ColourMultiplier.instance(if disabled then disabledColour else if on then onColour else offColour))

class IOSigModel extends ICComponentModel
:
    var on = false
    var colour = 0

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        val m = faceModels(dynamicIdx(orient, ortho))
        val t0 = dynamicT(orient) `with` t
        m.render(ccrs, t0, new IconTransformation(ioBorder), ColourMultiplier.instance(colour))
        m.render(ccrs, t0, new IconTransformation(ioSig), ColourMultiplier.instance(signalColour(if on then 255.toByte else 0.toByte)))

class LeverModel(x:Double, z:Double) extends OnOffModel(new Vector3(x, 0, z))
:
    override def getIcons = Seq(tLeverOffIcon, tLeverOnIcon)

class RedChipModel(x:Double, z:Double) extends OnOffModel(new Vector3(x, 0, z))
:
    override def getIcons = Seq(redChipOffIcon, redChipOnIcon)

class YellowChipModel(x:Double, z:Double) extends OnOffModel(new Vector3(x, 0, z))
:
    override def getIcons = Seq(yellowChipOffIcon, yellowChipOnIcon)

class PointerModel(x:Double, z:Double, scale:Double = 1) extends ICComponentModel
:
    val pos = new Vector3(x, 0, z).multiply(1/16D)
    val models = faceModels.map(_.copy.apply(new Translation(-0.5, 0, -0.5)).apply(new Scale(scale, 1, scale)))

    var angle = 0.0

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        models(dynamicIdx(orient, ortho)).render(ccrs, new Rotation(-angle, 0, 1, 0) `with` pos.translation
                `with` dynamicT(orient) `with` t, new IconTransformation(pointerIcon))

class CellStandModel extends CenteredSingleComponentModel
:
    override def getUVT = new IconTransformation(cellStandIcon)

abstract class CellWireModel extends ICComponentModel
:
    var signal:Byte = 0

    def signalColour(signal:Byte) = (signal&0xFF)/2+60<<24|0xFF

    def colourMult:IVertexOperation = ColourMultiplier.instance(signalColour(signal))

    def getUVT:UVTransformation

    override def renderModel(ccrs:CCRenderState, t:Transformation, orient:Int, ortho:Boolean): Unit =
        faceModels(dynamicIdx(orient, ortho)).render(ccrs, dynamicT(orient)
                `with` t, getUVT, colourMult)

class CellTopWireModel extends CellWireModel
:
    override def getUVT = new IconTransformation(nullCellWireTopIcon)

class NullCellBottomWireModel extends CellWireModel
:
    override def getUVT = new IconTransformation(nullCellWireBottomIcon)

class InvertCellBottomWireModel extends CellWireModel
{
    override def getUVT = new IconTransformation(invertCellWireBottomIcon)
}