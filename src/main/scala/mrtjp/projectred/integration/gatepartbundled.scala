/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.integration

import java.util.Random

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.{CuboidRayTraceResult, IndexedCuboid6}
import codechicken.lib.vec.{Cuboid6, Vector3}
import mrtjp.core.vec.VecLib
import mrtjp.projectred.api.{IBundledEmitter, IBundledTile, IConnectable, IScrewdriver}
import mrtjp.projectred.core.Configurator
import mrtjp.projectred.core.TFaceOrient.*
import mrtjp.projectred.transmission.BundledCommons.*
import mrtjp.projectred.transmission.{APIImpl_Transmission, TFaceBundledAquisitions}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing

trait TBundledGatePart extends GatePart with TFaceBundledAquisitions with IBundledEmitter
:
    def getLogicBundled = getLogic[TBundledGateLogic[TBundledGatePart]]

    abstract override def discoverStraightOverride(absDir:Int) =
        val pos = posOfStraight(absDir)
        world.getTileEntity(pos) match
            case t:IBundledTile => t.canConnectBundled(absDir^1)
            case _ if APIImpl_Transmission.canConnectBundled(world, pos, EnumFacing.values()(absDir^1)) => true
            case _ => super.discoverStraightOverride(absDir)

    override def getBundledSignal(r:Int) =
        val ir = toInternal(r)
        val logic = getLogicBundled
        if (logic.bundledOutputMask(shape)&1<<ir) != 0 then logic.getBundledOutput(this, ir)
        else null

    def getBundledInput(r:Int) =
        val ar = toAbsolute(r)
        if maskConnectsCorner(ar) then calcCornerArray(ar)
        else if maskConnectsStraight(ar) then calcStraightArray(ar)
        else if maskConnectsInside(ar) then calcInternalArray(ar)
        else null

    override def resolveArray(part:Any, r:Int) = part match
        case be:IBundledEmitter => be.getBundledSignal(r)
        case _ => null

trait TBundledGateLogic[T <: TBundledGatePart] extends GateLogic[T]
:
    abstract override def canConnectTo(gate:T, part:IConnectable, r:Int) = part match
        case be:IBundledEmitter => canConnectBundled(gate, r)
        case _ => super.canConnectTo(gate, part, r)

    def canConnectBundled(gate:T, r:Int):Boolean = canConnectBundled(gate.shape, r)
    def canConnectBundled(shape:Int, r:Int):Boolean = ((bundledInputMask(shape)|bundledOutputMask(shape))&1<<r) != 0

    def bundledInputMask(shape:Int) = 0
    def bundledOutputMask(shape:Int) = 0

    def getBundledOutput(gate:T, r:Int):Array[Byte] = null

class BundledGatePart extends RedstoneGatePart with TBundledGatePart with TComplexGatePart
:
    private var logic:BundledGateLogic = null

    override def getLogic[T]:T = logic.asInstanceOf[T]

    override def assertLogic(): Unit =
        if logic == null then logic = BundledGateLogic.create(this, subID)

    override def getType = GateDefinition.typeBundledGate

object BundledGateLogic
:
    def create(gate:BundledGatePart, subID:Int) = subID match
        case GateDefinition.BusTransceiver.ordinal => new BusTransceiver(gate)
        case GateDefinition.BusRandomizer.ordinal => new BusRandomizer(gate)
        case GateDefinition.BusConverter.ordinal => new BusConverter(gate)
        case GateDefinition.BusInputPanel.ordinal => new BusInputPanel(gate)
        case GateDefinition.SegmentDisplay.ordinal => new SegmentDisplay(gate)
        case _ => throw new IllegalArgumentException("Invalid gate subID: "+subID)

abstract class BundledGateLogic(val gate:BundledGatePart) extends RedstoneGateLogic[BundledGatePart] with TBundledGateLogic[BundledGatePart] with TComplexGateLogic[BundledGatePart]

