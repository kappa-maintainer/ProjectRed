/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.expansion

import java.util.concurrent.Callable
import java.util.{List as JList}

import codechicken.lib.colour.EnumColour
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.model.bakery.SimpleBlockRenderer
import codechicken.lib.vec.uv.{MultiIconTransformation, UVTransformation}
import codechicken.lib.vec.{Cuboid6, Vector3}
import mrtjp.core.fx.ParticleAction.*
import mrtjp.core.fx.particles.{BeamPulse2, SpriteParticle}
import mrtjp.projectred.ProjectRedExpansion
import mrtjp.projectred.expansion.CapabilityTeleposedEnderPearl.teleposedEnderPearlCapability
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.{TextureAtlasSprite, TextureMap}
import net.minecraft.entity.Entity
import net.minecraft.entity.item.{EntityEnderPearl, EntityItem}
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTBase, NBTTagCompound}
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability.IStorage
import net.minecraftforge.common.capabilities.*
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.jdk.CollectionConverters.*

class TileTeleposer extends TileMachine with TPoweredMachine
{
    var isCharged = false
    var storage = 0

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setInteger("storage", storage)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        storage = tag.getInteger("storage")
        isCharged = storage >= getTransportDraw
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(isCharged)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        isCharged = in.readBoolean()
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 2 => //doTransformFX()
        case 3 =>
            isCharged = in.readBoolean()
            markRender()
        case _ => super.read(in, key)
    }

    def sendTransformFX(): Unit =
    {
        writeStream(2).sendToChunk(this)
    }

    def sendICUpdate(): Unit =
    {
        writeStream(3).writeBoolean(storage >= getTransportDraw).sendToChunk(this)
    }

    override def getBlock = ProjectRedExpansion.machine2

    override def doesRotate = false

    def getMaxStorage = 16000
    def getDrawSpeed = 800
    def getDrawCeil = 600
    def getTransportDraw = 8000

    override def updateServer(): Unit =
    {
        super.updateServer()

        if storage >= getTransportDraw then
        {
            updateHeldItems()
            updateOrbits()
            if world.getTotalWorldTime%20 == 0 then
            {
                tryInfusePearlItem()
                tryTransportEnderProjectile()
            }
        }

        if cond.charge > getDrawCeil && storage < getMaxStorage then
        {
            var n = math.min(cond.charge-getDrawCeil, getDrawSpeed)/10
            n = math.min(n, getMaxStorage-storage)
            cond.drawPower(n*1000)
            storage += n
        }

        if world.getTotalWorldTime%10 == 0 then updateRendersIfNeeded()
    }

    override def updateClient(): Unit =
    {
        super.updateClient()

        if isCharged then {
            updateOrbits()
            updateHeldItems()
            doPearlBeamFX()
            doRandomSparklies()
        }
    }

    def updateRendersIfNeeded(): Unit =
    {
        val ic2 = storage >= getTransportDraw
        if isCharged != ic2 then sendICUpdate()
        isCharged = ic2
    }

    def updateOrbits(): Unit =
    {
        for e <- getProjectilesToOrbit do
            makeEntityOrbit(e)
    }

    def updateHeldItems(): Unit =
    {
        for e <- getAllItemEntities do
            makeEntityHeld(e)
    }

    def tryInfusePearlItem(): Unit =
    {
        val ei = getProminentHeldItem
        if ei != null && ei.getItem.getItem == Items.ENDER_PEARL && ei.getItem.getCount == 1 then
        {
            ei.setDead()
            val stack = new ItemStack(ProjectRedExpansion.itemInfusedEnderPearl)
            ItemInfusedEnderPearl.setLocation(stack, x, y, z)

            val ent = new EntityItem(world, ei.posX, ei.posY, ei.posZ, stack)
            ent.setPickupDelay(20)
            //ent.age = ei.age //TODO
            ent.hoverStart = ei.hoverStart

            world.spawnEntity(ent)
            sendTransformFX()
        }
    }

    def tryTransportEnderProjectile(): Unit =
    {
        if storage < getTransportDraw then return

        def dest = getDestination
        if dest != null && (dest != getPos) then
        {
            val te = world.getTileEntity(dest) match {
                case tile:TileTeleposer => tile
                case _ => null
            }
            if te != null && te.storage >= te.getTransportDraw then
            {
                val thatDest = te.getDestination
                if thatDest != null && thatDest.getX == x && thatDest.getY == y && thatDest.getZ == z then
                {
                    val ep = getProminentEnderProjectile
                    if ep != null then
                    {
                        ep.setDead()
                        if ep.getThrower != null then
                        {
                            val newEP = new EntityEnderPearl(world, ep.getThrower)
                            newEP.motionX = 0
                            newEP.motionY = 0.1
                            newEP.motionZ = 0
                            newEP.posX = dest.getX
                            newEP.posY = dest.getY+1
                            newEP.posZ = dest.getZ
                            CapabilityTeleposedEnderPearl.setTeleposed(newEP)
                            world.spawnEntity(newEP)
                            te.getProminentHeldItem.setPickupDelay(80)
                            storage -= getTransportDraw
                            te.storage -= te.getTransportDraw
                        }
                    }
                }
            }
        }
    }

    def getDestination:BlockPos =
    {
        val ei = getProminentHeldItem
        if ei != null && ei.getItem.getItem.isInstanceOf[ItemInfusedEnderPearl] &&
            ItemInfusedEnderPearl.hasLocation(ei.getItem) then
            ItemInfusedEnderPearl.getLocation(ei.getItem)
        else null
    }

    def getProminentHeldItem =
    {
        val all = getAllItemEntities
        all.headOption match
        {
            case Some(ie) if all.size == 1 => ie
            case _ => null
        }
    }

    def getProminentEnderProjectile =
    {
        val box = Cuboid6.full.copy.add(new Vector3(x, y+1, z)).aabb

        world.getEntitiesWithinAABB(classOf[EntityEnderPearl], box)
                .asInstanceOf[JList[EntityEnderPearl]]
                .asScala
                .filterNot(CapabilityTeleposedEnderPearl.isTeleposed)
                .headOption match
        {
            case Some(ep) => ep
            case _ => null
        }
    }

    def getProjectilesToOrbit =
    {
        val box = new Cuboid6(-3, 0, -3, 4, 4, 4).add(new Vector3(x, y, z)).aabb
        world.getEntitiesWithinAABB(classOf[EntityEnderPearl], box)
                .asInstanceOf[JList[EntityEnderPearl]]
                .asScala
                .filterNot(CapabilityTeleposedEnderPearl.isTeleposed)
    }

    def getAllItemEntities =
    {
        Cuboid6.full = new Cuboid6(0, 0, 0, 1, 1, 1)
        val box = Cuboid6.full.copy.add(new Vector3(x, y+1, z)).aabb
        world.getEntitiesWithinAABB(classOf[EntityItem], box)
                .asInstanceOf[JList[EntityItem]].asScala.filter{ ei =>
                    val s = ei.getItem
                    !s.isEmpty && (s.getItem == Items.ENDER_PEARL ||
                            s.getItem == ProjectRedExpansion.itemInfusedEnderPearl)
                }
    }

    def makeEntityOrbit(e:EntityEnderPearl): Unit =
    {
        val target = Vector3.center.copy.add(x, y+1, z)
        val rpos = new Vector3(e.posX, e.posY, e.posZ).subtract(target)
        val targetDistance = math.max(0.35, math.sqrt(rpos.x*rpos.x+rpos.z*rpos.z)*0.97)
        val targetHeight = rpos.y*0.97
        val orbitSpeed = 0.3
        var orbitAngle = math.atan2(rpos.z, rpos.x)+orbitSpeed
        if orbitAngle > math.Pi*2 then orbitAngle -= math.Pi*2
        else if orbitAngle < 0 then orbitAngle += math.Pi*2

        if e.posY > target.y then e.posY -= 0.05
        else if e.posY < target.y then e.posY += 0.05

        e.motionX = 0
        e.motionY = 0
        e.motionZ = 0
        e.setPosition(
            target.x+math.cos(orbitAngle)*targetDistance,
            target.y+targetHeight,
            target.z+math.sin(orbitAngle)*targetDistance
        )
    }

    def makeEntityHeld(e:EntityItem): Unit =
    {
        val target = Vector3.center.copy.add(x, y+0.6, z)
        val rpos = new Vector3(e.posX, e.posY, e.posZ).subtract(target)
        val targetPos = rpos.multiply(0.80).add(target)

        e.motionX = 0
        e.motionY = 0
        e.motionZ = 0

        val maxAge = e.getItem.getItem.getEntityLifespan(e.getItem, e.world)
        e.lifespan = e.getAge + maxAge

        e.setPosition(targetPos.x, targetPos.y, targetPos.z)
    }

    private var beams:AnyRef = null

    @SideOnly(Side.CLIENT)
    def doPearlBeamFX(): Unit =
    {
        if beams == null then beams = Array[BeamPulse2]()
        var beams2 = beams.asInstanceOf[Array[BeamPulse2]]

        val elist = getProjectilesToOrbit
        while beams2.length < elist.size do beams2 :+= null
        beams = beams2

        val source = Vector3.center.copy.add(x, y+1, z)

        for i <- elist.indices do
        {
            var beam = beams2(i)
            if beam == null || !beam.isAlive then
            {
                beam = new BeamPulse2(world)
                beam.setMaxAge(20)
                beam.alpha = 0.3
                beam.setRGB(0.5, 0.5, 0.5)
                beams2(i) = beam
                Minecraft.getMinecraft.effectRenderer.addEffect(beam)
            }
            val ent = elist(i)

            beam.setAge(beam.getAge%beam.getMaxAge)
            beam.setPos(source)
            beam.setTarget(ent.posX, ent.posY, ent.posZ)
            if source.copy.subtract(new Vector3(ent.posX, ent.posY, ent.posZ)).mag() < 0.75 then
                beam.doPulse(EnumColour.GREEN.rF, EnumColour.GREEN.gF, EnumColour.GREEN.bF)
        }

    }

    @SideOnly(Side.CLIENT)
    def doRandomSparklies(): Unit =
    {
        if world.getTotalWorldTime%15 == 0 && world.rand.nextDouble() > 0.20 then
        {
            val p = new SpriteParticle(world)
            p.setPos(new Vector3(x+0.5, y+1.0, z+0.5).add(new Vector3(1, 1, 1).multiply(world.rand.nextDouble())))
            p.isImmortal = true
            p.texture = "projectred:textures/particles/flutter1.png"
            p.rgb = new Vector3(EnumColour.PINK.rF, EnumColour.PINK.gF, EnumColour.PINK.bF)
            p.scale = new Vector3(0, 0, 0)
            p.alpha = 0

            import mrtjp.core.fx.ParticleAction.*
            val a = group(
                repeatForever(orbitAround(x+0.5, z+0.5, 0.30, 1)),
                sequence(
                    group(
                        changeAlphaTo(1, 5),
                        scaleTo(0.075, 0.075, 0.075, 20)
                    ),
                    group(
                        moveTo(x+0.5, y+1, z+0.5, 80),
                        sequence(
                            delay(45),
                            group(
                                changeAlphaTo(0, 20),
                                scaleTo(0, 0, 0, 5)
                            )
                        )
                    ),
                    kill()
                )
            )
            p.runAction(a)

            Minecraft.getMinecraft.effectRenderer.addEffect(p)
        }
    }

    @SideOnly(Side.CLIENT)
    def doTransformFX(): Unit =
    {
        for i <- 0 until 16 do
        {
            val start = new Vector3(x, y+1, z).add(Vector3.center)
            val p = new SpriteParticle(world)
            Minecraft.getMinecraft.effectRenderer.addEffect(p)
            p.setPos(start)
            p.isImmortal = true
            p.texture = "projectred:textures/particles/bubble.png"
            p.rgb = new Vector3(EnumColour.MAGENTA.rF, EnumColour.MAGENTA.gF, EnumColour.MAGENTA.bF)
            p.scale = new Vector3(0, 0, 0)
            val s = world.rand.nextDouble()*0.1
            val a1 = sequence(
                group(
                    scaleTo(0.025+s, 0.025+s, 0.025+s, 5),
                    moveTo(start.x+world.rand.nextDouble()-0.5, start.y+world.rand.nextDouble()-0.5, start.z+world.rand.nextDouble()-0.5, 10)
                ),
                sequence(
                    changeTexture("projectred:textures/particles/bubble_pop.png"),
                    scaleTo(0, 0, 0, 3)
                ),
                kill()
            )
            p.runAction(a1)
        }
    }
}

