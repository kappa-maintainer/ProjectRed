package mrtjp.projectred.transportation.pneumatics

import mrtjp.projectred.transportation.pneumatics.part.{PneumaticTubePart, PneumaticTubePayload}

case class PneumaticExits(exitDirMask:Int, weight:Int, mode:Option[PneumaticTransportMode])

class PneumaticExitPathfinder(
    startContainer:PneumaticTubePart,
    routeTable:GraphRouteTable,
    payload:PneumaticTubePayload,
    dirMask:Int,
    searchModes:Seq[PneumaticTransportMode] = PneumaticTransportMode.values.toSeq
):
    private var searched = false
    private var resultValue = PneumaticExits(0, Int.MaxValue, None)

    def result():PneumaticExits =
        if !searched then
            resultValue = search()
            searched = true
        resultValue

    private def search():PneumaticExits =
        searchModes.iterator.map { mode => mode -> searchMode(mode) }
            .find { case (_, found) => found.exitDirMask != 0 }
            .map { case (mode, found) => PneumaticExits(found.exitDirMask, found.weight, Some(mode)) }
            .getOrElse(PneumaticExits(0, Int.MaxValue, None))

    private def searchMode(mode:PneumaticTransportMode):PneumaticExits =
        var exitMask = 0
        var exitWeight = Int.MaxValue

        for side <- 0 until 6 if (dirMask & (1 << side)) != 0 do
            if startContainer.canItemExitEndpoint(payload, side, mode) then
                exitMask |= 1 << side
                exitWeight = 0

        if exitMask != 0 then return PneumaticExits(exitMask, exitWeight, Some(mode))

        for side <- 0 until 6 if (dirMask & (1 << side)) != 0 do
            val routes = routeTable.routeIteratorInDirection(side)
            while routes.hasNext do
                val route = routes.next()
                if route.weight > exitWeight then
                    // Routes in a direction are weight ordered.
                    while routes.hasNext do routes.next()
                else
                    route.end.container match
                        case tube:PneumaticTubePart =>
                            var destinationExit = false
                            for endSide <- 0 until 6 if !destinationExit do
                                if tube.canItemExitEndpoint(payload, endSide, mode) then destinationExit = true
                            if destinationExit then
                                if route.weight < exitWeight then
                                    exitWeight = route.weight
                                    exitMask = 0
                                exitMask |= 1 << side
                        case _ =>

        PneumaticExits(exitMask, exitWeight, Some(mode))
