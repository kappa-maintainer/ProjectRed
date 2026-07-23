/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.integration

import java.util
import java.util.List

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.CuboidRayTraceResult
import codechicken.lib.vec.Rotation
import codechicken.multipart.INeighborTileChangePart
import com.google.common.base.Predicate
import mrtjp.projectred.api.IScrewdriver
import mrtjp.projectred.core.Configurator
import mrtjp.projectred.core.TFaceOrient.*
import net.minecraft.block.material.Material
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.{AxisAlignedBB, BlockPos}
import net.minecraft.util.{EnumFacing, SoundCategory}
import net.minecraft.world.World

class SequentialGatePart extends RedstoneGatePart with TComplexGatePart
:
    private var logic:SequentialGateLogic = null

    override def getType = GateDefinition.typeComplexGate

    override def getLogic[T]:T = logic.asInstanceOf[T]
    def getLogicSequential = getLogic[SequentialGateLogic]

    override def assertLogic(): Unit =
        if logic == null then logic = SequentialGateLogic.create(this, subID)

class SequentialGatePartT extends SequentialGatePart with INeighborTileChangePart
:
    override def getType = GateDefinition.typeNeighborGate

    override def weakTileChanges() = getLogic[INeighborTileChangePart].weakTileChanges()

    override def onNeighborTileChanged(side:Int, weak:Boolean) =
        getLogic[INeighborTileChangePart].onNeighborTileChanged(side, weak)

object SequentialGateLogic
:

    def create(gate: SequentialGatePart, subID: Int): SequentialGateLogic = subID match
        case GateDefinition.SRLatch.ordinal => new SRLatch(gate)
        case GateDefinition.ToggleLatch.ordinal => new ToggleLatch(gate)
        case GateDefinition.Timer.ordinal => new Timer(gate)
        case GateDefinition.Sequencer.ordinal => new Sequencer(gate)
        case GateDefinition.Counter.ordinal => new Counter(gate)
        case GateDefinition.StateCell.ordinal => new StateCell(gate)
        case GateDefinition.Synchronizer.ordinal => new Synchronizer(gate)
        case GateDefinition.Comparator.ordinal => new Comparator(gate)
        case _ => throw new IllegalArgumentException("Invalid gate subID: " + subID)

abstract class SequentialGateLogic(val gate:SequentialGatePart) extends RedstoneGateLogic[SequentialGatePart] with TComplexGateLogic[SequentialGatePart]
:
    def tickSound(): Unit =
        if Configurator.logicGateSounds then
            gate.world.playSound(null, gate.pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.15F, 0.5f)

trait TExtraStateLogic extends SequentialGateLogic
:
    private var lState2:Byte = 0

    def state2 = lState2&0xFF
    def setState2(state:Int): Unit ={ lState2 = state.toByte }

    def clientState2 = false

    abstract override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setByte("state2", lState2)

    abstract override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        lState2 = tag.getByte("state2")

    abstract override def writeDesc(packet:MCDataOutput): Unit =
        super.writeDesc(packet)
        if clientState2 then packet.writeByte(lState2)

    abstract override def readDesc(packet:MCDataInput): Unit =
        super.readDesc(packet)
        if clientState2 then lState2 = packet.readByte()

    abstract override def read(packet:MCDataInput, key:Int) = key match
        case 11 => lState2 = packet.readByte()
        case _ => super.read(packet, key)

    def sendState2Update(): Unit ={ gate.getWriteStreamOf(11).writeByte(lState2) }

