/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import java.util.ArrayList as JAList

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.gui.GuiDraw
import codechicken.lib.model.bakery.{CCBakeryModel, SimpleBlockRenderer}
import codechicken.lib.render.buffer.BakingVertexBuffer
import codechicken.lib.render.item.IItemRenderer
import codechicken.lib.render.{CCModel, CCRenderState, OBJParser}
import codechicken.lib.texture.TextureUtils
import codechicken.lib.util.{TransformUtils, VertexDataUtils}
import codechicken.lib.vec.*
import codechicken.lib.vec.uv.{IconTransformation, MultiIconTransformation, UVTransformation}
import com.google.common.collect.ImmutableList
import com.mojang.realmsclient.gui.ChatFormatting.{BOLD, RED, RESET}
import mrtjp.core.gui.*
import mrtjp.core.inventory.{InvWrapper, TInventory, TInventoryCapablilityTile}
import mrtjp.core.item.{ItemKey, ItemKeyStack}
import mrtjp.core.vec.{Point, Rect, Size, Vec2}
import mrtjp.projectred.ProjectRedCore.log
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.integration.ComponentStore
import mrtjp.projectred.transmission.WireDef
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.block.model.*
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.{Blocks, Items}
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.{IBlockAccess, World}
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.opengl.GL11.*

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Map as MMap, Set as MSet}

