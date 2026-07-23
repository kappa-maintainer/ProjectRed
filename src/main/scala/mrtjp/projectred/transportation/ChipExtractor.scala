package mrtjp.projectred.transportation

import mrtjp.core.inventory.InvWrapper

import scala.collection.immutable.BitSet
import scala.collection.mutable.ListBuffer
import scala.util.boundary
import boundary.break

class ChipExtractor extends RoutingChip with TChipFilter with TChipOrientation
:
    private var remainingDelay = operationDelay

    private def operationDelay = 10

    private def itemsToExtract = 64

    override def update(): Unit = boundary:
        super.update()

        remainingDelay -= 1
        if remainingDelay > 0 then return
        remainingDelay = operationDelay

        val real = invProvider.getInventory(extractSide)
        if real == null then return

        val inv = real
        val filt = applyFilter(InvWrapper.wrapInternal(filter))

        val available = inv.getAllItemStacks
        for (k,v) <- available do
            val stackKey = k
            val stackSize = v

            if stackKey != null && filt.hasItem(stackKey) != filterExclude then
                var exclusions = BitSet.empty
                var s = router.getLogisticPath(stackKey, exclusions, true)
                if s != null then
                    var leftInRun = itemsToExtract
                    while s != null do
                        var toExtract = math.min(leftInRun, stackSize)
                        toExtract = math.min(toExtract, stackKey.getMaxStackSize)
                        toExtract = math.min(toExtract, s.itemCount)
                        if toExtract <= 0 then break()

                        val extracted = inv.extractItem(stackKey, toExtract)
                        if extracted <= 0 then break()

                        router.queueStackToSend(stackKey, extracted, s)

                        leftInRun -= extracted
                        if leftInRun <= 0 then break()

                        exclusions += s.responder
                        s = router.getLogisticPath(stackKey, exclusions, true)

    override def infoCollection(list:ListBuffer[String]): Unit =
        super.infoCollection(list)
        addOrientInfo(list)
        addFilterInfo(list)

    def getChipType = RoutingChipDefs.ITEMEXTRACTOR

    override def enableHiding = false