class SRLatch(gate:SequentialGatePart) extends SequentialGateLogic(gate) with TExtraStateLogic
:
    override def outputMask(shape:Int) = if (shape>>1) == 0 then 0xF else 5
    override def inputMask(shape:Int) = 0xA

    override def cycleShape(gate:SequentialGatePart) =
        gate.setShape((gate.shape+1)%4)
        setState2(flipMaskZ(state2))
        gate.setState(flipMaskZ(gate.state))
        gate.onOutputChange(0xF)
        gate.scheduleTick(2)
        true

    override def setup(gate:SequentialGatePart): Unit =
        setState2(2)
        gate.setState(0x30)

    override def onChange(gate:SequentialGatePart): Unit =
        val stateInput = state2

        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 0xA)
        val oldOutput = gate.state>>4

        if newInput != oldInput then
            if stateInput != 0xA && newInput != 0 && newInput != stateInput then //state needs changing
                gate.setState(newInput)
                setState2(newInput)
                gate.onOutputChange(oldOutput) //always going low
                gate.scheduleTick(2)
            else
                gate.setState(oldOutput<<4|newInput)
                gate.onInputChange()

    override def scheduledTick(gate:SequentialGatePart): Unit =
        val oldOutput = gate.state>>4
        val newOutput = calcOutput(gate)

        if oldOutput != newOutput then
            gate.setState(gate.state&0xF|newOutput<<4)
            gate.onOutputChange(outputMask(gate.shape))
        onChange(gate)

    def calcOutput(gate:SequentialGatePart):Int =
        var input = gate.state&0xF
        var stateInput = state2

        if (gate.shape&1) != 0 then //reverse
            input = flipMaskZ(input)
            stateInput = flipMaskZ(stateInput)

        if stateInput == 0xA then //disabled
            if input == 0xA then
                gate.scheduleTick(2)
                return 0

            stateInput =
                if input == 0 then if gate.world.rand.nextBoolean() then 2 else 8
                else input

            setState2(if (gate.shape&1) != 0 then flipMaskZ(stateInput) else stateInput)

        var output = shiftMask(stateInput, 1)
        if (gate.shape&2) == 0 then output |= stateInput
        if (gate.shape&1) != 0 then output = flipMaskZ(output) //reverse
        output

class ToggleLatch(gate:SequentialGatePart) extends SequentialGateLogic(gate) with TExtraStateLogic
:
    override def outputMask(shape:Int) = 5
    override def inputMask(shape:Int) = 0xA

    override def clientState2 = true

    override def setup(gate:SequentialGatePart): Unit =
        gate.setState(0x10)
        gate.sendStateUpdate()

    override def onChange(gate:SequentialGatePart): Unit =
        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 0xA)
        val high = newInput& ~oldInput

        if high == 2 || high == 8 then toggle(gate)

        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            gate.onInputChange()

    override def scheduledTick(gate:SequentialGatePart): Unit =
        val oldOutput = gate.state>>4
        val newOutput = if state2 == 0 then 1 else 4
        if oldOutput != newOutput then
            gate.setState(newOutput<<4|gate.state&0xF)
            gate.onOutputChange(5)
        onChange(gate)

    override def activate(gate:SequentialGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult) =
        if held.isEmpty || !held.getItem.isInstanceOf[IScrewdriver] then
            if !gate.world.isRemote then toggle(gate)
            true
        else false

    def toggle(gate:SequentialGatePart): Unit =
        setState2(state2^1)
        gate.scheduleTick(2)
        tickSound()

trait ITimerGuiLogic
:
    def getTimerMax:Int
    def setTimerMax(gate:GatePart, t:Int): Unit 

trait ICounterGuiLogic
:
    def getCounterMax:Int
    def setCounterMax(gate:GatePart, i:Int): Unit 

    def getCounterIncr:Int
    def setCounterIncr(gate:GatePart, i:Int): Unit 

    def getCounterDecr:Int
    def setCounterDecr(gate:GatePart, i:Int): Unit 

    def getCounterValue:Int
    def setCounterValue(gate:GatePart, i:Int): Unit 