class TileICPrinter extends TileICMachine with TInventory with TInventoryCapablilityTile
:
    var progress = 0.0
    var speed = 0.0
    var isWorking = false
    var inputICState = 0 // 0 - none, 1 - blank, 2 - written

    var externalItems = Set[ItemKey]()
    var watchers = MSet[EntityPlayer]()

    var requirementsDirty = true
    var requirements = Seq[ItemKeyStack]()

    override def getDisplayName = super.getDisplayName

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setFloat("prog", progress.toFloat)
        tag.setFloat("sp", speed.toFloat)
        tag.setBoolean("w", isWorking)
        tag.setByte("in", inputICState.toByte)
        saveInv(tag)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        progress = tag.getFloat("prog")
        speed = tag.getFloat("sp")
        isWorking = tag.getBoolean("w")
        inputICState = tag.getByte("in")
        loadInv(tag)

    override def writeDesc(out:MCDataOutput): Unit =
        super.writeDesc(out)
        out.writeFloat(progress.toFloat)
        out.writeFloat(speed.toFloat)
        out.writeBoolean(isWorking)
        out.writeByte(inputICState)

    override def readDesc(in:MCDataInput): Unit =
        super.readDesc(in)
        progress = in.readFloat()
        speed = in.readFloat()
        isWorking = in.readBoolean()
        inputICState = in.readByte()

    override def read(in:MCDataInput, key:Int) = key match
        case 4 => isWorking = false
        case 5 =>
            isWorking = true
            progress = in.readFloat()
            speed = in.readFloat()
        case 6 => inputICState = in.readByte()
        case 7 =>
            externalItems = Set.empty
            for i <- 0 until in.readInt() do
                externalItems += ItemKey.get(in.readItemStack())
        case _ => super.read(in, key)

    def sendStartWorking(): Unit =
        val out = writeStream(5)
        out.writeFloat(progress.toFloat)
        out.writeFloat(speed.toFloat)
        out.sendToChunk(this)

    def sendStopWorking(): Unit =
        writeStream(4).sendToChunk(this)

    def sendInputICStateUpdate(): Unit =
        writeStream(6).writeByte(inputICState).sendToChunk(this)

    def sendExternalItemMap(players:Iterable[EntityPlayer]): Unit =
        val out = writeStream(7).writeInt(externalItems.size)
        for item <- externalItems do
            out.writeItemStack(item.makeStack(1))
        out.sendToChunk(this)

    //0 - 17 = ingredients
    //18 = Blueprint input
    //19 = IC Input
    //20 = Output
    override def isItemValidForSlot(slot:Int, item:ItemStack) =
        if slot == 18 then item.getItem.isInstanceOf[ItemICBlueprint] && ItemICBlueprint.hasICInside(item)
        else if slot == 19 then item.getItem.isInstanceOf[ItemICChip]
        else if slot == 20 then false
        else true

    override protected val storage = Array.fill(21)(ItemStack.EMPTY)//new Array[ItemStack](21)
    override def getInventoryStackLimit = 64
    override def getName = "icprinter"

    override def getBlockFaceShape(side:Int) = BlockFaceShape.UNDEFINED//TODO, Do the do with the thing and the do.

    override def updateServer(): Unit =
        if isWorking then
            if world.getTotalWorldTime%10 == 0 && !canStart then
                doStop()
            else
                progress += speed
                if progress >= 1.0 then
                    doStop()
                    if canStart then onFinished()

        if !isWorking && world.getTotalWorldTime%10 == 0 && canStart then //delay check, can be expensive
            doStart()

    def canStart =
        if world.getTotalWorldTime%20 == 0 then
            checkIngredients() && checkOutputClear && checkInputIC && checkBlueprint && checkBlueprintFlags
        else checkOutputClear && checkInputIC && checkBlueprint && checkBlueprintFlags && checkIngredients() //use cheaper checks as fail-fast

    def checkBlueprint =
        val stack = getStackInSlot(18)
        !stack.isEmpty && stack.getItem.isInstanceOf[ItemICBlueprint] &&
                ItemICBlueprint.hasICInside(stack)

    def checkBlueprintFlags =
        val (_, err) = ItemICBlueprint.loadFlags(getStackInSlot(18))
        err == 0

    private def checkInputIC =
        val stack = getStackInSlot(19)
        !stack.isEmpty && stack.getItem.isInstanceOf[ItemICChip]

    private def checkOutputClear = getStackInSlot(20).isEmpty

    private def checkIngredients():Boolean =
        val ic = getStackInSlot(19)
        if !ic.isEmpty && ic.getItemDamage == 1 then return true

        val oldMap = externalItems
        val required = getRequiredResources

        var hasEnough = true
        for r <- required do if !containsEnoughOf(r) then
            hasEnough = false

        if externalItems != oldMap then sendExternalItemMap(watchers)
        hasEnough

    def getRequiredResources =
        if requirementsDirty then
            val stack = getStackInSlot(18)
            requirements = if !stack.isEmpty && ItemICBlueprint.hasICInside(stack) then
                val ic = ItemICBlueprint.loadTileMap(stack)
                TileICPrinter.resolveResources(ic)
            else Seq()
            requirementsDirty = false
        requirements

    def containsEnoughOf(stack:ItemKeyStack):Boolean =
        var a = (0 until 18).map(getStackInSlot).filter(s => !s.isEmpty && ItemKey.get(s) == stack.key).map(_.getCount).sum
        if world.isRemote then externalItems.contains(stack.key)
        else
            externalItems -= stack.key
            for s <- 0 until 6 if s != 1 && a < stack.stackSize do
                val side = EnumFacing.VALUES(s)
                val inv = InvWrapper.wrap(world, pos.offset(side), side.getOpposite)
                if inv != null then a += inv.getItemCount(stack.key)
            val enough = a >= stack.stackSize
            if enough then externalItems += stack.key
            enough

    def eatResource(stack:ItemKeyStack): Unit =
        var left = stack.stackSize
        for i <- 0 until 18 if left > 0 do
            val s = getStackInSlot(i)
            if !s.isEmpty && stack.key == ItemKey.get(s) then
                val toEat = math.min(left, s.getCount)
                left -= toEat
                s.shrink(toEat)
                if s.getCount <= 0 then setInventorySlotContents(i, ItemStack.EMPTY)
                else setInventorySlotContents(i, s)
        for s <- 0 until 6 if s != 1 && left > 0 do
            val side = EnumFacing.VALUES(s)
            val inv = InvWrapper.wrap(world, pos.offset(side), side.getOpposite)
            if inv != null then left -= inv.extractItem(stack.key, left)

    def doStart(): Unit =
        isWorking = true
        progress = 0.0
        speed = if getStackInSlot(19).getItemDamage == 1 then 0.05 else 0.0005
        sendStartWorking()

    def doStop(): Unit =
        isWorking = false
        progress = 0.0
        speed = 0.0
        sendStopWorking()

    def onFinished(): Unit =

        val bp = getStackInSlot(18)
        val chip = getStackInSlot(19)

        if chip.getItemDamage != 1 then getRequiredResources.foreach(eatResource)

        ItemICBlueprint.copyIC(bp, chip)
        setInventorySlotContents(19, ItemStack.EMPTY)
        setInventorySlotContents(20, chip)

    override def markDirty(): Unit =
        super.markDirty()
        requirementsDirty = true
        if !world.isRemote then
            if !canStart && isWorking then doStop()
            val oldICState = inputICState

            if !getStackInSlot(20).isEmpty then inputICState = 2
            else
                val s = getStackInSlot(19)
                if !s.isEmpty then
                    if ItemICBlueprint.hasICInside(s) then inputICState = 2
                    else inputICState = 1
                else inputICState = 0

            if inputICState != oldICState then sendInputICStateUpdate()

    override def onBlockActivated(player:EntityPlayer, side:Int):Boolean =
        if super.onBlockActivated(player, side) then return true
        if !world.isRemote then
            GuiICPrinter.open(player, createContainer(player), _.writePos(pos))
        true

    def createContainer(player:EntityPlayer) =
        val c = new ContainerPrinter(player, this)
        c.startWatchDelegate = {p =>
            watchers += p
            sendExternalItemMap(MSet(p))
        }
        c.stopWatchDelegate = {watchers -= _}
        c

    override def onBlockRemoval(): Unit =
        super.onBlockRemoval()
        dropInvContents(world, pos)

    //Client-side render things
    import TileICPrinter.*
    var lProgress = 1.0
    var lSpeed = 0.0
    var lState = LERPTOREST
    private var lerpCount = 0

    override def updateClient(): Unit =
        if isWorking then
            progress += speed
            progress = math.min(progress, 1.0)

        lState match
            case REST =>
                lProgress = 0
                lSpeed = 0
                if isWorking then lState = `REALTIME`
            case LERPTOREST =>
                lSpeed = -0.025
                lProgress += lSpeed
                if lProgress <= 0 then lState = REST
                else if isWorking then lState = LERPTOREALTIME
            case LERPTOREALTIME =>
                if lProgress > progress then
                    lSpeed = -0.16
                    lProgress += lSpeed
                    if lProgress <= progress then lState = REALTIME
                else
                    lSpeed = 0.16
                    lProgress += lSpeed
                    if lProgress >= progress then lState = REALTIME
            case REALTIME =>
                if progress >= 1.0 || !isWorking then
                    lerpCount = 0
                    lState = FIN
                else
                    lProgress = progress
                    lSpeed = speed
            case FIN =>
                lSpeed = 0
                lerpCount += 1
                if lerpCount >= 25 then lState = LERPTOREST

