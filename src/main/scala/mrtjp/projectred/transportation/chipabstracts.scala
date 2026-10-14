package mrtjp.projectred.transportation

import java.util.UUID

import com.mojang.realmsclient.gui.ChatFormatting
import mrtjp.core.inventory.{InvWrapper, SimpleInventory, VanillaWrapper}
import mrtjp.core.item.{ItemEquality, ItemKey, ItemKeyStack, ItemQueue}
import mrtjp.projectred.transportation.RoutingChipDefs.ChipVal
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.client.resources.I18n
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.lwjgl.input.Keyboard

import scala.collection.mutable.ListBuffer

/** Lang key prefix for routing chip tooltips. */
private[transportation] val ChipLang = "gui.projectred.transportation.chip."

abstract class RoutingChip
:
    private var routerContainer:IRouterContainer = null
    private var invProv:IInventoryProvider = null
    private var s = -1

    def setEnvironment(inventoryProvider:IInventoryProvider, irc:IRouterContainer, slot:Int): Unit =
        invProv = inventoryProvider
        routerContainer = irc
        s = slot

    def invProvider = invProv
    def router = routerContainer
    def slot = s

    def update(): Unit ={}

    def onEventReceived(event:NetworkEvent): Unit ={}

    /** Syncing **/
    def getSyncResponse(item:ItemKey, rival:SyncResponse):SyncResponse = null

    /** Broadcasting **/
    def requestPromise(request:RequestBranchNode, existingPromises:Int): Unit ={}
    def deliverPromise(promise:DeliveryPromise, requester:IRouterContainer): Unit ={}
    def getBroadcasts(col:ItemQueue): Unit ={}

    def getBroadcastPriority = Integer.MAX_VALUE
    def getWorkLoad = 0.0D

    /** Crafting **/
    def requestCraftPromise(requeset:RequestBranchNode):CraftingPromise = null
    def registerExcess(promise:DeliveryPromise): Unit ={}
    def getCraftedItem:ItemKeyStack = null
    def getProcessingItems = 0

    /** World interactions **/
    def onAdded(): Unit ={}
    def onRemoved(): Unit ={}
    def onNeighborTileChanged(side:Int, weak:Boolean): Unit ={}
    def weakTileChanges = false

    def save(tag:NBTTagCompound): Unit ={}

    def load(tag:NBTTagCompound): Unit ={}

    def infoCollection(list:ListBuffer[String]): Unit ={}

    def getChipType:ChipVal

    def openGui(player:EntityPlayer): Unit =
        if player.world.isRemote then return
        GuiChipConfig.open(player, createContainer(player), _.writeByte(player.inventory.currentItem))

    def createContainer(player:EntityPlayer) = new ContainerChipConfig(player, this)

trait TChipFilter extends RoutingChip
:
    val filter = new SimpleInventory(9, "filter", 1)
    var filterExclude = false

    var metaMatch = true
    var nbtMatch = true
    var oreMatch = false

    var damageGroupMode = 0
    val grpPerc = Seq(-1, 25, 50, 75, 99)

    // 0-none, 1-type, 2-slot
    var hideMode = 0

    def toggleExcludeMode(): Unit =
        filterExclude = !filterExclude

    def toggleMetaMode(): Unit =
        metaMatch = !metaMatch

    def toggleNBTMode(): Unit =
        nbtMatch = !nbtMatch

    def toggleOreMode(): Unit =
        oreMatch = !oreMatch

    def shiftDamageGroup(): Unit =
        damageGroupMode = (damageGroupMode+1)%5

    def shiftHiding(): Unit =
        hideMode = (hideMode+1)%3

    def applyFilter(inv:InvWrapper, patterns:Boolean=true, hide:Boolean=true):InvWrapper =
        if inv == null then return null

        if enablePatterns && patterns then
            inv.setMatchOptions(metaMatch, nbtMatch, oreMatch).setDamageGroup(grpPerc(damageGroupMode))

        if enableHiding && hide then hideMode match
            case 1 => inv.setHidePerType(true)
            case 2 => inv.setHidePerSlot(true)
            case _ =>

        inv

    def enableHiding = true
    def enableFilter = true
    def enablePatterns = true

    abstract override def save(tag:NBTTagCompound): Unit =
        filter.saveInv(tag)
        tag.setBoolean("mode", filterExclude)
        tag.setBoolean("mm", metaMatch)
        tag.setBoolean("nbtm", nbtMatch)
        tag.setBoolean("om", oreMatch)
        tag.setByte("grp", damageGroupMode.asInstanceOf[Byte])
        tag.setByte("hide", hideMode.asInstanceOf[Byte])
        super.save(tag)

    abstract override def load(tag:NBTTagCompound): Unit =
        filter.loadInv(tag)
        filterExclude = tag.getBoolean("mode")
        metaMatch = tag.getBoolean("mm")
        nbtMatch = tag.getBoolean("nbtm")
        oreMatch = tag.getBoolean("om")
        damageGroupMode = tag.getByte("grp")
        hideMode = tag.getByte("hide")
        super.load(tag)

    /** Hide-mode names, as lang key suffixes. */
    val hide = Seq("hideOff", "hideOnePerType", "hideOnePerStack")

    def addFilterInfo(list:ListBuffer[String]): Unit =
        if enableHiding then
            list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"hideMode", I18n.format(ChipLang+hide(hideMode))))

        if enablePatterns then
            val s = Seq(
                if metaMatch then Some(I18n.format(ChipLang+"labelMeta")) else None,
                if nbtMatch then Some(I18n.format(ChipLang+"labelNbt")) else None,
                if oreMatch then Some(I18n.format(ChipLang+"labelOre")) else None
            ).flatten.mkString(", ")
            val matching = if s.isEmpty then I18n.format(ChipLang+"matchingIgnoreAll") else s
            list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"matching", matching))
            if damageGroupMode!=0 then
                list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"damageGroup", grpPerc(damageGroupMode)))

        if enableFilter then
            val mode = I18n.format(ChipLang+(if filterExclude then "blacklist" else "whitelist"))
            list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"filterModeLabel", mode))
            list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"filterTitle"))
            var added = false

            for i <- 0 until filter.getSizeInventory do
                val stack = filter.getStackInSlot(i)
                if !stack.isEmpty then
                    list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEntry", stack.getDisplayName))
                    added = true

            if !added then list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEmpty"))

