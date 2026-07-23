/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.integration

import codechicken.lib.colour.{Colour, EnumColour}
import codechicken.lib.lighting.{LightModel, PlanarLightModel}
import codechicken.lib.math.MathHelper
import codechicken.lib.raytracer.IndexedCuboid6
import codechicken.lib.render.*
import codechicken.lib.render.pipeline.{ColourMultiplier, IVertexOperation}
import codechicken.lib.texture.{TextureDataHolder, TextureSpecial, TextureUtils}
import codechicken.lib.vec.*
import codechicken.lib.vec.uv.*
import mrtjp.core.vec.{InvertX, VecLib}
import mrtjp.projectred.core.{Configurator, RenderHalo}
import mrtjp.projectred.transmission.{UVT, WireModelGen}
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.lwjgl.opengl.GL11

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks

object ComponentStore
{
    val base = loadBase("base")
    val lightChip = loadCorrectedModel("chip")
    val leverOn = loadCorrectedModel("leveron")
    val leverOff = loadCorrectedModel("leveroff")
    val solarArray = loadCorrectedModel("solar")
    val rainSensor = loadCorrectedModel("rainsensor")
    val pointer = loadCorrectedModel("pointer")
    val busXcvr = loadCorrectedModel("array/busxcvr")
    val lightPanel1 = loadCorrectedModel("array/lightpanel1")
    val lightPanel2 = loadCorrectedModel("array/lightpanel2")
    val busRand = loadCorrectedModel("array/busrand")
    val busConv = loadCorrectedModel("array/busconv")
    val signalPanel = loadCorrectedModel("array/signalpanel")
    val busInput = loadCorrectedModel("array/businput")
    val icBundled = loadCorrectedModel("array/icbundled")

    val nullCellWireBottom = loadCorrectedModel("array/nullcellbottomwire").apply(new Translation(0.5, 0, 0.5))
    val nullCellWireTop = loadCorrectedModel("array/nullcelltopwire").apply(new Translation(0.5, 0, 0.5))
    val nullCellBase = loadBase("array/nullcellbase")
    val extendedCellWireBottom = loadCorrectedModel("array/extendedcellbottomwire").apply(new Translation(0.5, 0, 0.5))
    val extendedCellWireTop = loadCorrectedModel("array/extendedcelltopwire").apply(new Translation(0.5, 0, 0.5))
    val extendedCellBase = loadBase("array/extendedcellbase")
    val cellWireSide = loadCorrectedModel("array/cellsidewire").apply(new Translation(0.5, 0, 0.5))
    val cellFrame = loadCorrectedModel("array/cellstand").apply(new Translation(0.5, 0, 0.5))
    val cellPlate = loadCorrectedModel("array/cellplate").apply(new Translation(0.5, 0, 0.5))

    val stackLatchWireBottom = loadCorrectedModel("array/stacklatchwire").apply(new Translation(0.5, 0, 0.5))
    val stackStand = loadCorrectedModel("array/latchstand")

    val sevenSeg = loadCorrectedModels("array/7seg")
    val sixteenSeg = loadCorrectedModels("array/16seg")
    val segbus = loadCorrectedModel("array/segbus")

    val icChip = loadCorrectedModel("icchip")
    val icGlass = loadCorrectedModel("icglass")
    val icHousing = loadCorrectedModel("ichousing")

