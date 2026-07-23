package mrtjp.projectred.transportation

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.CuboidRayTraceResult
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.Vector3
import codechicken.microblock.handler.MicroblockProxy
import codechicken.microblock.{BlockMicroMaterial, ItemMicroPart}
import codechicken.multipart.{IMaskedRedstonePart, RedstoneInteractions, TMultiPart}
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.world.Messenger
import mrtjp.projectred.ProjectRedCore
import mrtjp.projectred.api.{IConnectable, IScrewdriver}
import mrtjp.projectred.core.IWirePart.*
import mrtjp.projectred.core.*
import net.minecraft.block.SoundType
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.*
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*

trait TRedstonePipe extends SubcorePipePart with TCenterRSAcquisitions with TCenterRSPropagation with IRedwirePart with IMaskedRedstonePart
{
    var signal:Byte = 0
    var hasRedstone = false

    abstract override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setBoolean("mat", hasRedstone)
        tag.setByte("signal", signal)
    }

    abstract override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        hasRedstone = tag.getBoolean("mat")
        signal = tag.getByte("signal")
    }

    abstract override def writeDesc(packet:MCDataOutput): Unit =
    {
        super.writeDesc(packet)
        packet.writeBoolean(hasRedstone)
        packet.writeByte(signal)
    }

    abstract override def readDesc(packet:MCDataInput): Unit =
    {
        super.readDesc(packet)
        hasRedstone = packet.readBoolean()
        signal = packet.readByte()
    }

    abstract override def read(packet:MCDataInput, key:Int) = key match
    {
        case 2 =>
            hasRedstone = packet.readBoolean()
            tile.markRender()
        case 3 =>
            signal = packet.readByte
            tile.markRender()
        case _ => super.read(packet, key)
    }

    def sendMatUpdate(): Unit =
    {
        if !world.isRemote then
        {
            if updateInward() then onMaskChanged()
            WirePropagator.propagateTo(this, FORCE)
        }
        getWriteStreamOf(2).writeBoolean(hasRedstone)
    }

    override def onSignalUpdate(): Unit =
    {
        tile.markDirty()
        getWriteStreamOf(3).writeByte(signal)
    }

    override def onPartChanged(part:TMultiPart): Unit =
    {
        if !world.isRemote then
        {
            WirePropagator.logCalculation()

            if updateOutward() then
            {
                onMaskChanged()
                WirePropagator.propagateTo(this, FORCE)
            }
            else WirePropagator.propagateTo(this, RISING)
        }
    }

    override def onNeighborChanged(): Unit =
    {
        if !world.isRemote then
        {
            WirePropagator.logCalculation()
            if updateExternalConns() then
            {
                onMaskChanged()
                WirePropagator.propagateTo(this, FORCE)
            }
            else WirePropagator.propagateTo(this, RISING)
        }
    }

    override def onAdded(): Unit =
    {
        super.onAdded()
        if !world.isRemote then
        {
            if updateInward() then onMaskChanged()
            WirePropagator.propagateTo(this, RISING)
        }
    }

    override def getDrops = if hasRedstone then
        (super.getDrops.asScala ++ Iterable.single(getMaterialStack)).asJava else super.getDrops

    def getMaterialStack =
        ItemMicroPart.create(769, BlockMicroMaterial.materialKey(Blocks.REDSTONE_BLOCK.getDefaultState))

    override def diminishOnSide(side:Int) = true

    override def strongPowerLevel(side:Int) = 0

    override def weakPowerLevel(side:Int) =
    {
        if !maskConnects(side) || !hasRedstone then 0
        else rsLevel
    }

    override def canConnectRedstone(side:Int) = hasRedstone

    override def getConnectionMask(side:Int) = 0x10

    abstract override def canConnectPart(part:IConnectable, s:Int) = part match
    {
        case rw:(IRedwirePart & IMaskedRedstonePart)
            if hasRedstone && (rw.getConnectionMask(s^1)&0x10) != 0 => true
        case _ => super.canConnectPart(part, s)
    }

    override def discoverStraightOverride(absDir:Int) =
    {
        if hasRedstone then
        {
            WirePropagator.setRedwiresConnectable(false)
            val b = (RedstoneInteractions.otherConnectionMask(world, pos, absDir, false)&
                RedstoneInteractions.connectionMask(this, absDir)) != 0
            WirePropagator.setRedwiresConnectable(true)
            b
        }
        else false
    }

    def rsLevel =
    {
        if WirePropagator.redwiresProvidePower then ((signal&0xFF)+16)/17
        else 0
    }

    override def getRedwireSignal(side:Int) = getSignal

    override def getSignal = signal&0xFF
    override def setSignal(sig:Int): Unit ={ signal = sig.toByte }

    override def propagateOther(mode:Int): Unit =
    {
        for s <- 0 until 6 do if !maskConnects(s) then
            WirePropagator.addNeighborChange(posOfStraight(s))
    }

    override def calculateSignal:Int =
    {
        if !hasRedstone then return 0
        WirePropagator.setDustProvidePower(false)
        WirePropagator.redwiresProvidePower = false
        var s = 0
        def raise(sig:Int): Unit = {if sig > s then s = sig}

        for s <- 0 until 6 do if maskConnectsOut(s) then
            raise(calcStraightSignal(s))

        WirePropagator.setDustProvidePower(true)
        WirePropagator.redwiresProvidePower = true
        s
    }

    override def calcStraightSignal(s:Int) = getStraight(s) match
    {
        case p:TMultiPart => resolveSignal(p, s^1)
        case null => calcStrongSignal(s)
    }

    override def resolveSignal(part:Any, s:Int) = part match
    {
        case rw:IRedwirePart if rw.diminishOnSide(s) => rw.getRedwireSignal(s)-1
        case re:IRedwireEmitter => re.getRedwireSignal(s)
        case _ => 0
    }

    abstract override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, item:ItemStack, hand:EnumHand):Boolean =
    {
        if super.activate(player, hit, item, hand) then return true

        //if (CommandDebug.WIRE_READING) debug(player) else
        if !item.isEmpty && item.getItem == ProjectRedCore.itemMultimeter then
        {
            item.damageItem(1, player)
            test(player)
            return true
        }

        if item.isEmpty && player.isSneaking && hasRedstone then
        {
            if !world.isRemote then
            {
                if hasRedstone && !player.capabilities.isCreativeMode then
                    PRLib.dropTowardsPlayer(world, pos, getMaterialStack, player)
                hasRedstone = false
                sendMatUpdate()
            }
            return true
        }

        if !item.isEmpty && !hasRedstone && item.getItem == MicroblockProxy.itemMicro && item.getItemDamage == 769 then
        {
            ItemMicroPart.getMaterial(item) match
            {
                case bm:BlockMicroMaterial if bm.state.getBlock == Blocks.REDSTONE_BLOCK =>
                    if !world.isRemote then
                    {
                        hasRedstone = true
                        world.playSound(null, pos, SoundType.GLASS.getPlaceSound, SoundCategory.BLOCKS, SoundType.GLASS.getVolume, SoundType.GLASS.getPitch)
                        sendMatUpdate()
                        if !player.capabilities.isCreativeMode then item.shrink(1)
                    }
                    return true
                case _ =>
            }
        }

        false
    }

    def debug(player:EntityPlayer) =
    {
        player.sendMessage(new TextComponentString(
            (if world.isRemote then "Client" else "Server")+" signal strength: "+getSignal))
        true
    }

    def test(player:EntityPlayer) =
    {
        if world.isRemote then Messenger.addMessage(pos.getX, pos.getY+.5f, pos.getZ, "/#f/#c[c] = "+getSignal)
        else
        {
            val packet = Messenger.createPacket
            packet.writeDouble(pos.getX+0.0D)
            packet.writeDouble(pos.getY+0.5D)
            packet.writeDouble(pos.getZ+0.0D)
            packet.writeString("/#c[s] = "+getSignal)
            packet.sendToPlayer(player)
        }
        true
    }

    @SideOnly(Side.CLIENT)
    override def doStaticTessellation(pos:Vector3, ccrs:CCRenderState): Unit =
    {
        super.doStaticTessellation(pos, ccrs)
        if hasRedstone then RenderPipe.renderRSWiring(this, pos, signal, ccrs)
    }
}

