package mrtjp.projectred.transportation.pneumatics

import mrtjp.projectred.ProjectRedCore
import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.vec.Vector3
import net.minecraft.util.math.BlockPos

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class ClientLinkCache:
    private val links = ListBuffer.empty[ClientLink]
    private var isActive = false

    var removedLinksCallback:Option[Seq[ClientLink] => Unit] = None
    var addedLinksCallback:Option[Seq[ClientLink] => Unit] = None

    def setLinks(newLinks:Vector[GraphLink]):Unit =
        links.clear()
        for lnk <- newLinks do links += ClientLink(lnk.weight, lnk.segments)

    def writeDesc(out:MCDataOutput):Unit =
        out.writeVarInt(links.size)
        for lnk <- links do lnk.writeDesc(out)
        out.writeBoolean(isActive)

    def readDesc(in:MCDataInput):Unit =
        links.clear()
        val size = in.readVarInt()
        for _ <- 0 until size do links += ClientLink.readDesc(in)
        isActive = in.readBoolean()

    def writeStateUpdate(out:MCDataOutput):Unit = out.writeBoolean(isActive)

    def writeLinkUpdate(out:MCDataOutput):Unit = writeDesc(out)

    def readStateUpdate(in:MCDataInput):Unit =
        isActive = in.readBoolean()

    def readLinkUpdate(in:MCDataInput):Unit =
        val oldList = links.toVector
        readDesc(in)

        val removed = oldList.filterNot(links.contains)
        val added = links.filterNot(oldList.contains)

        ProjectRedCore.log.info("Pneumatic client link update old={} new={} added={} removed={}",
            oldList.size, links.size, added.size, removed.size)
        if removed.nonEmpty then removedLinksCallback.foreach(_.apply(removed.toSeq))
        if added.nonEmpty then addedLinksCallback.foreach(_.apply(added.toSeq))

    def setActive(active:Boolean):Unit = isActive = active

case class ClientLink(weight:Int, segments:Vector[GraphLinkSegment]):
    def writeDesc(out:MCDataOutput):Unit =
        out.writeVarInt(weight)
        out.writeVarInt(segments.size)
        for seg <- segments do
            out.writeByte(seg.dir)
            out.writeVarInt(seg.length)

    def getPointListFor(blockPos:BlockPos):Vector[Vector3] =
        val points = Vector.newBuilder[Vector3]
        points += new Vector3(blockPos.getX + 0.5, blockPos.getY + 0.5, blockPos.getZ + 0.5)

        for seg <- segments do
            val last = points.result().last
            val next = seg.dir match
                case 0 => last.copy.add(0, -seg.length, 0)
                case 1 => last.copy.add(0,  seg.length, 0)
                case 2 => last.copy.add(0, 0, -seg.length)
                case 3 => last.copy.add(0, 0,  seg.length)
                case 4 => last.copy.add(-seg.length, 0, 0)
                case 5 => last.copy.add( seg.length, 0, 0)
            points += next
        points.result()

object ClientLink:
    def readDesc(in:MCDataInput):ClientLink =
        val weight = in.readVarInt()
        val segSize = in.readVarInt()
        val segments = Vector.newBuilder[GraphLinkSegment]
        for _ <- 0 until segSize do
            val dir = in.readUByte()
            val length = in.readVarInt()
            segments += GraphLinkSegment(dir, length)
        ClientLink(weight, segments.result())