    var baseIcon:TextureAtlasSprite = null
    var wireIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](3)
    var wireData:Array[Array[Colour]] = new Array[Array[Colour]](3)
    var redstoneTorchIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](2)
    var yellowChipIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](2)
    var redChipIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](2)
    var minusChipIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](2)
    var plusChipIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](2)
    var leverIcon:TextureAtlasSprite = null
    var solarIcons:Array[TextureAtlasSprite] = new Array[TextureAtlasSprite](3)
    var rainIcon:TextureAtlasSprite = null
    var pointerIcon:TextureAtlasSprite = null
    var busXcvrIcon:TextureAtlasSprite = null
    var cellIcon:TextureAtlasSprite = null
    var busRandIcon:TextureAtlasSprite = null
    var busConvIcon:TextureAtlasSprite = null
    var busInputIcon:TextureAtlasSprite = null
    var segment:TextureAtlasSprite = null
    var segmentDisp:TextureAtlasSprite = null
    var icChipIcon:TextureAtlasSprite = null
    var icChipIconOff:TextureAtlasSprite = null
    var icHousingIcon:TextureAtlasSprite = null

    def registerIcons(map:TextureMap): Unit =
    {
        val baseTex = "projectred:blocks/integration/"
        def register(path:String) = map.registerSprite(new ResourceLocation(baseTex+path))
        baseIcon = register("base")
        wireIcons(0) = register("surface/bordermatte")
        wireIcons(1) = register("surface/wirematte-OFF")
        wireIcons(2) = register("surface/wirematte-ON")
        for i <- 0 until 3 do
        {
            val res = new ResourceLocation(wireIcons(i).getIconName)
            wireData(i) = TextureUtils.loadTextureColours(new ResourceLocation(res.getNamespace,
                "textures/"+res.getPath+".png"))
        }

        redstoneTorchIcons(0) = map.registerSprite(new ResourceLocation("minecraft:blocks/redstone_torch_off"))
        redstoneTorchIcons(1) = map.registerSprite(new ResourceLocation("minecraft:blocks/redstone_torch_on"))
        yellowChipIcons(0) = register("yellowchipoff")
        yellowChipIcons(1) = register("yellowchipon")
        redChipIcons(0) = register("redchipoff")
        redChipIcons(1) = register("redchipon")
        minusChipIcons(0) = register("minuschipoff")
        minusChipIcons(1) = register("minuschipon")
        plusChipIcons(0) = register("pluschipoff")
        plusChipIcons(1) = register("pluschipon")

        for i <- 0 until 3 do solarIcons(i) = register("solar"+i)

        rainIcon = register("rainsensor")
        leverIcon = register("lever")
        pointerIcon = register("pointer")
        busXcvrIcon = register("busxcvr")
        cellIcon = register("cells")
        busRandIcon = register("busrand")
        busConvIcon = register("busconv")
        busInputIcon = register("businput")
        segment = register("segment")
        segmentDisp = register("segmentdisp")
        icChipIcon = register("ic_active")
        icChipIconOff = register("ic_inert")
        icHousingIcon = register("ic_housing")
    }

    def loadCorrectedModels(name: String): mutable.Map[String, CCModel] = {
        val m = OBJParser.parseModels(new ResourceLocation("projectred:textures/obj/integration/" + name + ".obj"), GL11.GL_QUADS, null)
        val models = m.asScala.map(m => m._1 -> m._2.backfacedCopy())
        models.values.foreach(_.computeNormals.shrinkUVs(0.0005))
        models
    }

    def loadCorrectedModel(name: String): CCModel =
        CCModel.combine(loadCorrectedModels(name).values.asJavaCollection)

    @deprecated
    def parseModels(name: String): mutable.Map[String, CCModel] =
        OBJParser.parseModels(new ResourceLocation("projectred:textures/obj/integration/" + name + ".obj"), 7, InvertX).asScala

    @deprecated("use loadCorrectedModels instead")
    def loadModels(name: String): mutable.Map[String, CCModel] = {
        val models = parseModels(name)
        models.values.foreach(_.computeNormals.shrinkUVs(0.0005))
        models
    }

    @deprecated("use loadCorrectedModel instead")
    def loadModel(name:String):CCModel =
    {
        val models = parseModels(name)
        val m = CCModel.combine(models.values.asJavaCollection)
        m.computeNormals
        m.shrinkUVs(0.0005)
        m
    }

    def loadBase(name: String): CCModel = {
        val m = loadCorrectedModel(name)
        m.apply(new Translation(0.5, 0, 0.5))
        //inset each face a little for things like posts that render overtop
        for i <- m.verts.indices do
            m.verts(i).vec.subtract(m.normals()(i).copy.multiply(0.0002))
        m
    }

    def orientT(orient: Int): Transformation = {
        var t = Rotation.sideOrientation(orient % 24 >> 2, orient & 3)
        if orient >= 24 then t = new Scale(-1, 1, 1).`with`(t)
        t.at(Vector3.center)
    }

    def dynamicT(orient: Int): Transformation =
        if orient == 0 then new RedundantTransformation
        else new Scale(-1, 1, 1).at(Vector3.center)

    def bakeCopy(base: CCModel, orient: Int): CCModel = {
        val m = base.copy
        if orient >= 24 then reverseFacing(m)
        m.apply(orientT(orient)).computeLighting(LightModel.standardLightModel)
        m
    }

    def bakeDynamic(base:CCModel) = Array(base.copy, reverseFacing(base.copy))

    private def reverseFacing(m:CCModel) =
    {
        for i <- m.verts.indices by 4 do
        {
            val vtmp = m.verts(i+1)
            val ntmp = m.normals()(i+1)
            m.verts(i+1) = m.verts(i+3)
            m.normals()(i+1) = m.normals()(i+3)
            m.verts(i+3) = vtmp
            m.normals()(i+3) = ntmp
        }
        m
    }

    def generateWireModels(name: String, count: Int): Seq[TWireModel] = {
        val xs = Seq.newBuilder[TWireModel]
        for i <- 0 until count do xs += generateWireModel(name + "-" + i)
        xs.result()
    }

    def generateWireModel(name: String): TWireModel = {
        val data = TextureUtils.loadTextureColours(new ResourceLocation(
            "projectred:textures/blocks/integration/surface/" + name + ".png"))

        if Configurator.logicwires3D then new WireModel3D(data)
        else new WireModel2D(data)
    }
}

