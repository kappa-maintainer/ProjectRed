/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.transportation

import java.util.UUID

import mrtjp.core.item.{ItemKey, ItemKeyStack}

import scala.collection.mutable.{ListBuffer, HashMap as MHashMap, Set as MSet}

object ChipCraftingExtension
:
    //Router UUID -> Set[Extension UUID]
    var map = MHashMap[UUID, MSet[UUID]]()

    def registerRouter(router:UUID, ext:UUID): Unit =
        map.getOrElseUpdate(router, MSet.empty) += ext

    def removeRouter(router:UUID, ext:UUID): Unit =
        map.get(router).foreach { extensions =>
            extensions -= ext
            if extensions.isEmpty then map.remove(router)
        }

    def getRoutersForExtension(ext:UUID) =
        map.collect:
            case (uuid, set) if set contains ext => uuid

class ChipCraftingExtension extends RoutingChip with TChipCrafterExtension with TActiveLostStack
{
    private var remainingDelay = operationDelay

    private def operationDelay = 40

    override def getMaxRequestAttempts = 8

    override def itemLostUnrecoverable(item:ItemKey, amount:Int): Unit ={}

    override def update(): Unit =
        remainingDelay -= 1
        if remainingDelay <= 0 then
            remainingDelay = operationDelay
            requestLostItems()

    override def onEventReceived(event:NetworkEvent) = event match
        case e:PayloadLostEnrouteEvent =>
            addLostItem(ItemKeyStack.get(e.item, e.remaining))
            e.remaining = 0
            e.setCanceled()
        case e:TrackedPayloadCancelledEvent =>
            addLostItem(ItemKeyStack.get(e.item, e.remaining))
            e.remaining = 0
            e.setCanceled()
        case _ =>

    override def onAdded(): Unit =
        ChipCraftingExtension.registerRouter(router.getRouter.getID, id)

    override def onRemoved(): Unit =
        ChipCraftingExtension.removeRouter(router.getRouter.getID, id)

    override def infoCollection(list:ListBuffer[String]): Unit =
        super.infoCollection(list)
        addExtIDInfo(list)

    override def getChipType = RoutingChipDefs.ITEMEXTENSION
}