class BusTransceiver(gate:BundledGatePart) extends BundledGateLogic(gate)
:
    var input0, output0, input2, output2:Array[Byte] = null

    override def bundledOutputMask(shape:Int) = 5
    override def bundledInputMask(shape:Int) = 5
    override def outputMask(shape:Int) = 0
    override def inputMask(shape:Int) = 10

    override def save(tag:NBTTagCompound): Unit =
        saveSignal(tag, "in0", input0)
        saveSignal(tag, "out0", output0)
        saveSignal(tag, "in2", input2)
        saveSignal(tag, "out2", output2)

    override def load(tag:NBTTagCompound): Unit =
        input0 = loadSignal(tag, "in0")
        input2 = loadSignal(tag, "in2")
        output0 = loadSignal(tag, "out0")
        output2 = loadSignal(tag, "out2")

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeInt(packClientData)

    override def readDesc(packet:MCDataInput): Unit =
        unpackClientData(packet.readInt)

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => unpackClientData(packet.readInt())
        case _ =>

    def sendClientUpdate(): Unit =
        gate.getWriteStreamOf(11).writeInt(packClientData)

    def packClientData = packDigital(output0)|packDigital(output2)<<16

    def unpackClientData(packed:Int): Unit =
        output0 = unpackDigital(output0, packed&0xFFFF)
        output2 = unpackDigital(output2, packed>>>16)

    override def getBundledOutput(gate:BundledGatePart, r:Int) = if r == 0 then output0 else output2

    def getBundledInput(r:Int) = raiseSignal(copySignal(gate.getBundledInput(r)), getBundledOutput(gate, r)) //OR'd w/ output

    override def onChange(gate:BundledGatePart): Unit =
        var inputChanged = false

        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 10)
        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            inputChanged = true

        val newInput0 = getBundledInput(0)
        if !signalsEqual(input0, newInput0) then
            input0 = newInput0
            inputChanged = true

        val newInput2 = getBundledInput(2)
        if !signalsEqual(input2, newInput2) then
            input2 = newInput2
            inputChanged = true

        if inputChanged then gate.onInputChange()
        if !signalsEqual(output0, getBundledOutput(0)) || !signalsEqual(output2, getBundledOutput(2)) then gate.scheduleTick(2)

    def getBundledOutput(r:Int):Array[Byte] =
        var input = gate.state&0xF
        if gate.shape == 1 then input = flipMaskZ(input)

        if r == 0 then return if (input&2) != 0 then input2 else null
        if r == 2 then return if (input&8) != 0 then input0 else null
        null

    override def scheduledTick(gate:BundledGatePart): Unit =
        output0 = getBundledOutput(0)
        output2 = getBundledOutput(2)
        onChange(gate)
        gate.onOutputChange(5)
        sendClientUpdate()

    override def cycleShape(gate:BundledGatePart) =
        gate.setShape(gate.shape^1)
        true

    override def lightLevel = 0

class BusRandomizer(gate:BundledGatePart) extends BundledGateLogic(gate)
:
    val rand = new Random

    var unpackedOut = new Array[Byte](16)
    var output = 0
    var mask = 0xFFFF

    override def bundledOutputMask(shape:Int) = 5
    override def inputMask(shape:Int) = 10
    override def outputMask(shape:Int) = 0

    override def save(tag:NBTTagCompound): Unit =
        tag.setShort("in", (mask&0xFFFF).asInstanceOf[Short])
        tag.setShort("out", (output&0xFFFF).asInstanceOf[Short])

    override def load(tag:NBTTagCompound): Unit =
        mask = tag.getShort("in")
        output = tag.getShort("out")
        unpackedOut = unpackDigital(unpackedOut, output)

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeShort(output)
        packet.writeShort(mask)

    override def readDesc(packet:MCDataInput): Unit =
        output = packet.readUShort()
        mask = packet.readUShort()

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => output = packet.readUShort()
        case 12 => mask = packet.readUShort()
        case _ =>

    def sendOutUpdate(): Unit =
        gate.getWriteStreamOf(11).writeShort(output)

    def sendMaskUpdate(): Unit =
        gate.getWriteStreamOf(12).writeShort(mask)

    override def onChange(gate:BundledGatePart): Unit =
        var inputChanged = false
        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 10)
        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            inputChanged = true

        var newMask = packDigital(gate.getBundledInput(2))
        if newMask == 0 then newMask = 0xFFFF
        if mask != newMask then
            mask = newMask
            inputChanged = true
            sendMaskUpdate()

        if inputChanged then gate.onInputChange()
        if newInput != 0 then gate.scheduleTick(2)

    override def scheduledTick(gate:BundledGatePart): Unit =
        val oldOut = output
        output = if (gate.state&0xF) != 0 then if gate.shape == 0 then calc1BitOut else calcNBitOut else oldOut
        if oldOut != output then
            unpackedOut = unpackDigital(unpackedOut, output)
            gate.onOutputChange(1)
            sendOutUpdate()
        onChange(gate)

    def calc1BitOut:Int =
        val high = Integer.bitCount(mask)
        val n = rand.nextInt(high)
        var v = 0
        var result = 0
        for i <- 0 until 16 do if result == 0 && (mask&1<<i) != 0 then
            if v == n then result = 1<<i
            v += 1
        result

    def calcNBitOut =
        var out = 0
        for i <- 0 until 16 do if (mask&1<<i) != 0 && rand.nextBoolean then out |= 1<<i
        out

    override def getBundledOutput(gate:BundledGatePart, r:Int) = if r == 0 then unpackedOut else null

    override def cycleShape(gate:BundledGatePart) =
        gate.setShape(gate.shape^1)
        true

    override def lightLevel = 0