import mrtjp.projectred.integration.ComponentStore.*

abstract class ComponentModel
{
    def renderModel(t: Transformation, orient: Int, ccrs: CCRenderState): Unit 

    def registerIcons(reg:TextureMap): Unit ={}
}

abstract class SingleComponentModel(m:CCModel, pos:Vector3 = Vector3.zero) extends ComponentModel
{
    val models: Array[CCModel] = {
        val xs = new Array[CCModel](48)
        val t = pos.copy.multiply(1 / 16D).translation
        for i <- 0 until 48 do xs(i) = bakeCopy(m.copy.apply(t), i)
        xs
    }

    def getUVT:UVTransformation

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        models(orient).render(ccrs, t, getUVT)
    }
}

abstract class MultiComponentModel(m:Seq[CCModel], pos:Vector3 = Vector3.zero) extends ComponentModel
{
    val models: Array[Array[CCModel]] = {
        val xs = Array.ofDim[CCModel](m.length, 48)
        val t = pos.copy.multiply(1 / 16D).translation
        for i <- m.indices do for j <- 0 until 48 do
            xs(i)(j) = bakeCopy(m.apply(i).copy.apply(t), j)
        xs
    }

    var state = 0

    def getUVT:UVTransformation

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        models(state)(orient).render(ccrs, t, getUVT)
    }
}

abstract class OnOffModel(m:CCModel, pos:Vector3 = Vector3.zero) extends SingleComponentModel(m, pos)
{
    var on = false

    def getIcons:Array[TextureAtlasSprite]

    override def getUVT = new IconTransformation(getIcons(if on then 1 else 0))
}

abstract class StateIconModel(m:CCModel, pos:Vector3 = Vector3.zero) extends SingleComponentModel(m, pos)
{
    var state = 0

    def getIcons:Array[TextureAtlasSprite]

    override def getUVT = new IconTransformation(getIcons(state))
}

class BaseComponentModel extends SingleComponentModel(base)
{
    override def getUVT = new IconTransformation(baseIcon)
}

trait TWireModel extends ComponentModel
{
    var on = false
    var disabled = false
}

object TWireModel
{
    def rectangulate(data: Array[Colour]): Seq[Rectangle4i] = {
        val wireCorners = new Array[Boolean](1024)

        for y <- 0 to 30 do for x <- 0 to 30 do Breaks.breakable {
            if data(y * 32 + x).rgba != -1 then Breaks.break()
            if overlap(wireCorners, x, y) then Breaks.break()
            if !segment2x2(data, x, y) then
                throw new RuntimeException("Wire segment not 2x2 at (" + x + ", " + y + ")")

            wireCorners(y * 32 + x) = true
        }

        var wireRectangles = Seq.newBuilder[Rectangle4i]
        for i <- 0 until 1024 do if wireCorners(i) then {
            val rect = new Rectangle4i(i % 32, i / 32, 0, 0)
            var x = rect.x + 2
            while x < 30 && wireCorners(rect.y * 32 + x) do x += 2
            rect.w = x - rect.x

            var y = rect.y + 2
            Breaks.breakable {
                while y < 30 do {
                    var advance = true
                    var dx = rect.x
                    while dx < rect.x + rect.w && advance do {
                        if !wireCorners(y * 32 + dx) then advance = false
                        dx += 2
                    }

                    if !advance then Breaks.break()

                    y += 2
                }
            }
            rect.h = y - rect.y

            for dy <- rect.y until rect.y + rect.h by 2 do
                for dx <- rect.x until rect.x + rect.w by 2 do
                    wireCorners(dy * 32 + dx) = false

            wireRectangles += rect
        }

        wireRectangles.result()
    }

    def overlap(wireCorners: Array[Boolean], x: Int, y: Int): Boolean =
        wireCorners(y * 32 + x - 1) || (y > 0 && wireCorners((y - 1) * 32 + x)) || (y > 0 && wireCorners((y - 1) * 32 + x - 1))

    def segment2x2(data: Array[Colour], x: Int, y: Int): Boolean =
        data(y * 32 + x + 1).rgba == -1 && data((y + 1) * 32 + x).rgba == -1 && data((y + 1) * 32 + x + 1).rgba == -1

