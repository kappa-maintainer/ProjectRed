/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.MCDataInput
import codechicken.lib.gui.GuiDraw
import codechicken.lib.model.bakery.SimpleBlockRenderer
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import mrtjp.core.gui.*
import mrtjp.core.inventory.{ArrayWrapInventory, InvWrapper, TInventory, TInventoryCapablilityTile}
import mrtjp.core.item.ItemKey
import mrtjp.core.vec.{Point, Size}
import mrtjp.projectred.ProjectRedExpansion
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.*
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.{CraftingManager, IRecipe}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{EnumFacing, NonNullList, ResourceLocation}
import net.minecraft.world.World
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import org.lwjgl.input.Keyboard

import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class TileProjectBench extends TileMachine with TInventory with ISidedInventory with TInventoryCapablilityTile with TGuiMachine
{
    val craftHelper = new CraftingResultTestHelper
    var isPlanRecipe = false

    private var recipeNeedsUpdate = true

    override def save(tag: NBTTagCompound): Unit = {
        super.save(tag)
        saveInv(tag)
    }

    override def load(tag: NBTTagCompound): Unit = {
        super.load(tag)
        loadInv(tag)
    }

    override def read(in: MCDataInput, key: Int) = key match {
        case 1 => writePlan()
        case 2 => clearGrid(in.readInt())
        case _ => super.read(in, key)
    }

    def sendWriteButtonAction(): Unit = {
        writeStream(1).sendToServer()
    }

    def sendClearGridAction(id: Int): Unit = {
        writeStream(2).writeInt(id).sendToServer()
    }

    override def getBlock = ProjectRedExpansion.machine2

    override def doesRotate = false

    override def doesOrient = false

    //0-8 crafting, 9-26 ingredients, 27 plan, 28 result
    override protected val storage = Array.fill(28)(ItemStack.EMPTY)//new Array[ItemStack](28)

    override def getInventoryStackLimit = 64

    override def getName = "project_bench"

    override def getDisplayName = super.getDisplayName

    override def canExtractItem(slot: Int, item: ItemStack, side: EnumFacing) = 9 until 27 contains slot

    override def canInsertItem(slot: Int, item: ItemStack, side: EnumFacing) = 9 until 27 contains slot

    override def getSlotsForFace(side: EnumFacing) = 9 until 27 toArray

    override def updateServer(): Unit = {
        updateRecipeIfNeeded()
        transferExcessToStorage()
    }

    override def updateClient(): Unit = {
//        updateRecipeIfNeeded()
    }

    def updateRecipeIfNeeded(): Unit = {
        if !recipeNeedsUpdate then return
        recipeNeedsUpdate = false
        updateRecipe()
    }

    def updateRecipe(): Unit = {
        isPlanRecipe = false
        craftHelper.clear()

        if (0 until 9).exists(!getStackInSlot(_).isEmpty) then {
            craftHelper.loadInputs((0 until 9).map(getStackInSlot).toArray)
            craftHelper.findRecipeFromInputs(world)
            if craftHelper.recipe != null then
                craftHelper.loadResultFromRecipe()
        }
        else {
            val plan = getStackInSlot(27)
            if !plan.isEmpty && ItemPlan.hasRecipeInside(plan) then {
                val inputs = ItemPlan.loadPlanInputs(plan)

                craftHelper.loadInputs(inputs)
                craftHelper.findRecipeFromInputs(world)
                if craftHelper.recipe != null then {
                    isPlanRecipe = true
                    craftHelper.loadResultFromRecipe()
                }
            }
        }
    }

    def writePlan(): Unit =
    {
        updateRecipeIfNeeded()

        if craftHelper.recipe != null && !isPlanRecipe then {
            val out = craftHelper.recipe.getCraftingResult(craftHelper.invCrafting)
            if !out.isEmpty then {
                val stack = getStackInSlot(27)
                if !stack.isEmpty then
                    ItemPlan.savePlan(stack, (0 until 9).map(getStackInSlot).toArray, out)
            }
        }
    }

    def clearGrid(id: Int): Unit =
    {
        world.getEntityByID(id) match {
            case p: EntityPlayer => p.openContainer match {
                case c: ContainerProjectBench =>
                    c.transferAllFromGrid()
                    updateRecipeIfNeeded()
                case _ =>
            }
            case _ =>
        }
    }

    def transferExcessToStorage(): Unit =
    {
        var w:InvWrapper = null
        for i <- 0 until 9 do {
            val s = getStackInSlot(i)
            if !s.isEmpty && s.getCount > 1 then {
                if w == null then
                    w = InvWrapper.wrapInternal(this, 9 until 27)

                val toMove = math.max(1, s.getCount/8)
                val ins = w.injectItem(ItemKey.get(s), toMove)
                if ins > 0 then {
                    s.shrink(ins)
                    setInventorySlotContents(i, s)
                }
            }
        }
    }

    override def markDirty(): Unit = {
        super.markDirty()
        recipeNeedsUpdate = true
    }

    override def onBlockRemoval(): Unit = {
        super.onBlockRemoval()
        dropInvContents(world, getPos)
    }

    override def openGui(player: EntityPlayer): Unit = {
        GuiProjectBench.open(player, createContainer(player), _.writePos(getPos))
    }

    override def createContainer(player: EntityPlayer): ContainerProjectBench = new ContainerProjectBench(player, this)
}