trait TChipPriority extends RoutingChip
:
    var preference = 0

    private def shift = if Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) then 10 else 1
    def prefUp(): Unit =
        preference = Math.min(prefScale, preference+shift)

    def prefDown(): Unit =
        preference = Math.max(-prefScale, preference-shift)

    def prefScale = 32

    abstract override def save(tag:NBTTagCompound): Unit =
        tag.setInteger("pref", preference)
        super.save(tag)

    abstract override def load(tag:NBTTagCompound): Unit =
        preference = tag.getInteger("pref")
        super.load(tag)

    def addPriorityInfo(list:ListBuffer[String]): Unit =
        list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"preference", preference))

trait TChipOrientation extends RoutingChip
:
    var extractOrient = -1

    def extractSide = if extractOrient <= -1 then invProvider.getInterfacedSide else extractOrient

    abstract override def save(tag:NBTTagCompound): Unit =
        tag.setInteger("orient", extractOrient)
        super.save(tag)

    abstract override def load(tag:NBTTagCompound): Unit =
        extractOrient = tag.getInteger("orient")
        super.load(tag)

    /** Direction names, as lang key suffixes. Indexed by side 0-5. */
    private val dirs = Seq("orientation.0", "orientation.1", "orientation.2",
        "orientation.3", "orientation.4", "orientation.5")

    def addOrientInfo(list:ListBuffer[String]): Unit =
        val dir = if extractOrient == -1 then I18n.format(ChipLang+"orientationDefault")
            else I18n.format(ChipLang+dirs(extractOrient))
        list+=(ChatFormatting.GRAY.toString+I18n.format(ChipLang+"extractOrientation", dir))

trait TChipStock extends RoutingChip
:
    val stock = new SimpleInventory(9, "stock", 127)
    var requestMode = 0 //0 - stock continuous, 1 - stock empty, 2 - stock infinite

    def shiftRequestMode(): Unit =
        requestMode = (requestMode+1)%3

    abstract override def save(tag:NBTTagCompound): Unit =
        stock.saveInv(tag)
        tag.setByte("rmode", requestMode.toByte)
        super.save(tag)

    abstract override def load(tag:NBTTagCompound): Unit =
        stock.loadInv(tag)
        requestMode = tag.getByte("rmode")
        super.load(tag)

    def addStockInfo(list:ListBuffer[String]): Unit =
        val mode = requestMode match
            case 0 => "fillShortMissing"
            case 1 => "fillShortEmpty"
            case 2 => "fillShortInfinite"
        list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"fillModeLabel", I18n.format(ChipLang+mode)))
        list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"stockTitle"))
        var added = false
        for i <- 0 until stock.getSizeInventory do
            val stack = stock.getStackInSlot(i)
            if !stack.isEmpty then
                list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEntryCount", stack.getDisplayName, stack.getCount))
                added = true
        if !added then list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEmpty"))

