package mrtjp.projectred.transportation.pneumatics.part

import codechicken.lib.vec.Vector3
import mrtjp.core.fx.ParticleAction.*
import mrtjp.core.fx.particles.CoreParticle
import net.minecraft.client.renderer.{BufferBuilder, GlStateManager, Tessellator}
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.Entity
import net.minecraft.world.World
import org.lwjgl.opengl.GL11
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import mrtjp.core.fx.TAlphaParticle
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

@SideOnly(Side.CLIENT)
class PneumaticSmokeParticle(w:World, val points:Seq[Vector3]) extends CoreParticle(w) with TAlphaParticle:
    private val SMOKE_TEXTURE = new ResourceLocation("projectred", "textures/particles/smoke.png")

    setPosition(points.head.x, points.head.y, points.head.z)
    particleGravity = 0
    alpha = 0
    particleMaxAge = 300
    particleTexture = Minecraft.getMinecraft.getTextureMapBlocks.getAtlasSprite("missingno")

    override def getFXLayer = 0

    override def renderParticle(buffer:BufferBuilder, entityIn:Entity, frame:Float,
        cosyaw:Float, cospitch:Float, sinyaw:Float, sinsinpitch:Float, cossinpitch:Float): Unit =

        super.renderParticle(buffer, entityIn, frame, cosyaw, cospitch, sinyaw, sinsinpitch, cossinpitch)

        if alpha <= 0.01f then return

        val tessellator = Tessellator.getInstance()
        tessellator.draw()

        GlStateManager.pushMatrix()
        Minecraft.getMinecraft.renderEngine.bindTexture(SMOKE_TEXTURE)
        GlStateManager.depthMask(true)
        GlStateManager.enableBlend()
        GlStateManager.disableCull()
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        val buf2 = tessellator.getBuffer
        buf2.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP)

        val slideProg = (particleAge + frame) / particleMaxAge.toFloat * 8
        val width = 2.0 / 16.0
        val cx = entityIn.posX
        val cy = entityIn.posY
        val cz = entityIn.posZ
        val r = particleRed
        val g = particleGreen
        val b = particleBlue
        val a = alpha.toFloat
        val j = getBrightnessForRender(frame)

        for i <- 0 until points.size - 1 do
            val p1 = points(i)
            val p2 = points(i + 1)
            val x1 = p1.x - cx
            val y1 = p1.y - cy
            val z1 = p1.z - cz
            val x2 = p2.x - cx
            val y2 = p2.y - cy
            val z2 = p2.z - cz

            val u1 = 0.0f
            val v1 = slideProg
            val u2 = 1.0f
            val v2 = v1 + ((p2.copy.subtract(p1).mag() * 2).toFloat)

            buf2.pos(x1 - width, y1, z1).tex(u1.toDouble, v2.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x2 - width, y2, z2).tex(u1.toDouble, v1.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x2 + width, y2, z2).tex(u2.toDouble, v1.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x1 + width, y1, z1).tex(u2.toDouble, v2.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()

            buf2.pos(x1, y1 - width, z1).tex(u1.toDouble, v2.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x2, y2 - width, z2).tex(u1.toDouble, v1.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x2, y2 + width, z2).tex(u2.toDouble, v1.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()
            buf2.pos(x1, y1 + width, z1).tex(u2.toDouble, v2.toDouble).color(r, g, b, a).lightmap(j, j).endVertex()

        tessellator.draw()
        GlStateManager.enableCull()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()

        Minecraft.getMinecraft.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP)
