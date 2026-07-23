/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.Transformation
import mrtjp.core.util.Enum
import mrtjp.projectred.integration.GateDefinition.GateDef
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.collection.mutable.ListBuffer

abstract class GateICTile extends ICTile with TConnectableICTile with TICTileOrient with IGuiICTile with ISEGateTile
:
    private var gateSubID:Byte = 0
    private var gateShape:Byte = 0

    def getLogic[T]:T
    def getLogicPrimitive = getLogic[GateTileLogic[GateICTile]]

    def subID = gateSubID&0xFF

    def shape = gateShape&0xFF
    def setShape(s:Int): Unit ={ gateShape = s.toByte }

    def preparePlacement(rot:Int, meta:Int): Unit =
        gateSubID = meta.toByte
        setRotation(rot)

    override def save(tag:NBTTagCompound): Unit =
        tag.setByte("orient", orientation)
        tag.setByte("subID", gateSubID)
        tag.setByte("shape", gateShape)
        tag.setByte("connMap", connMap)

    override def load(tag:NBTTagCompound): Unit =
        orientation = tag.getByte("orient")
        gateSubID = tag.getByte("subID")
        gateShape = tag.getByte("shape")
        connMap = tag.getByte("connMap")

    override def writeDesc(out:MCDataOutput): Unit =
        out.writeByte(orientation)
        out.writeByte(gateSubID)
        out.writeByte(gateShape)

    override def readDesc(in:MCDataInput): Unit =
        orientation = in.readByte()
        gateSubID = in.readByte()
        gateShape = in.readByte()

    override def read(in:MCDataInput, key:Int) = key match
        case 1 => orientation = in.readByte()
        case 2 => gateShape = in.readByte()
        case _ => super.read(in, key)

    override def readClientPacket(in:MCDataInput): Unit =
        readClientPacket(in, in.readUByte())

    def readClientPacket(in:MCDataInput, key:Int) = key match
        case 0 => rotate()
        case 1 => configure()
        case 2 => getLogicPrimitive.activate(this)
        case _ =>

    override def canConnectTile(part:ICTile, r:Int) =
        getLogicPrimitive.canConnectTo(this, part, toInternal(r))

    def onSchematicChanged(): Unit =
        editor.markSchematicChanged()

    override def update(): Unit =
        getLogicPrimitive.onTick(this)

    override def onNeighborChanged(): Unit =
        if !editor.network.isRemote then
            if updateConns() then
                onSchematicChanged()

    override def onAdded(): Unit =
        super.onAdded()
        if !editor.network.isRemote then
            updateConns()
            getLogicPrimitive.onGatePlaced(this)

    override def onRemoved(): Unit =
        super.onRemoved()
        if !editor.network.isRemote then
            notify(0xF)

    def configure(): Unit =
        if getLogicPrimitive.cycleShape(this) then
            updateConns()
            editor.network.markSave()
            sendShapeUpdate()
            notify(0xF)
            onSchematicChanged()

    def rotate(): Unit =
        setRotation((rotation+1)%4)
        updateConns()
        editor.network.markSave()
        sendOrientUpdate()
        notify(0xF)
        onSchematicChanged()

    def sendShapeUpdate(): Unit =
        writeStreamOf(2).writeByte(gateShape)

    def sendOrientUpdate(): Unit =
        writeStreamOf(1).writeByte(orientation)

    override def buildImplicitWireNet(r:Int) =
        val net = new ImplicitWireNet(tileMap, pos, r)
        net.calculateNetwork()
        if net.isRedundant then null else net

    override def allocateOrFindRegisters(linker:ISELinker): Unit =
        getLogicPrimitive.allocateOrFindRegisters(this, linker)

    def getInputRegister(r:Int, linker:ISELinker):Int =  linker.findInputRegister(pos, toAbsolute(r))

    def getOutputRegister(r:Int, linker:ISELinker):Int =  linker.findOutputRegister(pos, toAbsolute(r))

    override def declareOperations(linker:ISELinker): Unit =
        getLogicPrimitive.declareOperations(this, linker)

    override def onRegistersChanged(regIDs:Set[Int]): Unit =
        getLogicPrimitive.onRegistersChanged(this, regIDs)

    @SideOnly(Side.CLIENT)
    override def renderDynamic(ccrs:CCRenderState, t:Transformation, ortho:Boolean, frame:Float): Unit =
        RenderGateTile.renderDynamic(ccrs, this, t, ortho, frame)

    @SideOnly(Side.CLIENT)
    override def getPartName = ICGateDefinition(subID).name

    @SideOnly(Side.CLIENT)
    override def buildRolloverData(buffer:ListBuffer[String]): Unit =
        super.buildRolloverData(buffer)
        getLogicPrimitive.buildRolloverData(this, buffer)

    @SideOnly(Side.CLIENT)
    override def createGui = getLogicPrimitive.createGui(this)

    @SideOnly(Side.CLIENT)
    override def onClicked(): Unit =
        sendClientPacket(_.writeByte(2))

    @SideOnly(Side.CLIENT)
    override def getPickOp =
        TileEditorOpDefs.values(TileEditorOpDefs.SimpleIO.ordinal+subID).getOp

