package mrtjp.projectred.transportation

import codechicken.lib.raytracer.CuboidRayTraceResult
import codechicken.multipart.INeighborTileChangePart
import mrtjp.core.gui.{GuiLib, NodeContainer, Slot3}
import mrtjp.core.inventory.SimpleInventory
import mrtjp.core.item.{ItemKey, ItemKeyStack, ItemQueue}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumHand

class RoutedInterfacePipePart extends AbstractNetPipe with TNetworkPipe with INeighborTileChangePart
{
    val chipSlots = new SimpleInventory(4, "chips", 1)
    {
        override def markDirty(): Unit =
        {
            chipsNeedRefresh = true
        }

        override def isItemValidForSlot(i:Int, stack:ItemStack) =
            !stack.isEmpty &&
                stack.getItem.isInstanceOf[ItemRoutingChip] &&
                stack.hasTagCompound &&
                stack.getTagCompound.hasKey("chipROM")
    }

    val chips = new Array[RoutingChip](4)
    val chipStacks = new Array[ItemKey](4)

    private var chipsNeedRefresh = true

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        chipSlots.saveInv(tag)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        chipSlots.loadInv(tag)
    }

    override def onRemoved(): Unit =
    {
        super.onRemoved()
        if !world.isRemote then
        {
            for r <- chips do if r != null then r.onRemoved()
            chipSlots.dropInvContents(world, pos)
        }
    }

    override def updateServer(): Unit =
    {
        super.updateServer()

        if chipsNeedRefresh then
        {
            chipsNeedRefresh = false
            refreshChips()
        }

        for s <- chips do if s != null then s.update()
    }


    override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, item:ItemStack, hand:EnumHand):Boolean =
    {
        if super.activate(player, hit, item, hand) then true
        else if !item.isEmpty && item.getItem.isInstanceOf[ItemRoutingChip] then
        {
            (0 until chipSlots.getSizeInventory).find(i =>
                chipSlots.getStackInSlot(i).isEmpty && chipSlots.isItemValidForSlot(i, item)) match
            {
                case Some(i) =>
                    chipSlots.setInventorySlotContents(i, item.splitStack(1))
                    true
                case None => if !player.isSneaking then { openGui(player); true } else false
            }
        }
        else if !player.isSneaking then
        {
            openGui(player)
            true
        }
        else false
    }

    def openGui(player:EntityPlayer): Unit =
    {
        if world.isRemote then return
        GuiInterfacePipe.open(player, createContainer(player), _.writePos(pos))
    }

    def createContainer(player:EntityPlayer) = new ContainerInterfacePipe(this, player)

    def refreshChips(): Unit =
    {
        for i <- 0 until chipSlots.getSizeInventory do
        {
            val oldKey = chipStacks(i)
            val newStack = chipSlots.getStackInSlot(i)
            val newKey = if !newStack.isEmpty then ItemKey.get(newStack) else null

            if newKey != oldKey then
            {
                val oldChip = chips(i)
                val newChip = if !newStack.isEmpty && ItemRoutingChip.isValidChip(newStack) then
                    ItemRoutingChip.loadChipFromItemStack(newStack) else null

                if oldChip != null then
                {
                    oldChip.onRemoved()
                    chips(i) = null
                }

                if newChip != null then
                {
                    newChip.setEnvironment(this, this, i)
                    chips(i) = newChip
                    newChip.onAdded()
                }
            }
        }

        for i <- 0 until chipSlots.getSizeInventory do
        {
            val s = chipSlots.getStackInSlot(i)
            chipStacks(i) = if !s.isEmpty then ItemKey.get(s) else null
        }
    }

    override def getSyncResponse(item:ItemKey, rival:SyncResponse):SyncResponse =
    {
        var best = rival
        var found = false
        for r <- chips do if r != null then
        {
            val response = r.getSyncResponse(item, best)
            if response != null then if response.isPreferredOver(best) then
            {
                best = response
                found = true
            }
        }
        if found then
        {
            best.itemCount -= countInTransit(item)
            if best.itemCount > 0 then return best
        }
        null
    }

    override def requestPromise(request:RequestBranchNode, existingPromises:Int): Unit =
    {
        for r <- chips do if r != null then r.requestPromise(request, existingPromises)
    }

    override def deliverPromise(promise:DeliveryPromise, requestor:IRouterContainer): Unit =
    {
        for r <- chips do if r != null then r.deliverPromise(promise, requestor)
    }

    def postEventToChips(event:NetworkEvent): Unit =
    {
        val it = chips.filter(_ != null).iterator
        while it.hasNext && !event.isCanceled do
            it.next().onEventReceived(event)
    }


    override def postNetworkEvent(event:NetworkEvent): Unit =
    {
        super.postNetworkEvent(event:NetworkEvent)
        postEventToChips(event)
    }

    override def getBroadcasts(col:ItemQueue): Unit =
    {
        for r <- chips do if r != null then r.getBroadcasts(col)
    }

    override def getBroadcastPriority =
    {
        val all = chips.filter(_ != null).map(_.getBroadcastPriority)
        if all.isEmpty then Integer.MIN_VALUE else all.max
    }

    override def getWorkLoad =
    {
        val all = chips.filter(_ != null).map(_.getWorkLoad)
        if all.isEmpty then 0 else all.max
    }


    override def onNeighborTileChanged(side:Int, weak:Boolean): Unit =
    {
        for r <- chips do if r != null then r.onNeighborTileChanged(side, weak)
    }

    override def weakTileChanges():Boolean =
        chips.exists(r => r != null && r.weakTileChanges)

    override def requestCraftPromise(request:RequestBranchNode) =
    {
        val b = Seq.newBuilder[CraftingPromise]
        for r <- chips do if r != null then
        {
            val p = r.requestCraftPromise(request)
            if p != null then b += p
        }
        b.result()
    }

    override def registerExcess(promise:DeliveryPromise): Unit =
    {
        for r <- chips do if r != null then
            r.registerExcess(promise)
    }

    override def getCraftedItems =
    {
        var b = Seq.newBuilder[ItemKeyStack]
        for r <- chips do if r != null then
        {
            val s = r.getCraftedItem
            if s != null then b += s
        }
        b.result()
    }

    override def itemsToProcess =
        chips.filterNot(_ == null).foldLeft(0){(count, r) => r.getProcessingItems+count}
}

class ContainerInterfacePipe(pipe:RoutedInterfacePipePart, p:EntityPlayer) extends NodeContainer
{
    {
        for ((x, y), i) <- GuiLib.createSlotGrid(24, 12, 1, 4, 0, 8).zipWithIndex do
            addSlotToContainer(new Slot3(pipe.chipSlots, i, x, y))

        addPlayerInv(p, 8, 118)
    }
}