trait TTimerGateLogic extends SequentialGateLogic with ITimerGuiLogic
:
    var pointer_max = 38
    var pointer_start = -1L

    abstract override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setInteger("pmax", pointer_max)
        tag.setLong("pelapsed", if pointer_start < 0 then pointer_start else gate.world.getTotalWorldTime-pointer_start)

    abstract override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        pointer_max = tag.getInteger("pmax")
        pointer_start = tag.getLong("pelapsed")

    abstract override def writeDesc(packet:MCDataOutput): Unit =
        super.writeDesc(packet)
        packet.writeInt(pointer_max)
        packet.writeLong(pointer_start)

    abstract override def readDesc(packet:MCDataInput): Unit =
        super.readDesc(packet)
        pointer_max = packet.readInt()
        pointer_start = packet.readLong()

    abstract override def read(packet:MCDataInput, key:Int) = key match
        case 12 => pointer_max = packet.readInt()
        case 13 =>
            pointer_start = packet.readInt()
            if pointer_start >= 0 then pointer_start = gate.world.getTotalWorldTime-pointer_start
        case _ => super.read(packet, key)

    abstract override def onWorldLoad(gate: SequentialGatePart): Unit =
        if pointer_start >= 0 then pointer_start = gate.world.getTotalWorldTime-pointer_start

    def pointerValue =
        if pointer_start < 0 then 0
        else (gate.world.getTotalWorldTime-pointer_start).toInt

    def sendPointerMaxUpdate(): Unit ={ gate.getWriteStreamOf(12).writeInt(pointer_max) }
    def sendPointerUpdate(): Unit ={ gate.getWriteStreamOf(13).writeInt(if pointer_start < 0 then -1 else pointerValue)}

    override def getTimerMax = pointer_max+2
    override def setTimerMax(gate:GatePart, time:Int): Unit =
        var t = time
        val minTime = math.max(4, Configurator.minTimerTicks)
        if t < minTime then t = minTime
        if t != pointer_max then
            pointer_max = t-2
            sendPointerMaxUpdate()

    override def onTick(gate:SequentialGatePart): Unit =
        if pointer_start >= 0 then
            if gate.world.getTotalWorldTime >= pointer_start+pointer_max then pointerTick()
            else if pointer_start > gate.world.getTotalWorldTime then
                pointer_start = gate.world.getTotalWorldTime

    def pointerTick(): Unit 

    def resetPointer(): Unit =
        if pointer_start >= 0 then
            pointer_start = -1
            gate.tile.markDirty()
            if !gate.world.isRemote then sendPointerUpdate()

    def startPointer(): Unit =
        if pointer_start < 0 then
            pointer_start = gate.world.getTotalWorldTime
            gate.tile.markDirty()
            if !gate.world.isRemote then sendPointerUpdate()

    def interpPointer(f:Float) = if pointer_start < 0 then 0f else (pointerValue+f)/pointer_max

    override def activate(gate:SequentialGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult) =
        if held.isEmpty || !held.getItem.isInstanceOf[IScrewdriver] then
            if !gate.world.isRemote then GuiTimer.open(player, gate)
            true
        else false

class Timer(gate:SequentialGatePart) extends SequentialGateLogic(gate) with TTimerGateLogic
:
    override def outputMask(shape:Int) = 0xB
    override def inputMask(shape:Int) = 0xE

    override def setup(gate:SequentialGatePart): Unit ={ startPointer() }

    override def scheduledTick(gate:SequentialGatePart): Unit =
        gate.setState(gate.state&0xF)
        gate.onOutputChange(0xB)
        onChange(gate)

    override def onChange(gate:SequentialGatePart): Unit =
        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 0xE)

        if newInput != oldInput then
            gate.setState(gate.state&0xF0|newInput)
            gate.onInputChange()

        if gate.schedTime < 0 then
            if newInput > 0 then resetPointer() else startPointer()

    override def pointerTick(): Unit =
        resetPointer()
        if !gate.world.isRemote then
            gate.scheduleTick(2)
            gate.setState(0xB0|gate.state&0xF)
            gate.onOutputChange(0xB)
            tickSound()

