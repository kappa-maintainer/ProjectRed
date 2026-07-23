/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.exploration

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.gui.GuiDraw
import codechicken.lib.render.state.GlStateTracker
import codechicken.lib.util.ItemUtils
import codechicken.lib.vec.*
import mrtjp.core.block.{MTBlockTile, MultiTileBlock}
import mrtjp.core.inventory.{IInvWrapperRegister, InvWrapper, TInventory, VanillaWrapper}
import mrtjp.core.item.ItemKey
import mrtjp.core.world.WorldLib
import mrtjp.projectred.ProjectRedExploration
import mrtjp.projectred.core.PRLib
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.{IInventory, ISidedInventory}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class BlockBarrel extends MultiTileBlock(Material.WOOD)
{
    setHardness(2.0F)
    setSoundType(SoundType.WOOD)
    setCreativeTab(ProjectRedExploration.tabExploration)

    override def getPlayerRelativeBlockHardness(state:IBlockState, player:EntityPlayer, world:World, pos:BlockPos) =
        net.minecraftforge.common.ForgeHooks.blockStrength(state, player, world, pos)
}

class TileBarrel extends MTBlockTile with TInventory with ISidedInventory
{
    var amountStored = 0
    var item:ItemKey = null

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setInteger("storage", amountStored)
        saveInv(tag)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        amountStored = tag.getInteger("storage")
        loadInv(tag)
        refreshItemKey()
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(nonEmpty)
        if nonEmpty then {
            out.writeItemStack(item.makeStack(0))
            out.writeInt(getStoredAmount)
        }
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        silentClear()
        if in.readBoolean() then
        {
            item = ItemKey.get(in.readItemStack())
            amountStored = in.readInt()
            compactItems()
        }
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 => silentClear()
        case 2 =>
            silentClear()
            item = ItemKey.get(in.readItemStack())
            amountStored = in.readInt()
            compactItems()
        case _ => super.read(in, key)
    }

    override def getDisplayName = super.getDisplayName

    def sendItemUpdate(): Unit =
    {
        if isEmpty then
            writeStream(1).sendToChunk(this)
        else
            writeStream(2).writeItemStack(item.makeStack(0)).writeInt(getStoredAmount).sendToChunk(this)
    }

    override def getBlock = ProjectRedExploration.blockBarrel

    override protected val storage = Array.fill(2)(ItemStack.EMPTY)//new Array[ItemStack](2)
    override def getInventoryStackLimit = 64
    override def getName = "barrel"

    override def canInsertItem(slot:Int, stack:ItemStack, side:EnumFacing) =
        slot == 0 && side == EnumFacing.UP && (isEmpty || ItemKey.get(stack) == item)
    override def canExtractItem(slot:Int, stack:ItemStack, side:EnumFacing) =
        slot == 1 && side == EnumFacing.DOWN && (isEmpty || ItemKey.get(stack) == item)

    override def getSlotsForFace(side:EnumFacing) =
        if side == EnumFacing.DOWN then Array(1) else if side == EnumFacing.UP then Array(0) else Array.empty

    def getStackSpace = 128

    def getFreeSpace =
        if isEmpty then Int.MaxValue
        else getStackSpace*item.getMaxStackSize-getStoredAmount

    def getFreeStorageSpace =
        if isEmpty then Int.MaxValue
        else (getStackSpace-2)*item.getMaxStackSize-amountStored

    def getStoredAmount =
    {
        var i = amountStored
        if !getStackInSlot(0).isEmpty then i += getStackInSlot(0).getCount
        if !getStackInSlot(1).isEmpty then i += getStackInSlot(1).getCount
        i
    }

    def getStoredFullStacks =
        if isEmpty then 0
        else getStoredAmount/item.getMaxStackSize

    def getRemainderStacks =
        if isEmpty then 0
        else getStoredAmount%item.getMaxStackSize

    def getDoubleClickTicks = 8

    //TODO?
    //def isEmpty = getStoredAmount == 0
    def nonEmpty = !isEmpty

    override def onBlockActivated(player:EntityPlayer, side:Int):Boolean =
    {
        if super.onBlockActivated(player, side) then return true
        if world.isRemote then return true
        //TODO Hand passthrough
        if (checkDoubleClick() && importAll(player) > 0) ||
                importStack(ItemUtils.getHeldStack(player)) > 0 then needsUpdate = true

        true
    }

    override def onBlockClicked(player:EntityPlayer):Boolean =
    {
        if super.onBlockClicked(player) then return true
        if world.isRemote then return true
        if isEmpty then return false

        val inslot = getStackInSlot(1)
        val stored = getStoredAmount
        val toRem = math.min(
            if player.isSneaking then 1
            else inslot.getCount-(if stored > 1 && stored <= inslot.getMaxStackSize then 1 else 0),
            inslot.getMaxStackSize
        )
        val out = inslot.splitStack(toRem)

        setInventorySlotContents(1, if inslot.getCount <= 0 then ItemStack.EMPTY else inslot)

        PRLib.dropTowardsPlayer(world, getPos, out, player)
        needsUpdate = true
        true
    }


    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()

        if !world.isRemote then
        {
            var stack = getStackInSlot(1)
            while !stack.isEmpty && nonEmpty do
            {
                WorldLib.dropItem(world, getPos, stack)
                setInventorySlotContents(1, ItemStack.EMPTY)
                stack = getStackInSlot(1)
            }
        }
    }

    private var needsUpdate = false
    override def updateServer(): Unit =
    {
        if needsUpdate then sendItemUpdate()
        needsUpdate = false
    }

    private var lastActivateTime = -1L
    def checkDoubleClick():Boolean =
    {
        val dc = world.getTotalWorldTime-lastActivateTime <= getDoubleClickTicks
        lastActivateTime = world.getTotalWorldTime
        dc && item != null
    }

    def importAll(player:EntityPlayer) =
    {
        var s = 0
        for i <- 0 until player.inventory.getSizeInventory do
        {
            val stack = player.inventory.getStackInSlot(i)
            if !stack.isEmpty then
            {
                s += importStack(stack)
                if stack.getCount <= 0 then
                    player.inventory.setInventorySlotContents(i, ItemStack.EMPTY)
            }
        }
        if s > 0 then player.inventoryContainer.detectAndSendChanges()
        s
    }

    def importStack(stack:ItemStack):Int =
    {
        if stack.isEmpty then return 0
        if nonEmpty && !InvWrapper.areItemsSame(stack, item.makeStack(0)) then return 0
        var inslot = getStackInSlot(0)
        if inslot.isEmpty then inslot = stack.splitStack(0)
        val space = inslot.getMaxStackSize-inslot.getCount
        val toAdd = math.min(space, stack.getCount)
        if toAdd > 0 then
        {
            inslot.grow(toAdd)
            stack.shrink(toAdd)
            setInventorySlotContents(0, inslot)
        }
        toAdd
    }

    private var compacting = false
    override def markDirty(): Unit =
    {
        super.markDirty()
        compactItems()
        needsUpdate = true
    }

    def refreshItemKey(): Unit =
    {
        val inslot = getStackInSlot(1)
        item = if inslot.isEmpty then null else ItemKey.get(inslot)
    }

    def compactItems(): Unit =
    {
        if compacting then return
        compacting = true

        if !getStackInSlot(0).isEmpty then
        {
            val in = getStackInSlot(0)
            var out = getStackInSlot(1)

            if out.isEmpty || !InvWrapper.areItemsSame(in, out) then out = in.splitStack(0)
            val toAdd = math.min(in.getCount, out.getMaxStackSize-out.getCount)
            if toAdd > 0 then
            {
                in.shrink(toAdd)
                out.grow(toAdd)
            }
            setInventorySlotContents(1, out)

            refreshItemKey()

            val sAdd = math.min(in.getCount, getFreeStorageSpace)
            if sAdd > 0 then
            {
                in.shrink(sAdd)
                amountStored += sAdd
            }

            if in.getCount == 0 then setInventorySlotContents(0, ItemStack.EMPTY)
        }

        if amountStored > 0 then
        {
            var out = getStackInSlot(1)
            if out.isEmpty then out = item.makeStack(0)
            val toAdd = math.min(amountStored, out.getMaxStackSize-out.getCount)
            if toAdd > 0 then
            {
                out.grow(toAdd)
                amountStored -= toAdd
                setInventorySlotContents(1, out)
            }
        }

        refreshItemKey()

        //cleanup
        for i <- 0 until getSizeInventory do
            if !getStackInSlot(i).isEmpty && getStackInSlot(i).getCount <= 0 then
                setInventorySlotContents(i, ItemStack.EMPTY)

        compacting = false
    }

    def silentClear(): Unit =
    {
        item = null
        amountStored = 0
        compacting = true //dont run compactItems
        setInventorySlotContents(0, ItemStack.EMPTY)
        setInventorySlotContents(1, ItemStack.EMPTY)
        compacting = false
    }
}