trait TColourFilterPipe extends SubcorePipePart
{
    var colour:Byte = -1

    abstract override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setByte("colour", colour)
    }

    abstract override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        colour = if tag.hasKey("colour") then tag.getByte("colour") else -1
    }

    abstract override def writeDesc(packet:MCDataOutput): Unit =
    {
        super.writeDesc(packet)
        packet.writeByte(colour)
    }

    abstract override def readDesc(packet:MCDataInput): Unit =
    {
        super.readDesc(packet)
        colour = packet.readByte()
    }

    abstract override def read(packet:MCDataInput, key:Int) = key match
    {
        case 12 =>
            colour = packet.readByte()
            tile.markRender()
        case _ => super.read(packet, key)
    }

    def sendColourUpdate(): Unit =
    {
        getWriteStreamOf(12).writeByte(colour)
    }

    abstract override def getDrops =
        if colour > -1 then (super.getDrops.asScala ++ Iterable.single(getColourStack)).asJava
        else super.getDrops

    def getColourStack =
        if colour == -1 then ItemStack.EMPTY
        else ItemMicroPart.create(769, BlockMicroMaterial.materialKey(Blocks.WOOL.getStateFromMeta(colour)))

    abstract override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, item:ItemStack, hand:EnumHand):Boolean =
    {
        if super.activate(player, hit, item, hand) then return true

        def dropMaterial(): Unit =
        {
            if colour > -1 && !player.capabilities.isCreativeMode then
                PRLib.dropTowardsPlayer(world, pos, getColourStack, player)
        }

        if item.isEmpty && player.isSneaking && colour > -1 then
        {
            if !world.isRemote then
            {
                dropMaterial()
                colour = -1
                sendColourUpdate()
            }
            return true
        }

        if !item.isEmpty && item.getItem == MicroblockProxy.itemMicro && item.getItemDamage == 769 then
        {
            ItemMicroPart.getMaterial(item) match
            {
                case bm:BlockMicroMaterial if bm.state.getBlock == Blocks.WOOL && bm.state.getBlock.getMetaFromState(bm.state) != colour =>
                    if !world.isRemote then {
                        dropMaterial()
                        colour = bm.state.getBlock.getMetaFromState(bm.state).toByte
                        world.playSound(null, pos, bm.getSound.getPlaceSound, SoundCategory.BLOCKS, bm.getSound.getVolume, bm.getSound.getPitch)
                        sendColourUpdate()
                        if !player.capabilities.isCreativeMode then item.shrink(1)
                    }
                    return true
                case _ =>
            }
        }

        false
    }

    @SideOnly(Side.CLIENT)
    override def doStaticTessellation(pos:Vector3, ccrs:CCRenderState): Unit =
    {
        super.doStaticTessellation(pos, ccrs)
        if colour > -1 then RenderPipe.renderColourWool(this, pos, colour, ccrs)
    }
}

