/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication.lithography

import mrtjp.core.vec.Size

/**
  * 1.12 port of the high-version lithography data model (basic chain only).
  *
  * Deliberate deviations from the high version, all of them narrowing the
  * scope rather than extending it -- see MIGRATION.md:
  *
  *  - Only `PROCESS_64NM`, `ROUGH_WAFER` and single-layer designs exist here.
  *    `PURIFIED_WAFER`, `POLISHED_WAFER`, the 32/16/8nm nodes, the ADVANCED
  *    pipeline and multi-layer designs are unreachable in the high version
  *    as well: its ADVANCED pipeline diagram is a `new AbstractGuiNode() { }`
  *    stub, its production path never consults layer count, and its
  *    `Lithographer` class is three empty methods. The high-version
  *    `LithographyPipeline` compatibility matrix is therefore omitted too --
  *    with a single node and a single wafer it could never reject anything.
  *
  *  - The "standard die" is 64x64 tiles and the wafer is 12288nm. Board size
  *    is `selectedBoardSize * 16`, so 1.12 designs actually span 16x16 ..
  *    64x64 tiles, not the 1x1..4x4 the sizer grid suggests. Calibrating
  *    against the largest board keeps the defect rate meaningful over the
  *    whole range: 3.1% at 16x16 rising to 50% at 64x64. The high version
  *    calibrates against a 16x16 die and hard-codes that size in production,
  *    which for 1.12 would put every design but the smallest at a saturated
  *    100% defect rate. See [[Lithography.STANDARD_DIE_TILES]].
  */
object Lithography:

    /** Length of one IC tile, in nanometers. Matches `ProcessNode.PROCESS_64NM`. */
    val TILE_LENGTH_NM = 64

    /**
      * Tile length of the "standard die" the defect rates are calibrated
      * against. A wafer's `standardDieDefectRate` is the defect rate of a
      * die this many tiles square at the 64nm node.
      *
      * This is the *largest* board the IC workbench can produce. Board size
      * is `NewICNode.selectedBoardSize * 16`, with `maxBoardSize` of 4 sizer
      * units, so designs range over 16x16 .. 64x64 tiles. Calibrating against
      * the largest end is what makes the defect rates span a useful range:
      * 3.1% for a 16x16 design up to 50% for a full 64x64 one.
      *
      * The high version uses 16 here. Its `finishWork` hard-codes
      * `processNode.getTileWidth() * 16` with a TODO to read the real size,
      * which happens to match the *smallest* 1.12 board -- any larger design
      * would exceed a 100% defect rate.
      */
    val STANDARD_DIE_TILES = 64

    /**
      * Most dies a single wafer run may produce, so the result always fits
      * one inventory slot. The 16x16 board yields more than this and is
      * clamped; every larger design stays under it.
      */
    val MAX_DIE_OUTPUT = 64

/**
  * Smallest feature size the lithography table can resolve. The die footprint
  * of a design is its tile count multiplied by the node's tile length.
  */
enum ProcessNode(val tileLen:Int):

    case PROCESS_64NM extends ProcessNode(Lithography.TILE_LENGTH_NM)

    def getTileWidth:Int = tileLen
    def getTileHeight:Int = tileLen
    def getTileArea:Int = tileLen * tileLen

    def getDisplayName:String = s"${tileLen}nm"

/**
  * Silicon wafer grade. A rougher wafer is cheaper but carries a higher
  * defect rate.
  *
  * @param waferLen               wafer edge length in nanometers
  * @param standardDieDefectRate  defect rate of one standard die (see
  *                               [[Lithography.STANDARD_DIE_TILES]]) at the
  *                               64nm node
  */
enum WaferType(val waferLen:Int, val standardDieDefectRate:Double, val unlocalizedName:String):

    case ROUGH_WAFER extends WaferType(12288, 0.5, "roughWafer")

    def getWaferWidth:Int = waferLen
    def getWaferHeight:Int = waferLen
    def getWaferArea:Int = waferLen * waferLen

    /**
      * Defect rate per square nanometer. Back-calculated from
      * [[standardDieDefectRate]] so that a standard die comes out with exactly
      * that rate.
      */
    def getDefectRatePerUnitArea:Double =
        standardDieDefectRate / math.pow(Lithography.STANDARD_DIE_TILES * Lithography.TILE_LENGTH_NM, 2)

/**
  * Die size, die count and yield for one design cut from one wafer.
  *
  * Immutable: the high version keeps these as mutable setters to drive its
  * GUI, but nothing here needs that yet, and an immutable value is easier to
  * reason about from the machine.
  */
class YieldCalculator(val designSize:Size, val processNode:ProcessNode, val waferType:WaferType):

    /** Physical footprint of one die, in nanometers. */
    def getDieSize:Size =
        Size(designSize.width * processNode.getTileWidth, designSize.height * processNode.getTileHeight)

    def getWaferSize:Size =
        Size(waferType.getWaferWidth, waferType.getWaferHeight)

    /** How many dies the wafer is cut into, before any defect roll. */
    def getDieCount:Size =
        val die = getDieSize
        if die.width <= 0 || die.height <= 0 then Size.zeroSize
        else getWaferSize.divide(die.width, die.height)

    /**
      * Probability that any single die comes out defective. Grows with die
      * area, so a denser design yields fewer usable parts.
      */
    def getDefectChancePerDie:Double =
        val die = getDieSize
        die.width.toDouble * die.height * waferType.getDefectRatePerUnitArea

    /** Fraction of dies expected to survive, clamped to a sane range. */
    def getSingleLayerYield:Double =
        math.max(0.0, math.min(1.0, 1.0 - getDefectChancePerDie))

    /** Total dies a run may actually output, see [[Lithography.MAX_DIE_OUTPUT]]. */
    def getCappedDieCount:Int =
        val count = getDieCount
        math.min(count.width * count.height, Lithography.MAX_DIE_OUTPUT)
