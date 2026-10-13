/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.MCDataInput
import codechicken.lib.gui.GuiDraw
import codechicken.lib.texture.TextureUtils
import mrtjp.core.gui.*
import mrtjp.core.inventory.{TInventory, TInventoryCapablilityTile}
import mrtjp.core.vec.Point
import mrtjp.projectred.ProjectRedFabrication
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

/**
  * Prints a photomask design onto a blank photomask, producing a photomask set
  * that the lithography table can etch with.
  *
  * High-version equivalent: `PlottingTableBlockEntity`. Slot layout, work time
  * and power draw are taken from it: 10 seconds of work at 100W per tick.
  */
class TilePlottingTable extends TGuiFabricationMachine with TInventory with TInventoryCapablilityTile:

    override protected val storage = Array.fill(3)(ItemStack.EMPTY)

    override def getInventoryStackLimit = 64
    override def getName = "plotting_table"

    //Resolves the diamond between TileEntity and IInventory.
    override def getDisplayName = super.getDisplayName

    override def isItemValidForSlot(slot:Int, stack:ItemStack):Boolean = slot match
        case 0 => stack.getItem.isInstanceOf[ItemICBlueprint]
        case 1 => stack.getItem.isInstanceOf[ItemBlankPhotomask]
        case _ => false

    override def openGui(player:EntityPlayer):Unit =
        GuiPlottingTable.open(player, createContainer(player), _.writePos(getPos))

    override def createContainer(player:EntityPlayer):ContainerPlottingTable =
        new ContainerPlottingTable(player, this)

    override def canStartWork:Boolean =
        storage(0).getItem.isInstanceOf[ItemICBlueprint] &&
        storage(1).getItem.isInstanceOf[ItemBlankPhotomask] &&
        ICData.canFabricate(storage(0)) &&
        storage(2).isEmpty

    override def startWork():Int = 20 * 10

    override def tickWork(remainingWork:Int):Int =
        if cond.canWork then
            drainWork(1)
            1
        else 0

    override def finishWork():Unit =
        val out = new ItemStack(ProjectRedFabrication.itemPhotomaskSet)
        ICData.copyDesign(storage(0), out)
        setInventorySlotContents(2, out)
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

class ContainerPlottingTable(p:EntityPlayer, tile:TilePlottingTable) extends ContainerFabricationMachine(tile):

    addSlotToContainer(new Slot3(tile, 0, 56, 31)) //blueprint input
    addSlotToContainer(new Slot3(tile, 1, 56, 49)) //blank photomask input

    val outslot = new Slot3(tile, 2, 116, 40)
    outslot.canPlaceDelegate = { _ => false }
    addSlotToContainer(outslot)

    addPlayerInv(p, 8, 89)

    //Machine slots are 0..2, hotbar 3..11, main inventory 12..38.
    override def doMerge(stack:ItemStack, from:Int):Boolean =
        if from < 2 then //inputs to player inventory
            tryMergeItemStack(stack, 3, 39, false)
        else if from == 2 then //output to player inventory, from the back
            if tryMergeItemStack(stack, 3, 12, true) then true
            else tryMergeItemStack(stack, 12, 39, true)
        else //player inventory into the machine inputs
            tryMergeItemStack(stack, 0, 2, false)

class GuiPlottingTable(tile:TilePlottingTable, c:ContainerFabricationMachine) extends NodeGui(c, GuiFabricationMachine.WIDTH, GuiFabricationMachine.HEIGHT):

    override def drawBack_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawBackground(GuiPlottingTable.background, c, 80, 40, 24)

    override def drawFront_Impl(mouse:Point, frame:Float):Unit =
        GuiFabricationMachine.drawLabels(tile)

object GuiPlottingTable extends TGuiFactory:

    val background = new ResourceLocation("projectred", "textures/gui/plotting_table.png")

    override def getID = FabricationProxy.plottingTableGui

    @SideOnly(Side.CLIENT)
    override def buildGui(player:EntityPlayer, data:MCDataInput) =
        player.world.getTileEntity(data.readPos()) match
            case t:TilePlottingTable => new GuiPlottingTable(t, t.createContainer(player))
            case _ => null