trait ITeleposedItem
{
    var isTeleposed: Boolean = false
}

class TeleposedProperty extends ITeleposedItem with ICapabilityProvider with ICapabilitySerializable[NBTTagCompound]
{
    isTeleposed = false

    override def hasCapability(capability:Capability[?], facing:EnumFacing) = capability == teleposedEnderPearlCapability

    override def getCapability[T](capability:Capability[T], facing:EnumFacing) = {
        if capability == teleposedEnderPearlCapability then this.asInstanceOf[T]
        else null.asInstanceOf[T]
    }

    override def serializeNBT() = {
        val tag = new NBTTagCompound
        tag.setBoolean("isTeleposed", isTeleposed)
        tag
    }

    override def deserializeNBT(nbt:NBTTagCompound): Unit =
    {
        isTeleposed = nbt.getBoolean("isTeleposed")
    }
}

object CapabilityTeleposedEnderPearl
{
    val teleposedEnderPearlCapabilityID = new ResourceLocation("projectred:teleposedEnderPearl")

    @CapabilityInject(classOf[ITeleposedItem])
    var teleposedEnderPearlCapability:Capability[ITeleposedItem] = scala.compiletime.uninitialized

    def registerCapability(): Unit =
    {
        //Register the capability with the manager
        CapabilityManager.INSTANCE.register(classOf[ITeleposedItem], new IStorage[ITeleposedItem] {
            override def writeNBT(capability:Capability[ITeleposedItem], instance:ITeleposedItem, side:EnumFacing) = null
            override def readNBT(capability:Capability[ITeleposedItem], instance:ITeleposedItem, side:EnumFacing, nbt:NBTBase): Unit ={}
        }, new Callable[ITeleposedItem] {
            override def call() = null
        })

        //Subscribe to attachment events
        MinecraftForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    def onAttachCapability(event:AttachCapabilitiesEvent[Entity]): Unit =
    {
        event.getObject match {
            case ei:EntityEnderPearl =>
                event.addCapability(teleposedEnderPearlCapabilityID, new TeleposedProperty)
            case _ =>
        }
    }


    def setTeleposed(e:EntityEnderPearl): Unit =
    {
        e.getCapability(teleposedEnderPearlCapability, null) match {
            case t:ITeleposedItem => t.isTeleposed = true
            case _ =>
        }
    }

    def isTeleposed(e:EntityEnderPearl) =
    {
        e.getCapability(teleposedEnderPearlCapability, null) match {
            case t:ITeleposedItem => t.isTeleposed
            case _ => false
        }
    }
}

object RenderTeleposer extends SimpleBlockRenderer
{
    import java.lang.{Boolean as JBool}

