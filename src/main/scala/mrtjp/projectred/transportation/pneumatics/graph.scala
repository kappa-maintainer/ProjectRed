package mrtjp.projectred.transportation.pneumatics

import mrtjp.projectred.ProjectRedCore
import scala.collection.mutable

object PneumaticGraph:
    private var generation = 0L

    def currentGeneration:Long = generation

    def invalidate():Unit = generation += 1

trait GraphContainer:
    def getGraphNode:GraphNode
    def canPropagate(dir:Int):Boolean
    def getNodeTowards(dir:Int):GraphContainer
    def getLinkWeight:Int = 1
    def requiresActiveNode:Boolean
    def onNodeChanged(linksChanged:Boolean, stateChanged:Boolean):Unit

case class GraphLinkSegment(dir:Int, length:Int)

case class GraphLink(from:GraphContainer, to:GraphContainer, weight:Int, segments:Vector[GraphLinkSegment]):
    def direction:Int = segments.headOption.map(_.dir).getOrElse(-1)

case class GraphRouteEdge(from:GraphNode, to:GraphNode, dir:Int, weight:Int)

case class GraphRoute(start:GraphNode, end:GraphNode, weight:Int, edges:Vector[GraphRouteEdge]):
    def direction:Int = edges.headOption.map(_.dir).getOrElse(-1)

class GraphRouteTable(
    private val byDestination:Map[GraphNode, Vector[GraphRoute]],
    private val byDirection:Map[Int, Vector[GraphRoute]],
    val routes:Vector[GraphRoute]
):
    def getPathsTo(destination:GraphNode):Vector[GraphRoute] = byDestination.getOrElse(destination, Vector.empty)

    def routeIteratorInDirection(direction:Int):Iterator[GraphRoute] =
        byDirection.getOrElse(direction, Vector.empty).iterator

class GraphLinkPathfinder(startContainer:GraphContainer):
    private class SearchNode(
        val container:GraphContainer,
        val inputDir:Int,
        val initialDir:Int,
        val weight:Int,
        val path:Vector[Int]
    ):
        override def equals(other:Any):Boolean = other match
            case that:SearchNode => container == that.container && inputDir == that.inputDir
            case _ => false
        override def hashCode:Int = 31 * System.identityHashCode(container) + inputDir

    private val open = mutable.Queue.empty[SearchNode]
    private val openSet = mutable.HashSet.empty[SearchNode]
    private val closedSet = mutable.HashSet.empty[SearchNode]
    private val resultLinks = mutable.ArrayBuffer.empty[GraphLink]

    for dir <- 0 until 6 do
        if startContainer.canPropagate(dir) then
            val next = startContainer.getNodeTowards(dir)
            if next != null then
                val node = new SearchNode(next, dir ^ 1, dir, startContainer.getLinkWeight, Vector(dir))
                open.enqueue(node)
                openSet += node

    def result():Vector[GraphLink] =
        while open.nonEmpty do
            val prev = open.dequeue()
            openSet -= prev
            if !closedSet.contains(prev) then
                if prev.container.requiresActiveNode then
                    resultLinks += GraphLink(startContainer, prev.container, prev.weight, compressSegments(prev.path))
                else
                    for dir <- 0 until 6 do
                        if dir != prev.inputDir && prev.container.canPropagate(dir) then
                            val next = prev.container.getNodeTowards(dir)
                            if next != null then
                                val node = new SearchNode(next, dir ^ 1, prev.initialDir,
                                    prev.weight + prev.container.getLinkWeight, prev.path :+ dir)
                                if !openSet.contains(node) && !closedSet.contains(node) then
                                    open.enqueue(node)
                                    openSet += node
                closedSet += prev
        resultLinks.toVector

    private def compressSegments(path:Vector[Int]):Vector[GraphLinkSegment] =
        if path.isEmpty then Vector.empty
        else
            val segments = mutable.ArrayBuffer.empty[GraphLinkSegment]
            var dir = path.head
            var length = 1
            for next <- path.tail do
                if next == dir then length += 1
                else
                    segments += GraphLinkSegment(dir, length)
                    dir = next
                    length = 1
            segments += GraphLinkSegment(dir, length)
            segments.toVector