class BusConverter(gate:BundledGatePart) extends BundledGateLogic(gate)
:
    var bIn, bOut = 0
    var rsIn, rsOut = 0
    var bOutUnpacked:Array[Byte] = null

    override def bundledOutputMask(shape:Int) = if shape == 0 then 1 else 0
    override def bundledInputMask(shape:Int) = if shape == 0 then 0 else 1
    override def outputMask(shape:Int) = if shape == 0 then 10 else 14
    override def inputMask(shape:Int) = if shape == 0 then 4 else 0

    def setBOut(newBOut:Int): Unit =
        if bOut == newBOut then return
        bOut = newBOut
        bOutUnpacked = unpackDigital(bOutUnpacked, bOut)

    override def save(tag:NBTTagCompound): Unit =
        tag.setByte("in", rsIn.toByte)
        tag.setByte("out", rsOut.toByte)
        tag.setByte("in0", bIn.toByte)
        tag.setByte("out0", bOut.toByte)

    override def load(tag:NBTTagCompound): Unit =
        rsIn = tag.getByte("in")
        rsOut = tag.getByte("out")
        bIn = tag.getByte("in0")
        setBOut(tag.getByte("out0"))

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeShort(packClientData)

    override def readDesc(packet:MCDataInput): Unit =
        unpackClientData(packet.readUShort)

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => unpackClientData(packet.readUShort)
        case _ =>

    def sendClientUpdate(): Unit =
        gate.getWriteStreamOf(11).writeShort(packClientData)

    def packClientData = rsIn|rsOut<<4|mostSignificantBit(bIn)<<8|mostSignificantBit(bOut)<<12

    def unpackClientData(data:Int): Unit =
        rsIn = data&0xF
        rsOut = data>>4&0xF
        bIn = 1<<(data>>8&0xF)
        setBOut(1<<(data>>12&0xF))

    override def getOutput(gate:BundledGatePart, r:Int) =
        if gate.shape != 0 && r == 2 then rsOut else if (gate.state&0x10 << r) != 0 then 15 else 0

    override def getBundledOutput(gate:BundledGatePart, r:Int) = if gate.shape == 0 && r == 0 then bOutUnpacked else null

    override def onChange(gate:BundledGatePart): Unit =
        var changed = false

        val oldRSIn = rsIn
        rsIn = if gate.shape == 0 then gate.getRedstoneInput(2)/17 else 0
        if oldRSIn != rsIn then changed = true

        val oldBIn = bIn
        bIn = if gate.shape == 0 then 0 else packDigital(gate.getBundledInput(0))
        if oldBIn != bIn then changed = true

        if changed then
            gate.onInputChange()
            gate.scheduleTick(2)
            sendClientUpdate()

    override def scheduledTick(gate:BundledGatePart): Unit =
        var changeMask = 0

        val oldBOut = bOut
        setBOut(if gate.shape == 0 then 1<<rsIn else 0)
        if oldBOut != bOut then changeMask |= 1

        val oldRSOut = rsOut
        rsOut = if gate.shape == 0 then 0 else mostSignificantBit(bIn)
        if rsOut != oldRSOut then changeMask |= 4

        val oldOut2 = gate.state>>4
        val newOut2 = if (if gate.shape == 0 then rsIn else bIn) != 0 then 10 else 0
        if oldOut2 != newOut2 then
            gate.setState(gate.state&0xF|newOut2<<4)
            changeMask |= 10

        if changeMask != 0 then
            gate.onOutputChange(changeMask)
            sendClientUpdate()

        onChange(gate)

    /**
     * Shape 0: Analog to Digital (RS to Bundled)
     * Shape 1: Digital to Analog (Bundled to RS)
     */
    override def cycleShape(gate:BundledGatePart) =
        gate.setShape(gate.shape^1)
        true

    override def lightLevel = 0

object BusInputPanel
:
    val unpressed = VecLib.buildCubeArray(4, 4, new Cuboid6(3, 1, 3, 13, 3, 13), new Vector3(-0.25, 0, -0.25))
    val pressed = VecLib.buildCubeArray(4, 4, new Cuboid6(3, 1, 3, 13, 2.5, 13), new Vector3(-0.25, 0, -0.25))

