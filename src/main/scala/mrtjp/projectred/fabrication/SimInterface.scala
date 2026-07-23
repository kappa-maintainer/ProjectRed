package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import mrtjp.core.vec.Point
import mrtjp.projectred.fabrication.SEIntegratedCircuit.*
import net.minecraft.nbt.NBTTagCompound

import scala.collection.mutable.{ListBuffer, Map as MMap}

trait IICSimEngineContainerDelegate
{
    def registersDidChange(registers:Set[Int]): Unit 

    def ioRegistersDidChange(): Unit 

    def logDidChange(): Unit 
}

class ICSimEngineContainer extends ISEICDelegate
{
    import SEIntegratedCircuit.*

    var simEngine:SEIntegratedCircuit = null

    val logger = new SEStatLogger

    var systemTime = 0L

    /**
      * Mapped inputs and outputs of this IC.
      * Outputs go to the world, inputs come in from the world.
      * OOOO OOOO OOOO OOOO IIII IIII IIII IIII
      */
    val iostate = Array(0, 0, 0, 0)

    var delegate:IICSimEngineContainerDelegate = null

    def setInput(r:Int, state:Int): Unit =
    {
        iostate(r) = iostate(r)&0xFFFF0000|state&0xFFFF
    }

    def onInputChanged(mask:Int): Unit =
    {
        pushInputRegisters(mask)
    }

    def advanceTime(ticks:Long): Unit =
    {
        systemTime += ticks
        pushSystemTime()
    }

    def setOutput(r:Int, state:Int): Unit =
    {
        iostate(r) = iostate(r)&0xFFFF|(state&0xFFFF)<<16
    }

    def repropagate(): Unit =
    {
        simEngine.propagate(this)
    }

    private def pushInputRegisters(mask:Int): Unit =
    {
        for r <- 0 until 4 do if (mask&1<<r) != 0 then {
            val input = iostate(r)&0xFFFF
            for i <- 0 until 16 do
                simEngine.queueRegVal[Byte](REG_IN(r, i), if (input&1<<i) != 0 then 1 else 0)
        }
    }

    private def pushSystemTime(): Unit =
    {
        simEngine.queueRegVal(REG_SYSTIME, systemTime)
    }

    private def pullOutputRegisters(mask:Int): Unit = //TODO perhaps remove mask and just pull everything??
    {
        for r <- 0 until 4 do if (mask&1<<r) != 0 then {
            var output = 0
            for i <- 0 until 16 do if simEngine.getRegVal(REG_OUT(r, i)) != 0 then
                output |= 1<<i
            setOutput(r, output)
        }
    }

    override def registersDidChange(registers:Set[Int]): Unit =
    {
        if delegate != null then
            delegate.registersDidChange(registers)

        val firstIOReg = REG_IN(0, 0)
        val lastIOReg = REG_OUT(3, 15)
        if registers.exists {reg => reg >= firstIOReg && reg <= lastIOReg} then { //TODO potentially faster to pull and check
            pullOutputRegisters(0xF)
            if delegate != null then
                delegate.ioRegistersDidChange()
        }
    }

    override def icDidThrowErrorFlag(flag:Int, registers:Seq[Int], gates:Seq[Int]): Unit =
    {
        logger.logRuntimeFlag(flag, registers, gates)
        if delegate != null then delegate.logDidChange()
    }

    def recompileSimulation(map:ISETileMap): Unit =
    {
        logger.clear()

        //TODO temporary io check, non-issue once side io modes are stored map-level instead of tile-level
        val ioParts = map.tiles.collect {
            case (pos, io:IIOGateTile) => (pos, io)
        }
        for s <- 0 until 4 do {
            val sio = ioParts.filter(_._2.getIOSide == s)
            if sio.size > 1 then {
                val m = sio.head._2.getIOMode
                val c = sio.head._2.getConnMode
                val p = sio.keys.map{p => Point(p._1, p._2)}.toSeq

                if sio.exists(_._2.getIOMode != m) then
                    logger.logError(p, "io direction conflict")
                if sio.exists(_._2.getConnMode != c) then
                    logger.logError(p, "io connection type conflict")
            }
        }

        simEngine = ISELinker.linkFromMap(map, logger)
        systemTime = 0
        pushInputRegisters(0xF)
        pushSystemTime()
        pullOutputRegisters(0xF)

        if delegate != null then delegate.logDidChange()
    }

    def resetSimState(map:ISETileMap): Unit =
    {
        for i <- 0 until 4 do iostate(i) = 0
        systemTime = 0
        recompileSimulation(map)
    }

    def saveSimState(tag:NBTTagCompound): Unit =
    {
        tag.setBoolean("null_sim", simEngine == null)
        if simEngine == null then return

        tag.setIntArray("io_state", iostate)
        tag.setLong("sys_time", systemTime)

        val registers = simEngine.getRegisterMap
        for i <- 0 until registers.length do {
            registers(i) match {
                case StandardRegister(r:Long) => tag.setLong(s"reg[$i]", r)
                case StandardRegister(r:Int)  => tag.setInteger(s"reg[$i]", r)
                case StandardRegister(r:Byte) => tag.setByte(s"reg[$i]", r)
                case _ => //Dont save the register
            }
        }
    }