class CraftingResultTestHelper
{
    var recipe:IRecipe = null
    val invCrafting = new InventoryCrafting(new NodeContainer, 3, 3)
    val invResult = new InventoryCraftResult
    private var storage:Array[ItemStack] = null

    def clear(): Unit =
    {
        recipe = null
        for i <- 0 until 9 do
            invCrafting.setInventorySlotContents(i, ItemStack.EMPTY)
        invResult.setInventorySlotContents(0, ItemStack.EMPTY)
        storage = null
    }

    def setRecipe(r:IRecipe): Unit =
    {
        recipe = r
    }

    def findRecipeFromInputs(w:World): Unit =
    {
        val recipes = CraftingManager.REGISTRY.iterator
        recipe = recipes.asScala.find(_.matches(invCrafting, w)).orNull
    }

    def loadResultFromRecipe(): Unit =
    {
        if recipe != null then
            invResult.setInventorySlotContents(0, recipe.getCraftingResult(invCrafting))
    }

    def loadInputs(inputs:Array[ItemStack]): Unit =
    {
        for i <- 0 until 9 do
            invCrafting.setInventorySlotContents(i, inputs(i).copy)
    }

    def loadStorage(storage:Array[ItemStack], copy:Boolean): Unit =
    {
        this.storage = new Array[ItemStack](storage.length)
        for i <- storage.indices do {
            val s = storage(i)
            this.storage(i) = if copy then s.copy else s
        }
    }

    def consumeAndCraft(w:World):(ItemStack, NonNullList[ItemStack]) =
    {
        if !recipe.matches(invCrafting, w) then return (ItemStack.EMPTY, null)

        val result = recipe.getCraftingResult(invCrafting)
        if result.isEmpty then return (ItemStack.EMPTY, null)

        for i <- 0 until 9 do {
            val prevInput = invCrafting.getStackInSlot(i)
            if !prevInput.isEmpty && !eatIngredient(0, { input =>
                invCrafting.setInventorySlotContents(i, input)
                val resultSame = recipe.matches(invCrafting, w) && ItemStack.areItemStacksEqual(recipe.getCraftingResult(invCrafting), result)
                invCrafting.setInventorySlotContents(i, prevInput)
                resultSame
            }) then return (ItemStack.EMPTY, null)
        }

        (result, recipe.getRemainingItems(invCrafting))
    }

    private def eatIngredient(startIdx:Int, matchFunc:ItemStack => Boolean):Boolean =
    {
        var i = startIdx
        def increment() = {
            i = (i + 1) % storage.length; i
        }
        while { {
            val stack2 = storage(i)
            if !stack2.isEmpty && matchFunc(stack2) then {
                if stack2.getCount >= 1 then {
                    stack2.shrink(1)
                    return true
                }
            }
        } ; increment() != startIdx} do ()
        false
    }

    def consumeAndCraftToStorage(w:World, slotLimit:Int):Boolean =
    {
        val (result, remaining) = consumeAndCraft(w)

        if result.isEmpty then
            return false

        val wr = InvWrapper.wrapInternal(new ArrayWrapInventory(storage, "", slotLimit))

        for stack <- Seq(result) ++ remaining.asScala.filter(!_.isEmpty) do {
            val i = wr.injectItem(ItemKey.get(stack), stack.getCount)
            if i < stack.getCount then
                return false
        }

        true
    }

    def unloadStorage(inv:IInventory, idxToSlot:Int => Int): Unit =
    {
        for i <- storage.indices do
            inv.setInventorySlotContents(idxToSlot(i), storage(i))
    }
}

