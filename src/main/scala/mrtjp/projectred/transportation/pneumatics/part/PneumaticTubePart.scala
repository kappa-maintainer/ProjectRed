package mrtjp.projectred.transportation.pneumatics.part

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.raytracer.CuboidRayTraceResult
import codechicken.lib.render.CCRenderState
import codechicken.lib.texture.TextureUtils
import codechicken.lib.vec.Vector3
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.ItemKey
import mrtjp.projectred.api.IConnectable
import mrtjp.projectred.core.{TCenterConnectable, TSwitchPacket}
import mrtjp.projectred.ProjectRedCore
import mrtjp.projectred.transportation.{PipeBoxes, PneumaticSounds, RenderPipe, SubcorePipePart}
import mrtjp.projectred.transportation.pneumatics.{ClientLink, ClientLinkCache, GraphContainer, GraphNode, PneumaticExitPathfinder, PneumaticGraph, PneumaticTransport, PneumaticTransportContainer, PneumaticTransportDevice, PneumaticTransportMode}
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{BlockRenderLayer, EnumFacing, ITickable}
import net.minecraft.util.math.BlockPos
import net.minecraft.inventory.{IInventory, ISidedInventory}
import codechicken.multipart.TMultiPart
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import net.minecraft.client.Minecraft
import mrtjp.core.fx.ParticleAction.*

import scala.jdk.CollectionConverters.*