object TileICPrinter
:
    //render states
    val REST = 0
    val LERPTOREST = 1
    val LERPTOREALTIME = 2
    val REALTIME = 3
    val FIN = 4

    private var gRec = Map[(ItemKey), Seq[ItemKey]]()

    def cacheRecipe(key:ItemKey): Unit =
        val recipes = CraftingManager.REGISTRY.iterator
        for r <- recipes.asScala do try
            val out = ItemKey.get(r.getRecipeOutput)
            if out == key then
                //TODO, We need to do proper ingredient matching.
                val inputs = r.getIngredients.asScala.map(_.getMatchingStacks).filterNot(_ == null).map(i => ItemKey.get(i.head))
//                    r match
//                {
//                    case s:ShapedRecipes => s.recipeItems.toSeq.filterNot(_.getMatchingStacks.isEmpty).map(ItemKey.get)
//
//                    case s:ShapelessRecipes => s.recipeItems.toSeq.filterNot(_.getMatchingStacks.isEmpty).map(ItemKey.get)
//
//                    case s:ShapedOreRecipe => s.getInput.toSeq.flatMap {
//                        case s:ItemStack => Seq(s)
//                        case a:JAList[_] => a.toSeq.asInstanceOf[Seq[ItemStack]]
//                        case _ => Seq.empty
//                    }.map(ItemKey.get)
//
//                    case s:ShapelessOreRecipe => s.getInput.toSeq.flatMap {
//                        case s:ItemStack => Seq(s)
//                        case a:JAList[_] => a.toSeq.asInstanceOf[Seq[ItemStack]]
//                        case _ => Seq.empty
//                    }.map(ItemKey.get)
//
//                    case _ => Seq.empty
//                }
                if inputs.nonEmpty then
                    gRec += key -> inputs.toSeq
        catch
            case e:Exception =>
                log.error(s"Some mod messed up. The recipe $r has a null output.")
        gRec += key -> Seq.empty

    def getOrCacheComponents(in:ItemStack):Seq[ItemKey] =
        val key = ItemKey.get(in)
        if !gRec.contains(key) then cacheRecipe(key)
        gRec(key)

    def resolveResources(tmap:ICTileMapContainer) =
        val map = MMap[ItemKey, Double]()

        import mrtjp.core.item.ItemKeyConversions.*
        def add(key:ItemKey, amount:Double): Unit =
            val c = map.getOrElse(key, 0.0)
            map(key) = c+amount

        def addComponents(stack:ItemStack): Unit =
            getOrCacheComponents(stack).foreach(add(_, 0.25))

        import mrtjp.projectred.fabrication.{ICGateDefinition as gd}

        for part <- tmap.tiles.values do part match