class SlotProjectCrafting(player: EntityPlayer, tile: TileProjectBench, idx: Int, x: Int, y: Int)
    extends SlotCrafting(player, tile.craftHelper.invCrafting, tile.craftHelper.invResult, idx, x, y) with TSlot3 {

    override def canTakeStack(player: EntityPlayer): Boolean = {
        if tile.isPlanRecipe then {
            val storage = (9 until 27).map {tile.getStackInSlot}.toArray

            tile.craftHelper.loadStorage(storage, true)

            val (res, _) = tile.craftHelper.consumeAndCraft(player.world)
            return !res.isEmpty
        }

        //copied from super for obfuscation bug
        canRemoveDelegate()
    }

    override def onTake(player:EntityPlayer, stack:ItemStack): ItemStack =
    {
        val order = (9 until 27) ++ (0 until 9)
        val storage = order.map {tile.getStackInSlot}.toArray

        tile.craftHelper.loadStorage(storage, copy = true)
        val (_, rem) = tile.craftHelper.consumeAndCraft(player.world)
        tile.craftHelper.unloadStorage(tile, order.apply)

        FMLCommonHandler.instance().firePlayerCraftingEvent(player, stack, tile.craftHelper.invCrafting)
        onCrafting(stack)

        for i <- 0 until 9 do {
            val istack = tile.getStackInSlot(i)
            val rstack = rem.get(i)
            if !rstack.isEmpty then {
                if !tile.isPlanRecipe && istack.isEmpty then {
                    tile.setInventorySlotContents(i, rstack)
                } else if !addToStorageSlots(rstack) && !player.inventory.addItemStackToInventory(rstack) then
                    player.dropItem(rstack, false)
            }
        }

        def addToStorageSlots(stack:ItemStack):Boolean = {
            val w = InvWrapper.wrapInternal(tile, 9 until 27)
            val item = ItemKey.get(stack)
            stack.shrink(w.injectItem(item, stack.getCount))
            stack.isEmpty
        }

        tile.updateRecipe()
        stack
    }

    //Following 3 methods copy-pasted from TSlot3 for obfuscation issues
    override def getSlotStackLimit: Int = slotLimitCalculator()

    override def isItemValid(stack: ItemStack): Boolean = canPlaceDelegate(stack)

    override def onSlotChanged(): Unit = {
        super.onSlotChanged()
        slotChangeDelegate()
        slotChangeDelegate2()
    }
}

class ContainerProjectBench(player: EntityPlayer, tile: TileProjectBench) extends NodeContainer {
    {
        for ((x, y), i) <- GuiLib.createSlotGrid(48, 18, 3, 3, 0, 0).zipWithIndex do
            addSlotToContainer(new Slot3(tile, i, x, y))

        for ((x, y), i) <- GuiLib.createSlotGrid(8, 76, 9, 2, 0, 0).zipWithIndex do
            addSlotToContainer(new Slot3(tile, i + 9, x, y))

        val plan = new Slot3(tile, 27, 17, 36)
        plan.canPlaceDelegate = {
            _.getItem.isInstanceOf[ItemPlan]
        }
        plan.slotLimitCalculator = { () => 1 }
        addSlotToContainer(plan)

        val output = new SlotProjectCrafting(player, tile, 28, 143, 36)
        output.canPlaceDelegate = { _ => false }
        addSlotToContainer(output)

        addPlayerInv(player, 8, 126)
    }



    def transferAllFromGrid(): Unit = {
        for i <- 0 until 9 do if getSlot(i).getHasStack then
            transferStackInSlot(player, i)
        detectAndSendChanges()
    }

    override def slotClick(id: Int, mouse: Int, shift: ClickType, player: EntityPlayer) = {
        var mode = shift
        if id == 28 && mode == ClickType.PICKUP_ALL then mode = ClickType.PICKUP
        super.slotClick(id, mouse, mode, player)
    }

    override def transferStackInSlot(player:EntityPlayer, i:Int):ItemStack = {
        if i == 28 && !getSlot(28).canTakeStack(player) then
            ItemStack.EMPTY
        else
            super.transferStackInSlot(player, i)
    }

    override def doMerge(stack: ItemStack, from: Int): Boolean = {
        if 0 until 9 contains from then //crafting grid
        {
            if tryMergeItemStack(stack, 9, 27, false) then return true //merge to storage
            if tryMergeItemStack(stack, 29, 65, false) then return true //merge to inventory)
        }
        else if 9 until 27 contains from then //storage
        {
            if stack.getItem.isInstanceOf[ItemPlan] then {
                if !getSlot(27).getStack.isEmpty && ItemKey.get(getSlot(27).getStack) != ItemKey.get(stack) then
                    transferStackInSlot(player, 27) //transfer existing stack

                if tryMergeItemStack(stack, 27, 28, false) then return true //merge to plan
            }
            if tryMergeItemStack(stack, 29, 65, false) then return true //merge to inventory
        }
        else if from == 27 then //plan slot
        {
            if tryMergeItemStack(stack, 9, 27, true) then return true //merge to storage
            if tryMergeItemStack(stack, 29, 65, false) then return true //merge to inventory)
        }
        else if from == 28 then //output slot
        {
            if tryMergeItemStack(stack, 29, 65, true) then return true //merge to inventory
            if tryMergeItemStack(stack, 9, 27, true) then return true //merge to storage
        }
        else if 29 until 65 contains from then //player inventory
        {
            if stack.getItem.isInstanceOf[ItemPlan] then {
                if !getSlot(27).getStack.isEmpty && ItemKey.get(getSlot(27).getStack) != ItemKey.get(stack) then
                    transferStackInSlot(player, 27) //transfer existing stack

                if tryMergeItemStack(stack, 27, 28, false) then return true //merge to plan
            }
            if tryMergeItemStack(stack, 9, 27, false) then return true //merge to storage
        }

        false
    }
}