class PneumaticTubePart extends SubcorePipePart with TCenterConnectable with TSwitchPacket with ITickable with PneumaticTransportContainer with GraphContainer
:
    private val transport = new PneumaticTransport(this)
    private val graphNode = new GraphNode(this)
    private val linkCache = new ClientLinkCache
    private var lastRoundRobinDir = -1
    private var linkCallbacksSetup = false

    override def getPneumaticTransport = transport
    override def getGraphNode = graphNode
    override def requiresActiveNode:Boolean =
        var connectedTubes = 0
        for side <- 0 until 6 if maskConnects(side) do
            getStraight(side) match
                case _:PneumaticTubePart =>
                    connectedTubes += 1
                case _ if hasEndpointOnSide(side) =>
                    return true
                case _ =>

        // Junctions are graph nodes; straight two-way tube segments are
        // redundant and are traversed by GraphLinkPathfinder.
        connectedTubes > 2

    private def hasEndpointOnSide(side:Int):Boolean =
        if !maskConnects(side) || getStraight(side).isInstanceOf[PneumaticTubePart] then false
        else
            world.getTileEntity(posOfStraight(side)) match
                case device:PneumaticTransportDevice => device.canConnectTube(side ^ 1)
                case inventory:ISidedInventory => inventory.getSlotsForFace(EnumFacing.VALUES(side ^ 1)).nonEmpty
                case _:IInventory => true
                case provider:ICapabilityProvider =>
                    provider.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.VALUES(side ^ 1))
                case _ => false

    override def onNodeChanged(linksChanged:Boolean, stateChanged:Boolean):Unit =
        ProjectRedCore.log.info("Pneumatic node changed pos={} server={} linksChanged={} stateChanged={} active={}",
            pos, !world.isRemote, linksChanged, stateChanged, graphNode.isActive)
        if !world.isRemote then
            if linksChanged then
                linkCache.setLinks(graphNode.getLinks)
                linkCache.setActive(graphNode.isActive)
                sendLinkUpdate()
            else if stateChanged then
                linkCache.setActive(graphNode.isActive)
                val out = getWriteStreamOf(13)
                linkCache.writeStateUpdate(out)
    override def getLinkWeight = 1

    override def canPropagate(dir:Int):Boolean = maskConnects(dir)

    override def getNodeTowards(dir:Int):GraphContainer =
        if !maskConnects(dir) then null
        else getStraight(dir) match
            case tube:PneumaticTubePart => tube
            case _ => null

    override def getPipeType = mrtjp.projectred.transportation.PipeDefs.PNEUMATICTUBE

    override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setByte("last_dir", lastRoundRobinDir.toByte)
        transport.save(tag)

    override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        lastRoundRobinDir = tag.getByte("last_dir")
        transport.load(tag)

    override def writeDesc(packet:MCDataOutput): Unit =
        super.writeDesc(packet)
        transport.writeDesc(packet)

    override def readDesc(packet:MCDataInput): Unit =
        super.readDesc(packet)
        transport.readDesc(packet)

    override def read(packet:MCDataInput, key:Int) = key match
        case 10 => transport.readPayloadUpdate(packet)
        case 11 => transport.readPayloadRemove(packet)
        case 12 =>
            ProjectRedCore.log.info("Pneumatic client received link packet pos={}", pos)
            if world.isRemote then linkCache.readLinkUpdate(packet)
        case 13 =>
            ProjectRedCore.log.info("Pneumatic client received state packet pos={}", pos)
            if world.isRemote then linkCache.readStateUpdate(packet)
        case _ => super.read(packet, key)

    override def update(): Unit =
        transport.tick()
        if !world.isRemote then graphNode.onTick()

    @SideOnly(Side.CLIENT)
    override def doStaticTessellation(pos:Vector3, ccrs:CCRenderState): Unit =
        RenderPipe.renderPipe(this, pos, ccrs)

    @SideOnly(Side.CLIENT)
    override def doDynamicTessellation(pos:Vector3, frame:Float, ccrs:CCRenderState): Unit =
        RenderPipe.renderPneumaticItemFlow(this, pos, frame, ccrs)

    override def getDrops = super.getDrops

    override def onRemoved(): Unit =
        if !world.isRemote then
            graphNode.onRemoved()
            PneumaticGraph.invalidate()
            for payload <- transport.getPayloads.toSeq do
                val stack = payload.getItemStack
                if !stack.isEmpty then
                    world.spawnEntity(new net.minecraft.entity.item.EntityItem(world, pos.getX + 0.5D, pos.getY + 0.5D, pos.getZ + 0.5D, stack))
        super.onRemoved()

    override def canConnectPart(part:IConnectable, side:Int) = part match
        case _:PneumaticTubePart => true
        case device:PneumaticTransportDevice => device.canConnectTube(side ^ 1)
        case _ => false

    override def discoverStraightOverride(side:Int):Boolean =
        val target = world.getTileEntity(posOfStraight(side))
        target match
            case _ if getStraight(side).isInstanceOf[PneumaticTubePart] => true
            case device:PneumaticTransportDevice => device.canConnectTube(side ^ 1)
            case inventory:ISidedInventory => inventory.getSlotsForFace(EnumFacing.VALUES(side ^ 1)).nonEmpty
            case _:IInventory => true
            case provider:ICapabilityProvider =>
                provider.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.VALUES(side ^ 1))
            case null => false

    override def setOutputDirection(payload:PneumaticTubePayload): Unit =
        if world.isRemote then return
        val input = payload.getInputSide
        val exits = (connMap & 0x3F) & ~(1 << (input ^ 1))
        if exits == 0 then
            payload.setOutputSide(input ^ 1)
            return

        val routes = new PneumaticExitPathfinder(this, graphNode.getRouteTable, payload, exits, Seq(PneumaticTransportMode.PASSIVE_NORMAL)).result()
        val routeMask = routes.exitDirMask
        val transitMask = exits & (0 until 6).foldLeft(0) { (mask, side) =>
            if (exits & (1 << side)) != 0 && getStraight(side).isInstanceOf[PneumaticTubePart] then mask | (1 << side) else mask
        }
        // Do not fall back to an endpoint that the pathfinder already rejected
        // (for example a full inventory). Continue through another tube, or
        // reverse toward the input if no transit path remains.
        if routeMask == 0 && transitMask == 0 then
            payload.setOutputSide(input ^ 1)
        else
            payload.setOutputSide(chooseRoundRobin(if routeMask != 0 then routeMask else transitMask))

    private def chooseRoundRobin(mask:Int):Int =
        var side = lastRoundRobinDir
        side = (side + 1) % 6
        while (mask & (1 << side)) == 0 do
            side = (side + 1) % 6
        lastRoundRobinDir = side
        side

    override def onPayloadAdded(id:Int, payload:PneumaticTubePayload):Unit =
        if !world.isRemote then
            val out = getWriteStreamOf(10)
            transport.writePayloadUpdate(out, id, payload)

    override def onPayloadChanged(id:Int, payload:PneumaticTubePayload): Unit =
        if !world.isRemote then
            val out = getWriteStreamOf(10)
            transport.writePayloadUpdate(out, id, payload)

    override def onPayloadReachedOutput(id:Int, payload:PneumaticTubePayload):Boolean =
        if world.isRemote then return false
        val side = payload.getOutputSide
        getStraight(side) match
            case next:PneumaticTubePart =>
                if next.insertTransitPayload(side, payload) then true
                else
                    bouncePayload(id, payload)
                    false
            case _ =>
                val target = world.getTileEntity(posOfStraight(side))
                target match
                    case device:PneumaticTransportDevice if device.insertPayload(side ^ 1, payload) => true
                    case _ =>
                        insertIntoInventory(side, payload) match
                            case true => true
                            case false =>
                                bouncePayload(id, payload)
                                false

    override def onPayloadRemoved(id:Int, payload:PneumaticTubePayload):Unit =
        if !world.isRemote then getWriteStreamOf(11).writeInt(id)

    override def canItemEnterTube(payload:PneumaticTubePayload, side:Int):Boolean =
        if !maskConnects(side ^ 1) then false
        else
            val exits = (connMap & 0x3F) & ~(1 << (side ^ 1))
            if exits == 0 then false
            else
                val routes = new PneumaticExitPathfinder(this, graphNode.getRouteTable, payload, exits, Seq(PneumaticTransportMode.PASSIVE_NORMAL)).result()
                routes.exitDirMask != 0

    override def canItemExitTube(payload:PneumaticTubePayload, side:Int, mode:PneumaticTransportMode):Boolean =
        canItemExitEndpoint(payload, side, mode) || getStraight(side).isInstanceOf[PneumaticTubePart]

    def canItemExitEndpoint(payload:PneumaticTubePayload, side:Int, mode:PneumaticTransportMode):Boolean =
        if !maskConnects(side) then false
        else getStraight(side) match
            case _:PneumaticTubePart => false
            case _ =>
                world.getTileEntity(posOfStraight(side)) match
                    case device:PneumaticTransportDevice => device.canAcceptPayload(side ^ 1, payload, mode)
                    case _ =>
                        val inventory = getInventoryTarget(side)
                        inventory != null && inventory.hasSpaceForItem(ItemKey.get(payload.getItemStack))

    private def getInventoryTarget(side:Int):InvWrapper =
        val pos = posOfStraight(side)
        val target = world.getTileEntity(pos)
        target match
            case inventory:ISidedInventory =>
                InvWrapper.wrapInternal(inventory).setSlotsFromSide(side ^ 1)
            case inventory:IInventory =>
                InvWrapper.wrapInternal(inventory).setSlotsAll()
            case provider:ICapabilityProvider =>
                InvWrapper.wrap(world, pos, EnumFacing.VALUES(side ^ 1))
            case null => null

    private def bouncePayload(id:Int, payload:PneumaticTubePayload):Unit =
        val oldInput = payload.getInputSide
        val oldOutput = payload.getOutputSide
        // Direction fields are stored relative to the tube connection. Reverse both
        // sides so the failed endpoint is excluded on the next midpoint search.
        payload.setInputSide(oldOutput ^ 1)
        payload.setOutputSide(oldInput ^ 1)
        payload.resetProgress()
        onPayloadChanged(id, payload)

    private def insertIntoInventory(side:Int, payload:PneumaticTubePayload):Boolean =
        val inventory = getInventoryTarget(side)
        if inventory == null then return false

        val stack = payload.getItemStack
        val inserted = inventory.injectItem(ItemKey.get(stack), stack.getCount)
        if inserted <= 0 then return false

        stack.shrink(inserted)
        if stack.isEmpty then true
        else
            payload.setItemStack(stack)
            false

    override def onWorldJoin():Unit =
        super.onWorldJoin()
        if world.isRemote then
            if !linkCallbacksSetup then
                linkCache.removedLinksCallback = Some(onLinksRemoved)
                linkCache.addedLinksCallback = Some(onLinksAdded)
                linkCallbacksSetup = true
        else
            graphNode.markLinksChanged()
            graphNode.onTick()
            sendCurrentLinkUpdate()

    private def sendCurrentLinkUpdate():Unit =
        linkCache.setLinks(graphNode.getLinks)
        linkCache.setActive(graphNode.isActive)
        val out = getWriteStreamOf(12)
        linkCache.writeLinkUpdate(out)

    override def onAdded():Unit =
        super.onAdded()
        if !world.isRemote then
            graphNode.onAdded()

    override def onPartChanged(part:TMultiPart):Unit =
        super.onPartChanged(part)
        if !world.isRemote then
            graphNode.markLinksChanged()

    override def onNeighborChanged():Unit =
        super.onNeighborChanged()
        if !world.isRemote then
            graphNode.markLinksChanged()

    private def insertTransitPayload(side:Int, payload:PneumaticTubePayload):Boolean =
        payload.resetProgress()
        payload.resetOutput()
        payload.setInputSide(side)
        payload.setSpeed(12)
        transport.addPayload(payload)
        true

    override def insertPayload(side:Int, payload:PneumaticTubePayload):Boolean =
        if !canItemEnterTube(payload, side) then false
        else
            insertTransitPayload(side, payload)

    private def sendLinkUpdate():Unit =
        sendCurrentLinkUpdate()

    @SideOnly(Side.CLIENT)
    private def onLinksAdded(added:Seq[ClientLink]):Unit =
        if added.nonEmpty then
            world.playSound(pos.getX + 0.5D, pos.getY + 0.5D, pos.getZ + 0.5D,
                PneumaticSounds.pressurize, net.minecraft.util.SoundCategory.BLOCKS,
                0.7F + world.rand.nextFloat * 0.3F, 1.0F, false)
        for lnk <- added do spawnLinkParticle(lnk, 10)

    @SideOnly(Side.CLIENT)
    private def onLinksRemoved(removed:Seq[ClientLink]):Unit =
        if removed.nonEmpty then
            world.playSound(pos.getX + 0.5D, pos.getY + 0.5D, pos.getZ + 0.5D,
                PneumaticSounds.depressurize, net.minecraft.util.SoundCategory.BLOCKS,
                0.7F + world.rand.nextFloat * 0.3F, 1.0F, false)
        for lnk <- removed do spawnLinkParticle(lnk, 5)

    @SideOnly(Side.CLIENT)
    private def spawnLinkParticle(link:ClientLink, duration:Int):Unit =
        ProjectRedCore.log.info("Pneumatic client spawning smoke pos={} segments={} duration={}",
            pos, link.segments.size, duration)
        val points = link.getPointListFor(pos)
        val particle = new PneumaticSmokeParticle(world, points)
        particle.setRBGColorF(0.7f, 0.7f, 0.7f)
        particle.setAlphaF(0.0f)
        particle.setMaxAge((duration + 6) * 20)
        particle.runAction(sequence(
            changeAlphaTo(0.7, 1),
            changeAlphaTo(0.0, duration),
            kill()
        ))
        Minecraft.getMinecraft.effectRenderer.addEffect(particle)