    def border(wire: Rectangle4i): Rectangle4i = {
        val border = new Rectangle4i(wire.x - 2, wire.y - 2, wire.w + 4, wire.h + 4)
        if border.x < 0 then {
            border.w += border.x; border.x = 0
        }
        if border.y < 0 then {
            border.h += border.y; border.y = 0
        }
        if border.x + border.w >= 32 then border.w -= border.x + border.w - 32
        if border.y + border.h >= 32 then border.h -= border.y + border.h - 32
        border
    }
}

class WireModel3D(data:Array[Colour]) extends SingleComponentModel(WireModel3D.generateModel(data)) with TWireModel
{
    override def getUVT: UVTransformation =
        if disabled then new IconTransformation(wireIcons(0))
        else if on then new MultiIconTransformation(wireIcons(0), wireIcons(2))
        else new MultiIconTransformation(wireIcons(0), wireIcons(1))
}

object WireModel3D
{
    def generateModel(data: Array[Colour]): CCModel = {
        val wireRectangles = TWireModel.rectangulate(data)
        val model = CCModel.quadModel(wireRectangles.length * 40)
        var i = 0
        for rect <- wireRectangles do {
            generateWireSegment(model, i, rect)
            i += 40
        }
        model.computeNormals()
        model.shrinkUVs(0.0005)
        model
    }

    def generateWireSegment(model:CCModel, i:Int, rect:Rectangle4i): Unit =
    {
        generateWireSegment(model, i, TWireModel.border(rect), 0.01, 0)
        generateWireSegment(model, i+20, rect, 0.02, 1)
    }

    def generateWireSegment(model:CCModel, i:Int, rect:Rectangle4i, h:Double, icon:Int): Unit =
    {
        val x1 = rect.x/32D
        val x2 = (rect.x+rect.w)/32D
        val z1 = rect.y/32D
        val z2 = (rect.y+rect.h)/32D
        val d = 0.0004-h/50D //little offset for wires go ontop of the border
        model.generateBlock(i, x1+d, 0.125, z1+d, x2-d, 0.125+h, z2-d, 1)
        for v <- i until i+20 do model.verts(v).uv.tex = icon
    }
}

class WireModel2D(data:Array[Colour]) extends ComponentModel with TWireModel
{
    var icons:Array[TextureSpecial] = scala.compiletime.uninitialized
    private val iconIndex = WireModel2D.claimIdx()

    override def renderModel(t: Transformation, orient: Int, ccrs: CCRenderState): Unit =
    {
        WireModel2D.models(orient).render(ccrs, t, new IconTransformation(icons(if disabled then 0 else if on then 2 else 1)))
    }

    override def registerIcons(map:TextureMap): Unit =
    {
        val wireRectangles = TWireModel.rectangulate(data)
        icons = new Array[TextureSpecial](wireData.length)
        for tex <- icons.indices do
        {
            val texMap = new Array[Int](1024)
            for rect <- wireRectangles do
            {
                fillMask(texMap, rect, 2)
                fillMask(texMap, TWireModel.border(rect), 1)
            }

            val pSize = Math.sqrt(wireData(0).length).asInstanceOf[Int]
            val size = Math.max(32, pSize)
            val relM = size/32
            val relP = size/pSize

            val imageData = new Array[Int](size*size)
            for i <- imageData.indices do
            {
                val x = i%size
                val y = i/size
                val t = texMap(y/relM*32+x/relM)
                if t != 0 then imageData(i) = wireData(if t == 1 then 0 else tex)(y/relP*pSize+x/relP).argb()
            }

            icons(tex) = TextureUtils.getTextureSpecial(map,
                "projectred:integration/wire2d_"+iconIndex+"_"+tex).addTexture(new TextureDataHolder(imageData, size))
        }
    }

    def fillMask(map:Array[Int], r:Rectangle4i, v:Int): Unit =
    {
        for i <- r.x until r.x+r.w do for j <- r.y until r.y+r.h do
            if map(j*32+i) < v then map(j*32+i) = v
    }
}

object WireModel2D
{
    val models: Array[CCModel] = {
        val xs = new Array[CCModel](48)
        val m = CCModel.quadModel(4).generateBlock(0, 0, 0, 0, 1, 1 / 8D + 0.002, 1, ~2).computeNormals()
        m.shrinkUVs(0.0005)
        for i <- 0 until 48 do xs(i) = bakeCopy(m, i)
        xs
    }

    private var iconCounter = 0

    def claimIdx(): Int = {
        iconCounter += 1; iconCounter - 1
    }
}

trait TRedstoneTorchModel extends OnOffModel
{
    def getLightPos:Vector3
}