//            case p:TorchICPart => add(new ItemStack(Blocks.REDSTONE_TORCH), 0.25)
            case p:LeverICTile => add(new ItemStack(Blocks.LEVER), 0.25)
            case p:ButtonICTile => add(new ItemStack(Blocks.STONE_BUTTON), 0.25)
            case p:AlloyWireICTile => add(WireDef.RED_ALLOY.makeStack, 0.25)
            case p:InsulatedWireICTile => add(WireDef.INSULATED_WIRES(p.colour&0xFF).makeStack, 0.25)
            case p:BundledCableICTile => add(WireDef.BUNDLED_WIRES((p.colour+1)&0xFF).makeStack, 0.25)
            case p:GateICTile => gd.apply(p.subID) match
                case gd.IOSimple =>
                    add(new ItemStack(Items.GOLD_NUGGET), 1)
                    add(PartDefs.PLATE.makeStack, 1.50)
                    add(PartDefs.CONDUCTIVEPLATE.makeStack, 0.50)
                case gd.IOAnalog =>
                    add(new ItemStack(Items.GOLD_NUGGET), 1)
                    add(PartDefs.PLATE.makeStack, 1.50)
                    add(new ItemStack(Items.REDSTONE), 0.25)
                    add(PartDefs.CONDUCTIVEPLATE.makeStack, 0.25)
                case gd.IOBundled =>
                    add(new ItemStack(Items.GOLD_NUGGET), 1)
                    add(PartDefs.PLATE.makeStack, 1.50)
                    add(PartDefs.BUNDLEDPLATE.makeStack, 0.25)
                    add(PartDefs.CONDUCTIVEPLATE.makeStack, 0.25)
                case d if d.intDef != null => addComponents(d.intDef.makeStack)
                case _ =>
            case _ =>

        map.map(e => ItemKeyStack.get(e._1, e._2.ceil.toInt)).toSeq.sorted