abstract class GateTileLogic[T <: GateICTile]
:
    def canConnectTo(gate:T, part:ICTile, r:Int):Boolean

    def cycleShape(gate:T) = false

    def onGatePlaced(gate:T): Unit ={}

    def onTick(gate:T): Unit ={}

    def activate(gate:T): Unit ={}

    def allocateOrFindRegisters(gate:T, linker:ISELinker): Unit 

    def declareOperations(gate:T, linker:ISELinker): Unit 

    def onRegistersChanged(gate:T, regIDs:Set[Int]): Unit ={}

    @SideOnly(Side.CLIENT)
    def buildRolloverData(gate:T, buffer:ListBuffer[String]): Unit ={}

    @SideOnly(Side.CLIENT)
    def createGui(gate:T):ICTileGui = new ICGateGui(gate)

object ICGateDefinition extends Enum
{
    type EnumVal = ICGateDef

    import mrtjp.projectred.integration.{GateDefinition as gd}

    val IOSimple = ICGateDef("Simple IO", ICTileDefs.IOGate.id)
    val IOAnalog = ICGateDef("Analog IO", ICTileDefs.IOGate.id)
    val IOBundled = ICGateDef("Bundled IO", ICTileDefs.IOGate.id)

    val OR = ICGateDef("OR gate", ICTileDefs.SimpleGate.id, gd.OR)
    val NOR = ICGateDef("NOR gate", ICTileDefs.SimpleGate.id, gd.NOR)
    val NOT = ICGateDef("NOT gate", ICTileDefs.SimpleGate.id, gd.NOT)
    val AND = ICGateDef("AND gate", ICTileDefs.SimpleGate.id, gd.AND)
    val NAND = ICGateDef("NAND gate", ICTileDefs.SimpleGate.id, gd.NAND)
    val XOR = ICGateDef("XOR gate", ICTileDefs.SimpleGate.id, gd.XOR)
    val XNOR = ICGateDef("XNOR gate", ICTileDefs.SimpleGate.id, gd.XNOR)
    val Buffer = ICGateDef("Buffer gate", ICTileDefs.SimpleGate.id, gd.Buffer)
    val Multiplexer = ICGateDef("Multiplexer", ICTileDefs.SimpleGate.id, gd.Multiplexer)
    val Pulse = ICGateDef("Pulse Former", ICTileDefs.ComplexGate.id, gd.Pulse)
    val Repeater = ICGateDef("Repeater", ICTileDefs.ComplexGate.id, gd.Repeater)
    val Randomizer = ICGateDef("Randomizer", ICTileDefs.ComplexGate.id, gd.Randomizer)
    val SRLatch = ICGateDef("SR Latch", ICTileDefs.ComplexGate.id, gd.SRLatch)
    val ToggleLatch = ICGateDef("Toggle Latch", ICTileDefs.ComplexGate.id, gd.ToggleLatch)
    val TransparentLatch = ICGateDef("Transparent Latch", ICTileDefs.ComplexGate.id, gd.TransparentLatch)
    val Timer = ICGateDef("Timer", ICTileDefs.ComplexGate.id, gd.Timer)
    val Sequencer = ICGateDef("Sequencer", ICTileDefs.ComplexGate.id, gd.Sequencer)
    val Counter = ICGateDef("Counter", ICTileDefs.ComplexGate.id, gd.Counter)
    val StateCell = ICGateDef("State Cell", ICTileDefs.ComplexGate.id, gd.StateCell)
    val Synchronizer = ICGateDef("Synchronizer", ICTileDefs.ComplexGate.id, gd.Synchronizer)
    val DecRandomizer = ICGateDef("Dec Randomizer", ICTileDefs.ComplexGate.id, gd.DecRandomizer)
    val NullCell = ICGateDef("Null Cell", ICTileDefs.ArrayGate.id, gd.NullCell)
    val InvertCell = ICGateDef("Invert Cell", ICTileDefs.ArrayGate.id, gd.InvertCell)
    val BufferCell = ICGateDef("Buffer Cell", ICTileDefs.ArrayGate.id, gd.BufferCell)

    case class ICGateDef(unlocal:String, gateType:Int, intDef:GateDef = null) extends Value
    :
        override def name = unlocal
}