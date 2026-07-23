/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.{Transformation, Translation}
import mrtjp.core.vec.Point
import mrtjp.projectred.fabrication.TileEditorOp.*
import mrtjp.projectred.fabrication.ICComponentStore.*
import net.minecraftforge.fml.relauncher.{Side, SideOnly}


abstract class OpWire extends TileEditorOp
:
    override def checkOp(editor:ICTileMapEditor, start:Point, end:Point) =
        editor.getTile(start) == null

    override def writeOp(editor:ICTileMapEditor, start:Point, end:Point, out:MCDataOutput): Unit =
        out.writeByte(start.x).writeByte(start.y)
        out.writeByte(end.x).writeByte(end.y)

    override def readOp(editor:ICTileMapEditor, in:MCDataInput): Unit =
        val start = Point(in.readUByte(), in.readUByte())
        val end = Point(in.readUByte(), in.readUByte())
        val end2 = start+Point((end-start).vectorize.axialProject)

        for px <- math.min(start.x, end2.x) to math.max(start.x, end2.x) do
            for py <- math.min(start.y, end2.y) to math.max(start.y, end2.y) do
                val point = Point(px, py)
                if !isOnBorder(editor.size, point) then
                    if editor.getTile(point) == null then
                        editor.setTile(point, createPart)

    def createPart:ICTile

    @SideOnly(Side.CLIENT)
    override def renderHover(ccrs:CCRenderState, editor:ICTileMapEditor, point:Point, x:Double, y:Double, xSize:Double, ySize:Double): Unit =
        if editor.getTile(point) != null then return

        renderHolo(x, y, xSize, ySize, editor.size, point,
            if isOnBorder(editor.size, point) then 0x33FF0000 else 0x33FFFFFF)

        val t = orthoPartT(x, y, xSize, ySize, editor.size, point.x, point.y)
        doRender(ccrs, t, 0)

    @SideOnly(Side.CLIENT)
    override def renderDrag(ccrs:CCRenderState, editor:ICTileMapEditor, start:Point, end:Point, x:Double, y:Double, xSize:Double, ySize:Double): Unit =
        if editor.getTile(start) != null then return

        val end2 = start+Point((end-start).vectorize.axialProject)

        for px <- math.min(start.x, end2.x) to math.max(start.x, end2.x) do
            for py <- math.min(start.y, end2.y) to math.max(start.y, end2.y) do
                val point = Point(px, py)
                renderHolo(x, y, xSize, ySize, editor.size, point,
                    if isOnBorder(editor.size, point) then 0x44FF0000 else 0x44FFFFFF)

                if editor.getTile(point) == null then
                    val t = orthoPartT(x, y, xSize, ySize, editor.size, px, py)
                    var m = 0
                    if px > start.x then {m |= 8; if px != end2.x then m |= 2}
                    if px < start.x then {m |= 2; if px != end2.x then m |= 8}
                    if py > start.y then {m |= 1; if py != end2.y then m |= 4}
                    if py < start.y then {m |= 4; if py != end2.y then m |= 1}
                    if px == start.x && end2.x > start.x then m |= 2
                    if px == start.x && end2.x < start.x then m |= 8
                    if py == start.y && end2.y > start.y then m |= 4
                    if py == start.y && end2.y < start.y then m |= 1
                    doRender(ccrs, t, m)

    @SideOnly(Side.CLIENT)
    override def renderImage(ccrs:CCRenderState, x:Double, y:Double, width:Double, height:Double): Unit =
        val t = orthoGridT(width, height) `with` new Translation(x, y, 0)
        doInvRender(ccrs, t)

    @SideOnly(Side.CLIENT)
    def doRender(ccrs:CCRenderState, t:Transformation, conn:Int): Unit 
    @SideOnly(Side.CLIENT)
    def doInvRender(ccrs:CCRenderState, t:Transformation): Unit 

class OpAlloyWire extends OpWire
:
    override def createPart = ICTileDefs.AlloyWire.createPart

    @SideOnly(Side.CLIENT)
    override def doRender(ccrs:CCRenderState, t:Transformation, conn:Int): Unit =
        val r = RenderTileAlloyWire
        r.connMap = conn.toByte
        r.signal = 255.toByte
        r.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def doInvRender(ccrs:CCRenderState, t:Transformation): Unit =
        RenderTileAlloyWire.prepairInv()
        RenderTileAlloyWire.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def getOpName = createPart.getPartName

class OpInsulatedWire(colour:Int) extends OpWire
:
    override def createPart =
        val part = ICTileDefs.InsulatedWire.createPart.asInstanceOf[InsulatedWireICTile]
        part.colour = colour.toByte
        part

    @SideOnly(Side.CLIENT)
    override def doRender(ccrs:CCRenderState, t:Transformation, conn:Int): Unit =
        val r = RenderTileInsulatedWire
        r.connMap = conn.toByte
        r.signal = 255.toByte
        r.colour = colour.toByte
        r.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def doInvRender(ccrs:CCRenderState, t:Transformation): Unit =
        RenderTileInsulatedWire.prepairInv(colour)
        RenderTileInsulatedWire.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def getOpName = EnumColour.values()(colour&0xFF).getLocalizedName+" Insulated wire"

class OpBundledCable(colour:Int) extends OpWire
{
    override def createPart =
        val part = ICTileDefs.BundledCable.createPart.asInstanceOf[BundledCableICTile]
        part.colour = colour.toByte
        part

    @SideOnly(Side.CLIENT)
    override def doRender(ccrs:CCRenderState, t:Transformation, conn:Int): Unit =
        val r = RenderTileBundledCable
        r.connMap = conn.toByte
        r.colour = colour.toByte
        r.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def doInvRender(ccrs:CCRenderState, t:Transformation): Unit =
        RenderTileBundledCable.prepairInv(colour)
        RenderTileBundledCable.render(ccrs, t, true)

    @SideOnly(Side.CLIENT)
    override def getOpName = (if colour != -1 then EnumColour.values()(colour&0xFF).getLocalizedName+" " else "")+"Bundled cable"
}