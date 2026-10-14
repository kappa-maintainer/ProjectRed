/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.gui.GuiDraw
import codechicken.lib.texture.TextureUtils
import mrtjp.core.gui.{GuiLib, NodeContainer}
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.*
import net.minecraft.client.resources.I18n
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation

import scala.jdk.CollectionConverters.*

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
  * Only the logic lives here; the flags are also what the renderer reads to
  * pick the front-face sprite, so changes are pushed to the client and trigger
  * a rebake.
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
            //Both flags pick the front-face sprite, so the client has to rebake.
            markRender()
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

/**
  * Opens a container GUI when the machine is used. 1.12's equivalent,
  * `TGuiMachine`, also lives in the expansion module and is out of reach
  * here, so this is the fabrication-local version.
  */
trait TGuiFabricationMachine extends TFabricationPowered:

    abstract override def onBlockActivated(player:EntityPlayer, side:Int):Boolean =
        if super.onBlockActivated(player, side) then true
        else if !player.isSneaking then
            if !getWorld.isRemote then openGui(player)
            true
        else false

    def openGui(player:EntityPlayer):Unit

    def createContainer(player:EntityPlayer):Container

/**
  * Container base for the fabrication machines. Syncs the same quantities
  * `ContainerPoweredMachine` and `ContainerProcessingMachine` do in the
  * expansion module, which again is not reachable from this jar:
  * conductor charge and flow drive the two tanks, remaining and total work
  * drive the progress arrow.
  */
class ContainerFabricationMachine(tile:TFabricationPowered) extends NodeContainer:

    private var lastCharge = -2
    private var lastFlow = -2
    private var lastWorkRemaining = -1
    private var lastWorkMax = -1

    override def detectAndSendChanges():Unit =
        super.detectAndSendChanges()

        for listener <- listeners.asScala do
            if lastCharge != tile.cond.charge then
                listener.sendWindowProperty(this, 0, tile.cond.charge)
            if lastFlow != tile.cond.flow then
                listener.sendWindowProperty(this, 1, tile.cond.flow & 0xFFFF)
                listener.sendWindowProperty(this, 2, tile.cond.flow >> 16 & 0xFFFF)
            if lastWorkRemaining != tile.workRemaining then
                listener.sendWindowProperty(this, 3, tile.workRemaining)
            if lastWorkMax != tile.workMax then
                listener.sendWindowProperty(this, 4, tile.workMax)

        lastCharge = tile.cond.charge
        lastFlow = tile.cond.flow
        lastWorkRemaining = tile.workRemaining
        lastWorkMax = tile.workMax

    override def updateProgressBar(id:Int, bar:Int):Unit = id match
        case 0 => tile.cond.charge = bar
        case 1 => tile.cond.flow = tile.cond.flow & 0xFFFF0000 | bar & 0xFFFF
        case 2 => tile.cond.flow = tile.cond.flow & 0xFFFF | (bar & 0xFFFF) << 16
        case 3 => tile.workRemaining = bar
        case 4 => tile.workMax = bar
        case _ => super.updateProgressBar(id, bar)

    def canConductorWork:Boolean = tile.cond.canWork
    def isFlowFull:Boolean = tile.cond.flow == -1
    def getChargeScaled(scale:Int):Int = tile.cond.getChargeScaled(scale)
    def getFlowScaled(scale:Int):Int = tile.cond.getFlowScaled(scale)
    def getProgressScaled(scale:Int):Int = tile.progressScaled(scale)

/**
  * Drawing shared by the three machine GUIs. Only the progress arrow differs
  * between them: the plotting and lithography tables put it at (80, 40) and
  * scale it to 24px, the packaging table at (104, 41) scaled to 20px.
  *
  * The atlas offsets for the arrow and both tanks are identical to the ones
  * the 1.12 furnace GUI already uses, which is why the high-version
  * backgrounds can be used as-is.
  */
object GuiFabricationMachine:

    val WIDTH = 176
    val HEIGHT = 171

    /**
      * Lang key of one table variant. Mirrors how `ItemBlockCore` builds its
      * key from the block's translation key plus the item damage, so the GUI
      * title and the item name always agree.
      */
    def machineNameKey(meta:Int):String = s"tile.projectred.fabrication.fabricationMachine|$meta.name"

    def drawBackground(background:ResourceLocation, c:ContainerFabricationMachine,
                       progressX:Int, progressY:Int, progressWidth:Int):Unit =
        TextureUtils.changeTexture(background)
        GuiDraw.drawTexturedModalRect(0, 0, 0, 0, WIDTH, HEIGHT)

        GuiDraw.drawTexturedModalRect(progressX, progressY, 176, 0, c.getProgressScaled(progressWidth) + 1, 16)

        if c.canConductorWork then
            GuiDraw.drawTexturedModalRect(16, 16, 177, 18, 7, 9)
        GuiLib.drawVerticalTank(16, 26, 177, 27, 7, 48, c.getChargeScaled(48))

        if c.isFlowFull then
            GuiDraw.drawTexturedModalRect(27, 16, 185, 18, 7, 9)
        GuiLib.drawVerticalTank(27, 26, 185, 27, 7, 48, c.getFlowScaled(48))

    def drawLabels(tile:TileFabricationMachine):Unit =
        val title = I18n.format(machineNameKey(tile.getBlockMetadata))
        GuiDraw.drawString(title, 8, 6, EnumColour.GRAY.argb, false)
        GuiDraw.drawString(I18n.format("container.inventory"), 8, 79, EnumColour.GRAY.argb, false)
