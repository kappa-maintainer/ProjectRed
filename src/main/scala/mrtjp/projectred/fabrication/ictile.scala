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
import mrtjp.core.vec.Point
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.collection.mutable.ListBuffer

object ICTileDefs extends Enum
:
    type EnumVal = ICTileDef

//    val Torch = CircuitPartDef(() => new TorchICPart)
    val Lever = ICTileDef(() => new LeverICTile)
    val Button = ICTileDef(() => new ButtonICTile)

    val AlloyWire = ICTileDef(() => new AlloyWireICTile)
    val InsulatedWire = ICTileDef(() => new InsulatedWireICTile)
    val BundledCable = ICTileDef(() => new BundledCableICTile)

    val IOGate = ICTileDef(() => new IOGateICTile)
    val SimpleGate = ICTileDef(() => new ComboGateICTile)
    val ComplexGate = ICTileDef(() => new SequentialGateICTile)
    val ArrayGate = ICTileDef(() => new ArrayGateICTile)

    case class ICTileDef(factory:() => ICTile) extends Value
    :
        def id = ordinal
        override def name = s"$id"

        def createPart = factory.apply()

object ICTile
:
    def createTile(id: Int): ICTile = ICTileDefs(id).createPart

abstract class ICTile extends ISETile
:
    var editor:ICTileMapEditor = null
    var tileMap:ICTileMapContainer = null
    var pos:Point = null

    def bindEditor(ic:ICTileMapEditor): Unit =
        editor = ic
        bindTileMap(ic.tileMapContainer)

    def bindTileMap(tm:ICTileMapContainer): Unit =
        tileMap = tm

    def bindPos(p:Point): Unit =
        pos = p

    def unbind(): Unit =
        editor = null
        tileMap = null
        pos = null

    def id = getPartType.id

    def getPartType:ICTileDefs.ICTileDef

    def save(tag:NBTTagCompound): Unit ={}
    def load(tag:NBTTagCompound): Unit ={}

    def writeDesc(out:MCDataOutput): Unit ={}
    def readDesc(in:MCDataInput): Unit ={}

    def writeStreamOf(key:Int):MCDataOutput = editor.network.getTileStream(pos).writeByte(key)
    def read(in:MCDataInput): Unit = { read(in, in.readUByte()) }
    def read(in:MCDataInput, key:Int) = key match
        case 0 => readDesc(in)
        case _ =>

    def sendDescUpdate(): Unit = { writeDesc(writeStreamOf(0)) }

    def update(): Unit ={}
    def scheduledTick(): Unit ={}
    def scheduleTick(ticks:Int): Unit ={ editor.scheduleTick(pos, ticks) }

    def onAdded(): Unit ={}
    def onRemoved(): Unit ={}

    def onNeighborChanged(): Unit ={}

    def onRegistersChanged(regIDs:Set[Int]): Unit //alerts part if any register in the circuit has changed.

    @SideOnly(Side.CLIENT)
    def onClicked(): Unit ={}
    @SideOnly(Side.CLIENT)
    def onActivated(): Unit ={}

    @SideOnly(Side.CLIENT)
    def getPartName:String
    @SideOnly(Side.CLIENT)
    def getPickOp:TileEditorOp = null
    @SideOnly(Side.CLIENT)
    def buildRolloverData(buffer:ListBuffer[String]): Unit =
        buffer += getPartName

    @SideOnly(Side.CLIENT)
    def renderDynamic(ccrs:CCRenderState, t:Transformation, ortho:Boolean, frame:Float): Unit ={}

trait TClientNetICTile extends ICTile
:
    def readClientPacket(in:MCDataInput): Unit 

    @SideOnly(Side.CLIENT)
    def sendClientPacket(writer:MCDataOutput => Unit = {_ => }): Unit =
        editor.sendClientPacket(this, writer)

trait IGuiICTile extends TClientNetICTile
{
    @SideOnly(Side.CLIENT)
    def createGui:ICTileGui
}