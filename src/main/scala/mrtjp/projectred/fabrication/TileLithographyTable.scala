/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.data.MCDataInput
import mrtjp.core.gui.*
import mrtjp.core.inventory.{TInventory, TInventoryCapablilityTile}
import mrtjp.core.vec.Point
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.fabrication.lithography.{Lithography, ProcessNode, YieldCalculator}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/**
  * Etches a silicon wafer using a photomask set, cutting it into individual
  * dies. Some dies come out defective and are output separately.
  *
  * High-version equivalent: `LithographyTableBlockEntity`, 60 seconds of work
  * at 100W per tick.
  *
  * One deliberate improvement on the high version: it hard-codes the design
  * size to 16x16 tiles with a `TODO Source from photomask itemstack instead of
  * assuming 16x16`. The design size is available here (see
  * [[ICData.getDesignSize]]), so the real size is used instead. Boards span
  * 16x16 .. 64x64 tiles (`selectedBoardSize * 16`, not the 1x1..4x4 the sizer
  * grid suggests), so assuming 16x16 would misjudge every board but the
  * smallest. See MIGRATION.md.
  */
class TileLithographyTable extends TGuiFabricationMachine with TInventory with TInventoryCapablilityTile:

    override protected val storage = Array.fill(4)(ItemStack.EMPTY)

    override def getInventoryStackLimit = 64
    override def getName = "lithography_table"

    //Resolves the diamond between TileEntity and IInventory.
    override def getDisplayName = super.getDisplayName

    override def isItemValidForSlot(slot:Int, stack:ItemStack):Boolean = slot match
        case 0 => stack.getItem.isInstanceOf[ItemPhotomaskSet]
        case 1 => stack.getItem.isInstanceOf[ItemRoughSiliconWafer]
        case _ => false

    override def openGui(player:EntityPlayer):Unit =
        GuiLithographyTable.open(player, createContainer(player), _.writePos(getPos))

    override def createContainer(player:EntityPlayer):ContainerLithographyTable =
        new ContainerLithographyTable(player, this)

    override def canStartWork:Boolean =
        storage(0).getItem.isInstanceOf[ItemPhotomaskSet] &&
        storage(1).getItem.isInstanceOf[ItemRoughSiliconWafer] &&
        ICData.canFabricate(storage(0)) &&
        storage(2).isEmpty && storage(3).isEmpty

    override def startWork():Int = 20 * 60

    override def tickWork(remainingWork:Int):Int =
        if cond.canWork then
            drainWork(1)
            1
        else 0

    override def finishWork():Unit =
        val photomask = storage(0)
        val wafer = storage(1).getItem.asInstanceOf[ItemRoughSiliconWafer]

        val calc = new YieldCalculator(ICData.getDesignSize(photomask), ProcessNode.PROCESS_64NM, wafer.getWaferType)
        val total = calc.getCappedDieCount
        val defectChance = calc.getDefectChancePerDie

        val defective = (0 until total).count(_ => world.rand.nextDouble() < defectChance)
        val valid = total - defective

        if valid > 0 then
            val out = new ItemStack(ProjectRedFabrication.itemValidDie, valid)
            ICData.copyDesign(photomask, out)
            setInventorySlotContents(2, out)

        if defective > 0 then
            setInventorySlotContents(3, new ItemStack(ProjectRedFabrication.itemInvalidDie, defective))

        decrStackSize(1, 1)

    override def save(tag:NBTTagCompound):Unit =
        super.save(tag)
        saveInv(tag)

    override def load(tag:NBTTagCompound):Unit =
        super.load(tag)
        loadInv(tag)

    override def onBlockRemoval():Unit =
        super.onBlockRemoval()
        dropInvContents(world, pos)

class ContainerLithographyTable(p:EntityPlayer, tile:TileLithographyTable) extends ContainerFabricationMachine(tile):

    addSlotToContainer(new Slot3(tile, 0, 56, 31)) //photomask set input
    addSlotToContainer(new Slot3(tile, 1, 56, 49)) //wafer input

    val validOut = new Slot3(tile, 2, 110, 31)
    validOut.canPlaceDelegate = { _ => false }
    addSlotToContainer(validOut)

    val invalidOut = new Slot3(tile, 3, 110, 49)
    invalidOut.canPlaceDelegate = { _ => false }
    addSlotToContainer(invalidOut)

    addPlayerInv(p, 8, 89)

    //Machine slots are 0..3, hotbar 4..12, main inventory 13..39.
    override def doMerge(stack:ItemStack, from:Int):Boolean =
        if from < 2 then //inputs to player inventory
            tryMergeItemStack(stack, 4, 40, false)
        else if from < 4 then //outputs to player inventory, from the back
            if tryMergeItemStack(stack, 4, 13, true) then true
            else tryMergeItemStack(stack, 13, 40, true)
        else //player inventory into the machine inputs
            tryMergeItemStack(stack, 0, 2, false)

class GuiLithographyTable(tile:TileLithographyTable, c:ContainerFabricationMachine) extends NodeGui(c, GuiFabricationMachine.WIDTH, GuiFabricationMachine.HEIGHT):

    override def drawBack_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawBackground(GuiLithographyTable.background, c, 80, 40, 24)

    override def drawFront_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawLabels(tile)

object GuiLithographyTable extends TGuiFactory:

    val background = new ResourceLocation("projectred", "textures/gui/lithography_table.png")

    override def getID = FabricationProxy.lithographyTableGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer, data:MCDataInput) =
        player.world.getTileEntity(data.readPos()) match
            case t:TileLithographyTable => new GuiLithographyTable(t, t.createContainer(player))
            case _ => null
