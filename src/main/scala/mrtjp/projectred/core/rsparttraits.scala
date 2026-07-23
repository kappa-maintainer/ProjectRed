package mrtjp.projectred.core

import codechicken.multipart.{IFaceRedstonePart, IRedstonePart, RedstoneInteractions, TMultiPart}
import mrtjp.projectred.core.IWirePart.*
import net.minecraft.block.BlockRedstoneWire
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

trait TRSAcquisitionsCommons extends TAcquisitionsCommons with IRedstonePart
{
    def calcStraightSignal(r:Int):Int

    def calcInternalSignal(r:Int):Int

    def resolveSignal(part:Any, dir:Int):Int
}

trait TFaceRSAcquisitions extends TRSAcquisitionsCommons with TFaceAcquisitions with IFaceRedstonePart
{
    def calcCornerSignal(r:Int) = resolveSignal(getCorner(r), rotFromCorner(r))

    override def calcStraightSignal(r:Int) = resolveSignal(getStraight(r), rotFromStraight(r))

    override def calcInternalSignal(r:Int) = resolveSignal(getInternal(r), rotFromInternal(r))

    def calcCenterSignal = resolveSignal(getCenter, side)

    def calcStrongSignal(r:Int) = RedstoneInteractions.getPowerTo(this, absoluteDir(r))*17

    def calcWeakSignal(r:Int) =
    {
        val pos = posOfStraight(r)
        if world.isBlockNormalCube(pos, false) then
            world.getRedstonePowerFromNeighbors(pos)*17
        else 0
    }

    def calcMaxSignal(r:Int, strong:Boolean, dustLimit:Boolean):Int =
    {
        var i = calcDustRedwireSignal(r)
        if i > -1 && dustLimit then return i
        i = calcStrongSignal(r)
        if i > 0 || strong then return i
        calcWeakSignal(r)
    }

    def calcUndersideSignal =
    {
        val face = EnumFacing.byIndex(side)
        world.getRedstonePower(pos.offset(face), face)*17
    }

    def calcDustRedwireSignal(r:Int) =
    {
        val pos = posOfStraight(r)
        val b = world.getBlockState(pos)
        if b.getBlock == Blocks.REDSTONE_WIRE then
            Math.max(b.getValue(BlockRedstoneWire.POWER)-1, 0)
        else -1
    }

    override def getFace = side
}

trait TCenterRSAcquisitions extends TRSAcquisitionsCommons with TCenterAcquisitions
{
    override def calcStraightSignal(s:Int) = resolveSignal(getStraight(s), s^1)

    override def calcInternalSignal(s:Int) = resolveSignal(getInternal(s), s^1)

    def calcStrongSignal(s:Int) = RedstoneInteractions.getPowerTo(this, s)*17

    def calcWeakSignal(s:Int) =
    {
        val pos = this.pos.offset(EnumFacing.byIndex(s))
        if world.isBlockNormalCube(pos, false) then
            world.getRedstonePowerFromNeighbors(pos)*17
        else 0
    }
}

trait TPropagationCommons extends TMultiPart with IWirePart
{
    var propagationMask:Int = 0

    def propagate(prev:TMultiPart, mode:Int): Unit 

    def propagateOther(mode:Int): Unit ={}

    def propagateExternal(to:TMultiPart, at:BlockPos, from:TMultiPart, mode:Int): Unit =
    {
        if to != null then {
            if to == from then return
            if propagateTo(to, mode) then return
        }
        WirePropagator.addNeighborChange(at)
    }

    def propagateInternal(to:TMultiPart, from:TMultiPart, mode:Int): Unit =
    {
        if to == from then return
        propagateTo(to, mode)
    }

    def propagateTo(part:TMultiPart, mode:Int) = part match
    {
        case w:IWirePart =>
            WirePropagator.propagateTo(w, this, mode)
            true
        case _ => false
    }
}

trait TFacePropagation extends TPropagationCommons with TFaceConnectable
{
    propagationMask = 0xF

    override def propagate(prev:TMultiPart, mode:Int): Unit =
    {
        if mode != FORCED then WirePropagator.addPartChange(this)
        for r <- 0 until 4 do if (propagationMask&1<<r) != 0 then {
            if maskConnectsInside(r) then propagateInternal(getInternal(r), prev, mode)
            else if maskConnectsStraight(r) then propagateExternal(getStraight(r), posOfStraight(r), prev, mode)
            else if maskConnectsCorner(r) then propagateExternal(getCorner(r), posOfCorner(r), prev, mode)
        }

        if maskConnectsCenter then propagateInternal(getCenter, prev, mode)
        propagateOther(mode)
    }
}

trait TCenterPropagation extends TPropagationCommons with TCenterConnectable
{
    propagationMask = 0x3F

    override def propagate(prev:TMultiPart, mode:Int): Unit =
    {
        if mode != FORCED then WirePropagator.addPartChange(this)
        for s <- 0 until 6 do if (propagationMask&1<<s) != 0 then {
            if maskConnectsIn(s) then propagateInternal(getInternal(s), prev, mode)
            else if maskConnectsOut(s) then propagateExternal(getStraight(s), posOfStraight(s), prev, mode)
        }
        propagateOther(mode)
    }
}

trait TRSPropagationCommons extends TPropagationCommons
{
    def calculateSignal:Int

    def getSignal:Int
    def setSignal(signal:Int): Unit 

    override def updateAndPropagate(prev:TMultiPart, mode:Int): Unit =
    {
        if mode == DROPPING && getSignal == 0 then return
        val newSignal = calculateSignal
        if newSignal < getSignal then
        {
            if newSignal > 0 then WirePropagator.propagateAnalogDrop(this)
            setSignal(0)
            propagate(prev, DROPPING)
        }
        else if newSignal > getSignal then
        {
            setSignal(newSignal)
            if mode == DROPPING then propagate(null, RISING)
            else propagate(prev, RISING)
        }
        else if mode == DROPPING then propagateTo(prev, RISING)
        else if mode == FORCE then propagate(prev, FORCED)
    }
}

trait TFaceRSPropagation extends TFacePropagation with TRSPropagationCommons

trait TCenterRSPropagation extends TCenterPropagation with TRSPropagationCommons

trait IRedwirePart extends IWirePart with IRedwireEmitter

/**
  * Implemented by parts that emit a full-strength red alloy signal.
  */
trait IRedwireEmitter
{
    /**
      * For face parts, dir is a rotation. For center parts, it is a forge
      * direction.
      *
      * @return Signal strength from 0 to 255.
      */
    def getRedwireSignal(dir:Int):Int
}

trait IInsulatedRedwirePart extends IRedwirePart
{
    def getInsulatedColour:Int
}