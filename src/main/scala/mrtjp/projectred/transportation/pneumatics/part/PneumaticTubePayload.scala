package mrtjp.projectred.transportation.pneumatics.part

import codechicken.lib.data.{MCDataInput, MCDataOutput}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object PneumaticTubePayload:
    final val MAX_PROGRESS = 255

class PneumaticTubePayload:
    val MAX_PROGRESS = PneumaticTubePayload.MAX_PROGRESS

    private var progress = 0
    private var speed = 0
    private var inputSide = -1
    private var outputSide = -1
    private var itemStack = ItemStack.EMPTY

    def this(stack:ItemStack) =
        this()
        itemStack = stack

    def getProgress = progress
    def getSpeed = speed
    def getInputSide = inputSide
    def getOutputSide = outputSide
    def getItemStack = itemStack

    def setProgress(value:Int): Unit = progress = value
    def setSpeed(value:Int): Unit = speed = value
    def setInputSide(value:Int): Unit = inputSide = value
    def setOutputSide(value:Int): Unit = outputSide = value
    def setItemStack(value:ItemStack): Unit = itemStack = value

    def incrementProgress(): Unit = progress += speed

    def hasOutputSide = outputSide != -1

    def resetOutput(): Unit = outputSide = -1

    def resetProgress(): Unit = progress = math.max(0, progress - MAX_PROGRESS)

    def isPassedHalfWay = progress > MAX_PROGRESS / 2

    def getCurrentSide = if progress < MAX_PROGRESS / 2 then inputSide else outputSide

    def isCorrupted = itemStack.isEmpty || itemStack.getCount <= 0

    def save(tag:NBTTagCompound): Unit =
        tag.setInteger("progress", progress)
        tag.setInteger("speed", speed)
        tag.setInteger("input_side", inputSide)
        tag.setInteger("output_side", outputSide)
        val itemTag = new NBTTagCompound
        itemStack.writeToNBT(itemTag)
        tag.setTag("item_stack", itemTag)

    def load(tag:NBTTagCompound): Unit =
        progress = tag.getInteger("progress")
        speed = tag.getInteger("speed")
        inputSide = tag.getInteger("input_side")
        outputSide = tag.getInteger("output_side")
        itemStack = if tag.hasKey("item_stack", 10) then new ItemStack(tag.getCompoundTag("item_stack")) else ItemStack.EMPTY

    def writeDesc(out:MCDataOutput): Unit =
        out.writeByte(progress)
        out.writeByte(speed)
        out.writeByte(inputSide)
        out.writeByte(outputSide)
        out.writeItemStack(itemStack)

    def readDesc(in:MCDataInput): Unit =
        progress = in.readUByte()
        speed = in.readUByte()
        inputSide = in.readByte()
        outputSide = in.readByte()
        itemStack = in.readItemStack()
