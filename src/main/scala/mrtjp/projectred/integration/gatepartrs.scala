/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.integration

import java.util.Random

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.multipart.IRandomDisplayTickPart
import codechicken.multipart.handler.MultipartProxy
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.{Configurator, TFaceRSAcquisitions}
import mrtjp.projectred.core.IRedwireEmitter
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing

abstract class RedstoneGatePart extends GatePart with TFaceRSAcquisitions with IRandomDisplayTickPart
{
    /**
     * Mapped inputs and outputs of the gate.
     * OOOO IIII
     * High nybble is output.
     * Low nybble is input
     */
    private var gateState:Byte = 0

    def state = gateState&0xFF
    def setState(s:Int): Unit ={ gateState = s.toByte }

    def getLogicRS = getLogic[RedstoneGateLogic[RedstoneGatePart]]

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setByte("state", gateState)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        gateState = tag.getByte("state")
    }

    override def writeDesc(packet:MCDataOutput): Unit =
    {
        super.writeDesc(packet)
        packet.writeByte(gateState)
    }

    override def readDesc(packet:MCDataInput): Unit =
    {
        super.readDesc(packet)
        gateState = packet.readByte()
    }

    override def read(packet:MCDataInput, key:Int) = key match
    {
        case 5 =>
            gateState = packet.readByte()
            if Configurator.staticGates then tile.markRender()
        case _ => super.read(packet, key)
    }

    def sendStateUpdate(): Unit =
    {
        getWriteStreamOf(5).writeByte(gateState)
    }

    def onInputChange(): Unit =
    {
        tile.markDirty()
        sendStateUpdate()
    }

    def onOutputChange(mask:Int): Unit =
    {
        tile.markDirty()
        sendStateUpdate()
        tile.internalPartChange(this)
        notifyExternals(toAbsoluteMask(mask))
    }

    override def strongPowerLevel(side:Int):Int =
    {
        if (side&6) == (this.side&6) then return 0
        val ir = toInternal(absoluteRot(side))
        if (getLogicRS.outputMask(shape)&1<<ir) != 0 then getLogicRS.getOutput(this, ir) else 0
    }

    override def weakPowerLevel(side:Int) = strongPowerLevel(side)

    override def canConnectRedstone(side:Int) =
    {
        if (side&6) == (this.side&6) then false
        else getLogicRS.canConnect(this, toInternal(absoluteRot(side)))
    }

    override def notifyExternals(mask:Int): Unit =
    {
        var smask = 0

        for r <- 0 until 4 do if (mask&1<<r) != 0 then {
            val absSide = absoluteDir(r)
            val pos = this.pos.offset(EnumFacing.values()(absSide))

            world.neighborChanged(pos, MultipartProxy.block, pos)
            for s <- 0 until 6 do if s != (absSide^1) && (smask&1<<s) == 0 then
                world.neighborChanged(pos.offset(EnumFacing.values()(s)), MultipartProxy.block, pos)

            smask |= 1<<absSide
        }
    }

    def getRedstoneInput(r:Int) =
    {
        val ar = toAbsolute(r)
        if maskConnectsCorner(ar) then calcCornerSignal(ar)
        else if maskConnectsStraight(ar) then calcStraightSignal(ar)
        else if maskConnectsInside(ar) then calcInternalSignal(ar)
        else calcMaxSignal(ar, getLogicRS.requireStrongInput(r), false)
    }

    override def resolveSignal(part:Any, r:Int) = part match
    {
        case re:IRedwireEmitter => re.getRedwireSignal(r)
        case _ => 0
    }

    override def randomDisplayTick(rand:Random): Unit =
    {
        RenderGate.spawnParticles(this, rand)
    }
}

abstract class RedstoneGateLogic[T <: RedstoneGatePart] extends GateLogic[T]
{
    override def canConnectTo(gate:T, part:IConnectable, r:Int) = part match
    {
        case re:IRedwireEmitter => canConnect(gate, r)
        case _ => false
    }

    def canConnect(gate:T, r:Int):Boolean = canConnect(gate.shape, r)
    def canConnect(shape:Int, r:Int):Boolean = ((inputMask(shape)|outputMask(shape))&1<<r) != 0

    def outputMask(shape:Int) = 0
    def inputMask(shape:Int) = 0

    def getOutput(gate:T, r:Int) = if (gate.state&0x10<<r) != 0 then 15 else 0
    def getInput(gate:T, mask:Int) =
    {
        var input = 0
        for r <- 0 until 4 do if (mask&1<<r) != 0 && gate.getRedstoneInput(r) > 0 then input |= 1<<r
        input
    }

    def requireStrongInput(r:Int) = false
}