class GraphNode(val container:GraphContainer):
    private var links = Vector.empty[GraphLink]
    private var linksGeneration = -1L
    private var linksDirty = true
    private var routeTable:GraphRouteTable = null
    private var routeGeneration = -1L
    private var active = false

    def isActive:Boolean = active

    def getLinks:Vector[GraphLink] =
        if linksDirty || linksGeneration != PneumaticGraph.currentGeneration then
            val oldLinks = links
            val oldActive = active
            active = container.requiresActiveNode
            val next = if active then new GraphLinkPathfinder(container).result() else Vector.empty
            links = next
            linksGeneration = PneumaticGraph.currentGeneration
            linksDirty = false
            val linksChanged = oldLinks != next
            val stateChanged = oldActive != active
            ProjectRedCore.log.info("Pneumatic graph rebuild container={} active={} oldLinks={} newLinks={} linksChanged={} stateChanged={}",
                System.identityHashCode(container), active, oldLinks.size, next.size, linksChanged, stateChanged)
            if linksChanged || stateChanged then container.onNodeChanged(linksChanged, stateChanged)
        links

    def getRouteTable:GraphRouteTable =
        if routeTable == null || routeGeneration != PneumaticGraph.currentGeneration then
            routeTable = new GraphRoutePathfinder(this).result()
            routeGeneration = PneumaticGraph.currentGeneration
        routeTable

    def getLinksIfPresent:Option[Vector[GraphLink]] =
        if linksDirty || linksGeneration != PneumaticGraph.currentGeneration then None else Some(links)

    def markLinksChanged():Unit =
        if !linksDirty then
            ProjectRedCore.log.info("Pneumatic graph marked dirty container={}", System.identityHashCode(container))
        linksDirty = true
        routeTable = null
        PneumaticGraph.invalidate()

    def markRouteTableChanged():Unit =
        routeTable = null
        routeGeneration = -1L

    def onTick():Unit = getLinks

    def onAdded():Unit = markLinksChanged()

    def onRemoved():Unit = markLinksChanged()

class GraphRoutePathfinder(start:GraphNode):
    private val queue = new java.util.PriorityQueue[GraphRoute](new java.util.Comparator[GraphRoute]:
        override def compare(a:GraphRoute, b:GraphRoute):Int =
            val weightCompare = Integer.compare(a.weight, b.weight)
            if weightCompare != 0 then weightCompare else Integer.compare(a.direction, b.direction)
    )
    private val best = mutable.HashMap.empty[(GraphNode, Int), Int]
    private val byDestination = mutable.HashMap.empty[GraphNode, mutable.ArrayBuffer[GraphRoute]]
    private val byDirection = mutable.HashMap.empty[Int, mutable.ArrayBuffer[GraphRoute]]
    private val allRoutes = mutable.ArrayBuffer.empty[GraphRoute]

    for link <- start.getLinks do
        val end = link.to.getGraphNode
        val edge = GraphRouteEdge(start, end, link.direction, link.weight)
        val route = GraphRoute(start, end, link.weight, Vector(edge))
        queue.add(route)

    def result():GraphRouteTable =
        while !queue.isEmpty do
            val route = queue.poll()
            val key = (route.end, route.direction)
            if route.end != start && (!best.contains(key) || route.weight < best(key)) then
                best(key) = route.weight
                allRoutes += route
                byDestination.getOrElseUpdate(route.end, mutable.ArrayBuffer.empty) += route
                byDirection.getOrElseUpdate(route.direction, mutable.ArrayBuffer.empty) += route

                for link <- route.end.getLinks do
                    val next = link.to.getGraphNode
                    val alreadyVisited = route.edges.exists(edge => edge.from == next || edge.to == next)
                    if !alreadyVisited then
                        val edge = GraphRouteEdge(route.end, next, link.direction, link.weight)
                        queue.add(GraphRoute(route.start, next, route.weight + link.weight, route.edges :+ edge))

        val destinations = byDestination.view.map { case (node, paths) => node -> paths.toVector.sortBy(_.weight) }.toMap
        val directions = byDirection.view.map { case (dir, paths) => dir -> paths.toVector.sortBy(_.weight) }.toMap
        new GraphRouteTable(destinations, directions, allRoutes.toVector.sortBy(_.weight))