object RenderBarrel extends TileEntitySpecialRenderer[TileBarrel] //with TCubeMapRender
{
    override def render(tile:TileBarrel, x:Double, y:Double, z:Double, frame:Float, destroyProgress:Int, alpha:Float): Unit =
    {
        if tile.item == null then return

        val stack = tile.item.makeStack(1)
        val fr = Minecraft.getMinecraft.fontRenderer
        val tm = Minecraft.getMinecraft.getTextureManager

        val stackSize = tile.item.getMaxStackSize
        val stacks = tile.getStoredFullStacks
        val extra = tile.getRemainderStacks
        val total = tile.getStoredAmount

        val text =
            if total > 0 then
            {
                if stackSize == 1 || stacks == 0 then s"$total"
                else if extra == 0 then s"$stacks x $stackSize"
                else s"$stacks x $stackSize + $extra"
            }
            else ""

        val tw = GuiDraw.getStringWidth(text)
        val tsc = 0.875/math.max(85, tw)

        val label = tile.item.getName
        val lw = GuiDraw.getStringWidth(label)
        val lsc = 0.875/math.max(128, lw)

        val faceT = new TransformationList(
            new Rotation(math.Pi, 1, 0, 0),
            new Translation(0, 1, 1)
        )

        val itemT = new TransformationList(
            new Scale(1/16D, 1/16D, -1.0E-04F) `at` new Vector3(0, 1, 1),
            new Scale(1/2D, 1/2D, 1) `at` Vector3.center,
            new Translation(0, 0.05, 0.0005)
        )

        val textT = new TransformationList(
            new Scale(tsc, tsc, 1) `at` new Vector3(0, 1, 1),
            new Translation(0.5-tw*tsc/2.0, -0.5/16D, 0.001)
        )

        val labelT = new TransformationList(
            new Scale(lsc, lsc, 1) `at` new Vector3(0, 1, 1),
            new Translation(0.5-lw*lsc/2.0, -14.5/16D, 0.001)
        )

        for i <- 0 until 4 do
        {
            val finalT = new TransformationList(
                Rotation.quarterRotations(i).at(Vector3.center),
                new Translation(x, y, z)
            )

            GlStateTracker.pushStates(GlStateTracker.State.GL_ALPHA_TEST, GlStateTracker.State.GL_BLEND, GlStateTracker.State.GL_LIGHTING)
            disableBlend()
            disableLighting()
            color(1, 1, 1, 1)

            pushMatrix()
            new TransformationList(faceT, itemT, finalT).glApply()
            Minecraft.getMinecraft.getRenderItem.renderItemAndEffectIntoGUI(stack, 0,0)
            popMatrix()

            pushMatrix()
            new TransformationList(faceT, textT, finalT).glApply()
            GuiDraw.drawString(text, 0, 0, 0xFFFFFFFF, false)
            popMatrix()

            pushMatrix()
            new TransformationList(faceT, labelT, finalT).glApply()
            GuiDraw.drawString(label, 0, 0, 0xFFFFFFFF, false)
            popMatrix()

            GlStateTracker.popState()
        }
    }
}

