package mrtjp.projectred.transportation

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import mrtjp.core.item.ItemKeyStack
import mrtjp.projectred.core.Configurator
import mrtjp.projectred.transportation.Priorities.NetworkPriority
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos

import java.util.UUID
import scala.collection.immutable.HashSet

object AbstractPipePayload
:
    private var maxID = 0

    def claimID() =
        if maxID < Short.MaxValue then maxID += 1
        else maxID = 0
        maxID

    def create():NetworkPayload = new NetworkPayload(claimID())

//    def apply(stack:ItemStack):NetworkPayload = apply(ItemKeyStack.get(stack))
//    def apply(stack:ItemKeyStack):NetworkPayload = apply(claimID(), stack)
//
//    def apply(id:Int, stack:ItemStack):NetworkPayload = apply(id, ItemKeyStack.get(stack))
//    def apply(id:Int, stack:ItemKeyStack):NetworkPayload =
//    {
//        val r = new NetworkPayload(id)
//        r.payload = stack.copy
//        r
//    }

class AbstractPipePayload(val payloadID:Int)
:
    var payload:ItemKeyStack = null
    var parent:PayloadPipePart[?] = null

    // 0000 0000 PPPP PPPP SSSS SSSS 0EOO OIII
    // I = input
    // O = output
    // E = isEntering
    // S = speed
    // P = progress
    var data = 0

    private var wanderThroughs = 0
    def tickPayloadWander(): Unit =
        if Configurator.maxPipesWandered > 0 then
            wanderThroughs += 1

    def isEntering = ((data>>6)&1) != 0
    def isEntering_=(b:Boolean): Unit ={ if b then data |= 0x40 else data &= ~0x40 }

    def speed = ((data>>8)&0xFF)/100.0F
    def speed_=(f:Float): Unit ={ data = data&0xFFFF00FF|((f*100).toInt&0xFF)<<8 }

    def progress = ((data>>16)&0xFF)/100.0F
    def progress_=(f:Float): Unit ={ data = data&0xFF00FFFF|((f*100).toInt&0xFF)<<16 }

    def input = data&0x7
    def input_=(i:Int): Unit ={ data = (data& ~0x7)|(i&0x7) }

    def output = (data>>3)&0x7
    def output_=(i:Int): Unit ={ data = (data& ~0x38)|(i&0x7)<<3 }

    def bind(p:PayloadPipePart[?]): Unit ={ parent = p }

    def reset(): Unit =
        isEntering = true
        input = 6
        output = 6

    def preItemRemove(): Unit ={}

    def moveProgress(prog:Float): Unit =
        progress += prog

    def getItemStack = payload.makeStack

    def setItemStack(item:ItemStack): Unit =
        payload = ItemKeyStack.get(item)

    def isCorrupted = getItemStack.isEmpty || getItemStack.getCount <= 0 ||
            (Configurator.maxPipesWandered > 0 && wanderThroughs > Configurator.maxPipesWandered)

    override def equals(other:Any) = other match
        case that:AbstractPipePayload => payloadID == that.payloadID
        case _ => false

    override def hashCode() = payloadID

    def save(tag:NBTTagCompound): Unit =
        tag.setInteger("idata", data)
        val tag2 = new NBTTagCompound
        getItemStack.writeToNBT(tag2)
        tag.setTag("Item", tag2)

    def load(tag:NBTTagCompound): Unit =
        data = tag.getInteger("idata")
        setItemStack(new ItemStack(tag.getCompoundTag("Item")))

    def writeDesc(packet:MCDataOutput): Unit =
        packet.writeItemStack(getItemStack)
        packet.writeInt(data)

    def readDesc(packet:MCDataInput): Unit =
        setItemStack(packet.readItemStack())
        data = packet.readInt()


    def getEntityForDrop(pos: BlockPos):EntityItem = getEntityForDrop(pos.getX, pos.getY, pos.getZ)
    def getEntityForDrop(x:Int, y:Int, z:Int):EntityItem =
        val dir = if isEntering then input else output
        val prog = progress
        var deltaX = x+0.5D
        var deltaY = y+0.25D
        var deltaZ = z+0.5D
        dir match
            case 0 => deltaY = (y-0.25D)+(1.0D-prog)
            case 1 => deltaY = (y-0.25D)+prog
            case 2 => deltaZ = z+(1.0D-prog)
            case 3 => deltaZ = z+prog
            case 4 => deltaX = x+(1.0D-prog)
            case 5 => deltaX = x+prog
            case _ =>

        val item = new EntityItem(parent.world, deltaX, deltaY, deltaZ, payload.makeStack)
        item.motionX = 0
        item.motionY = 0
        item.motionZ = 0
        item.hoverStart = 0
        dir match
            case 0 => item.motionY = -speed
            case 1 => item.motionY = +speed
            case 2 => item.motionZ = -speed
            case 3 => item.motionZ = +speed
            case 4 => item.motionX = -speed
            case 5 => item.motionX = +speed
            case _ =>
        item.setPickupDelay(10)
        item.lifespan = 1600
        item

