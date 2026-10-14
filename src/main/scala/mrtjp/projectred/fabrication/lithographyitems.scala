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
  * The Fabrication production chain items: rough silicon wafer, blank photomask,
  * photomask set, and the valid/invalid die pair.
  *
  * These are produced and consumed by the three tables rather than by crafting
  * recipes. See MIGRATION.md for the chain.
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