class Sequencer(gate:SequentialGatePart) extends SequentialGateLogic(gate) with ITimerGuiLogic
:
    var pointer_max = 40

    override def outputMask(shape:Int) = 0xF

    override def onChange(gate:SequentialGatePart): Unit ={}
    override def scheduledTick(gate:SequentialGatePart): Unit ={}

    override def getTimerMax = pointer_max
    override def setTimerMax(gate:GatePart, time:Int): Unit =
        var t = time
        val minTime = math.max(4, Configurator.minTimerTicks)
        if t < minTime then t = minTime
        if t != pointer_max then
            pointer_max = t
            sendPointerMaxUpdate()

    override def save(tag:NBTTagCompound): Unit ={ tag.setInteger("pmax", pointer_max) }
    override def load(tag:NBTTagCompound): Unit ={ pointer_max = tag.getInteger("pmax") }

    override def writeDesc(packet:MCDataOutput): Unit ={ packet.writeInt(pointer_max) }
    override def readDesc(packet:MCDataInput): Unit ={ pointer_max = packet.readInt() }

    override def read(packet:MCDataInput, key:Int) = key match
        case 12 => pointer_max = packet.readInt()
        case _ =>

    def sendPointerMaxUpdate(): Unit ={ gate.getWriteStreamOf(12).writeInt(pointer_max) }

    override def onTick(gate:SequentialGatePart): Unit =
        if !gate.world.isRemote then
            val oldOut = gate.state>>4
            var out = 1 << (gate.world.getWorldTime % (pointer_max * 4) / pointer_max).toInt
            if gate.shape == 1 then out = flipMaskZ(out)
            if oldOut != out then
                gate.setState(out<<4)
                gate.onOutputChange(0xF)
                tickSound()

    override def cycleShape(gate:SequentialGatePart) =
        gate.setShape(gate.shape^1)
        true

    override def activate(gate:SequentialGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult) =
        if held.isEmpty || !held.getItem.isInstanceOf[IScrewdriver] then
            if !gate.world.isRemote then GuiTimer.open(player, gate)
            true
        else false

class Counter(gate:SequentialGatePart) extends SequentialGateLogic(gate) with ICounterGuiLogic
:
    var value = 0
    var max = 10
    var incr = 1
    var decr = 1

    override def outputMask(shape:Int) = 5
    override def inputMask(shape:Int) = 10

    override def save(tag:NBTTagCompound): Unit =
        tag.setInteger("val", value)
        tag.setInteger("max", max)
        tag.setInteger("inc", incr)
        tag.setInteger("dec", decr)

    override def load(tag:NBTTagCompound): Unit =
        value = tag.getInteger("val")
        max = tag.getInteger("max")
        incr = tag.getInteger("inc")
        decr = tag.getInteger("dec")

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeInt(value).writeInt(max).writeInt(incr).writeInt(decr)

    override def readDesc(packet:MCDataInput): Unit =
        value = packet.readInt()
        max = packet.readInt()
        incr = packet.readInt()
        decr = packet.readInt()

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => value = packet.readInt()
        case 12 => max = packet.readInt()
        case 13 => incr = packet.readInt()
        case 14 => decr = packet.readInt()
        case _ =>

    def sendValueUpdate(): Unit ={ gate.getWriteStreamOf(11).writeInt(value) }
    def sendMaxUpdate(): Unit ={ gate.getWriteStreamOf(12).writeInt(max) }
    def sendIncrUpdate(): Unit ={ gate.getWriteStreamOf(13).writeInt(incr) }
    def sendDecrUpdate(): Unit ={ gate.getWriteStreamOf(14).writeInt(decr) }

    override def getCounterValue = value
    override def getCounterMax = max
    override def getCounterIncr = incr
    override def getCounterDecr = decr

    override def setCounterValue(gate:GatePart, i:Int): Unit =
        val oldVal = value
        value = Math.min(max, Math.max(0, i))
        if value != oldVal then
            tickSound()
            sendValueUpdate()

    override def setCounterMax(gate:GatePart, i:Int): Unit =
        val oldMax = max
        max = Math.min(32767, Math.max(1, i))
        if max != oldMax then
            tickSound()
            sendMaxUpdate()
            val oldVal = value
            value = Math.min(value, Math.max(0, i))
            if value != oldVal then
                sendValueUpdate()
                gate.scheduleTick(2)

    override def setCounterIncr(gate:GatePart, i:Int): Unit =
        val oldIncr = incr
        incr = Math.min(max, Math.max(1, i))
        if incr != oldIncr then
            tickSound()
            sendIncrUpdate()

    override def setCounterDecr(gate:GatePart, i:Int): Unit =
        val oldDecr = decr
        decr = Math.min(max, Math.max(1, i))
        if decr != oldDecr then
            tickSound()
            sendDecrUpdate()

    def onChange(gate:SequentialGatePart): Unit =
        val oldInput = gate.state&0xF
        var newInput = getInput(gate, 0xA)
        if gate.shape == 1 then newInput = flipMaskZ(newInput)
        val high = newInput& ~oldInput

        if (high&2) != 0 then setCounterValue(gate, value+incr)
        if (high&8) != 0 then setCounterValue(gate, value-decr)
        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            gate.onInputChange()
            gate.scheduleTick(2)

    override def cycleShape(gate:SequentialGatePart) =
        gate.setShape(if gate.shape == 1 then 0 else 1)
        true

    def scheduledTick(gate:SequentialGatePart): Unit =
        val oldOutput = gate.state
        var newOutput = 0
        if value == max then newOutput = 1
        else if value == 0 then newOutput = 4
        if newOutput != oldOutput then gate.setState(gate.state&0xF|newOutput<<4)
        if newOutput != oldOutput then gate.onOutputChange(5)

    override def activate(gate:SequentialGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult) =
        if held.isEmpty || !held.getItem.isInstanceOf[IScrewdriver] then
            if !gate.world.isRemote then GuiCounter.open(player, gate)
            true
        else false

