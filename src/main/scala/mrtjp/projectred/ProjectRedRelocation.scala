package mrtjp.projectred

import mrtjp.core.data.{ModConfig, SpecialConfigGui, TModGuiFactory}
import mrtjp.projectred.api.ProjectRedAPI
import mrtjp.projectred.relocation.*
import net.minecraft.client.gui.GuiScreen
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-relocation", useMetadata = true, modLanguage = "scala")
object ProjectRedRelocation
:
    ProjectRedAPI.relocationAPI = APIImpl_Relocation

    var blockMovingRow:BlockMovingRow = scala.compiletime.uninitialized
    var blockFrame:BlockFrame = scala.compiletime.uninitialized

    var tabRelocation: CreativeTabs = new CreativeTabs("projectred.relocation"):
        override def createIcon = new ItemStack(blockFrame)

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        RelocationProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        APIImpl_Relocation.isPreInit = false
        RelocationConfig.loadConfig()
        RelocationProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        RelocationProxy.postinit()

class RelocationConfigGui(parent:GuiScreen) extends SpecialConfigGui(parent, "projectred-relocation", RelocationConfig.config)

class GuiConfigFactory extends TModGuiFactory
:
    override def createConfigGui(parentScreen:GuiScreen):GuiScreen = new RelocationConfigGui(parentScreen)

object RelocationConfig extends ModConfig("projectred-relocation")
{
    var moveLimit = 2048

    var moverMap = Array(
        "default -> saveload"
    )

    var setMap = Array(
        "minecraft:bed -> minecraft:bed",
        "minecraft:wooden_door -> minecraft:wooden_door",
        "minecraft:iron_door -> minecraft:iron_door"
    )

    override def getFileName = "ProjectRedRelocation"

    override protected def initValues(): Unit =
        val general = BaseCategory("General", "Basic settings")
        moveLimit = general.put("moveLimit", moveLimit, "Maximum amount of blocks that can be moved at once.")

        val movers = BaseCategory("Tile Movers", buildMoverDesc)
        moverMap = movers.put("mover registry", moverMap.toIndexedSeq).toArray
        moverMap = movers.put("mover registry", MovingTileRegistry.parseAndSetMovers(moverMap.toIndexedSeq).toIndexedSeq, force = true).toArray

        val sets = BaseCategory("Latched Sets", buildLatchSetsDesc)
        setMap = sets.put("latch registry", setMap.toIndexedSeq).toArray
        setMap = sets.put("latch registry", StickRegistry.parseAndAddLatchSets(setMap.toIndexedSeq).toIndexedSeq, force = true).toArray


    def buildMoverDesc: String =
        var s =
            """Used to configure which registered Tile Mover is used for a block. Key-Value pairs are defined using
              |the syntax key -> value.
              |Most blocks are configurable, but some mods may have opted to lock which handlers can be used for its
              |blocks.
              |Possible keys:
              |    'default' - to assign default handler.
              |    mod:<modID>' - to assign every block from a mod.
              |    <modID>:<blockname>' - to assign block from a mod for every meta.
              |    <modID>:<blockname>m<meta>' - to assign block from mod for specific meta.
            """.stripMargin

        s += "\nAvailable tile movers:\n"
        for (k, v) <- MovingTileRegistry.moverDescMap do
            s += "    '" + k + "' - " + v + "\n"

        if MovingTileRegistry.mandatoryMovers.nonEmpty then
            s += "\nMovers locked via API:\n"
            for (k, v) <- MovingTileRegistry.mandatoryMovers do
                s += "    " + k + " -> " + v + "\n"

        s

    def buildLatchSetsDesc:String =
        """Used to define which pairs of blocks will be stuck together.
          |Latched sets will always move in pairs, even if only one of them are actually connected to a frame.
          |'block1 -> block2' means that if block1 is moved, any block2 connected to it will also move.
          |However, moving block2 does not move block1. To do that, you must also register block2 -> block1.
          |Sets are defined using the syntax of key -> value.
          |Possible keys and values:
          |    '<modID>:<blockname>' - to assign block from a mod for every meta.
          |    '<modID>:<blockname>#<property>=<value>[,<property>=<value>[,…]]' - to assign block from mod with only the given properties matching.
        """.stripMargin
}