class GuiProjectBench(tile: TileProjectBench, c: ContainerProjectBench) extends NodeGui(c, 176, 208) {
    {
        val write = new IconButtonNode {
            override def drawButton(mouseover: Boolean): Unit = {
                TextureUtils.changeTexture(GuiProjectBench.background)
                GuiDraw.drawTexturedModalRect(position.x, position.y, 176, 0, 14, 14)
            }
        }
        write.position = Point(18, 56)
        write.size = Size(14, 14)
        write.clickDelegate = { () => tile.sendWriteButtonAction() }
        addChild(write)

        val clear = new IconButtonNode {
            override def drawButton(mouseover: Boolean): Unit = {
                TextureUtils.changeTexture(GuiProjectBench.background)
                GuiDraw.drawTexturedModalRect(position.x, position.y, 176, 15, 8, 8)
            }
        }
        clear.position = Point(37, 17)
        clear.size = Size(8, 8)
        clear.clickDelegate = { () => tile.sendClearGridAction(Minecraft.getMinecraft.player.getEntityId) }
        addChild(clear)
    }

    override def update_Impl(): Unit =
    {
        tile.updateRecipeIfNeeded()
    }

    override def drawBack_Impl(mouse: Point, rframe: Float): Unit = {
        TextureUtils.changeTexture(GuiProjectBench.background)
        GuiDraw.drawTexturedModalRect(0, 0, 0, 0, size.width, size.height)

        val plan = tile.getStackInSlot(27)
        if tile.isPlanRecipe && !plan.isEmpty then {
            val inputs = ItemPlan.loadPlanInputs(plan)
            for ((x, y), i) <- GuiLib.createSlotGrid(48, 18, 3, 3, 0, 0).zipWithIndex do {
                val stack = inputs(i)
                if !stack.isEmpty then {
                    GuiDraw.drawRect(x, y, 16, 16, EnumColour.GRAY.argb)
                    ItemDisplayNode.renderItem(Point(x, y), Size(16, 16), zPosition, false, stack)
                }
            }
        }

        GuiDraw.drawString("Project Bench", 8, 6, EnumColour.GRAY.argb, false)
        GuiDraw.drawString("Inventory", 8, 116, EnumColour.GRAY.argb, false)
    }

    override def drawFront_Impl(mouse: Point, rframe: Float): Unit = {
        if Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) then
            GuiProjectBench.drawPlanOutputOverlay(c.slots)
    }
}

object GuiProjectBench extends TGuiFactory {
    val background = new ResourceLocation("projectred", "textures/gui/project_bench.png")

    override def getID = ExpansionProxy.projectbenchGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player: EntityPlayer, data: MCDataInput) = {
        player.world.getTileEntity(data.readPos) match {
            case t: TileProjectBench => new GuiProjectBench(t, t.createContainer(player))
            case _ => null
        }
    }

    def drawPlanOutputOverlay(slots:Iterable[TSlot3]): Unit = {
        for slot <- slots do if slot.getHasStack then {
            val stack = slot.getStack
            if ItemPlan.hasRecipeInside(stack) then {
                val output = ItemPlan.loadPlanOutput(stack)
                GuiDraw.drawRect(slot.xPos, slot.yPos, 16, 16, EnumColour.LIGHT_BLUE.argb(0xCC))
                ItemDisplayNode.renderItem(Point(slot.xPos + 1, slot.yPos + 1), Size(14, 14), 0, true, output)
            }
        }
    }
}

object RenderProjectBench extends SimpleBlockRenderer
{
    import org.apache.commons.lang3.tuple.Triple

    var bottom: TextureAtlasSprite = scala.compiletime.uninitialized
    var top: TextureAtlasSprite = scala.compiletime.uninitialized
    var side1: TextureAtlasSprite = scala.compiletime.uninitialized
    var side2: TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT: UVTransformation = scala.compiletime.uninitialized

    override def getWorldTransforms(state: IExtendedBlockState) = Triple.of(0, 0, iconT)
    override def getItemTransforms(stack: ItemStack) = Triple.of(0, 0, iconT)
    override def shouldCull() = true

    override def registerIcons(map: TextureMap): Unit = {
        bottom = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/projectbench/bottom"))
        top = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/projectbench/top"))
        side1 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/projectbench/side1"))
        side2 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/projectbench/side2"))

        iconT = new MultiIconTransformation(bottom, top, side1, side1, side2, side2)
    }
}