class RedstoneTorchModel(x:Double, z:Double, h:Int) extends OnOffModel(RedstoneTorchModel.genModel(x, z, h)) with TRedstoneTorchModel
{
    override val getLightPos: Vector3 = new Vector3(x, h - 1, z).multiply(1 / 16D)

    override def getIcons: Array[TextureAtlasSprite] = redstoneTorchIcons
}

class FlippedRSTorchModel(x:Double, z:Double) extends OnOffModel(RedstoneTorchModel.genModel(x, z, 4).apply(
    new Rotation(180*MathHelper.torad, 0, 0, 1).at(Vector3.center).`with`(new Translation(new Vector3(0, -6, 0).
            multiply(1/16D))))) with TRedstoneTorchModel
{
    override val getLightPos: Vector3 = new Vector3(x, 4 + 1, z).multiply(1 / 16D)

    override def getIcons: Array[TextureAtlasSprite] = redstoneTorchIcons
}

object RedstoneTorchModel
{
    def genModel(x: Double, z: Double, h: Int): CCModel = {
        val m = CCModel.quadModel(20)
        m.verts(0) = new Vertex5(7 / 16D, 10 / 16D, 9 / 16D, 7 / 16D, 8 / 16D)
        m.verts(1) = new Vertex5(9 / 16D, 10 / 16D, 9 / 16D, 9 / 16D, 8 / 16D)
        m.verts(2) = new Vertex5(9 / 16D, 10 / 16D, 7 / 16D, 9 / 16D, 6 / 16D)
        m.verts(3) = new Vertex5(7 / 16D, 10 / 16D, 7 / 16D, 7 / 16D, 6 / 16D)
        m.generateBlock(4, 6 / 16D, (10 - h) / 16D, 7 / 16D, 10 / 16D, 11 / 16D, 9 / 16D, 0x33)
        m.generateBlock(12, 7 / 16D, (10 - h) / 16D, 6 / 16D, 9 / 16D, 11 / 16D, 10 / 16D, 0xF)
        m.apply(new Translation(-0.5 + x / 16, (h - 10) / 16D, -0.5 + z / 16))
        m.computeNormals
        m.shrinkUVs(0.0005)
        m.apply(new Scale(1.0005))
        m
    }
}

class LeverModel(x:Double, z:Double) extends MultiComponentModel(Seq(leverOn, leverOff), new Vector3(x, 2, z))
{
    override def getUVT = new IconTransformation(leverIcon)
}

class YellowChipModel(x:Double, z:Double) extends OnOffModel(lightChip, new Vector3(x, 0, z))
{
    override def getIcons: Array[TextureAtlasSprite] = yellowChipIcons
}

class RedChipModel(x:Double, z:Double) extends OnOffModel(lightChip, new Vector3(x, 0, z))
{
    override def getIcons: Array[TextureAtlasSprite] = redChipIcons
}

class MinusChipModel(x:Double, z:Double) extends OnOffModel(lightChip, new Vector3(x, 0, z))
{
    override def getIcons: Array[TextureAtlasSprite] = minusChipIcons
}

class PlusChipModel(x:Double, z:Double) extends OnOffModel(lightChip, new Vector3(x, 0, z))
{
    override def getIcons: Array[TextureAtlasSprite] = plusChipIcons
}

class SolarModel(x:Double, z:Double) extends StateIconModel(solarArray, new Vector3(x, 0, z))
{
    override def getIcons: Array[TextureAtlasSprite] = solarIcons
}

class RainSensorModel(x:Double, z:Double) extends SingleComponentModel(rainSensor, new Vector3(x, 0, z))
{
    override def getUVT = new IconTransformation(rainIcon)
}

class PointerModel(x:Double, y:Double, z:Double, scale:Double = 1) extends ComponentModel
{
    val models: Array[CCModel] = bakeDynamic(pointer.copy.apply(new Scale(scale, 1, scale)))
    val pos: Vector3 = new Vector3(x, y - 1, z).multiply(1 / 16D)

    var angle = 0D

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        models(orient).render(ccrs, new Rotation(-angle+MathHelper.pi, 0, 1, 0).`with`(pos.translation()).
                `with`(dynamicT(orient)).`with`(t), new IconTransformation(pointerIcon))
    }
}

abstract class BundledCableModel(model:CCModel, pos:Vector3, uCenter:Double, vCenter:Double) extends SingleComponentModel(model, pos)
{
    for orient <- 0 until 48 do
    {
        val side = orient%24>>2
        val r = orient&3
        val reflect = orient >= 24
        val rotate = (r+WireModelGen.reorientSide(side))%4 >= 2

        var t:Transformation = new RedundantTransformation
        if reflect then t = t.`with`(new Scale(-1, 0, 1))
        if rotate then t = t.`with`(Rotation.quarterRotations(2))

        if !t.isInstanceOf[RedundantTransformation] then
            models(orient).apply(new UVT(t.at(new Vector3(uCenter, 0, vCenter))))
    }
}