class StateCell(gate:SequentialGatePart) extends SequentialGateLogic(gate) with TTimerGateLogic with TExtraStateLogic
:
    override def outputMask(shape:Int) =
        var output = 9
        if shape == 1 then output = flipMaskZ(output)
        output

    override def inputMask(shape:Int) =
        var input = 6
        if shape == 1 then input = flipMaskZ(input)
        input

    override def cycleShape(gate:SequentialGatePart) =
        gate.setShape((gate.shape+1)%2)
        true

    override def onChange(gate:SequentialGatePart): Unit =
        val oldInput = gate.state&0xF
        var newInput = getInput(gate, 0xE)
        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            gate.onInputChange()

            if gate.shape == 1 then newInput = flipMaskZ(newInput)
            if (newInput&4) != 0 && state2 == 0 then
                setState2(1)
                sendState2Update()
                gate.scheduleTick(2)

            if state2 != 0 then if (newInput&6) != 0 then resetPointer()
            else startPointer()

    override def pointerTick(): Unit =
        resetPointer()
        if !gate.world.isRemote then
            setState2(0)
            sendState2Update()
            gate.setState(0x10|gate.state&0xF)
            gate.onOutputChange(outputMask(gate.shape))
            gate.scheduleTick(2)
            tickSound()

    override def scheduledTick(gate:SequentialGatePart): Unit =
        var output = 0
        if state2 != 0 then output = 8
        if gate.shape == 1 then output = flipMaskZ(output)

        gate.setState(output<<4|gate.state&0xF)
        gate.onOutputChange(outputMask(gate.shape))

class Synchronizer(gate:SequentialGatePart) extends SequentialGateLogic(gate) with TExtraStateLogic
:
    override def outputMask(shape:Int) = 1
    override def inputMask(shape:Int) = 14

    override def onChange(gate:SequentialGatePart): Unit =
        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 14)
        val high = newInput& ~oldInput
        if oldInput != newInput then
            val oldValue = state2

            gate.setState(gate.state&0xF0|newInput)
            gate.onInputChange()
            if (newInput&4) != 0 then setState2(0)
            else
                if (high&2) != 0 then setState2(state2|1) //right
                if (high&8) != 0 then setState2(state2|2) //left
            if right && left then gate.scheduleTick(2)

            if state2 != oldValue then sendState2Update()

    override def scheduledTick(gate:SequentialGatePart): Unit =
        val oldValue = state2
        if !pulsing && right && left then
            gate.setState(gate.state|1<<4)
            gate.onOutputChange(1)
            setState2(state2|4) //pulsing
            gate.scheduleTick(2)
        else if pulsing then
            gate.setState(gate.state& ~0x10)
            gate.onOutputChange(1)
            setState2(0) //off
        if state2 != oldValue then sendState2Update()

    def right = (state2&1) != 0
    def left = (state2&2) != 0
    def pulsing = (state2&4) != 0

