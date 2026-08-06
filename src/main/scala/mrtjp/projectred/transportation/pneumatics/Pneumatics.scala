package mrtjp.projectred.transportation.pneumatics

import mrtjp.projectred.transportation.pneumatics.part.PneumaticTubePayload
import net.minecraft.nbt.{NBTTagCompound, NBTTagList}

import scala.collection.mutable.{ArrayBuffer, HashMap}

enum PneumaticTransportMode:
    case PASSIVE_NORMAL
    case PASSIVE_BACKSTUFF

class PneumaticTransport(val container:PneumaticTransportContainer):
    private val payloads = HashMap[Int, PneumaticTubePayload]()
    private var nextId = 0

    def getPayload(id:Int) = payloads.getOrElse(id, null)
    def getPayloads = payloads.values

    def addPayload(id:Int, payload:PneumaticTubePayload): Unit =
        payloads.put(id, payload)
        if id >= nextId then nextId = id + 1

    def addPayload(payload:PneumaticTubePayload):Int =
        val id = nextIdValue()
        payloads.put(id, payload)
        container.onPayloadAdded(id, payload)
        id

    def removePayload(id:Int) = payloads.remove(id).orNull

    def removePayloadsWhere(predicate:PneumaticTubePayload => Boolean):Seq[Int] =
        val removed = payloads.collect { case (id, payload) if predicate(payload) => id }.toSeq
        removed.foreach(payloads.remove)
        removed

    def tick(): Unit =
        val remove = ArrayBuffer[Int]()
        for (id, payload) <- payloads do
            val before = payload.getProgress
            payload.incrementProgress()
            val after = payload.getProgress
            val boundary = PneumaticTubePayload.MAX_PROGRESS / 2 - payload.getSpeed
            if before < boundary && after >= boundary then
                container.setOutputDirection(payload)
                container.onPayloadChanged(id, payload)
            if before < PneumaticTubePayload.MAX_PROGRESS && after >= PneumaticTubePayload.MAX_PROGRESS || before >= PneumaticTubePayload.MAX_PROGRESS then
                if container.onPayloadReachedOutput(id, payload) then remove += id
        remove.distinct.foreach { id =>
            payloads.remove(id).foreach(payload => container.onPayloadRemoved(id, payload))
        }

    def save(tag:NBTTagCompound): Unit =
        val list = new NBTTagList
        for (id, payload) <- payloads do
            val payloadTag = new NBTTagCompound
            payloadTag.setInteger("id", id)
            payload.save(payloadTag)
            list.appendTag(payloadTag)
        tag.setTag("payloads", list)
        tag.setInteger("next_id", nextId)

    def load(tag:NBTTagCompound): Unit =
        payloads.clear()
        val list = tag.getTagList("payloads", 10)
        for i <- 0 until list.tagCount do
            val payloadTag = list.getCompoundTagAt(i)
            val payload = new PneumaticTubePayload
            payload.load(payloadTag)
            if !payload.isCorrupted then payloads.put(payloadTag.getInteger("id"), payload)
        nextId = tag.getInteger("next_id")

    def writeDesc(out:codechicken.lib.data.MCDataOutput): Unit =
        out.writeByte(payloads.size)
        for (id, payload) <- payloads do
            out.writeInt(id)
            payload.writeDesc(out)

    def readDesc(in:codechicken.lib.data.MCDataInput): Unit =
        payloads.clear()
        for _ <- 0 until in.readUByte() do
            val id = in.readInt()
            val payload = new PneumaticTubePayload
            payload.readDesc(in)
            payloads.put(id, payload)

    def writePayloadUpdate(out:codechicken.lib.data.MCDataOutput, id:Int, payload:PneumaticTubePayload): Unit =
        out.writeInt(id)
        payload.writeDesc(out)

    def readPayloadUpdate(in:codechicken.lib.data.MCDataInput): Unit =
        val id = in.readInt()
        val payload = payloads.getOrElseUpdate(id, new PneumaticTubePayload)
        payload.readDesc(in)

    def writePayloadRemove(out:codechicken.lib.data.MCDataOutput, id:Int): Unit =
        out.writeInt(id)

    def readPayloadRemove(in:codechicken.lib.data.MCDataInput): Unit =
        payloads.remove(in.readInt())

    private def nextIdValue():Int =
        while payloads.contains(nextId) do nextId += 1
        val id = nextId
        nextId += 1
        id

trait PneumaticTransportContainer:
    def getPneumaticTransport:PneumaticTransport

    def setOutputDirection(payload:PneumaticTubePayload): Unit
    def onPayloadAdded(id:Int, payload:PneumaticTubePayload):Unit
    def onPayloadChanged(id:Int, payload:PneumaticTubePayload): Unit
    def onPayloadReachedOutput(id:Int, payload:PneumaticTubePayload):Boolean
    def onPayloadRemoved(id:Int, payload:PneumaticTubePayload):Unit

    def canItemEnterTube(payload:PneumaticTubePayload, side:Int):Boolean
    def canItemExitTube(payload:PneumaticTubePayload, side:Int, mode:PneumaticTransportMode):Boolean
    def insertPayload(side:Int, payload:PneumaticTubePayload):Boolean

trait PneumaticTransportDevice:
    def canConnectTube(side:Int):Boolean
    def canAcceptPayload(side:Int, payload:PneumaticTubePayload, mode:PneumaticTransportMode):Boolean
    def insertPayload(side:Int, payload:PneumaticTubePayload):Boolean

class PneumaticQueue:
    private val queue = ArrayBuffer[PneumaticTubePayload]()
    private var backstuffed = false

    def isEmpty = queue.isEmpty
    def isBackstuffed = backstuffed
    def size = queue.size

    def add(payload:PneumaticTubePayload): Unit = queue += payload

    def addBackstuffed(payload:PneumaticTubePayload): Unit =
        queue.prepend(payload)
        backstuffed = true

    def poll():PneumaticTubePayload =
        if queue.isEmpty then null else
            val payload = queue.remove(0)
            if queue.isEmpty then backstuffed = false
            payload

    def peek:PneumaticTubePayload = if queue.isEmpty then null else queue.head

    def foreach(f:PneumaticTubePayload => Unit): Unit = queue.foreach(f)

    def save(tag:NBTTagCompound): Unit =
        val list = new NBTTagList
        queue.foreach { payload =>
            val payloadTag = new NBTTagCompound
            payload.save(payloadTag)
            list.appendTag(payloadTag)
        }
        tag.setTag("payloads", list)
        tag.setBoolean("backstuffed", backstuffed)

    def load(tag:NBTTagCompound): Unit =
        queue.clear()
        backstuffed = tag.getBoolean("backstuffed")
        val list = tag.getTagList("payloads", 10)
        for i <- 0 until list.tagCount do
            val payload = new PneumaticTubePayload
            payload.load(list.getCompoundTagAt(i))
            if !payload.isCorrupted then queue += payload
        if queue.isEmpty then backstuffed = false