class NetworkPayload(payloadID:Int) extends AbstractPipePayload(payloadID)
:
    // Extended Data
    // 0000 NNNN PPPP PPPP SSSS SSSS 0EOO OIII
    // I = input
    // O = output
    // E = isEntering
    // S = speed
    // P = progress
    // N = priority index ******
    def priorityIndex = (data>>24)&0xF
    def priorityIndex_=(i:Int): Unit ={ data = (data& ~0xF000000)|(i&0xF)<<24 }

    override def preItemRemove(): Unit =
        resetTrip()

    var destinationIP = -1
    var destinationUUID:UUID = null
    var hasArrived = false

    def netPriority = Priorities(priorityIndex)

    def setDestination(ip:Int, p:NetworkPriority) =
        destinationIP = ip
        priorityIndex = p.ordinal
        val router = RouterServices.getRouter(ip)
        if router != null then destinationUUID = router.getID
        else destinationIP = -1
        this

    def resetTrip(): Unit =
        if destinationIP > -1 then
            val r = RouterServices.getRouter(destinationIP)
            if r != null then //r.getParent.itemLost(payload)
                r.getContainer.postNetworkEvent(PayloadLostEnrouteEvent(payload.key, payload.stackSize))
        destinationIP = -1
        destinationUUID = null
        hasArrived = false
        priorityIndex = Priorities.WANDERING.ordinal

    def refreshIP(): Unit =
        val router = RouterServices.getRouter(destinationIP)
        if router == null || router.getID != destinationUUID then destinationIP = RouterServices.getIPforUUID(destinationUUID)

class PayloadMovement[T <: AbstractPipePayload]
:
    import scala.jdk.CollectionConverters.*
    var delegate = HashSet[T]()
    var inputQueue = HashSet[T]()
    var outputQueue = HashSet[T]()
    private var delay = 0

    private implicit def payloadToT(p:AbstractPipePayload):T = p.asInstanceOf[T]

    def get(id:Int):T = delegate.find(_.payloadID == id).getOrElse(null.asInstanceOf[T])

    def getOrElseUpdate(id:Int, f:Unit => T):T =
        val payload = get(id)
        if payload == null then
            val newInput = f(())
            add(newInput)
            newInput
        else payload

    def scheduleLoad(item:T): Unit =
        delay = 10
        inputQueue += item

    def executeLoad(): Unit =
        delay -= 1
        if delay > 0 then return

        delegate ++= inputQueue
        inputQueue = HashSet[T]()

    def exececuteRemove(): Unit =
        delegate --= outputQueue
        outputQueue = HashSet[T]()

    def scheduleRemoval(item:T) =
        if outputQueue.contains(item) then false
        else
            outputQueue += item
            true

    def unscheduleRemoval(item:T) =
        if outputQueue.contains(item) then
            outputQueue -= item
            true
        else false

    def add(e:T): Unit =
        delegate += e

    def it = delegate.iterator
    def Jdel = delegate.asJavaCollection
