package mrtjp.projectred.fabrication

import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Set as MSet}

class SEIntegratedCircuit(
    $registers:Seq[ISERegister], //Registers in the circuit, indexed by regID
    $gates:Seq[ISEGate], //Gates in the circuit, indexed by gateID
    $regDependents:Map[Int, Seq[Int]] //Register deps [regID -> Seq(gateID)]
)
{
    val registers:Array[ISERegister] = $registers.toArray
    val gates:Array[ISEGate] = $gates.toArray

    val regDependents:Array[Array[Int]] = {
        val b = mutable.ArrayBuilder.make[Array[Int]]
        for i <- 0 until registers.length do
            b += $regDependents.getOrElse(i, Seq.empty).toArray
        b.result()
    }

    val changeQueue:ListBuffer[Int] = new ListBuffer[Int]

    def getRegisterMap:Seq[ISERegister] = registers

    def computeAll():Boolean =
    {
        for i <- gates do
            i.compute(this)
        propagate(null)
        changeQueue.isEmpty
    }

    def getRegVal[T](regID:Int):T = registers(regID).getVal[T]

    def queueRegVal[T](regID:Int, newVal:T): Unit =
    {
        if registers(regID).queueVal[T](newVal) then
            changeQueue += regID
    }

    def propagate(callback:ISEICDelegate):Boolean =
    {
        val allChanges = Set.newBuilder[Int]
        var changes:List[Int] = List.empty

        val allComputes = Array.fill[Int](gates.length)(0)
        var computes = MSet[Int]()

        var hasOverflow = false
        var overflowGateID = -1

        def fetch(): Unit = {
            changes = changeQueue.result()
            changeQueue.clear()
            allChanges ++= changes
        }

        def checkOverflow(): Unit = {
            for i <- computes do {
                allComputes(i) += 1
                if allComputes(i) > 32 then {
                    hasOverflow = true
                    overflowGateID = i
                    return
                }
            }

            computes.clear()
        }

        fetch()

        while { {
            for regID <- changes do
                registers(regID).pushVal(this)

            for regID <- changes do {
                for gateID <- regDependents(regID) do {
                    if !computes(gateID) then {
                        gates(gateID).compute(this)
                        computes += gateID
                    }
                }
            }

            fetch()
            checkOverflow()
        }
        ; changes.nonEmpty && !hasOverflow} do ()

        if hasOverflow then
            if callback != null then callback.icDidThrowErrorFlag(
                SEIntegratedCircuit.COMPUTE_OVERFLOW, changes, Seq(overflowGateID))

        val ch = allChanges.result()
        if ch.nonEmpty then {
            if callback != null then callback.registersDidChange(ch)
            true
        } else
            false
    }

    override def toString = {
        val builder = new mutable.StringBuilder()
        builder.append("SEIntegratedCircuit: DUMP\n")
        builder.append("=== Registers ===\n")
        for i <- 0 until registers.length do {
            val reg = registers(i)
            builder.append(s"reg[$i] = ")
            reg match {
                case StandardRegister(r:Byte) => builder.append(s"$r {byte}")
                case StandardRegister(r:Long) => builder.append(s"$r {long}")
                case StandardRegister(r:Int)  => builder.append(s"$r {int}")
                case ConstantRegister(r:Byte) => builder.append(s"$r {byte}")
                case _ => builder.append(s"${reg.getVal} {unknown}")
            }
            builder.append("\n")
        }
        builder.result()
    }
}

object SEIntegratedCircuit
{
    //Reserved registers
    val REG_IN_BASE = 0
    def REG_IN(r:Int, o:Int) = REG_IN_BASE+r*16+o

    val REG_OUT_BASE = 64
    def REG_OUT(r:Int, o:Int) = REG_OUT_BASE+r*16+o

    val REG_SYSTIME = 128

    val REG_ZERO = 129
    val REG_ONE = 130

    //Flags
    val COMPUTE_OVERFLOW = 1
}

trait ISEICDelegate
{
    def registersDidChange(registers:Set[Int]): Unit 

    def icDidThrowErrorFlag(flag:Int, registers:Seq[Int], gates:Seq[Int]): Unit 
}

trait ISERegister
{
    def getVal[T]:T

    def queueVal[T](newVal:T):Boolean

    def pushVal(ic:SEIntegratedCircuit): Unit 
}

trait ISEGate
{
    def compute(ic:SEIntegratedCircuit): Unit 
}

case class StandardRegister[Type](var value:Type) extends ISERegister
{
    var queuedVal:Type = value

    override def getVal[T] = value.asInstanceOf[T]

    override def queueVal[T](newVal:T) =
    {
        queuedVal = newVal.asInstanceOf[Type]
        value != queuedVal
    }

    override def pushVal(ic:SEIntegratedCircuit): Unit =
    {
        value = queuedVal
    }
}

case class ConstantRegister[Type](c:Type) extends ISERegister
{
    override def getVal[T] = c.asInstanceOf[T]
    override def queueVal[T](newVal:T) = false
    override def pushVal(ic:SEIntegratedCircuit): Unit ={}
}