    import mrtjp.projectred.expansion.BlockProperties.*
    import org.apache.commons.lang3.tuple.Triple

    var bottom:TextureAtlasSprite = scala.compiletime.uninitialized
    var top1:TextureAtlasSprite = scala.compiletime.uninitialized
    var top2:TextureAtlasSprite = scala.compiletime.uninitialized
    var side1:TextureAtlasSprite = scala.compiletime.uninitialized
    var side2:TextureAtlasSprite = scala.compiletime.uninitialized

    var iconT1:UVTransformation = scala.compiletime.uninitialized
    var iconT2:UVTransformation = scala.compiletime.uninitialized

    override def handleState(state: IExtendedBlockState, world: IBlockAccess, pos: BlockPos): IExtendedBlockState = {

        world.getTileEntity(pos) match {
            case t:TileTeleposer => state.withProperty(UNLISTED_CHARGED_PROPERTY, t.isCharged.asInstanceOf[JBool])
            case _ => state
        }
    }

    override def getWorldTransforms(state: IExtendedBlockState) = {
        val isCharged = state.getValue(UNLISTED_CHARGED_PROPERTY)
        Triple.of(0, 0, if isCharged then iconT2 else iconT1)
    }

    override def getItemTransforms(stack: ItemStack) = Triple.of(0, 0, iconT1)
    override def shouldCull() = true

    override def registerIcons(map:TextureMap): Unit =
    {
        bottom = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/teleposer/bottom"))
        top1 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/teleposer/top1"))
        top2 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/teleposer/top2"))
        side1 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/teleposer/side1"))
        side2 = map.registerSprite(new ResourceLocation("projectred:blocks/mechanical/teleposer/side2"))

        iconT1 = new MultiIconTransformation(bottom, top1, side1, side1, side1, side1)
        iconT2 = new MultiIconTransformation(bottom, top2, side2, side2, side2, side2)
    }
}