class BusXcvrCableModel extends BundledCableModel(busXcvr, new Vector3(8, 0, 8), 10/32D, 14/32D)
{
    override def getUVT = new IconTransformation(busXcvrIcon)
}

class BusRandCableModel extends BundledCableModel(busRand, new Vector3(8, 0, 8), 7/32D, 12/32D)
{
    override def getUVT = new IconTransformation(busRandIcon)
}

class
BusConvCableModel extends BundledCableModel(busConv, new Vector3(8, 0, 8), 7/32D, 12/32D)
{
    override def getUVT = new IconTransformation(busConvIcon)
}

class BusInputPanelCableModel extends BundledCableModel(busInput, new Vector3(8, 0, 8), 16/32D, 16/32D)
{
    override def getUVT = new IconTransformation(busInputIcon)
}

class SigLightPanelModel(pos:Vector3, rotY:Boolean) extends ComponentModel
{
    def this(x:Double, z:Double, rotY:Boolean) = this(new Vector3(x, 0, z), rotY)

    val displayModels = new Array[CCModel](16)
    val models = new Array[CCModel](48)
    val modelsSI = new Array[CCModel](48)

    var sideInd = true

    var signal = 0
    var disableMask = 0

    var offColour = 0x420000FF
    var onColour = 0xEC0000FF
    var disableColour: Int = EnumColour.GRAY.rgba

    {
        for i <- 0 until 16 do
        {
            val m = CCModel.quadModel(4)
            val x = i%4
            val z = i/4
            val y = 10/32D+0.0001D

            m.verts(0) = new Vertex5(x, y, z+1, x, z)
            m.verts(1) = new Vertex5(x+1, y, z+1, x+1, z)
            m.verts(2) = new Vertex5(x+1, y, z, x+1, z+1)
            m.verts(3) = new Vertex5(x, y, z, x, z+1)
            m.apply(new Scale(1/16D, 1, 1/16D).`with`(new Translation(-2/16D, 0, -2/16D)))
            m.apply(new UVTranslation(22, 0))
            m.apply(new UVScale(1/32D))
            m.computeNormals
            m.shrinkUVs(0.0005)
            displayModels(i) = m
        }

        pos.multiply(1/16D)

        val base = lightPanel2.copy
        val baseSI = lightPanel1.copy

        if rotY then
        {
            base.apply(Rotation.quarterRotations(2))
            baseSI.apply(Rotation.quarterRotations(2))
        }
        base.apply(pos.translation())
        baseSI.apply(pos.translation())

        for i <- 0 until 48 do
        {
            models(i) = bakeCopy(base, i)
            modelsSI(i) = bakeCopy(baseSI, i)
        }
    }

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        val icont = new IconTransformation(busXcvrIcon)
        (if sideInd then modelsSI else models)(orient).render(ccrs, t, icont)

        val dPos = pos.copy
        if orient >= 24 then dPos.x = 1-dPos.x

        val dispT = (if rotY then new RedundantTransformation else Rotation.quarterRotations(2)).
                `with`(dPos.translation()).`with`(orientT(orient%24)).`with`(t)

        for i <- 0 until 16 do
            displayModels(i).render(ccrs, dispT, icont, PlanarLightModel.standardLightModel, ColourMultiplier.instance(
                if (signal&1<<i) != 0 then onColour else if (disableMask&1<<i) != 0 then disableColour else offColour))
    }
}

class SignalBarModel(x:Double, z:Double) extends ComponentModel
{
    val models = new Array[CCModel](48)
    val bars = new Array[CCModel](16)
    val barsInv = new Array[CCModel](16)
    var barsBg:CCModel = uninitialized
    var barsBgInv:CCModel = uninitialized

    val pos: Vector3 = new Vector3(x, 0, z).multiply(1 / 16D)

    var signal = 0
    var inverted = false

