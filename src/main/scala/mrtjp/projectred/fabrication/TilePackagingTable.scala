/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.MCDataInput
import codechicken.lib.gui.GuiDraw
import mrtjp.core.gui.*
import mrtjp.core.inventory.{TInventory, TInventoryCapablilityTile}
import mrtjp.core.vec.Point
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.integration.GateDefinition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*

/**
  * Packages a valid die together with a ring of core plates into a finished
  * fabricated IC gate.
  *
  * High-version equivalent: `PackagingTableBlockEntity`. The 3x3 grid takes
  * the die in the centre; the four edge slots take the plate matching that
  * side's interface type and the four corners take plain plates. Slots whose
  * contents do not match are reported to the client so the GUI can highlight
  * them.
  *
  * 20 seconds of work at 100W per tick.
  */
class TilePackagingTable extends TGuiFabricationMachine with TInventory with TInventoryCapablilityTile:

    /** Centre of the 3x3 grid. */
    val dieSlot = 4

    /** Output slot, just past the grid. */
    val outputSlot = 9

    /** Edge slot for each IC side, in the IC's own rotation order (top/right/bottom/left). */
    val sideSlots = Array(1, 5, 7, 3)

    /** The four corners of the grid. */
    val cornerSlots = Array(0, 2, 6, 8)

    private var problematicSlotMask = 0

    override protected val storage = Array.fill(10)(ItemStack.EMPTY)

    override def getInventoryStackLimit = 64
    override def getName = "packaging_table"

    //Resolves the diamond between TileEntity and IInventory.
    override def getDisplayName = super.getDisplayName

    override def isItemValidForSlot(slot:Int, stack:ItemStack):Boolean =
        if slot == dieSlot then stack.getItem.isInstanceOf[ItemValidDie]
        else slot != outputSlot

    /** Bitmask of grid slots that currently hold the wrong item, for the GUI. */
    def getProblematicSlotMask:Int = problematicSlotMask

    override def openGui(player:EntityPlayer):Unit =
        GuiPackagingTable.open(player, createContainer(player), _.writePos(getPos))

    override def createContainer(player:EntityPlayer):ContainerPackagingTable =
        new ContainerPackagingTable(player, this)

    /** Plate required by one side, chosen by that side's interface type. */
    def getPlateFor(kind:ICInterfaceType):PartDefs.PartVal = kind match
        case ICInterfaceType.NC                 => PartDefs.PLATE
        case ICInterfaceType.REDSTONE           => PartDefs.CONDUCTIVEPLATE
        case ICInterfaceType.ANALOG             => PartDefs.CONDUCTIVEPLATE
        case ICInterfaceType.BUNDLED            => PartDefs.BUNDLEDPLATE

    override def canStartWork:Boolean =
        problematicSlotMask = 0

        val die = storage(dieSlot)
        if !die.getItem.isInstanceOf[ItemValidDie] || !ICData.canFabricate(die) then
            problematicSlotMask |= 1 << dieSlot
            return false

        if !storage(outputSlot).isEmpty then
            problematicSlotMask |= 1 << outputSlot

        for r <- 0 until 4 do
            val required = getPlateFor(ICData.getInterfaceType(die, r))
            if !storage(sideSlots(r)).isItemEqual(required.makeStack) then
                problematicSlotMask |= 1 << sideSlots(r)

        for slot <- cornerSlots do
            if !storage(slot).isItemEqual(PartDefs.PLATE.makeStack) then
                problematicSlotMask |= 1 << slot

        problematicSlotMask == 0

    override def startWork():Int = 20 * 20

    override def tickWork(remainingWork:Int):Int =
        if cond.canWork then
            drainWork(1)
            1
        else 0

    override def finishWork():Unit =
        val gate = GateDefinition.ICGate.makeStack
        ItemICBlueprint.copyToGate(storage(dieSlot), gate)
        setInventorySlotContents(outputSlot, gate)

        for slot <- 0 until outputSlot do
            decrStackSize(slot, 1)

    override def save(tag:NBTTagCompound):Unit =
        super.save(tag)
        saveInv(tag)

    override def load(tag:NBTTagCompound):Unit =
        super.load(tag)
        loadInv(tag)

    override def onBlockRemoval():Unit =
        super.onBlockRemoval()
        dropInvContents(world, pos)

class ContainerPackagingTable(p:EntityPlayer, tile:TilePackagingTable) extends ContainerFabricationMachine(tile):

    private var lastMask = -1

    /** Mirror of the tile's mask, updated from the server. */
    var problematicSlotMask = 0

    //3x3 grid, slots 0..8, then the output slot.
    for i <- 0 until 9 do
        addSlotToContainer(new Slot3(tile, i, 46 + i % 3 * 18, 22 + i / 3 * 18))

    val outslot = new Slot3(tile, 9, 135, 40)
    outslot.canPlaceDelegate = { _ => false }
    addSlotToContainer(outslot)

    addPlayerInv(p, 8, 89)

    override def detectAndSendChanges():Unit =
        super.detectAndSendChanges()

        for listener <- listeners.asScala do
            if lastMask != tile.getProblematicSlotMask then
                listener.sendWindowProperty(this, 5, tile.getProblematicSlotMask)
        lastMask = tile.getProblematicSlotMask

    override def updateProgressBar(id:Int, bar:Int):Unit = id match
        case 5 => problematicSlotMask = bar
        case _ => super.updateProgressBar(id, bar)

    //Machine slots are 0..9, hotbar 10..18, main inventory 19..45.
    override def doMerge(stack:ItemStack, from:Int):Boolean =
        if from < 9 then //grid to player inventory
            tryMergeItemStack(stack, 10, 46, false)
        else if from == 9 then //output to player inventory, from the back
            if tryMergeItemStack(stack, 10, 19, true) then true
            else tryMergeItemStack(stack, 19, 46, true)
        else //player inventory into the grid
            tryMergeItemStack(stack, 0, 9, false)

class GuiPackagingTable(tile:TilePackagingTable, c:ContainerPackagingTable) extends NodeGui(c, GuiFabricationMachine.WIDTH, GuiFabricationMachine.HEIGHT):

    override def drawBack_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawBackground(GuiPackagingTable.background, c, 104, 41, 20)

        //Tint grid slots that hold the wrong item.
        val colour = EnumColour.RED.argb(0x55)
        for i <- 0 until 9 do
            if (c.problematicSlotMask & 1 << i) != 0 then
                val slot = c.getSlot(i)
                GuiDraw.drawRect(slot.xPos, slot.yPos, 16, 16, colour)

    override def drawFront_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawLabels("Packaging Table")

object GuiPackagingTable extends TGuiFactory:

    val background = new ResourceLocation("projectred", "textures/gui/packaging_table.png")

    override def getID = FabricationProxy.packagingTableGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer, data:MCDataInput) =
        player.world.getTileEntity(data.readPos()) match
            case t:TilePackagingTable => new GuiPackagingTable(t, t.createContainer(player))
            case _ => null
