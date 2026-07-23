/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.render.pipeline.ColourMultiplier
import codechicken.lib.render.{CCModel, CCRenderState}
import codechicken.lib.vec.*
import codechicken.lib.vec.uv.{IconTransformation, UVScale}
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11.*

object RenderICTileMap
:
    def registerIcons(reg:TextureMap): Unit =
        ICComponentStore.registerIcons(reg)

    def renderOrtho(ccrs:CCRenderState, map:ICTileMapContainer, x:Double, y:Double, xSize:Double, ySize:Double, frame:Float): Unit =
        val t = ICComponentStore.orthoGridT(xSize, ySize) `with` new Translation(x, y, 0)
        renderBoard(ccrs, map, t, true)
        renderTiles(ccrs, map, t, true, frame)

    def renderDynamic(ccrs:CCRenderState, map:ICTileMapContainer, t:Transformation, frame:Float): Unit =
        disableDepth()
        renderBoard(ccrs, map, t, true)
        renderTiles(ccrs, map, t, true, frame)
        enableDepth()

    def renderBoard(ccrs:CCRenderState, map:ICTileMapContainer, t:Transformation, ortho:Boolean): Unit =
        PrefboardRenderer.render(ccrs, map, t, ortho)

    def renderTiles(ccrs:CCRenderState, map:ICTileMapContainer, t:Transformation, ortho:Boolean, frame:Float): Unit =
        for ((x, y), part) <- map.tiles do
            val tlist = new TransformationList(
                new Scale(1.0/map.size.width, 1, 1.0/map.size.height),
                new Translation(x*1.0/map.size.width, 0, y*1.0/map.size.height),
                t
            )
            part.renderDynamic(ccrs, tlist, ortho, frame)

import mrtjp.projectred.fabrication.ICComponentStore.*
object PrefboardRenderer
:
    import scala.jdk.CollectionConverters.*
    private var boardModels = Map[(Int, Int), Seq[CCModel]]()
    private var cornerModels = Map[(Int, Int), Seq[CCModel]]()
    private var edgeModels = Map[(Int, Int), Seq[CCModel]]()

    private def createBoardModel(w:Int, h:Int):Seq[CCModel] =
        faceModels.map(_.copy.apply(new UVScale(w, h)))

    private def createCornerModel(w:Int, h:Int):Seq[CCModel] =
        val corners = Seq((0, 0), (0, h-1), (w-1, h-1), (w-1, 0)).map
          { pair =>
            new TransformationList(
                new Scale(1.0/w, 1, 1.0/h),
                new Translation(pair._1*1.0/w, 0, pair._2*1.0/h)
            )
        }

        faceModels.map
          { m =>
            var models = Seq[CCModel]()
            for t <- corners do
                models :+= m.copy.apply(t)
            CCModel.combine(models.toList.asJavaCollection)
        }

    private def createEdgeModel(w:Int, h:Int):Seq[CCModel] =
        val edges = Seq((0, 0, 1, h), (0, 0, w, 1), (w-1, 0, 1, h), (0, h-1, w, 1)).map
          { pair =>
            (new TransformationList(
                new Scale(1.0/w, 1, 1.0/h),
                new Scale(pair._3, 1, pair._4),
                new Translation(pair._1*1.0/w, 0, pair._2*1.0/h)
            ), new UVScale(pair._3, pair._4))
        }

        faceModels.map
          { m =>
            var models = Seq[CCModel]()
            for (t, uvt) <- edges do
                models :+= m.copy.apply(t).apply(uvt)
            CCModel.combine(models.asJavaCollection)
        }

    private def getBoardModel(w:Int, h:Int) =
        if !boardModels.contains((w, h)) then
            boardModels += (w, h) -> createBoardModel(w, h)
        boardModels((w, h))

    private def getCornerModel(w:Int, h:Int) =
        if !cornerModels.contains((w, h)) then
            cornerModels+= (w, h) -> createCornerModel(w, h)
        cornerModels((w, h))

    private def getEdgeModel(w:Int, h:Int) =
        if !edgeModels.contains((w, h)) then
            edgeModels+= (w, h) -> createEdgeModel(w, h)
        edgeModels((w, h))

    def render(ccrs:CCRenderState, map:ICTileMapContainer, t:Transformation, ortho:Boolean): Unit =
        val w = map.size.width
        val h = map.size.height

        def bind(s:String): Unit =
            val r = new ResourceLocation("projectred", "textures/blocks/fabrication/"+s+".png")
            Minecraft.getMinecraft.getTextureManager.bindTexture(r)

        ccrs.reset()
        ccrs.pullLightmap()

        for ((tex, models) <- Seq(("prefboard", getBoardModel(w, h)),
            ("prefboard_edge", getEdgeModel(w, h)), ("prefboard_corner", getCornerModel(w, h))))
            bind(tex)
            ccrs.startDrawing(GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR)
            models(if ortho then 1 else 0).render(ccrs, t)

            ccrs.draw()

