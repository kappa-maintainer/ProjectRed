/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.relocation

import java.util.{Set as JSet}

import mrtjp.projectred.api.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.collection.immutable.Queue

object APIImpl_Relocation extends IRelocationAPI
{
    var isPreInit = true

    override def registerTileMover(name:String, desc:String, handler:ITileMover) =
    {
        assert(isPreInit)
        MovingTileRegistry.registerTileMover(name, desc, handler)
    }

    override def registerPreferredMover(key:String, value:String): Unit =
    {
        assert(isPreInit)
        MovingTileRegistry.preferredMovers :+= (key, value)
    }

    override def registerMandatoryMover(key:String, value:String): Unit =
    {
        assert(isPreInit)
        MovingTileRegistry.mandatoryMovers :+= (key, value)
    }

    override def registerFrameInteraction(interaction:IFrameInteraction): Unit =
    {
        StickRegistry.interactionList :+= interaction
    }

    override def getRelocator = Relocator_Impl

    override def getStickResolver = StickResolver_Impl

    override def isMoving(world:World, pos:BlockPos) =
        MovementManager.isMoving(world, pos)
}

object StickResolver_Impl extends StickResolver
{
    private var world:World = scala.compiletime.uninitialized
    private var start:BlockPos = scala.compiletime.uninitialized
    private var excl:Set[BlockPos] = scala.compiletime.uninitialized

    override def getStructure(w:World, pos:BlockPos, ex:BlockPos*):JSet[BlockPos] =
    {
        world = w
        start = pos
        excl = ex.toSet
        val result = iterate(Queue(start))
        world = null
        start = null
        excl = null
        result.asJava
    }

    @tailrec
    private def iterate(open:Seq[BlockPos], closed:Set[BlockPos] = Set.empty):Set[BlockPos] = open match {
        case Seq() => closed
        case Seq(next, rest@_*) =>
            val toCheck = Vector.newBuilder[BlockPos]

            for to <- resolveSticks(world, next) do {
                if !closed(to) && !open.contains(to) && !excl(to) then
                    if !world.isAirBlock(to) && !ProjectRedAPI.relocationAPI.isMoving(world, to) then
                        toCheck += to
            }

            iterate(rest ++ toCheck.result(), closed + next)
    }

    def resolveSticks(w:World, pos:BlockPos):Set[BlockPos] =
    {
        val f1 = StickRegistry.getFrame(w, pos)
        if f1 == null then
            return Set.empty[BlockPos]

        val b = Set.newBuilder[BlockPos]

        for s <- 0 until 6 do {
            val side = EnumFacing.values()(s)
            if f1.stickOut(w, pos, side) then {
                val p2 = pos.offset(side)
                val f2 = StickRegistry.getFrame(w, p2)
                if f2 == null || f2.stickIn(w, p2, side.getOpposite) then
                    b += p2
            }
        }

        for stick <- f1.getAdditionalSticks.asScala do
            b += stick

        b.result()
    }
}