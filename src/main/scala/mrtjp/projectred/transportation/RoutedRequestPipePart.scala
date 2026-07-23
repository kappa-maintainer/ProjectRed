package mrtjp.projectred.transportation

import codechicken.lib.packet.PacketCustom
import codechicken.lib.raytracer.CuboidRayTraceResult
import mrtjp.core.item.ItemKey
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand

class RoutedRequestPipePart extends AbstractNetPipe with TNetworkPipe
{
    override def centerReached(r:NetworkPayload): Unit =
    {
        if !maskConnects(r.output) && !world.isRemote then if itemFlow.scheduleRemoval(r) then
        {
            r.resetTrip()
            r.moveProgress(0.375F)
            r.speed = 0.075F
            val ent = r.getEntityForDrop(pos)
            ent.posY += 0.1F
            world.spawnEntity(ent)
        }
    }


    override def activate(player:EntityPlayer, hit:CuboidRayTraceResult, item:ItemStack, hand:EnumHand):Boolean =
    {
        if super.activate(player, hit, item, hand) then return true
        if !player.isSneaking then {
            openGui(player)
            true
        }
        else false
    }

    private def openGui(player:EntityPlayer): Unit =
    {
        if world.isRemote then return
        val packet = new PacketCustom(TransportationSPH.channel, TransportationSPH.gui_Request_open)
        packet.writePos(pos).sendToPlayer(player)
    }

    override def getDirForIncomingItem(r:NetworkPayload):Int =
    {
        val dir = inOutSide
        if dir == 6 then
        {
            val count = Integer.bitCount(connMap&0x3F)

            if count <= 1 then return r.input
            else if count == 2 then
                (0 until 6).find(i => i != (r.input^1) && (connMap&1<<i) != 0).getOrElse(dir)
            else dir
        }
        else dir
    }

    override def getActiveFreeSpace(item:ItemKey) =
    {
        if getInventory != null then super.getActiveFreeSpace(item)
        else Integer.MAX_VALUE
    }
}
