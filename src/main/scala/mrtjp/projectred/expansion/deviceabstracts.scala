/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.multipart.BlockMultipart
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.world.WorldLib
import mrtjp.projectred.core.PRLib
import mrtjp.projectred.transportation.*
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}
import net.minecraft.util.EnumFacing

import scala.collection.mutable.ListBuffer

class ItemStorage
{
    private val storage = ListBuffer[PressurePayload]()
    var backlogged = false

    def isEmpty = storage.isEmpty

    def add(item:PressurePayload): Unit ={ storage.prepend(item) }

    def add(item:ItemStack): Unit =
    {
        val p = new PressurePayload(AbstractPipePayload.claimID())
        p.setItemStack(item)
        add(p)
    }

    def addBacklog(item:PressurePayload): Unit ={ storage.append(item); backlogged = true }

    def poll() =
    {
        val item = storage.remove(storage.size-1)
        if storage.size == 0 then backlogged = false
        item
    }

    def peek = storage(storage.size-1)

    def save(tag:NBTTagCompound): Unit =
    {
        val nbttaglist = new NBTTagList
        for r <- storage do
        {
            val payloadData = new NBTTagCompound
            nbttaglist.appendTag(payloadData)
            r.save(payloadData)
        }
        tag.setTag("itemFlow", nbttaglist)
    }

    def load(tag:NBTTagCompound): Unit =
    {
        val nbttaglist = tag.getTagList("itemFlow", 0)
        for j <- 0 until nbttaglist.tagCount do
        {
            try
            {
                val payloadData = nbttaglist.getCompoundTagAt(j)
                val r = new PressurePayload(AbstractPipePayload.claimID())
                r.load(payloadData)
                if !r.isCorrupted then add(r)
            }
            catch {case t:Throwable =>}
        }
    }
}

trait TActiveDevice extends TileMachine
{
    val itemStorage = new ItemStorage
    var powered = false
    var active = false

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setBoolean("pow", powered)
        tag.setBoolean("act", active)
        itemStorage.save(tag)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        powered = tag.getBoolean("pow")
        active = tag.getBoolean("act")
        itemStorage.load(tag)
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(powered).writeBoolean(active)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        powered = in.readBoolean()
        active = in.readBoolean()
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 4 =>
            powered = in.readBoolean()
            active = in.readBoolean()
            markRender()
        case _ => super.read(in, key)
    }

    def sendStateUpdate(): Unit =
    {
        writeStream(4).writeBoolean(powered).writeBoolean(active).sendToChunk(this)
    }

    def shouldAcceptBacklog = true
    def shouldAcceptInput = !powered && itemStorage.isEmpty

    override def onScheduledTick(): Unit =
    {
        if !getWorld.isRemote then
        {
            if !itemStorage.isEmpty then
            {
                exportBuffer()
                scheduleTick(if itemStorage.isEmpty then 4 else 16)
            }
            else if !powered then
            {
                active = false
                onDeactivate()
                sendStateUpdate()
            }
        }
    }

    override def onNeighborBlockChange(): Unit =
    {
        if getWorld.isBlockPowered(getPos) then
        {
            if powered then return
            powered = true
            markDirty()
            if active then return
            active = true
            onActivate()
            sendStateUpdate()
        }
        else
        {
            if active && !isTickScheduled then scheduleTick(4)
            powered = false
            markDirty()
        }
    }

    def onActivate(): Unit 
    def onDeactivate(): Unit ={}

    def exportBuffer(): Unit =
    {
        while !itemStorage.isEmpty do
        {
            val r = itemStorage.peek
            if exportPipe(r) || exportInv(r) || exportEject(r) then itemStorage.poll()
            else itemStorage.backlogged = true

            if itemStorage.backlogged then return
        }
    }

    def exportPipe(r:PressurePayload) =
    {
        BlockMultipart.getPart(getWorld, getPos.offset(EnumFacing.VALUES(side)), 6) match
        {
            case pipe:TPressureTube if pipe.hasDestination(r, side^1) =>
                pipe.injectPayload(r, side)
                true
            case _ => false
        }
    }

    def exportInv(r:PressurePayload) =
    {
        val w = InvWrapper.wrap(getWorld, getPos.offset(EnumFacing.VALUES(side)), EnumFacing.VALUES(side^1))
        if w != null then
        {
            r.payload.stackSize -= w.injectItem(r.payload.key, r.payload.stackSize)
            r.payload.stackSize <= 0
        }
        else false
    }

    def exportEject(r:PressurePayload):Boolean =
    {
        val pos = getPos.offset(EnumFacing.VALUES(side))
        if getWorld.isBlockLoaded(pos) &&
                !getWorld.isAirBlock(pos) then return false

        WorldLib.centerEject(getWorld, getPos, r.payload.makeStack, side, 0.25D)
        true
    }

    override def onBlockRemoval(): Unit =
    {
        super.onBlockRemoval()
        while !itemStorage.isEmpty do
            WorldLib.dropItem(getWorld, getPos, itemStorage.poll().payload.makeStack)
    }
}

trait TPressureActiveDevice extends TActiveDevice with TPressureDevice
{
    override def acceptItem(item:PressurePayload, side:Int):Boolean =
    {
        if !canConnectSide(side) then return false

        if canAcceptInput(item.payload.key, side) && shouldAcceptInput then
        {
            itemStorage.add(item)
            active = true
            sendStateUpdate()
            scheduleTick(4)
            exportBuffer()
            true
        }
        else if canAcceptBacklog(item.payload.key, side) && shouldAcceptBacklog then
        {
            itemStorage.addBacklog(item)
            active = true
            sendStateUpdate()
            scheduleTick(4)
            true
        }
        else false
    }
}
