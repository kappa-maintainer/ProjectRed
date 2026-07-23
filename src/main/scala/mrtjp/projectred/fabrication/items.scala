/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import java.util.{List as JList}

import codechicken.lib.colour.EnumColour
import codechicken.lib.gui.GuiDraw
import codechicken.lib.render.CCRenderState
import codechicken.lib.render.item.map.IMapRenderer
import codechicken.lib.texture.TextureUtils
import com.mojang.realmsclient.gui.ChatFormatting
import mrtjp.core.item.ItemCore
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.fabrication.IIOGateTile.*
import mrtjp.projectred.integration.GateDefinition
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.{Item, ItemMap, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.*
import net.minecraft.world.World
import net.minecraft.world.storage.MapData
import org.lwjgl.opengl.GL11

class ItemICBlueprint extends Item //hack to allow first-person map rendering of blueprints
:
    setMaxStackSize(1)
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    override def addInformation(stack:ItemStack, world:World, tooltip:JList[String], advanced:ITooltipFlag): Unit =
        import ChatFormatting.*

        if ItemICBlueprint.hasICInside(stack) then
            val size = ItemICBlueprint.getICSize(stack)
            tooltip.add(GRAY.toString+ItemICBlueprint.getICName(stack))
            tooltip.add(GRAY.toString+s"${size.width} x ${size.height}")

            val (warnings, errors) = ItemICBlueprint.loadFlags(stack)

            if warnings != 0 then
                tooltip.add(s"$YELLOW$BOLD" + "!" + s"$RESET$GRAY contains $warnings " + (if warnings > 1 then "warnings" else "warning"))

            if errors != 0 then
                tooltip.add(s"$RED$BOLD" + "X" + s"$RESET$GRAY contains $errors " + (if errors > 1 then "errors" else "error"))
        else tooltip.add(GRAY.toString+"empty blueprint")

object ItemICBlueprint
:
    def assertStackTag(stack:ItemStack): Unit =
        if !stack.hasTagCompound then stack.setTagCompound(new NBTTagCompound)

    def saveTileMap(tm:ICTileMapContainer, stack:ItemStack): Unit =
        assertStackTag(stack)
        val tag1 = stack.getTagCompound
        val tag2 = new NBTTagCompound
        tm.saveTiles(tag2)
        tag1.setTag("tilemap", tag2)
        tag1.setString("icname", tm.name)
        tag1.setByte("icw", tm.size.width.toByte)
        tag1.setByte("ich", tm.size.height.toByte)

    def saveFlags(stack:ItemStack, logger:SEStatLogger): Unit =
        assertStackTag(stack)
        val tag = stack.getTagCompound
        tag.setByte("log_warn", logger.getWarnings.size.toByte)
        tag.setByte("log_err", logger.getErrors.size.toByte)
        tag.setByte("log_rtf", logger.getRuntimeFlags.size.toByte)

    def loadTileMap(tm:ICTileMapContainer, stack:ItemStack): Unit =
        val tag = stack.getTagCompound
        if tag.hasKey("tilemap") then
            tm.loadTiles(tag.getCompoundTag("tilemap"))

    def loadFlags(stack:ItemStack):(Int, Int) =
        val tag = stack.getTagCompound
        (tag.getByte("log_warn")&0xFF, tag.getByte("log_err")&0xFF)

    def loadTileMap(stack:ItemStack):ICTileMapContainer =
        val tm = new ICTileMapContainer
        loadTileMap(tm, stack)
        tm

    def getICName(stack:ItemStack) =
        stack.getTagCompound.getString("icname")

    def getICSize(stack:ItemStack) =
        val tag = stack.getTagCompound
        Size(tag.getByte("icw"), tag.getByte("ich"))

    def hasICInside(stack:ItemStack) =
        stack.hasTagCompound && stack.getTagCompound.hasKey("tilemap")

    def copyIC(from:ItemStack, to:ItemStack): Unit =
        assertStackTag(to)

        val totag = to.getTagCompound
        val fromtag = from.getTagCompound
        totag.setTag("tilemap", fromtag.getCompoundTag("tilemap"))
        totag.setString("icname", fromtag.getString("icname"))
        totag.setByte("icw", fromtag.getByte("icw"))
        totag.setByte("ich", fromtag.getByte("ich"))
        totag.setByte("log_warn", fromtag.getByte("log_warn"))
        totag.setByte("log_err", fromtag.getByte("log_err"))
        totag.setByte("log_rtf", fromtag.getByte("log_rtf"))

    def removeIC(stack:ItemStack): Unit =
        if !stack.hasTagCompound then return
        val tag = stack.getTagCompound
        Seq("tilemap", "icname", "icw", "ich", "log_warn", "log_err", "log_rtf").foreach(tag.removeTag)

    def saveICToGate(tm:ICTileMapContainer, gate:ItemStack): Unit =
        assertStackTag(gate)

        val ioparts = tm.tiles.values.collect{case io:IIOGateTile => io}.toSeq
        var (ri, ro, bi, bo) = (0, 0, 0, 0)
        val connmodes = new Array[Int](4)

        for r <- 0 until 4 do
            val sparts = ioparts.filter(_.getIOSide == r)

            val ioMode =
                val in = sparts.exists(_.getIOMode == Input)
                val out = sparts.exists(_.getIOMode == Output)
                if in && !out then Input
                else if out && !in then Output
                else Closed //IO conflict???

            val connMode = if sparts.exists(_.getConnMode == Simple) then Simple
            else if sparts.exists(_.getConnMode == Analog) then Analog
            else if sparts.exists(_.getConnMode == Bundled) then Bundled
            else NoConn

            connmodes(r) = connMode
            (ioMode, connMode) match
                case (Input, Simple)    => ri |= 1<<r
                case (Input, Analog)    => ri |= 1<<r
                case (Input, Bundled)   => bi |= 1<<r

                case (Output, Simple)   => ro |= 1<<r
                case (Output, Analog)   => ro |= 1<<r
                case (Output, Bundled)  => bo |= 1<<r
                case _ =>

        val tag = gate.getTagCompound
        saveTileMap(tm, gate)
        tag.setShort("masks", ICGateLogic.packIO(ri, ro, bi, bo).toShort)
        tag.setShort("cmode", ICGateLogic.packConnModes(connmodes).toShort)

    def copyToGate(bp:ItemStack, gate:ItemStack): Unit =
        assertStackTag(gate)
        val ic = loadTileMap(bp)
        saveICToGate(ic, gate)

    def getGateMasks(stack:ItemStack) =
        ICGateLogic.unpackIO(stack.getTagCompound.getShort("masks"))

    def getConnModes(stack:ItemStack) =
        ICGateLogic.unpackConnModes(stack.getTagCompound.getShort("cmode"))

object ItemRenderICBlueprint extends IMapRenderer
:
    val background = new ResourceLocation("projectred", "textures/gui/map_background.png")

    override def shouldHandle(stack:ItemStack, inFrame:Boolean) = stack.getItem.isInstanceOf[ItemICBlueprint]

    override def renderMap(stack:ItemStack, inFrame:Boolean): Unit =
        import net.minecraft.client.renderer.GlStateManager.*
        pushMatrix()
        disableLighting()
        disableDepth()

        val ccrs = CCRenderState.instance()
        ccrs.startDrawing(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

        if inFrame then
            rotate(180.0F, 0.0F, 0.0F, 1.0F)
            scale(0.0078125F, 0.0078125F, 0.0078125F)
            translate(-64.0F, -64.0F, 0.0F)
            translate(0.0F, 0.0F, -1.0F)

            overlayBlueprintBackground(ccrs, 0)
            if ItemICBlueprint.hasICInside(stack) then
                val tm = ItemICBlueprint.loadTileMap(stack)
                if tm.nonEmpty then
                    overlayTiles(ccrs, tm)
                    overlayName(ccrs, tm.name, 0, 122)

        else
            rotate(180.0F, 0.0F, 1.0F, 0.0F)
            rotate(180.0F, 0.0F, 0.0F, 1.0F)
            scale(0.38F, 0.38F, 0.38F)
            translate(-0.5F, -0.5F, 0.0F)
            scale(0.0078125F, 0.0078125F, 0.0078125F)

            overlayBlueprintBackground(ccrs, 7)
            if ItemICBlueprint.hasICInside(stack) then
                val tm = ItemICBlueprint.loadTileMap(stack)
                if tm.nonEmpty then
                    overlayTiles(ccrs, tm)
                    overlayName(ccrs, tm.name, 0, 128)

        enableLighting()
        enableDepth()
        popMatrix()

    private def overlayBlueprintBackground(ccrs:CCRenderState, expand:Double): Unit =
        TextureUtils.changeTexture(background)
        val buffer = ccrs.getBuffer

        buffer.pos(0 - expand, 128 + expand, 0.0D).tex(0, 1).endVertex()
        buffer.pos(128 + expand, 128 + expand, 0.0D).tex(1, 1).endVertex()
        buffer.pos(128 + expand, 0 - expand, 0.0D).tex(1, 0).endVertex()
        buffer.pos(0 - expand, 0 - expand, 0.0D).tex(0, 0).endVertex()

        ccrs.draw()

    private def overlayTiles(ccrs:CCRenderState, tm:ICTileMapContainer): Unit =
        val sf = 128/scala.math.max(tm.size.width, tm.size.height)
        val rs = tm.size*sf
        val rp = Point(Size(128, 128)/2-rs/2)
        RenderICTileMap.renderOrtho(ccrs, tm, rp.x, rp.y, rs.width, rs.height, 0)

    private def overlayName(ccrs:CCRenderState, name:String, x:Double, y:Double): Unit =
        pushMatrix()
        translate(x, y, 0)
        scale(0.55, 0.55, 1)

        GuiDraw.drawRect(0, 0, GuiDraw.getStringWidth(name)+4, 11, 0x5F000000)
        GuiDraw.drawString(name, 2, 2, EnumColour.WHITE.argb, false)

        popMatrix()

class ItemICChip extends ItemCore
:
    setMaxStackSize(1)
    setHasSubtypes(true)
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    override def addInformation(stack:ItemStack, world:World, tooltip:JList[String], advanced:ITooltipFlag): Unit =
        ItemICChip.addInfo(stack, tooltip)
        if stack.getItemDamage == 1 then
            tooltip.add("Creative-mode only chip.")
            tooltip.add("Instant and free prints.")
            tooltip.add("Rightclick to add IC Gate to inventory.")

    override def onItemRightClick(world:World, player:EntityPlayer, hand:EnumHand):ActionResult[ItemStack] =
        val stack = player.getHeldItem(hand)
        if stack.getItemDamage == 1 && ItemICBlueprint.hasICInside(stack) then //creative chip
            val gate = GateDefinition.ICGate.makeStack
            ItemICBlueprint.copyToGate(stack, gate)
            if !player.inventory.addItemStackToInventory(gate) then
                player.entityDropItem(gate, player.getEyeHeight)
            return new ActionResult(EnumActionResult.SUCCESS, stack)
        super.onItemRightClick(world, player, hand)

    override def getSubItems(tab:CreativeTabs, subItems:NonNullList[ItemStack]): Unit =
        if isInCreativeTab(tab) then
            subItems.add(new ItemStack(this, 1, 0))
            subItems.add(new ItemStack(this, 1, 1))

object ItemICChip
:
    def addInfo(stack:ItemStack, list:JList[String]): Unit =
        if ItemICBlueprint.hasICInside(stack) then
            list.add(ChatFormatting.GRAY.toString+ItemICBlueprint.getICName(stack))