class ContainerPrinter(player:EntityPlayer, tile:TileICPrinter) extends NodeContainer
:
    var i = 0
    for (x, y) <- GuiLib.createSlotGrid(8, 75, 9, 2, 0, 0) do
        addSlotToContainer(new Slot3(tile, i, x, y))
        i += 1
    addSlotToContainer({val s = new Slot3(tile, 18, 63, 20); s.slotLimitCalculator = {() => 1}; s})
    addSlotToContainer({val s = new Slot3(tile, 19, 63, 46); s.slotLimitCalculator = {() => 1}; s})
    addSlotToContainer({val s = new Slot3(tile, 20, 134, 33); s.slotLimitCalculator = {() => 1}; s})

    addPlayerInv(player, 8, 119)

    //0 - 17 = ingredients
    //18 = Blueprint input
    //19 = IC Input
    //20 = Output
    override def doMerge(stack:ItemStack, from:Int):Boolean =
        if from == 20 then
            if tryMergeItemStack(stack, 21, 57, true) then return true
        else if 0 until 20 contains from then
            if tryMergeItemStack(stack, 21, 57, false) then return true
        else
            stack.getItem match
                case i:ItemICBlueprint => if tryMergeItemStack(stack, 18, 19, false) then return true
                case i:ItemICChip => if tryMergeItemStack(stack, 19, 20, false) then return true
                case _ =>

            if tryMergeItemStack(stack, 0, 18, false) then return true
        false

class GuiICPrinter(c:ContainerPrinter, tile:TileICPrinter) extends NodeGui(c, 176, 201)
:
    var list:ItemListNode = null
    val flagBox = new Rect(86, 19, 8, 8)

    var hasErrors = false

    
    val clip = new ClipNode
    clip.position = Point(8, 17)
    clip.size = Size(48, 48)
    addChild(clip)

    val pan = new PanNode
    pan.size = Size(48, 48)
    pan.scrollModifier = Vec2(0, 1)
    pan.scrollBarHorizontal = false
    clip.addChild(pan)

    list = new ItemListNode
    list.zPosition = -0.01
    list.itemSize = Size(14, 14)
    list.displayNodeFactory = {stack =>
        val d = new ItemDisplayNode
        d.zPosition = -0.01
        d.backgroundColour = if tile.containsEnoughOf(stack) then
            EnumColour.LIME.argb(0x44) else EnumColour.RED.argb(0x44)
        d
    }
    pan.addChild(list)
    list.items = tile.getRequiredResources
    list.reset()

    override def update_Impl(): Unit =
        if mcInst.world.getTotalWorldTime%10 == 0 then
            list.items = tile.getRequiredResources
            list.reset()

        hasErrors = tile.checkBlueprint && !tile.checkBlueprintFlags

    override def drawBack_Impl(mouse:Point, rframe:Float): Unit =
        TextureUtils.changeTexture(GuiICPrinter.background)
        GuiDraw.drawTexturedModalRect(0, 0, 0, 0, 176, 201)
        if tile.isWorking then
            val dx = 37*tile.progress
            GuiDraw.drawTexturedModalRect(86, 32, 176, 0, dx.toInt, 18)
        if hasErrors then
            GuiDraw.drawTexturedModalRect(flagBox.x, flagBox.y, 176, 19, flagBox.width, flagBox.height) //draw the error symbol

        GuiDraw.drawString("IC Printer", 8, 6, EnumColour.GRAY.argb, false)

    override def drawFront_Impl(mouse:Point, rframe:Float): Unit =
        if hasErrors then
            val m2 = convertPointFromScreen(mouse)
            if flagBox.contains(m2) then
                GuiDraw.drawMultiLineTip(ItemStack.EMPTY, m2.x+12, m2.y-12,
                    Seq(s"$RED$BOLD" + "X" + s"$RESET blueprint contains errors").asJava)

object GuiICPrinter extends TGuiFactory
:
    val background = new ResourceLocation("projectred", "textures/gui/ic_printer.png")

    override def getID = FabricationProxy.icPrinterGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer, data:MCDataInput) =
        player.world.getTileEntity(data.readPos()) match
            case t:TileICPrinter => new GuiICPrinter(t.createContainer(player), t)
            case _ => null

