/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import mrtjp.core.item.ItemCore
import mrtjp.projectred.ProjectRedFabrication
import mrtjp.projectred.fabrication.lithography.WaferType

/**
  * Shell-only ports of the high-version Fabrication production chain items.
  * No crafting logic, GUI hooks, or tooltip data yet -- registration, model,
  * and localization only. See MIGRATION.md.
  */
class ItemRoughSiliconWafer extends ItemCore
:
    setCreativeTab(ProjectRedFabrication.tabFabrication)

    /** Grade of silicon this item carries. Mirrors `BaseSiliconWaferItem`. */
    def getWaferType:WaferType = WaferType.ROUGH_WAFER

class ItemBlankPhotomask extends ItemCore
:
    setCreativeTab(ProjectRedFabrication.tabFabrication)

class ItemPhotomaskSet extends ItemCore
:
    setCreativeTab(ProjectRedFabrication.tabFabrication)

class ItemValidDie extends ItemCore
:
    setCreativeTab(ProjectRedFabrication.tabFabrication)

class ItemInvalidDie extends ItemCore
:
    setCreativeTab(ProjectRedFabrication.tabFabrication)