    {
        for i <- 1 to 16 do
        {
            val bar = CCModel.quadModel(4)
            val y = 12/32D+0.0001D
            bar.verts(0) = new Vertex5(0, y, 0, 0, 0)
            bar.verts(1) = new Vertex5(0, y, i, 0, i)
            bar.verts(2) = new Vertex5(1, y, i, 2, i)
            bar.verts(3) = new Vertex5(1, y, 0, 2, 0)
            bar.apply(new UVTranslation(22, 0))
            bar.apply(new UVScale(1/32D, 1/128D))
            bar.shrinkUVs(0.0005)

            val bar1 = bar.backfacedCopy
            bar1.apply(new Translation(-0.5, 0, -12))
            bar1.apply(new Scale(1/16D, 1, -1/64D))
            bar1.computeNormals
            val bar2 = bar.copy
            bar2.apply(new Translation(-0.5, 0, -1.25*4))
            bar2.apply(new Scale(1/16D, 1, 1/64D))
            bar2.computeNormals

            bars(i-1) = bar1
            barsInv(i-1) = bar2
        }

        val t = new Scale(4/8D+1, 0.9999D, 4/32D+1)
        barsBg = bars(15).copy.apply(t)
        barsBgInv = barsInv(15).copy.apply(t)

        val base = signalPanel.copy.apply(pos.translation())
        for i <- 0 until 48 do models(i) = bakeCopy(base, i)
    }

    def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        val iconT = new IconTransformation(busConvIcon)
        models(orient%24).render(ccrs, t, iconT)
        val position = new TransformationList(pos.translation).`with`(orientT(orient%24)).`with`(t)
        (if inverted then barsBgInv else barsBg).render(ccrs, position, iconT, PlanarLightModel.standardLightModel,
            ColourMultiplier.instance(0x535353FF))
        (if inverted then barsInv else bars)(Math.min(signal, 15)).render(ccrs, position, iconT,
            PlanarLightModel.standardLightModel, ColourMultiplier.instance(0xEC0000FF))
    }
}

class InputPanelButtonsModel extends ComponentModel
{
    val unpressed: Array[IndexedCuboid6] = VecLib.buildCubeArray(4, 4, new Cuboid6(3, 1, 3, 13, 3, 13), new Vector3(-0.25, 0, -0.25))
    val pressed: Array[IndexedCuboid6] = VecLib.buildCubeArray(4, 4, new Cuboid6(3, 1, 3, 13, 2.5, 13), new Vector3(-0.25, 0, -0.25))
    val lights: Array[IndexedCuboid6] = VecLib.buildCubeArray(4, 4, new Cuboid6(3, 1, 3, 13, 2.5, 13), new Vector3(-0.25, 0, -0.25).add(0.2))

    var pressMask = 0
    var pos: BlockPos = BlockPos.ORIGIN
    var orientationT:Transformation = uninitialized

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        val icon = new IconTransformation(baseIcon)
        for i <- 0 until 16 do
        {
            ccrs.setPipeline(PlanarLightModel.standardLightModel, orientT(orient).`with`(t), icon,
                ColourMultiplier.instance(EnumColour.values()(i).rgba))
            BlockRenderer.renderCuboid(ccrs, if (pressMask&1<<i) != 0 then pressed(i) else unpressed(i), 1)
        }
    }

    def renderLights(): Unit =
    {
        for i <- 0 until 16 do if (pressMask&1<<i) != 0 then
            RenderHalo.addLight(pos, i, lights(i).copy.apply(orientationT))
    }
}

abstract class CellWireModel extends ComponentModel
{
    var signal:Byte = 0

    def signalColour(signal: Byte): Int = (signal & 0xFF) / 2 + 60 << 24 | 0xFF

    def colourMult:IVertexOperation = ColourMultiplier.instance(signalColour(signal))
}

object CellTopWireModel
{
    val left = new Array[CCModel](24)
    val right = new Array[CCModel](24)

    {
        val cellWireLeft = cellWireSide.copy.apply(new Translation(-7.001/16D, 0, 0))
        val cellWireRight = cellWireSide.copy.apply(new Translation(7.001/16D, 0, 0))

        for i <- 0 until 24 do
        {
            left(i) = bakeCopy(cellWireLeft, i)
            right(i) = bakeCopy(cellWireRight, i)
        }
    }
}

class CellTopWireModel(wireTop:CCModel) extends CellWireModel
{
    val top = new Array[CCModel](24)
    var conn = 0

    for i <- 0 until 24 do top(i) = bakeCopy(wireTop, i)

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        val icont = new IconTransformation(cellIcon)
        top(orient).render(ccrs, t, icont, colourMult)
        import mrtjp.projectred.integration.CellTopWireModel.*
        if (conn&2) == 0 then right(orient).render(ccrs, t, icont, colourMult)
        if (conn&8) == 0 then left(orient).render(ccrs, t, icont, colourMult)
    }
}

class CellBottomWireModel(wireBottom:CCModel) extends CellWireModel
{
    val bottom = new Array[CCModel](24)

    for i <- 0 until 24 do bottom(i) = bakeCopy(wireBottom, i)

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        bottom(orient).render(ccrs, t, new IconTransformation(cellIcon), colourMult)
    }
}