class BusInputPanel(gate:BundledGatePart) extends BundledGateLogic(gate)
:
    var pressMask = 0

    var bOut = 0
    var bOutUnpack:Array[Byte] = null

    override def bundledOutputMask(shape:Int) = 4
    override def bundledInputMask(shape:Int) = 0
    override def outputMask(shape:Int) = 0
    override def inputMask(shape:Int) = 1

    def setBOut(newBOut:Int): Unit =
        if bOut == newBOut then return
        bOut = newBOut
        bOutUnpack = unpackDigital(bOutUnpack, bOut)

    override def save(tag:NBTTagCompound): Unit =
        tag.setShort("press", pressMask.toShort)
        tag.setShort("mask", bOut.toShort)

    override def load(tag:NBTTagCompound): Unit =
        pressMask = tag.getShort("press")
        setBOut(tag.getShort("mask")&0xFFFF)

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeShort(pressMask)

    override def readDesc(packet:MCDataInput): Unit =
        pressMask = packet.readUShort

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => pressMask = packet.readShort()
        case _ =>

    def sendClientUpdate(): Unit =
        gate.getWriteStreamOf(11).writeShort(pressMask)

    override def getOutput(gate:BundledGatePart, r:Int) = if (gate.state&0x10<<r) != 0 then 15 else 0

    override def getBundledOutput(gate:BundledGatePart, r:Int) = bOutUnpack

    override def onChange(gate:BundledGatePart): Unit =
        var inputChanged = false

        val oldInput = gate.state&0xF
        val newInput = getInput(gate, 1)
        if oldInput != newInput then
            gate.setState(gate.state&0xF0|newInput)
            inputChanged = true

        if (gate.state&1) != 0 then pressMask = 0

        val oldBInput = bOut
        val newBInput = pressMask
        if oldBInput != newBInput then inputChanged = true

        if inputChanged then
            gate.onInputChange()
            gate.scheduleTick(2)

    override def scheduledTick(gate:BundledGatePart): Unit =
        var outputChanged:Boolean = false

        val oldBOut = bOut
        val newBOut = pressMask
        if oldBOut != newBOut then
            setBOut(pressMask)
            outputChanged = true
            sendClientUpdate()

        if outputChanged then gate.onOutputChange(bundledOutputMask(gate.shape))
        onChange(gate)

    import mrtjp.projectred.integration.BusInputPanel.*
    override def getSubParts(gate:BundledGatePart) = (0 until 16).map(i => new IndexedCuboid6(i,
        (if (pressMask&1<<i) != 0 then pressed else unpressed)(i).copy.apply(VecLib.orientT(gate.orientation))))

    override def activate(part:BundledGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult):Boolean =
        if !held.isEmpty && held.getItem.isInstanceOf[IScrewdriver] then return false

        val hitdata = hit.cuboid6.data.asInstanceOf[Int]
        if hitdata != -1 then
            if !part.world.isRemote then
                pressMask ^= (1<<hitdata)
                onChange(part)
            return true
        false

class SegmentDisplay(gate:BundledGatePart) extends BundledGateLogic(gate)
:
    var bInH = 0
    var colour:Byte = EnumColour.RED.ordinal.toByte

    override def bundledInputMask(shape:Int) = 1

    override def save(tag:NBTTagCompound): Unit =
        tag.setByte("in", bInH.toByte)
        tag.setByte("col", colour)

    override def load(tag:NBTTagCompound): Unit =
        bInH = tag.getByte("in")
        colour = tag.getByte("col")

    override def writeDesc(packet:MCDataOutput): Unit =
        packet.writeByte(bInH)
        packet.writeByte(colour)

    override def readDesc(packet:MCDataInput): Unit =
        bInH = packet.readByte()
        colour = packet.readByte()

    override def read(packet:MCDataInput, key:Int) = key match
        case 11 => bInH = packet.readByte()
        case 12 =>
            colour = packet.readByte()
            if Configurator.staticGates then gate.tile.markRender()
        case _ =>

    def sendClientUpdate(): Unit =
        gate.getWriteStreamOf(11).writeByte(bInH)

    def sendColourUpdate(): Unit =
        gate.getWriteStreamOf(12).writeByte(colour)

    override def cycleShape(gate:BundledGatePart) =
        gate.setShape(gate.shape^1)
        true

    override def onChange(gate:BundledGatePart): Unit =
        val newBIn = packDigital(gate.getBundledInput(0))
        if (bInH<<8|gate.state) != newBIn then
            gate.setState(newBIn&0xFF)
            bInH = newBIn>>8
            gate.onInputChange()
            sendClientUpdate()
            gate.scheduleTick(2)

    override def scheduledTick(gate:BundledGatePart): Unit =
        onChange(gate)

    override def activate(gate:BundledGatePart, player:EntityPlayer, held:ItemStack, hit:CuboidRayTraceResult):Boolean =
        if !held.isEmpty then
            val c = EnumColour.fromDyeStack(held)
            if c != null && c.ordinal != (colour&0xFF) && c != EnumColour.BLACK then
                if !gate.world.isRemote then
                    colour = c.ordinal.toByte
                    sendColourUpdate()
                return true
        false
