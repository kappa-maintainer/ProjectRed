package mrtjp.projectred.transportation

import codechicken.multipart.TileMultipart
import mrtjp.core.inventory.InvWrapper
import mrtjp.core.item.ItemKey
import net.minecraft.inventory.IInventory
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.items.CapabilityItemHandler

import scala.annotation.tailrec
import scala.collection.immutable.Queue

object PressurePriority
{
    val backlog = 1<<0
    val inventory = 1<<1
}

object PressurePathFinder
{
    var pipe:TPressureSubsystem = null
    var item:ItemKey = null
    var searchDirs = 0
    var colour = -1

    var invDirs = 0
    var backlogDirs = 0

    private var shortestDist = Integer.MAX_VALUE
    private var shortestBDist = Integer.MAX_VALUE

    def clear(): Unit =
    {
        pipe = null
        item = null
        searchDirs = 0
        colour = -1
        invDirs = 0
        backlogDirs = 0
        shortestDist = Integer.MAX_VALUE
        shortestBDist = Integer.MAX_VALUE
    }

    def start(): Unit =
    {
        val q = Queue.newBuilder[Node]
        for s <- 0 until 6 if (searchDirs&1<<s) != 0 && pipe.maskConnects(s) do q += Node(pipe.pos, s)
        iterate(q.result(), Set(Node(pipe.pos)))
    }

    @tailrec
    private def iterate(open:Seq[Node], closed:Set[Node] = Set.empty):Unit = open match
    {
        case Seq() =>
        case Seq(next, rest@_*) => getTile(next.pos) match
        {
            case dev:TPressureDevice =>
                if dev.canAcceptInput(item, next.dir^1) then setInvPath(next)
                else if dev.canAcceptBacklog(item, next.dir^1) then setBacklog(next)
                iterate(rest, closed+next)

            case inv:IInventory =>
                if InvWrapper.wrapInternal(inv).setSlotsFromSide(next.dir^1).hasSpaceForItem(item) then setInvPath(next)
                iterate(rest, closed+next)

            case tmp:TileMultipart => tmp.partMap(6) match
            {
                case p:TPressureTube =>
                    val upNext = Vector.newBuilder[Node]
                    for s <- 0 until 6 do if s != (next.dir^1) && p.maskConnects(s) then
                    {
                        val route = next --> (s, p.getPathWeight, p.pathFilter(next.dir^1, s))
                        if route.flagRouteTo && route.allowColor(colour) && route.allowItem(item) then
                            if !closed(route) && !open.contains(route) then upNext += route
                    }
                    iterate(rest++upNext.result(), closed+next)

                case _ => iterate(rest, closed+next)
            }
            //This will always be hit as all tiles are cap providers.
            case tmp:ICapabilityProvider =>
                if tmp.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.VALUES(next.dir^1)) then
                    if InvWrapper.wrap(pipe.world, next.pos, EnumFacing.VALUES(next.dir^1)).hasSpaceForItem(item) then setInvPath(next)
                iterate(rest, closed+next)
            //Theoretically this will never be hit anymore, as all tiles are cap providers.
            case null => iterate(rest, closed+next)
        }
        case _ =>
    }

    private def setInvPath(n:Node): Unit =
    {
        if n.dist < shortestDist then
        {
            shortestDist = n.dist
            invDirs = 0
        }
        if n.dist == shortestDist then invDirs |= 1<<n.hop
    }

    private def setBacklog(n:Node): Unit =
    {
        if n.dist < shortestBDist then
        {
            shortestBDist = n.dist
            backlogDirs = 0
        }
        if n.dist == shortestBDist then backlogDirs |= 1<<n.hop
    }

    private def getTile(pos:BlockPos) = pipe.world.getTileEntity(pos)

    private object Node
    {
        def apply(pos:BlockPos):Node = new Node(pos, 0, 6, 6)
        def apply(pos:BlockPos, dir:Int):Node = new Node(pos.offset(EnumFacing.values()(dir)), 1, dir, dir)
    }
    private class Node(val pos:BlockPos, val dist:Int, val dir:Int, val hop:Int, filters:Set[PathFilter] = Set.empty) extends Path(filters) with Ordered[Node]
    {
        def -->(toDir:Int, distAway:Int, filter:PathFilter):Node = new Node(pos.offset(EnumFacing.values()(toDir)), dist+distAway, toDir, hop, filters+filter)
        def -->(toDir:Int, distAway:Int):Node = new Node(pos.offset(EnumFacing.values()(toDir)), dist+distAway, toDir, hop, filters)
        def -->(toDir:Int):Node = this -->(toDir, 1)

        override def compare(that:Node) = dist-that.dist

        override def equals(other:Any) = other match
        {
            case that:Node =>
                pos == that.pos && dir == that.dir
            case _ => false
        }

        override def hashCode = pos.hashCode

        override def toString = "@"+pos.toString+": delta("+dir+") hop("+hop+")"
    }
}
