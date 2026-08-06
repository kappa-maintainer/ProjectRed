/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.multipart.BlockMultipart
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.ItemKey
import mrtjp.core.world.WorldLib
import mrtjp.projectred.transportation.pneumatics.{PneumaticQueue, PneumaticTransportContainer, PneumaticTransportDevice, PneumaticTransportMode}
import mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing

import scala.collection.mutable.ListBuffer

trait TActiveDevice extends TileMachine
:
    var powered = false
    var active = false

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setBoolean("pow", powered)
        tag.setBoolean("act", active)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        powered = tag.getBoolean("pow")
        active = tag.getBoolean("act")

    override def writeDesc(out:MCDataOutput): Unit =
        super.writeDesc(out)
        out.writeBoolean(powered).writeBoolean(active)

    override def readDesc(in:MCDataInput): Unit =
        super.readDesc(in)
        powered = in.readBoolean()
        active = in.readBoolean()

    override def read(in:MCDataInput, key:Int) = key match
        case 4 =>
            powered = in.readBoolean()
            active = in.readBoolean()
            markRender()
        case _ => super.read(in, key)

    def sendStateUpdate(): Unit =
        writeStream(4).writeBoolean(powered).writeBoolean(active).sendToChunk(this)

    override def onScheduledTick(): Unit =
        if !getWorld.isRemote && !powered then
            active = false
            onDeactivate()
            sendStateUpdate()

    override def onNeighborBlockChange(): Unit =
        if getWorld.isBlockPowered(getPos) then
            if powered then return
            powered = true
            markDirty()
            if active then return
            active = true
            onActivate()
            sendStateUpdate()
        else
            if active && !isTickScheduled then scheduleTick(4)
            powered = false
            markDirty()

    def onActivate(): Unit 
    def onDeactivate(): Unit ={}

trait TPneumaticActiveDevice extends TActiveDevice with PneumaticTransportDevice
:
    val pneumaticQueue = new PneumaticQueue

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        val queueTag = new NBTTagCompound
        pneumaticQueue.save(queueTag)
        tag.setTag("pneumaticQueue", queueTag)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        pneumaticQueue.load(tag.getCompoundTag("pneumaticQueue"))

    def shouldAcceptPneumaticInput = !powered && pneumaticQueue.isEmpty
    def shouldAcceptPneumaticBackstuff = true

    override def canConnectTube(side:Int):Boolean = pneumaticCanConnectSide(side)

    def pneumaticCanConnectSide(side:Int):Boolean = canConnectSide(side)

    override def canAcceptPayload(side:Int, payload:PneumaticTubePayload, mode:PneumaticTransportMode):Boolean =
        if !pneumaticCanConnectSide(side) then false
        else
            val key = ItemKey.get(payload.getItemStack)
            mode match
                case PneumaticTransportMode.PASSIVE_NORMAL =>
                    canAcceptInput(key, side) && shouldAcceptPneumaticInput
                case PneumaticTransportMode.PASSIVE_BACKSTUFF =>
                    canAcceptBacklog(key, side) && shouldAcceptPneumaticBackstuff

    override def insertPayload(side:Int, payload:PneumaticTubePayload):Boolean =
        val key = ItemKey.get(payload.getItemStack)
        val accepted = if canAcceptInput(key, side) && shouldAcceptPneumaticInput then
            pneumaticQueue.add(payload)
            true
        else if canAcceptBacklog(key, side) && shouldAcceptPneumaticBackstuff then
            pneumaticQueue.addBackstuffed(payload)
            true
        else false

            if accepted then
                active = true
                sendStateUpdate()
                scheduleTick(4)
            accepted

    override def onScheduledTick(): Unit =
        if !getWorld.isRemote then
            if !pneumaticQueue.isEmpty then
                exportPneumaticQueue()
                scheduleTick(if pneumaticQueue.isEmpty then 4 else 16)
            else if !powered then
                active = false
                onDeactivate()
                sendStateUpdate()

    def exportPneumaticQueue(): Unit =
        while !pneumaticQueue.isEmpty do
            val payload = pneumaticQueue.poll()
            if !exportPneumaticTube(payload) && !exportPneumaticInventory(payload) && !exportPneumaticEject(payload) then
                pneumaticQueue.addBackstuffed(payload)

            if pneumaticQueue.isBackstuffed then return

    def exportPneumaticTube(payload:PneumaticTubePayload):Boolean =
        BlockMultipart.getPart(getWorld, getPos.offset(EnumFacing.VALUES(side)), 6) match
            case container:PneumaticTransportContainer => container.insertPayload(side, payload)
            case _ => false

    def exportPneumaticInventory(payload:PneumaticTubePayload):Boolean =
        val inventory = InvWrapper.wrap(getWorld, getPos.offset(EnumFacing.VALUES(side)), EnumFacing.VALUES(side ^ 1))
        if inventory == null then false
        else
            val stack = payload.getItemStack
            val inserted = inventory.injectItem(ItemKey.get(stack), stack.getCount)
            if inserted <= 0 then false
            else
                stack.shrink(inserted)
                if stack.isEmpty then true
                else
                    payload.setItemStack(stack)
                    false

    def exportPneumaticEject(payload:PneumaticTubePayload):Boolean =
        val outputPos = getPos.offset(EnumFacing.VALUES(side))
        if getWorld.isBlockLoaded(outputPos) && !getWorld.isAirBlock(outputPos) then false
        else
            WorldLib.centerEject(getWorld, getPos, payload.getItemStack, side, 0.25D)
            true

    override def onBlockRemoval(): Unit =
        super.onBlockRemoval()
        while !pneumaticQueue.isEmpty do
            WorldLib.dropItem(getWorld, getPos, pneumaticQueue.poll().getItemStack)

    def canAcceptInput(item:ItemKey, side:Int):Boolean
    def canAcceptBacklog(item:ItemKey, side:Int):Boolean
    def canConnectSide(side:Int):Boolean
