/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import codechicken.lib.render.CCRenderState
import codechicken.lib.vec.Transformation
import com.mojang.realmsclient.gui.ChatFormatting
import mrtjp.core.vec.Point
import mrtjp.projectred.fabrication.SEIntegratedCircuit.REG_ZERO
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.collection.mutable.ListBuffer

class ButtonICTile extends ICTile with TICTileAcquisitions with IRedwireICGate with TClientNetICTile with ISEGateTile
{
    val outputRegs = Array(REG_ZERO, REG_ZERO, REG_ZERO, REG_ZERO)
    var on = false
    var sched = -1L

    override def save(tag:NBTTagCompound): Unit =
    {
        super.save(tag)
        tag.setBoolean("on", on)
        tag.setLong("sched", sched)
    }

    override def load(tag:NBTTagCompound): Unit =
    {
        super.load(tag)
        on = tag.getBoolean("on")
        sched = tag.getLong("sched")
    }

    override def writeDesc(out:MCDataOutput): Unit =
    {
        super.writeDesc(out)
        out.writeBoolean(on)
    }

    override def readDesc(in:MCDataInput): Unit =
    {
        super.readDesc(in)
        on = in.readBoolean()
    }

    override def read(in:MCDataInput, key:Int) = key match
    {
        case 1 => on = in.readBoolean()
        case _ => super.read(in, key)
    }

    override def readClientPacket(in:MCDataInput): Unit =
    {
        press()
    }

    def press(): Unit =
    {
        if !on then {
            on = true
            pushToRegisters()
            sendStateUpdate()
            sched = editor.network.getEditorWorld.getTotalWorldTime+20 //schedule depress
        }
    }

    def depress(): Unit =
    {
        if on then {
            on = false
            pushToRegisters()
            sendStateUpdate()
            sched = -1 //clear depress schedule
        }
    }

    override def update(): Unit =
    {
        if sched != -1 && editor.network.getEditorWorld.getTotalWorldTime >= sched then
            depress()
    }

    def sendStateUpdate(): Unit =
    {
        writeStreamOf(1).writeBoolean(on)
    }

    override def getPartType = ICTileDefs.Button

    override def onAdded(): Unit =
    {
        if !editor.network.isRemote then notify(0xF)
    }

    override def onRemoved(): Unit =
    {
        if !editor.network.isRemote then notify(0xF)
    }

    def pushToRegisters(): Unit =
    {
        for r <- 0 until 4 do
            editor.simEngineContainer.simEngine.queueRegVal[Byte](outputRegs(r), if on then 1 else 0)

        editor.simEngineContainer.simEngine.propagate(editor.simEngineContainer)
    }

    override def onRegistersChanged(regIDs:Set[Int]): Unit ={} //we dont care if other registers change

    override def canOutputTo(r:Int) = true

    override def canInputFrom(r:Int) = false //this is output only 'gate'

    override def buildImplicitWireNet(r:Int):IWireNet = null //TODO

    override def allocateOrFindRegisters(linker:ISELinker): Unit =
    {
        for r <- 0 until 4 do
            outputRegs(r) = linker.findOutputRegister(pos, r)
    }

    override def declareOperations(linker:ISELinker): Unit =
    {
        on = false
        sched = -1
        if editor != null then
            sendStateUpdate()
    }

    @SideOnly(Side.CLIENT)
    override def onClicked(): Unit =
    {
        sendClientPacket()//data not necessary, only 1 reason to send this.
    }

    @SideOnly(Side.CLIENT)
    override def getPartName = "Button"

    @SideOnly(Side.CLIENT)
    override def getPickOp = TileEditorOpDefs.Button.getOp


    @SideOnly(Side.CLIENT)
    override def buildRolloverData(buffer:ListBuffer[String]): Unit =
    {
        super.buildRolloverData(buffer)
        buffer += ChatFormatting.GRAY.toString+"state: "+(if on then "on" else "off")
    }

    @SideOnly(Side.CLIENT)
    override def renderDynamic(ccrs:CCRenderState, t:Transformation, ortho:Boolean, frame:Float): Unit =
    {
        RenderTileButton.prepairDynamic(this)
        RenderTileButton.render(ccrs, t, ortho)
    }
}

class OpButton extends SimplePlacementOp
{
    override def doPartRender(ccrs:CCRenderState, t:Transformation): Unit =
    {
        RenderTileButton.prepairInv()
        RenderTileButton.render(ccrs, t, true)
    }

    override def createPart = ICTileDefs.Button.createPart

    @SideOnly(Side.CLIENT)
    override def getOpName = "Button"
}