/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.*
import net.minecraft.nbt.NBTTagCompound

/**
  * Power-driven base for the fabrication production chain.
  *
  * Mirrors the high-version `FabricationMachineBlockEntity`, which extends
  * `BasePoweredBlockEntity` and runs a `canStartWork / startWork / tickWork /
  * finishWork` cycle while drawing a fixed amount of power per tick of work.
  *
  * 1.12 already has an equivalent in `TileProcessingMachine`, but that lives
  * in the expansion module and the build splits modules into separate jars
  * (`fabricationPatterns` vs `mechanicalPatterns` in build.gradle), so it is
  * not visible from here. This is a fabrication-local version built directly
  * on the core power API.
  *
  * Only the logic lives here; turning `isCharged`/`isWorking` into block
  * appearance is a rendering concern handled separately.
  */
trait TFabricationPowered extends TileFabricationMachine with TPowerTile with ILowLoadMachine:

    /** Packet key for work-state updates. Keys 1 and up are free on this hierarchy. */
    private val KEY_WORK_STATE = 2

    /** Power drawn per unit of work, in the units `PowerConductor.drawPower` takes. */
    val workPowerDraw = 1100.0D

    val cond = new PowerConductor(this, idRange) with TPowerDrawPoint

    var isCharged = false
    var isWorking = false
    var workRemaining = 0
    var workMax = 0

    override def conductor(dir:Int):PowerConductor = cond

    override def canConnectPart(part:IConnectable, s:Int, edgeRot:Int):Boolean = part match
        case _:ILowLoadPowerLine => true
        case _:ILowLoadMachine   => true
        case _                   => false

    //region Subclass hooks

    /** Whether a new run may begin. Re-checked before starting and while running. */
    def canStartWork:Boolean

    /** Begins a run. Returns the total work the run requires. */
    def startWork():Int

    /** Advances a run. Returns how much work was done this tick. */
    def tickWork(remainingWork:Int):Int

    /** Called once when a run completes. Should place outputs and consume inputs. */
    def finishWork():Unit

    //endregion

    /** Power cost of the work done this tick, drawn from the conductor. */
    def drainWork(work:Int):Unit =
        if work > 0 then cond.drawPower(work * workPowerDraw)

    def cancelWork():Unit =
        isWorking = false
        workRemaining = 0
        workMax = 0

    /** Aborts a run that has become invalid, e.g. its inputs were removed. */
    def cancelWorkIfNeeded():Unit =
        if isWorking && !canStartWork then
            cancelWork()
            sendWorkUpdate()

    def progressScaled(scale:Int):Int =
        if !isWorking || workMax <= 0 || workRemaining <= 0 then 0
        else scale * (workMax - workRemaining) / workMax

    override def updateServer():Unit =
        super.updateServer()
        cond.update()

        val wasCharged = isCharged
        val wasWorking = isWorking

        isCharged = cond.canWork

        if !isWorking then
            if canStartWork then
                isWorking = true
                workRemaining = startWork()
                workMax = workRemaining
        else
            workRemaining -= tickWork(workRemaining)
            if workRemaining <= 0 then
                cancelWork()
                finishWork()

        if isCharged != wasCharged || isWorking != wasWorking then
            sendWorkUpdate()

    override def markDirty():Unit =
        super.markDirty()
        cancelWorkIfNeeded()

    def sendWorkUpdate():Unit =
        writeStream(KEY_WORK_STATE).writeBoolean(isCharged).writeBoolean(isWorking).sendToChunk(this)

    override def read(in:MCDataInput, key:Int):Unit = key match
        case KEY_WORK_STATE =>
            isCharged = in.readBoolean()
            isWorking = in.readBoolean()
        case _ => super.read(in, key)

    abstract override def writeDesc(out:MCDataOutput):Unit =
        super.writeDesc(out)
        out.writeBoolean(isCharged)
        out.writeBoolean(isWorking)

    abstract override def readDesc(in:MCDataInput):Unit =
        super.readDesc(in)
        isCharged = in.readBoolean()
        isWorking = in.readBoolean()

    abstract override def save(tag:NBTTagCompound):Unit =
        super.save(tag)
        cond.save(tag)
        tag.setBoolean("isCharged", isCharged)
        tag.setBoolean("isWorking", isWorking)

    abstract override def load(tag:NBTTagCompound):Unit =
        super.load(tag)
        cond.load(tag)
        isCharged = tag.getBoolean("isCharged")
        isWorking = tag.getBoolean("isWorking")
