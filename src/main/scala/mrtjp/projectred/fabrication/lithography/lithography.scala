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
  *  - The "standard die" is 4x4 tiles, not 16x16, and the wafer is 1024nm
  *    rather than 4096nm. The high version calibrates `defectRatePerUnitArea`
  *    against a 16x16-tile die at the 64nm node, but 1.12 IC boards are at
  *    most 4x4 tiles (see `guiicworkbench.NewICNode.maxBoardSize`). Reusing
  *    its constants verbatim would put every 1.12 design far outside the
  *    calibrated range: 256..4096 dies per wafer and 0.2%..3.1% defect
  *    rates, which both overflows the stack limit and flattens the yield
  *    mechanic into a no-op. See [[Lithography.STANDARD_DIE_TILES]].
  */
object Lithography:

    /** Length of one IC tile, in nanometers. Matches `ProcessNode.PROCESS_64NM`. */
    val TILE_LENGTH_NM = 64

    /**
      * Tile length of the "standard die" the defect rates are calibrated
      * against. A wafer's `standardDieDefectRate` is the defect rate of a
      * die this many tiles square at the 64nm node.
      *
      * The high version uses 16 here, matching its own IC board limit. 1.12
      * boards max out at 4x4, so 4 is used instead: it makes the largest
      * supported design the calibration point, which keeps defect rates
      * meaningful across the whole 1x1..4x4 range.
      */
    val STANDARD_DIE_TILES = 4

    /**
      * Most dies a single wafer run may produce, so the result always fits
      * one inventory slot. The 1x1 design exceeds this and is clamped; every
      * larger design stays under it.
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

    case ROUGH_WAFER extends WaferType(1024, 0.5, "roughWafer")

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