object RenderTileAlloyWire
:
    var connMap:Byte = 0
    var signal:Byte = 0

    def prepairInv(): Unit =
        connMap = 0xF
        signal = 0xFF.toByte

    def prepairDynamic(part:AlloyWireICTile): Unit =
        connMap = part.connMap
        signal = part.signal

    def render(ccrs:CCRenderState, t:Transformation, ortho:Boolean): Unit =
        prepairRender(ccrs)
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(redwireIcons(connMap&0xFF)),
            ColourMultiplier.instance((signal&0xFF)/2+60<<24|0xFF))
        finishRender(ccrs)

object RenderTileInsulatedWire
:
    var connMap:Byte = 0
    var signal:Byte = 0
    var colour:Byte = 0

    def prepairInv(c:Int): Unit =
        connMap = 0xF
        signal = 255.toByte
        colour = c.toByte

    def prepairDynamic(part:InsulatedWireICTile): Unit =
        connMap = part.connMap
        signal = part.signal
        colour = part.colour

    def render(ccrs:CCRenderState, t:Transformation, ortho:Boolean): Unit =
        prepairRender(ccrs)
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(redwireIcons(connMap&0xFF)),
            ColourMultiplier.instance((signal&0xFF)/2+60<<24|0xFF))
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(insulatedwireIcons(connMap&0xFF)),
            ColourMultiplier.instance(EnumColour.values()(colour&0xFF).rgba))
        finishRender(ccrs)

object RenderTileBundledCable
:
    var connMap:Byte = 0
    var colour:Byte = 0

    def prepairInv(c:Int): Unit =
        connMap = 0xF
        colour = c.toByte

    def prepairDynamic(part:BundledCableICTile): Unit =
        connMap = part.connMap
        colour = part.colour

    def render(ccrs:CCRenderState, t:Transformation, ortho:Boolean): Unit =
        prepairRender(ccrs)
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(bundledwireIcons(connMap&0xFF)))
        if colour != -1 then faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(bundledColourIcon),
            ColourMultiplier.instance(EnumColour.values()(colour&0xFF).rgba))
        finishRender(ccrs)

object RenderTileLever
:
    var on = false

    def prepairInv(): Unit =
        on = false

    def prepairDynamic(part:LeverICTile): Unit =
        on = part.on

    def render(ccrs:CCRenderState, t:Transformation, ortho:Boolean): Unit =
        prepairRender(ccrs)
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(if on then leverOnIcon else leverOffIcon))
        finishRender(ccrs)

object RenderTileButton
{
    var on = false

    def prepairInv(): Unit =
        on = false

    def prepairDynamic(part:ButtonICTile): Unit =
        on = part.on

    def render(ccrs:CCRenderState, t:Transformation, ortho:Boolean): Unit =
        prepairRender(ccrs)
        faceModels(dynamicIdx(0, ortho)).render(ccrs, t, new IconTransformation(if on then buttonOnIcon else buttonOffIcon))
        finishRender(ccrs)
}