object RenderICPrinter extends SimpleBlockRenderer
:
    import java.lang.{Integer as JInt}
    import java.util.{List as JList}

    import BlockICMachine.*
    import org.apache.commons.lang3.tuple.Triple

    val lowerBoxes =
        val array = new Array[CCModel](4)
        val box = CCModel.quadModel(24).generateBlock(0, new Cuboid6(0, 0, 0, 1, 10/16D, 1))
        for r <- 0 until 4 do
            val m = box.copy.apply(Rotation.quarterRotations(r).at(Vector3.center))
            m.computeNormals()
            m.shrinkUVs(0.0005)
            array(r) = m
        array

    var headIcon:TextureAtlasSprite = scala.compiletime.uninitialized
    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var side1:TextureAtlasSprite = scala.compiletime.uninitialized
    var side2:TextureAtlasSprite = scala.compiletime.uninitialized
    var top:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT:UVTransformation = scala.compiletime.uninitialized

    override def handleState(state:IExtendedBlockState, world: IBlockAccess, pos: BlockPos):IExtendedBlockState = world.getTileEntity(pos) match
        case t:TileICPrinter =>
            state.withProperty(UNLISTED_ROTATION_PROPERTY, t.rotation.asInstanceOf[JInt])
        case _ => state

    override def getWorldTransforms(state:IExtendedBlockState) =
        val rot = state.getValue(UNLISTED_ROTATION_PROPERTY)
        Triple.of(0, rot, iconT)

    override def getItemTransforms(stack:ItemStack) = Triple.of(0, 0, iconT)

    override def shouldCull() = false

    override def bakeQuads(face:EnumFacing, state:IExtendedBlockState):JList[BakedQuad] =
        val buffer = BakingVertexBuffer.create
        val worldData = getWorldTransforms(state)
        val ccrs = CCRenderState.instance

        ccrs.reset()
        ccrs.startDrawing(0x7, DefaultVertexFormats.ITEM, buffer)
        lowerBoxes(worldData.getMiddle).render(ccrs, worldData.getRight)
        buffer.finishDrawing()

        val quads = buffer.bake
        if face == null && !shouldCull then
            return quads
        else if face != null then
            return VertexDataUtils.sortFaceData(quads).get(face)

        ImmutableList.of()

    override def bakeItemQuads(face:EnumFacing, stack:ItemStack):JList[BakedQuad] =
        val buffer = BakingVertexBuffer.create
        val worldData = getItemTransforms(stack)
        val ccrs = CCRenderState.instance

        ccrs.reset()
        ccrs.startDrawing(0x7, DefaultVertexFormats.ITEM, buffer)
        lowerBoxes(worldData.getMiddle).render(ccrs, worldData.getRight)
        buffer.finishDrawing()

        val quads = buffer.bake
        if face == null && !shouldCull then
            return quads
        else if face != null then
            return VertexDataUtils.sortFaceData(quads).get(face)
        ImmutableList.of()

    override def registerIcons(reg:TextureMap): Unit =
        def register(t:String) = reg.registerSprite(new ResourceLocation("projectred:blocks/fabrication/printer/"+t))

        headIcon = register("printerhead")
        bottom = register("bottom")
        side1 = register("side1")
        side2 = register("side2")
        top = register("top")

        iconT = new MultiIconTransformation(bottom, top, side2, side1, side2, side2)