    def loadSimState(tag:NBTTagCompound): Unit =
    {
        if tag.getBoolean("null_sim") then return

        val io = tag.getIntArray("io_state")
        if io.length == 4 then for i <- 0 until 4 do iostate(i) = io(i)
        systemTime = tag.getLong("sys_time")

        val registers = simEngine.getRegisterMap
        for i <- 0 until registers.length do {
            val reg = registers(i)
            reg match {
                case StandardRegister(r:Long) => reg.queueVal[Long](tag.getLong(s"reg[$i]"))
                case StandardRegister(r:Int)  => reg.queueVal[Int](tag.getInteger(s"reg[$i]"))
                case StandardRegister(r:Byte) => reg.queueVal[Byte](tag.getByte(s"reg[$i]"))
                case _ => //Dont load the register
            }
            reg.pushVal(simEngine)
        }
    }
}

class SEStatLogger extends ISEStatLogger
{
    private val log = ListBuffer[String]()

    private val warnings = ListBuffer[(Seq[Point], String)]()
    private val errors = ListBuffer[(Seq[Point], String)]()

    //Flags, RegPoints, GatePoints
    private val runtimeFlags = ListBuffer[(Int, Seq[Point], Seq[Point])]()

    private val regIDToPoints = MMap[Int, Set[Point]]()
    private val gateIDToPoints = MMap[Int, Set[Point]]()

    override def clear(): Unit =
    {
        log.clear()
        warnings.clear()
        errors.clear()
        runtimeFlags.clear()
    }

    override def logInfo(message:String): Unit =
    {
        log += message
    }

    override def logWarning(points:Seq[Point], message:String): Unit =
    {
        warnings += points -> message
    }

    override def logError(points:Seq[Point], message:String): Unit =
    {
        errors += points -> message
    }

    override def logRuntimeFlag(flag:Int, registers:Seq[Int], gates:Seq[Int]): Unit =
    {
        runtimeFlags += ((flag, registers.flatMap {regIDToPoints.getOrElse(_, Set.empty)},
                gates.flatMap {gateIDToPoints.getOrElse(_, Set.empty)}))
    }

    override def logRegAlloc(id:Int, points:Set[Point]): Unit =
    {
        regIDToPoints += id -> points
    }

    override def logGateAlloc(id:Int, points:Set[Point]): Unit =
    {
        gateIDToPoints += id -> points
    }

    def getWarnings:Seq[(Seq[Point], String)] = warnings.toSeq
    def getWarningsForPoint(p:Point):Seq[(Seq[Point], String)] = warnings.filter(_._1 contains p).toSeq

    def getErrors:Seq[(Seq[Point], String)] = errors.toSeq
    def getErrorsForPoint(p:Point):Seq[(Seq[Point], String)] = errors.filter(_._1 contains p).toSeq

    def getRuntimeFlags:Seq[(Seq[Point], String)] = runtimeFlags.map {p => (p._2 ++ p._3, runtimeFlagToMessage(p._1))}.toSeq
    def getRuntimeFlagsForPoint(p:Point):Seq[(Seq[Point], String)] = getRuntimeFlags.filter(_._1 contains p)

    private def runtimeFlagToMessage(flag:Int):String = flag match {
        case COMPUTE_OVERFLOW => "COMPUTE OVERFLOW!"
    }

    def writeLog(out:MCDataOutput): Unit =
    {
        def writeList(list:ListBuffer[(Seq[Point], String)]): Unit = {
            val s = list.size
            out.writeShort(s)
            for i <- 0 until s do {
                val (points, desc) = list(i)
                val s2 = points.size
                out.writeShort(s2)
                for p <- points do
                    out.writeByte(p.x).writeByte(p.y)
                out.writeString(desc)
            }
        }

        writeList(warnings)
        writeList(errors)

        out.writeShort(runtimeFlags.size)
        for (i, rPoints, gPoints) <- runtimeFlags do {
            out.writeByte(i)

            out.writeByte(rPoints.size)
            for rp <- rPoints do
                out.writeByte(rp.x).writeByte(rp.y)

            out.writeByte(gPoints.size)
            for gp <- gPoints do
                out.writeByte(gp.x).writeByte(gp.y)
        }
    }

    def readLog(in:MCDataInput): Unit =
    {
        def readList(list:ListBuffer[(Seq[Point], String)]): Unit =
        {
            list.clear()
            for _ <- 0 until in.readUShort() do {
                val points = Seq.newBuilder[Point]
                for _ <- 0 until in.readUShort() do
                    points += Point(in.readUByte(), in.readUByte())

                list += points.result() -> in.readString()
            }
        }

        readList(warnings)
        readList(errors)

        runtimeFlags.clear()
        for _ <- 0 until in.readUShort() do {
            val flag = in.readUByte()
            val rPoints = (0 until in.readUByte()) map {_ => Point(in.readUByte(), in.readUByte())}
            val gPoints = (0 until in.readUByte()) map {_ => Point(in.readUByte(), in.readUByte())}

            runtimeFlags += ((flag, rPoints, gPoints))
        }
    }
}