trait IInventoryProvider
{
    def getInventory(extractSide:Int):InvWrapper
    def getInventory:InvWrapper
    def getInterfacedSide:Int
}

trait TInventoryPipe[T <: AbstractPipePayload] extends PayloadPipePart[T] with IInventoryProvider
{
    var inOutSide:Byte = 0

    abstract override def read(packet:MCDataInput, key:Int) = key match
    {
        case 6 =>
            inOutSide = packet.readByte
            tile.markRender()
        case _ => super.read(packet, key)
    }

    def sendOrientUpdate(): Unit =
    {
        getWriteStreamOf(6).writeByte(inOutSide)
    }

    abstract override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setByte("io", inOutSide)
    }

    abstract override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        inOutSide = tag.getByte("io")
    }

    abstract override def writeDesc(packet:MCDataOutput): Unit =
    {
        super.writeDesc(packet)
        packet.writeByte(inOutSide)
    }

    abstract override def readDesc(packet:MCDataInput): Unit =
    {
        super.readDesc(packet)
        inOutSide = packet.readByte
    }

    abstract override def onNeighborChanged(): Unit =
    {
        super.onNeighborChanged()
        shiftOrientation(false)
    }

    abstract override def onPartChanged(p:TMultiPart): Unit =
    {
        super.onPartChanged(p)
        shiftOrientation(false)
    }

    abstract override def onAdded(): Unit =
    {
        super.onAdded()
        shiftOrientation(false)
    }

    abstract override def discoverStraightOverride(s:Int):Boolean =
    {
        InvWrapper.wrap(world, posOfStraight(s), EnumFacing.byIndex(s^1)) match {
            case null => false
            case _ => true
        }
    }

    def shiftOrientation(force:Boolean): Unit =
    {
        if world.isRemote then return
        val invalid = force || inOutSide == 6 || !maskConnects(inOutSide) || getInventory == null
        if !invalid then return
        var found = false
        val oldSide = inOutSide

        import scala.util.control.Breaks.*
        breakable {
            if inOutSide > 5 then inOutSide = 5 //if invalid, start at side 0

            for i <- 0 until 6 do {
                inOutSide = ((inOutSide+1)%6).toByte
                if maskConnects(inOutSide) then {
                    if getInventory != null then {
                        found = true
                        break()
                    }
                }
            }
        }

        if !found then inOutSide = 6
        if oldSide != inOutSide then sendOrientUpdate()
    }

    override def getInventory(extractSide:Int) =
    {
        if (0 until 6 contains inOutSide) && (0 until 6 contains extractSide) then
            InvWrapper.wrap(world, posOfStraight(inOutSide), EnumFacing.VALUES(extractSide))
        else null
    }

    override def getInventory:InvWrapper =
    {
        getInventory(getInterfacedSide)
    }

    override def getInterfacedSide = if !(0 to 5 contains inOutSide) then -1 else inOutSide^1

    abstract override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, item:ItemStack, hand:EnumHand):Boolean =
    {
        if super.activate(player, hit, item, hand) then return true

        if !item.isEmpty && item.getItem.isInstanceOf[IScrewdriver] && item.getItem.asInstanceOf[IScrewdriver].canUse(player, item) then
        {
            if !world.isRemote then
            {
                shiftOrientation(true)
                item.getItem.asInstanceOf[IScrewdriver].damageScrewdriver(player, item)
            }
            return true
        }

        false
    }
}
