package mrtjp.projectred.transmission

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.vec.Rotation
import codechicken.multipart.TMultiPart
import mrtjp.core.world.Messenger
import mrtjp.projectred.api.{IBundledEmitter, IBundledTile, IConnectable, IMaskedBundledTile}
import mrtjp.projectred.core.IWirePart.*
import mrtjp.projectred.core.{IInsulatedRedwirePart, IWirePart, WirePropagator}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.TextComponentString

trait IBundledCablePart extends IWirePart with IBundledEmitter
:
    def getBundledSignal:Array[Byte]

    def calculateSignal:Array[Byte]

    def setSignal(newSignal:Array[Byte]): Unit 

    def getBundledColour:Int

trait TBundledCableCommons extends TWireCommons with TBundledAquisitionsCommons with IBundledCablePart
:
    var signal = new Array[Byte](16) //server-side only
    var colour:Byte = 0

    def getWireType = WireDef.values(WireDef.BUNDLED_N.meta+colour+1)

    override def preparePlacement(side:Int, meta:Int): Unit =
        super.preparePlacement(side, meta)
        colour = (meta-WireDef.BUNDLED_0.meta).toByte

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setByteArray("signal", signal)
        tag.setByte("colour", colour)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        signal = tag.getByteArray("signal")
        colour = tag.getByte("colour")

    override def writeDesc(packet:MCDataOutput): Unit =
        super.writeDesc(packet)
        packet.writeByte(colour)

    override def readDesc(packet:MCDataInput): Unit =
        super.readDesc(packet)
        colour = packet.readByte()

    override def canConnectPart(part:IConnectable, dir:Int) = part match
        case b:IBundledCablePart => b.getBundledColour == -1 || colour == -1 || b.getBundledColour == colour
        case ins:IInsulatedRedwirePart => true
        case be:IBundledEmitter => true
        case _ => false

    protected var propagatingMask = 0xFFFF
    override def updateAndPropagate(from:TMultiPart, mode:Int): Unit =
        import mrtjp.projectred.transmission.BundledCommons.*
        val mask = getUpdateMask(from, mode)
        if mode == DROPPING && isSignalZero(getBundledSignal, mask) then return

        val newSignal = calculateSignal
        applyChangeMask(getBundledSignal, newSignal, mask)

        propagatingMask = mask

        if dropSignalsLessThan(getBundledSignal, newSignal) then
            if !isSignalZero(newSignal, mask) then WirePropagator.propagateAnalogDrop(this)
            propagate(from, DROPPING)
        else if !signalsEqual(getBundledSignal, newSignal) then
            setSignal(newSignal)
            if mode == DROPPING then propagate(null, RISING)
            else propagate(from, RISING)
        else if mode == DROPPING then propagateTo(from, RISING)
        else if mode == FORCE then propagate(from, FORCED)

        propagatingMask = 0xFFFF

    private def getUpdateMask(from:TMultiPart, mode:Int) = from match
        case ins:IInsulatedRedwirePart => 1<<ins.getInsulatedColour
        case b:IBundledCablePart if mode == DROPPING =>
            var m = 0
            val osignal = b.getBundledSignal
            for i <- 0 until 16 do if osignal(i) == 0 then m |= 1<<i
            m
        case b:IBundledCablePart if mode == RISING =>
            var m = 0
            val osignal = b.getBundledSignal
            for i <- 0 until 16 do if (osignal(i)&0xFF) > (getBundledSignal.apply(i)&0xFF) then m |= 1<<i
            m
        case _ => 0xFFFF

    override def resolveArray(part:Any, r:Int) =
        part match
            case b:IBundledCablePart =>
                val osig = b.getBundledSignal
                for i <- 0 until 16 do if (osig(i)&0xFF)-1 > (tmpSignal(i)&0xFF) then
                    tmpSignal(i) = (osig(i)-1).toByte
            case i:IInsulatedRedwirePart =>
                val s = i.getRedwireSignal(r)-1
                if s > (tmpSignal(i.getInsulatedColour)&0xFF) then
                    tmpSignal(i.getInsulatedColour) = s.toByte
            case b:IBundledEmitter => BundledCommons.raiseSignal(tmpSignal, b.getBundledSignal(r))
            case t:TileEntity => BundledCommons.raiseSignal(tmpSignal,
                APIImpl_Transmission.getBundledSignal(t.getWorld, t.getPos, EnumFacing.values()(r)))
            case _ =>
        tmpSignal

    protected val tmpSignal = new Array[Byte](16)
    protected def tmpSignalClear(): Unit =
        for i <- 0 until 16 do tmpSignal(i) = 0.toByte

    override def propagateTo(part:TMultiPart, mode:Int) =
        def shouldPropogate(part:TMultiPart, mode:Int) = part match
            case ins:IInsulatedRedwirePart => (propagatingMask&1<<ins.getInsulatedColour) != 0
            case _ => true

        if shouldPropogate(part, mode) then super.propagateTo(part, mode)
        else true

    override def setSignal(newSignal:Array[Byte]): Unit =
        if newSignal == null then signal.mapInPlace(_ => 0.toByte)
        else for i <- 0 until 16 do signal(i) = newSignal(i)

    override def getBundledSignal = signal

    override def getBundledSignal(dir:Int) = if maskConnects(dir) then getBundledSignal else null

    override def getBundledColour = colour

    override def debug(player:EntityPlayer):Boolean =
        val sb = new StringBuilder
        for i <- 0 until 16 do
            val s = Integer.toHexString(signal(i)&0xFF).toUpperCase
            if s.length == 1 then sb.append('0')
            sb.append(s)
        player.sendMessage(new TextComponentString(sb.toString()))
        true

    override def test(player:EntityPlayer) =
        if !world.isRemote then
            var s = ""
            for i <- 0 until 16 do if getBundledSignal.apply(i) != 0 then s = s+"["+i+"]"

            if s == "" then s = "off"
            val packet = Messenger.createPacket
            //TODO we have writeVector in 1.12.
            packet.writeDouble(pos.getX + 0.0D)
            packet.writeDouble(pos.getY + 0.5D)
            packet.writeDouble(pos.getZ + 0.0D)
            packet.writeString("/#f"+s)
            packet.sendToPlayer(player)
        true

    override def useStaticRenderer = true