class CellFrameModel extends SingleComponentModel(cellFrame)
{
    override def getUVT = new IconTransformation(cellIcon)
}

class CellPlateModel extends SingleComponentModel(cellPlate)
{
    override def getUVT = new IconTransformation(cellIcon)
}

class NullCellBaseModel extends SingleComponentModel(nullCellBase)
{
    override def getUVT = new IconTransformation(cellIcon)
}

class ExtendedCellBaseModel extends SingleComponentModel(extendedCellBase)
{
    override def getUVT = new IconTransformation(cellIcon)
}

class StackLatchStandModel(x:Double, z:Double) extends SingleComponentModel(stackStand, new Vector3(x, 2, z))
{
    override def getUVT = new IconTransformation(cellIcon)
}

trait SegModel
{
    var signal = 0
    var colour_on: Int = EnumColour.RED.rgba
    var colour_off: Int = EnumColour.BLACK.rgba

    def setColourOn(colour:Byte): Unit =
    {
        colour_on = EnumColour.values()(colour&0xFF).rgba
    }
}

class SevenSegModel(x:Double, z:Double) extends SingleComponentModel(sevenSeg("base"), new Vector3(x, 0, z)) with SegModel
{
    val segModels: IndexedSeq[CCModel] = (0 until 8).map(i => sevenSeg(i.toString))
    val dPos: Translation = new Vector3(x, 0, z).multiply(1 / 16D).translation

    override def getUVT = new IconTransformation(segment)

    override def renderModel(t:Transformation, orient:Int, ccrs:CCRenderState): Unit =
    {
        super.renderModel(t, orient, ccrs)

        val iconT = new IconTransformation(segmentDisp)
        val dispT = dPos.`with`(orientT(orient%24)).`with`(t)


        for i <- 0 until 8 do
            segModels(i).render(ccrs, dispT, iconT, PlanarLightModel.standardLightModel, ColourMultiplier.instance(
                if (signal&1<<i) != 0 then colour_on else colour_off))
    }
}

class SixteenSegModel(x:Double, z:Double) extends SingleComponentModel(sixteenSeg("base"), new Vector3(x, 0, z)) with SegModel
{
    val segModels: IndexedSeq[CCModel] = (0 until 16).map(i => sixteenSeg(i.toString))
    val dPos: Translation = new Vector3(x, 0, z).multiply(1 / 16D).translation

    override def getUVT = new IconTransformation(segment)

    override def renderModel(t: Transformation, orient: Int, ccrs: CCRenderState): Unit =
    {
        super.renderModel(t, orient, ccrs)

        val iconT = new IconTransformation(segmentDisp)
        val dispT = dPos.`with`(orientT(orient%24)).`with`(t)

        for i <- 0 until 16 do
            segModels(i).render(ccrs, dispT, iconT, PlanarLightModel.standardLightModel, ColourMultiplier.instance(
                if (signal&1<<i) != 0 then colour_on else colour_off))
    }
}

class SegmentBusCableModel extends BundledCableModel(segbus, new Vector3(8, 0, 8), 9/32D, 16.5/32D)
{
    override def getUVT = new IconTransformation(segment)
}

class SidedICBundledCableModel extends BundledCableModel(icBundled, new Vector3(8, 0, 8), 7/32D, 12/32D)
{
    var sidemask = 0

    override def getUVT = new IconTransformation(busConvIcon)

    override def renderModel(t: Transformation, orient: Int, ccrs: CCRenderState): Unit =
    {
        for r <- 0 until 4 do if (sidemask&1<<r) != 0 then
            super.renderModel(t, orient&0xFC|((orient&3)+r)%4, ccrs)
    }
}

class SidedWireModel(val wires:Seq[TWireModel]) extends ComponentModel
{
    var sidemask = 0

    override def renderModel(t: Transformation, orient: Int, ccrs: CCRenderState): Unit =
    {
        for r <- 0 until 4 do if (sidemask&1<<r) != 0 then
            wires(r).renderModel(t, orient, ccrs)
    }

    override def registerIcons(map:TextureMap): Unit =
    {
        wires.foreach(_.registerIcons(map))
    }
}

class ICChipModel extends SingleComponentModel(icChip, new Vector3(8, 2, 8))
{
    override def getUVT = new IconTransformation(icChipIcon)
}

class ICChipHousingModel extends SingleComponentModel(icHousing, new Vector3(8, 0, 8))
{
    val glass: CCModel = icGlass.copy.apply(new Vector3(8 / 16D, 0, 8 / 16D).translation())

    override def getUVT = new IconTransformation(icHousingIcon)

    def renderDynamic(t:Transformation, ccrs:CCRenderState): Unit =
    {
        glass.render(ccrs, t, getUVT)
    }
}