class Comparator(gate:SequentialGatePart) extends SequentialGateLogic(gate) with INeighborTileChangePart
:
    var lState2:Short = 0

    def state2 = lState2&0xFFFF
    def setState2(i:Int): Unit ={ lState2 = i.toShort }

    override def outputMask(shape:Int) = 1
    override def inputMask(shape:Int) = 0xE

    override def save(tag:NBTTagCompound): Unit ={ tag.setShort("state2", lState2) }
    override def load(tag:NBTTagCompound): Unit ={ lState2 = tag.getShort("state2") }

    override def cycleShape(gate:SequentialGatePart) =
        gate.setShape(if gate.shape > 0 then 0 else 1)
        true

    override def requireStrongInput(r:Int) = r%2 == 1

    override def canConnect(shape:Int, r:Int) = true

    override def getOutput(gate:SequentialGatePart, r:Int) =
        if r == 0 then state2&0xF
        else 0

    def getAnalogInput(r:Int) = (gate.getRedstoneInput(r)+16)/17

    def calcInputA:Int =
        //TODO comparator calculations may not be accurate anymore

        val absDir = EnumFacing.byIndex(Rotation.rotateSide(gate.side, gate.toAbsolute(2)))
        var pos = gate.tile.getPos.offset(absDir)
        var state = gate.world.getBlockState(pos)

        if state.hasComparatorInputOverride then
            return state.getComparatorInputOverride(gate.world, pos)

        val i = getAnalogInput(2)

        if i < 15 && state.isNormalCube then
            pos = pos.offset(absDir)
            state = gate.world.getBlockState(pos)

            if state.hasComparatorInputOverride then
                return state.getComparatorInputOverride(gate.world, pos)

            if state.getMaterial == Material.AIR then
                val entityitemframe = findItemFrame(gate.world, absDir, pos)
                if entityitemframe != null then
                    return entityitemframe.getAnalogOutput

        i

    /**
      * Copied from BlockRedstoneComparator#findItemFrame(World, EnumFacing, BlockPos)
      */
    private def findItemFrame(world:World, facing: EnumFacing, pos:BlockPos) =
        val list:util.List[EntityItemFrame] = world.getEntitiesWithinAABB[EntityItemFrame](classOf[EntityItemFrame], new AxisAlignedBB(pos.getX.toDouble, pos.getY.toDouble, pos.getZ.toDouble,
            (pos.getX + 1).toDouble, (pos.getY + 1).toDouble, (pos.getZ + 1).toDouble), new Predicate[Entity] {
                override def apply(input:Entity) = input != null && (input.getHorizontalFacing == facing)
            }
        )
        if list.size == 1 then list.get(0) else null

    def calcInput = getAnalogInput(1)<<4|calcInputA<<8|getAnalogInput(3)<<12

    def digitize(analog:Int) =
        var digital = 0
        for i <- 0 until 4 do if (analog>>i*4&0xF) > 0 then digital |= 1<<i
        digital

    override def onChange(gate:SequentialGatePart): Unit =
        val oldInput = state2&0xFFF0
        val newInput = calcInput
        if oldInput != newInput then
            setState2(state2&0xF|newInput)
            gate.setState(digitize(newInput|calcOutput)|gate.state&0xF0)
            gate.onInputChange()
        if (state2&0xF) != calcOutput then gate.scheduleTick(2)

    def calcOutput =
        if gate.shape == 0 then if inputA >= inputB then inputA else 0
        else Math.max(inputA - inputB, 0)

    def inputA = state2>>8&0xF
    def inputB = Math.max(state2>>4&0xF, state2>>12&0xF)

    override def scheduledTick(gate:SequentialGatePart): Unit =
        val oldOutput = state2&0xF
        val newOutput = calcOutput
        if oldOutput != newOutput then
            setState2(state2&0xFFF0|newOutput)
            gate.setState(gate.state&0xF|digitize(newOutput)<<4)
            gate.onOutputChange(1)

    override def onNeighborTileChanged(side:Int, weak:Boolean): Unit =
        if side == Rotation.rotateSide(gate.side, gate.toAbsolute(2)) then gate.onChange()

    override def weakTileChanges() = true