object RenderICPrinterDynamic extends TileEntitySpecialRenderer[TileICPrinter]
:
    var models = getMods

    def getMods =
        val map = OBJParser.parseModels(new ResourceLocation("projectred:textures/obj/fabrication/printer.obj"), 7, null).asScala.toMap
        map.values.foreach { m =>
            m.verts = m.backfacedCopy.verts
            m.apply(new Translation(8/16D, 10/16D, 8/16D))
            m.computeNormals()
            m.shrinkUVs(0.0005)
        }
        map

    var progress = 0.0
    var speed = 0.0
    var frame = 0.0
    var icState = 0
    var rasterMode = false

    override def render(tile:TileICPrinter, x:Double, y:Double, z:Double, partialTicks:Float, destroyStage:Int, alpha:Float): Unit =
        val ptile = tile.asInstanceOf[TileICPrinter]

        progress = ptile.lProgress
        speed = ptile.lSpeed
        frame = partialTicks
        icState = ptile.inputICState
        rasterMode = ptile.lState == TileICPrinter.REALTIME

        val t = ptile.rotationT `with` new Translation(x, y, z)
        val iconT = new MultiIconTransformation(RenderICPrinter.headIcon)
        val ccrs = CCRenderState.instance()
        TextureUtils.bindBlockTexture()

        enableBlend()
        blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        ccrs.reset()
        ccrs.pullLightmap()
        ccrs.startDrawing(0x7, DefaultVertexFormats.ITEM)
        renderPrinter(ccrs, t, iconT)
        ccrs.draw()

        disableBlend()

    def renderPrinter(ccrs:CCRenderState, t:Transformation, iconT:UVTransformation): Unit =
        if icState != 0 then renderICChip(ccrs, t)
        renderFrame(ccrs, t, iconT)
        renderShaft(ccrs, t, iconT)
        renderGlass(ccrs, t, iconT)

    def renderICChip(ccrs:CCRenderState, t:Transformation): Unit =
        import ComponentStore.*
        icChip.render(ccrs, Rotation.quarterRotations(2) `with` new Translation(0.5, 9.5/16D, 0.5) `with` t,
            new IconTransformation(if icState == 1 then icChipIconOff else icChipIcon))

    def renderFrame(ccrs:CCRenderState, t:Transformation, iconT:UVTransformation): Unit =
        models("frame").render(ccrs, t, iconT)

    def renderShaft(ccrs:CCRenderState, t:Transformation, iconT:UVTransformation): Unit =
        val min = -4.5/16D
        val max = 4.5/16D
        val p = progress+speed*frame
        val subT = new Translation(0, 0, min+(max-min)*p) `with` t

        models("shaft").render(ccrs, subT, iconT)
        renderHead(ccrs, subT, iconT)

    def renderHead(ccrs:CCRenderState, t:Transformation, iconT:UVTransformation): Unit =
        val amp = 3.5/16D
        val freq = 900
        val p = progress+speed*frame
        val trans = if rasterMode then math.cos(p*freq)*amp else amp*2*p-amp
        val subT = new Translation(trans, 0, 0) `with` t
        models("head").render(ccrs, subT, iconT)

    def renderGlass(ccrs:CCRenderState, t:Transformation, iconT:UVTransformation): Unit =
        models("glass").render(ccrs, t, iconT)
object RenderICPrinterItem extends IItemRenderer:

    //A single instance of this across reloads is fine.
    val wrapped = new CCBakeryModel()
    var entity:EntityLivingBase = scala.compiletime.uninitialized
    var world:World = scala.compiletime.uninitialized

    lazy val overrideList = new ItemOverrideList():
        override def handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World, entity: EntityLivingBase) =
            RenderICPrinterItem.entity = entity
            RenderICPrinterItem.world = if world == null then if entity == null then null else entity.world else null
            originalModel

    override def renderItem(stack: ItemStack, transformType: ItemCameraTransforms.TransformType): Unit =
        val model = wrapped.getOverrides.handleItemState(wrapped, stack, world, entity)
        Minecraft.getMinecraft.getRenderItem.renderModel(model, stack)
        val ccrs = CCRenderState.instance()
        val matrix = new Matrix4()
        val iconT = new MultiIconTransformation(RenderICPrinter.headIcon)

        ccrs.reset()
        ccrs.pullLightmap()
        ccrs.startDrawing(0x7, DefaultVertexFormats.ITEM)
        RenderICPrinterDynamic.renderPrinter(ccrs, matrix, iconT)
        ccrs.draw()

    override def getTransforms = TransformUtils.DEFAULT_BLOCK

    override def isAmbientOcclusion = true

    override def isGui3d = true

    override def getOverrides = overrideList
