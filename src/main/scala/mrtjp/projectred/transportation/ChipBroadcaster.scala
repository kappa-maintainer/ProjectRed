package mrtjp.projectred.transportation

import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.{ItemKey, ItemKeyStack, ItemQueue}
import mrtjp.projectred.transportation.RoutingChipDefs.ChipVal

import scala.collection.mutable.ListBuffer

case class BroadcastObject(stack:ItemKeyStack, requester:IRouterContainer)
:
    var priority:Priorities.Priority = null

trait TActiveBroadcastStack extends RoutingChip
:
    private var orders = Seq[BroadcastObject]()

    def addOrder(stack:ItemKeyStack, requester:IRouterContainer, priority:Priorities.Priority): Unit =
        orders.find(p => p.stack.key == stack.key && p.requester == requester) match
            case Some(p) =>
                p.stack.stackSize += stack.stackSize
                val idx = orders.indexOf(p)
                orders = orders.take(idx) ++ orders.drop(idx+1) :+ p
            case _ =>
                val b = BroadcastObject(stack, requester)
                b.priority = priority
                orders :+= b
        onOrdersChanged()

    def pop(amount:Int) =
        val BroadcastObject(stack, _) = orders.head
        stack.stackSize -= amount
        if stack.stackSize <= 0 then
            orders = orders.tail
            true
        else false

    def popAll() =
        val out = orders.head
        orders = orders.tail
        out

    def restackOrders(): Unit =
        orders = orders.tail :+ orders.head

    def peek =
        if orders.isEmpty then null
        else orders.head

    def hasOrders = orders.nonEmpty

    def getDeliveryCount(item:ItemKey) = orders.foldLeft(0)(
        (b, p) => b+(if p.stack.key == item then p.stack.stackSize else 0))

    def getTotalDeliveryCount = orders.foldLeft(0)((b, p) => b+p.stack.stackSize)

    def onOrdersChanged(): Unit ={}

    def getStacksToExtract:Int

    def getItemsToExtract:Int

    def extractItem(item:ItemKey, amount:Int):Int

    def timeOutOnFailedExtract:Boolean

    def doExtractOperation(): Unit =
        if !hasOrders then return

        var stacksRemaining = getStacksToExtract
        var itemsRemaining = getItemsToExtract

        val wh, cont = new scala.util.control.Breaks
        wh.breakable:
            while hasOrders && stacksRemaining > 0 && itemsRemaining > 0 do cont.breakable
              :
                val bObj = peek
                val BroadcastObject(stack, req) = bObj

                val real = invProvider.getInventory
                if real == null then
                    popAll()
                    req.postNetworkEvent(TrackedPayloadCancelledEvent(stack.key, stack.stackSize, router))
                    cont.break()

                if !router.getRouter.canRouteTo(req.getRouter.getIPAddress, stack.key, bObj.priority) then
                    popAll()
                    req.postNetworkEvent(TrackedPayloadCancelledEvent(stack.key, stack.stackSize, router))
                    cont.break()

                var toExtract = stack.stackSize
                toExtract = math.min(toExtract, itemsRemaining)
                toExtract = math.min(toExtract, stack.key.getMaxStackSize)

                var restack = false

                val dspace = req.getActiveFreeSpace(stack.key)
                if dspace < toExtract then
                    toExtract = dspace
                    if toExtract <= 0 then
                        restackOrders()
                        wh.break()
                    restack = true

                val removed = extractItem(stack.key, toExtract)
                if removed <= 0 && timeOutOnFailedExtract then
                    popAll()
                    req.postNetworkEvent(TrackedPayloadCancelledEvent(stack.key, stack.stackSize, router))
                    cont.break()

                if removed > 0 then
                    router.queueStackToSend(stack.key, removed, bObj.priority, req.getRouter.getIPAddress)

                if !pop(removed) && restack then restackOrders()

                stacksRemaining -= 1
                itemsRemaining -= removed

class ChipBroadcaster extends RoutingChip with TChipFilter with TChipOrientation with TChipPriority with TActiveBroadcastStack
:
    filterExclude = true

    private var timeRemaining = operationDelay

    def operationDelay = 10

    override def getStacksToExtract = 8

    override def getItemsToExtract = 64

    override def timeOutOnFailedExtract = true

    override def extractItem(item:ItemKey, amount:Int) =
        val real = invProvider.getInventory(extractSide)
        if real != null then
            val inv = applyFilter(real)
            inv.extractItem(item, amount)
        else 0

    override def update(): Unit =
        timeRemaining -= 1
        if timeRemaining > 0 then return
        timeRemaining = operationDelay

        doExtractOperation()

    override def requestPromise(request:RequestBranchNode, existingPromises:Int): Unit =
        val real = invProvider.getInventory(extractSide)
        if real == null then return

        val inv = applyFilter(real)
        val filt = applyFilter(InvWrapper.wrapInternal(filter), hide=false)

        val requested = request.getRequestedPackage

        for (key, amount) <- inv.getAllItemStacks.filter{p => requested.matches(p._1) && filt.hasItem(p._1) != filterExclude} do
            val available = amount-request.root.getExistingPromisesFor(router, key)
            val toAdd = math.min(request.getMissingCount, available)
            if toAdd > 0 then request.addPromise(
                new DeliveryPromise(key, toAdd, router)
            )

    override def deliverPromise(promise:DeliveryPromise, requester:IRouterContainer): Unit =
        addOrder(ItemKeyStack.get(promise.item, promise.size), requester, Priorities.ACTIVEB)

    override def getBroadcasts(col:ItemQueue): Unit =
        val real = invProvider.getInventory(extractSide)
        if real == null then return

        val inv = applyFilter(real)
        val filt = applyFilter(InvWrapper.wrapInternal(filter), hide=false)

        val items = inv.getAllItemStacks
        for (k, v) <- items do if filt.hasItem(k) != filterExclude then
            val toAdd = v-getDeliveryCount(k)
            if toAdd > 0 then col += k -> toAdd

    override def getBroadcastPriority = preference

    override def onRemoved(): Unit =
        while hasOrders do
            val BroadcastObject(s, r) = popAll()
            r.postNetworkEvent(TrackedPayloadCancelledEvent(s.key, s.stackSize, router))

    override def infoCollection(list:ListBuffer[String]): Unit =
        super.infoCollection(list)
        addPriorityInfo(list)
        addOrientInfo(list)
        addFilterInfo(list)

    override def enableHiding = true
    override def enableFilter = true
    override def enablePatterns = false

    def getChipType:ChipVal = RoutingChipDefs.ITEMBROADCASTER
