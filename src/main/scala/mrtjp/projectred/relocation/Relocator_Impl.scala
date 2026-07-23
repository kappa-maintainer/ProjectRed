/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.relocation

import java.util.{Set as JSet}

import mrtjp.projectred.api.{IMovementCallback, Relocator}
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Set as MSet, Stack as MStack}

class RelocationRun
:
    var world:World = scala.compiletime.uninitialized
    var dir = -1
    var speed = 0.0
    var callback:IMovementCallback = scala.compiletime.uninitialized
    val blocks = MSet[BlockPos]()

    def clear(): Unit =
        world = null
        dir = -1
        speed = 0
        callback = null
        blocks.clear()

object Relocator_Impl extends Relocator
{
    var mainStack = new MStack[RelocationRun]()
    var tempStack = new MStack[RelocationRun]()

    private def assertState(): Unit =
        if mainStack.isEmpty then throw new IllegalStateException("Relocator stack is empty.")

    override def push(): Unit =
        val r = if tempStack.isEmpty then new RelocationRun else tempStack.pop()
        mainStack.push(r)

    override def pop(): Unit =
        assertState()
        val r = mainStack.pop()
        r.clear()
        tempStack.push(r)

    override def setWorld(world:World): Unit =
        assertState()
        val top = mainStack.top
        if top.world != null then throw new IllegalStateException("World already set.")
        top.world = world

    override def setDirection(dir:Int): Unit =
        assertState()
        val top = mainStack.top
        if top.dir != -1 then throw new IllegalStateException("Direction already set.")
        top.dir = dir

    override def setSpeed(speed:Double): Unit =
        assertState()
        val top = mainStack.top
        if top.speed > 0 then throw new IllegalStateException("Speed already set.")
        top.speed = speed

    override def setCallback(callback:IMovementCallback): Unit =
        assertState()
        val top = mainStack.top
        if top.callback != null then throw new IllegalStateException("Callback already set.")
        top.callback = callback

    override def addBlock(bc:BlockPos): Unit =
        assertState()
        mainStack.top.blocks += bc

    override def addBlocks(blocks:JSet[BlockPos]): Unit =
        for b <- blocks.asScala do addBlock(b)

    override def execute() =
        assertState()
        val top = mainStack.top
        if top.world == null then throw new IllegalStateException("World must be set before move.")
        if top.world.isRemote then throw new IllegalStateException("Movements cannot be executed client-side.")
        if top.dir == -1 then throw new IllegalStateException("Direction must be set before move.")
        if top.speed <= 0 then throw new IllegalStateException("Speed must be greater than 0.")
        if top.speed >= 1 then throw new IllegalStateException("Speed must be less than 1.")
        if top.blocks.isEmpty then throw new IllegalStateException("No blocks queued for move.")
        MovementManager.tryStartMove(top.world, top.blocks.toSet, top.dir, top.speed, top.callback)
}