class BundledCablePart extends WirePart with TFaceBundledAquisitions with TBundledCableCommons
:
    override def calculateSignal =
        tmpSignalClear()
        for r <- 0 until 4 do if maskConnects(r) then
            if maskConnectsCorner(r) then calcCornerArray(r)
            else
                if maskConnectsStraight(r) then calcStraightArray(r)
                calcInternalArray(r)
        if maskConnectsCenter then calcCenterArray
        tmpSignal

    override def calcStraightArray(r:Int) =
        world.getTileEntity(posOfStraight(r)) match
            case ibe:IBundledEmitter => resolveArray(ibe, absoluteDir(rotFromStraight(r)))
            case t:TileEntity if APIImpl_Transmission.isValidInteractionFor(world, t.getPos, EnumFacing.values()(rotFromStraight(r))) =>
                resolveArray(t, absoluteDir(rotFromStraight(r)))
            case _ => super.calcStraightArray(r)

    override def discoverStraightOverride(absDir:Int) =
        val pos = this.pos.offset(EnumFacing.values()(absDir))
        world.getTileEntity(pos) match
            case b:IMaskedBundledTile => b.canConnectBundled(absDir^1) &&
                    (b.getConnectionMask(absDir^1)&1<<Rotation.rotationTo(absDir, side)) != 0
            case b:IBundledTile => b.canConnectBundled(absDir^1)
            case _ => APIImpl_Transmission.canConnectBundled(world, pos, EnumFacing.values()(absDir^1))

class FramedBundledCablePart extends FramedWirePart with TCenterBundledAquisitions with TBundledCableCommons
:
    override def calculateSignal =
        tmpSignalClear()
        for s <- 0 until 6 do if maskConnects(s) then
            if maskConnectsOut(s) then calcStraightArray(s)
            else calcInternalArray(s)

        tmpSignal

    override def calcStraightArray(s:Int) =
        world.getTileEntity(posOfStraight(s)) match
            case ibe:IBundledEmitter => resolveArray(ibe, s^1)
            case t:TileEntity if APIImpl_Transmission.isValidInteractionFor(world, t.getPos, EnumFacing.values()(s^1)) =>
                resolveArray(t, s^1)
            case _ => super.calcStraightArray(s)

    override def discoverStraightOverride(absDir:Int) =
        val pos = this.pos.offset(EnumFacing.byIndex(absDir))
        world.getTileEntity(pos) match
            case b:IMaskedBundledTile => b.canConnectBundled(absDir^1) &&
                    (b.getConnectionMask(absDir^1)&0x10) != 0
            case b:IBundledTile => b.canConnectBundled(absDir^1)
            case _ => APIImpl_Transmission.canConnectBundled(world, pos, EnumFacing.values()(absDir^1))