object BarrelInvWrapper extends IInvWrapperRegister
{
    override def wrapperID = "pr_barrel"

    override def matches(inv:IInventory) = inv.isInstanceOf[TileBarrel]

    override def create(inv:IInventory) = new BarrelInvWrapper(inv)
}

class BarrelInvWrapper(inv:IInventory) extends VanillaWrapper(inv, false)
{
    def getBarrel = inv.asInstanceOf[TileBarrel]

    override def getSpaceForItem(item:ItemKey) =
        if slots.contains(0) && (getBarrel.isEmpty || item == getBarrel.item) then getBarrel.getFreeSpace else 0

    override def hasSpaceForItem(item:ItemKey) = getSpaceForItem(item) > 0

    override def getItemCount(item:ItemKey) =
    {
        var count = if slots.contains(1) && getBarrel.nonEmpty && eq.matches(item, getBarrel.item) then getBarrel.getStoredAmount else 0
        if hidePerSlot || hidePerType then count -= 1
        math.max(count, 0)
    }

    override def hasItem(item:ItemKey) = getItemCount(item) > 0

    override def injectItem(item:ItemKey, toAdd:Int) =
    {
        var itemsLeft = toAdd
        if slots.contains(0) && (getBarrel.isEmpty ||item == getBarrel.item) then
        {
            import scala.util.control.Breaks.*
            breakable { while itemsLeft > 0 && getBarrel.getFreeSpace > 0 do
            {
                val toAdd = math.min(item.getMaxStackSize, itemsLeft)
                val added = getBarrel.importStack(item.makeStack(toAdd))
                itemsLeft -= added
            }}
        }
        toAdd-itemsLeft
    }

    override def extractItem(item:ItemKey, toExtract:Int) =
    {
        var itemsLeft = toExtract
        val hidden = if hidePerSlot || hidePerType then 1 else 0

        if slots.contains(1) && eq.matches(item, getBarrel.item) then
        {
            while itemsLeft > 0 && getBarrel.getStoredAmount-hidden > 0 do
            {
                var toRem = math.min(itemsLeft, getBarrel.getStoredAmount)
                toRem = math.min(toRem, item.getMaxStackSize)

                val bottomStack = getBarrel.getStackInSlot(1)
                bottomStack.shrink(toRem)
                itemsLeft -= toRem

                if bottomStack.getCount <= 0 then getBarrel.setInventorySlotContents(1, ItemStack.EMPTY)
                else getBarrel.markDirty()
            }
        }
        toExtract-itemsLeft
    }

    override def getAllItemStacks:Map[ItemKey, Int] =
    {
        if slots.contains(1) && getBarrel.nonEmpty then
        {
            val count = getBarrel.getStoredAmount-(if hidePerSlot || hidePerType then 1 else 0)
            if count > 0 then return Map(getBarrel.item -> count)
        }
        Map.empty
    }
}
