/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import mrtjp.core.vec.Size
import net.minecraft.item.ItemStack

/**
  * Electrical interface presented by one side of a fabricated IC. Decides
  * which core plate the packaging table consumes on that side.
  */
enum ICInterfaceType:
    case NC
    case REDSTONE
    case ANALOG
    case BUNDLED

/**
  * Shared on-stack data for an IC design as it travels the production chain:
  * blueprint -> photomask set -> die -> fabricated gate.
  *
  * The high version carries this in an `ICDataComponent`. 1.12 has no data
  * components, so it stays in item NBT -- but using exactly the keys the
  * existing IC workbench and IC gate already read and write, so a design
  * produced by one is usable by the others. Serialization itself remains in
  * [[ItemICBlueprint]]; this object only adds the queries the new machines
  * need. See MIGRATION.md.
  */
object ICData:

    //region Queries

    /** True if the stack carries a design at all. */
    def hasDesign(stack:ItemStack):Boolean =
        ItemICBlueprint.hasICInside(stack)

    /** Errors reported by the last compile of this design. */
    def getErrorCount(stack:ItemStack):Int =
        val tag = stack.getTagCompound
        if tag == null then 0 else tag.getByte("log_err") & 0xFF

    /** Warnings reported by the last compile of this design. */
    def getWarningCount(stack:ItemStack):Int =
        val tag = stack.getTagCompound
        if tag == null then 0 else tag.getByte("log_warn") & 0xFF

    /**
      * High-version `canFabricate()` equivalent: the design exists and
      * compiled without errors. 1.12 has no separate "isBuilt" flag, so the
      * presence of a tilemap stands in for it.
      */
    def canFabricate(stack:ItemStack):Boolean =
        hasDesign(stack) && getErrorCount(stack) == 0

    /**
      * Design size in tiles.
      *
      * Read from the tilemap tag itself rather than the blueprint's top-level
      * `icw`/`ich`. Both hold the same value, but `ICTileMapContainer.saveTiles`
      * writes `sw`/`sh` inside the tilemap and `loadTiles` restores from them,
      * so that copy is the one that actually defines the design -- and it
      * travels with the tilemap when the design is copied between items.
      */
    def getDesignSize(stack:ItemStack):Size =
        val tag = stack.getTagCompound
        if tag == null || !tag.hasKey("tilemap") then Size.zeroSize
        else
            val tm = tag.getCompoundTag("tilemap")
            Size(tm.getByte("sw") & 0xFF, tm.getByte("sh") & 0xFF)

    /**
      * Interface type for one side, derived from the packed IO masks and
      * connection modes, the same pair [[ICGateLogic]] uses to build a gate.
      */
    def getInterfaceType(stack:ItemStack, side:Int):ICInterfaceType =
        import IIOGateTile.*

        val tag = stack.getTagCompound
        if tag == null || !tag.hasKey("masks") then ICInterfaceType.NC
        else
            val (ri, ro, bi, bo) = ICGateLogic.unpackIO(tag.getShort("masks") & 0xFFFF)
            val hasIO = ((ri | ro | bi | bo) >> side & 1) != 0
            if !hasIO then ICInterfaceType.NC
            else ICGateLogic.unpackConnModes(tag.getShort("cmode") & 0xFFFF)(side) match
                case Bundled => ICInterfaceType.BUNDLED
                case Analog  => ICInterfaceType.ANALOG
                case Simple  => ICInterfaceType.REDSTONE
                case _       => ICInterfaceType.NC

    /** All four sides, in the IC's own rotation order. */
    def getInterfaceTypes(stack:ItemStack):Seq[ICInterfaceType] =
        (0 until 4).map(getInterfaceType(stack, _))

    //endregion

    //region Mutations

    /**
      * Copies the entire design onto another stack: tilemap, name, size,
      * compile flags and the derived IO interface. After this the target is
      * self-describing and can be used by any later stage of the chain.
      */
    def copyDesign(from:ItemStack, to:ItemStack):Unit =
        ItemICBlueprint.copyIC(from, to)
        deriveInterface(to)

    /**
      * Recomputes and stores the packed IO masks and connection modes from the
      * design's own IO tiles. A no-op design (no IO tiles) yields all-zero
      * masks, which [[getInterfaceType]] reports as [[ICInterfaceType.NC]].
      */
    def deriveInterface(stack:ItemStack):Unit =
        ItemICBlueprint.saveICToGate(ItemICBlueprint.loadTileMap(stack), stack)

    /** Strips the design, leaving a blank item. */
    def clear(stack:ItemStack):Unit =
        ItemICBlueprint.removeIC(stack)

    //endregion
