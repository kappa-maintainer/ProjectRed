package mrtjp.projectred.core

import codechicken.lib.colour.EnumColour
import codechicken.lib.render.{BlockRenderer, CCRenderState}
import codechicken.lib.vec.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.opengl.GL11.*

object RenderHalo
:
    private var renderList = Vector[LightCache]()
    private val renderEntityPos = new Vector3
    private val vec = new Vector3

    private class LightCache(val pos:BlockPos, val color:Int, val cube:Cuboid6, val alpha:Int = 128,
        val customColour:Int = 0, val hasCustomColour:Boolean = false) extends Ordered[LightCache]
    :
        def this(x:Int, y:Int, z:Int, c:Int, cube:Cuboid6) = this(new BlockPos(x, y, z), c, cube)

        private def renderDist = vec.set(pos.getX, pos.getY, pos.getZ).subtract(renderEntityPos).magSquared

        override def compare(o:LightCache) =
            val ra = renderDist
            val rb = o.renderDist
            if ra == rb then 0 else if ra < rb then 1 else -1

    def addLight(pos:BlockPos, color:Int, box:Cuboid6): Unit =
        renderList :+= new LightCache(pos, color, box)

    def addMultiLight(pos:BlockPos, box:Cuboid6, signal:Array[Byte]): Unit =
        val (colour, maximum) = blendSignalColours(signal)
        if colour != 0 then renderList :+= new LightCache(pos, 0, box, haloAlpha(maximum), colour, true)

    @SideOnly(Side.CLIENT)
    def renderInventoryMultiHalo(box:Cuboid6, signal:Array[Byte], transform:Transformation): Unit =
        val (colour, maximum) = blendSignalColours(signal)
        if colour != 0 then
            prepareRenderState()
            renderRgbaHalo(box, colour, haloAlpha(maximum), transform)
            restoreRenderState()

    private def haloAlpha(maximum:Int):Int =
        // Keep the first bundled level visible, then scale smoothly through level 15.
        24 + maximum * 104 / 255

    private def blendSignalColours(signal:Array[Byte]):(Int, Int) =
        var total = 0
        var maximum = 0
        for value <- signal do
            val strength = value & 0xFF
            total += strength
            maximum = math.max(maximum, strength)
        if total == 0 then return (0, 0)

        var red = 0.0D
        var green = 0.0D
        var blue = 0.0D
        for color <- 0 until 16 do
            val strength = signal(color) & 0xFF
            if strength > 0 then
                val source = EnumColour.values()(color).rgba
                val weight = strength.toDouble / total
                red += ((source >> 24) & 0xFF) / 255.0D * weight
                green += ((source >> 16) & 0xFF) / 255.0D * weight
                blue += ((source >> 8) & 0xFF) / 255.0D * weight
        (math.min(255, (red*255).toInt) << 24 |
            math.min(255, (green*255).toInt) << 16 |
            math.min(255, (blue*255).toInt) << 8 | 0xFF, maximum)

    @SubscribeEvent
    def onRenderWorldLast(event:RenderWorldLastEvent): Unit =
        if renderList.isEmpty then return
        val w = Minecraft.getMinecraft.world
        val entity = Minecraft.getMinecraft.getRenderViewEntity
        renderEntityPos.set(entity.posX, entity.posY+entity.getEyeHeight, entity.posZ)

        renderList = renderList.sorted

        pushMatrix()
        // Adjust translation for camera movement between frames (using camra coordinates for numeric stability).
        translate(
             entity.posX-(entity.posX-entity.lastTickPosX)*event.getPartialTicks-entity.lastTickPosX,
             entity.posY-(entity.posY-entity.lastTickPosY)*event.getPartialTicks-entity.lastTickPosY,
             entity.posZ-(entity.posZ-entity.lastTickPosZ)*event.getPartialTicks-entity.lastTickPosZ
        )
        prepareRenderState()

        val it = renderList.iterator
        val max = if Configurator.lightHaloMax < 0 then renderList.size else Configurator.lightHaloMax

        var i = 0
        while i < max && it.hasNext do
            val cc = it.next()
            renderHalo(w, cc)
            i += 1
        renderList = Vector()

        restoreRenderState()
        popMatrix()

    def prepareRenderState(): Unit =
        enableBlend()
        blendFunc(GL_SRC_ALPHA, GL_ONE)
        disableTexture2D()
        disableLighting()
        disableCull()
        depthMask(false)

        val rs = CCRenderState.instance()
        rs.reset()
        rs.startDrawing(GL_QUADS, DefaultVertexFormats.ITEM)

    def restoreRenderState(): Unit =
        CCRenderState.instance().draw()
        depthMask(true)
        color(1, 1, 1, 1)
        enableCull()
        enableLighting()
        enableTexture2D()
        blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        disableBlend()

    private def renderHalo(world:World, cc:LightCache): Unit =
        CCRenderState.instance().setBrightness(world, cc.pos)
        // Make sure to use camera coordinates for the halo transformation.
        val entity = Minecraft.getMinecraft.getRenderViewEntity
        val transform = new Translation(cc.pos.getX-entity.posX, cc.pos.getY-entity.posY, cc.pos.getZ-entity.posZ)
        if cc.hasCustomColour then renderRgbaHalo(cc.cube, cc.customColour, cc.alpha, transform)
        else renderHalo(cc.cube, cc.color, cc.alpha, transform)

    def renderHalo(cuboid:Cuboid6, colour:Int, t:Transformation): Unit =
        renderHalo(cuboid, colour, 128, t)

    def renderHalo(cuboid:Cuboid6, colour:Int, alpha:Int, t:Transformation): Unit =
        renderRgbaHalo(cuboid, EnumColour.values()(colour).rgba, alpha, t)

    private def renderRgbaHalo(cuboid:Cuboid6, colour:Int, alpha:Int, t:Transformation): Unit =
        val rs = CCRenderState.instance()
        rs.reset()
        rs.setPipeline(t)
        rs.baseColour = colour
        rs.alphaOverride = alpha
        BlockRenderer.renderCuboid(rs, cuboid, 0)