trait TChipMatchMatrix extends RoutingChip
:
    var matchData = Array.fill[Int](9)(packMatchData(true, true, false, 0))

    val grpPerc = Seq(-1, 25, 50, 75, 99)

    def getMatchInventory:IInventory

    def setData(i:Int, meta:Boolean, nbt:Boolean, ore:Boolean, group:Int): Unit =
        matchData(i) = packMatchData(meta, nbt, ore, group)

    def getData(i:Int) = unpackMatchData(matchData(i))

    def packMatchData(meta:Boolean, nbt:Boolean, ore:Boolean, group:Int) =
        var data = 0
        if meta then data |= 1<<0
        if nbt then data |= 1<<1
        if ore then data |= 1<<2
        data |= group<<3
        data

    def unpackMatchData(data:Int) = ((data&1) != 0, (data&2) != 0, (data&4) != 0, data>>3)

    def matchMeta(i:Int) = getData(i)._1
    def matchNBT(i:Int) = getData(i)._2
    def matchOre(i:Int) = getData(i)._3
    def matchGroup(i:Int) = getData(i)._4

    def toggleMatchMeta(i:Int) =
        val (meta, nbt, ore, group) = getData(i)
        setData(i, !meta, nbt, ore, group)
    def toggleMatchNBT(i:Int) =
        val (meta, nbt, ore, group) = getData(i)
        setData(i, meta, !nbt, ore, group)
    def toggleMatchOre(i:Int) =
        val (meta, nbt, ore, group) = getData(i)
        setData(i, meta, nbt, !ore, group)
    def toggleMatchGroup(i:Int) =
        val (meta, nbt, ore, group) = getData(i)
        setData(i, meta, nbt, ore, (group+1)%5)

    abstract override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        tag.setIntArray("matchData", matchData)

    abstract override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        matchData = tag.getIntArray("matchData")

    def createEqualityFor(i:Int) =
        val eq = new ItemEquality
        val (meta, nbt, ore, group) = getData(i)
        eq.setFlags(meta, nbt, ore, group)
        eq

trait TChipCrafter extends RoutingChip
:
    var matrix = new SimpleInventory(10, "matrix", 127)
    var extMatrix = new SimpleInventory(9, "ext_matrix", 1)
    :
        override def isItemValidForSlot(slot:Int, stack:ItemStack) =
            !stack.isEmpty && ItemRoutingChip.hasChipInside(stack) &&
                    RoutingChipDefs.getForStack(stack) == RoutingChipDefs.ITEMEXTENSION

    abstract override def save(tag:NBTTagCompound): Unit =
        super.save(tag)
        matrix.saveInv(tag)
        extMatrix.saveInv(tag)

    abstract override def load(tag:NBTTagCompound): Unit =
        super.load(tag)
        matrix.loadInv(tag)
        extMatrix.loadInv(tag)

    def getAmountForIngredient(item:ItemKey) =
        var amount = 0
        for i <- 0 until 9 do
            val s = matrix.getStackInSlot(i)
            if !s.isEmpty && ItemKey.get(s) == item then
                amount += s.getCount
        amount

    def isIngredient(item:ItemKey):Boolean =
        (0 until 9).exists { i =>
            val s = matrix.getStackInSlot(i)
            !s.isEmpty && ItemKey.get(s) == item
        }

    def addMatrixInfo(list:ListBuffer[String]): Unit =
        list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"matrixTitle"))
        var added = false

        for i <- 0 until 9 do
            val stack = matrix.getStackInSlot(i)

            if !stack.isEmpty then
                list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEntryCount", stack.getDisplayName, stack.getCount))
                added = true
        if !added then list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"listEmpty"))

        val stack = matrix.getStackInSlot(9)
        if !stack.isEmpty then
            list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"matrixYields", stack.getDisplayName, stack.getCount))

    def addExtInfo(list:ListBuffer[String]): Unit =
        val count = (0 until 9).count{!extMatrix.getStackInSlot(_).isEmpty}
        list += (ChatFormatting.GRAY.toString+I18n.format(ChipLang+"extensionsCount", count))

trait TChipCrafterExtension extends RoutingChip
:
    var id = UUID.randomUUID()

    override def save(tag:NBTTagCompound): Unit =
        tag.setString("extid", id.toString)

    override def load(tag:NBTTagCompound): Unit =
        id = UUID.fromString(tag.getString("extid"))

    def randomizeUUID(): Unit =
        id = UUID.randomUUID()

    def addExtIDInfo(list:ListBuffer[String]): Unit =
        list += ChatFormatting.GRAY.toString+I18n.format(ChipLang+"extensionIdValue", id.toString.split("-")(0))
