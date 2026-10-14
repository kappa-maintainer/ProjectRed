package mrtjp.projectred

import mrtjp.projectred.core.*
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent, FMLServerStartingEvent}
import org.apache.logging.log4j.{LogManager, Logger}

/**
  * Lang key prefix for text the server sends to players.
  *
  * A dedicated server cannot resolve ProjectRed's own keys: Forge only injects
  * `assets/<modid>/lang/en_us.lang` into its language map, and ProjectRed's files
  * live under the shared `assets/projectred/lang/` domain, which matches no mod id.
  * Such text must therefore be sent as a `TextComponentTranslation`, which the
  * client resolves against its own language files.
  */
private[projectred] val MsgLang = "message.projectred."

@Mod(modid = "projectred-core", useMetadata = true, modLanguage = "scala", guiFactory = "mrtjp.projectred.core.GuiConfigFactory")
object ProjectRedCore
:
    val log: Logger = LogManager.getFormatterLogger("ProjectRed")

    /** Items **/
    var itemPart:ItemPart = scala.compiletime.uninitialized
    var itemDrawPlate:ItemDrawPlate = scala.compiletime.uninitialized
    var itemScrewdriver:ItemScrewdriver = scala.compiletime.uninitialized
    var itemMultimeter:ItemMultimeter = scala.compiletime.uninitialized

    val tabCore: CreativeTabs = new CreativeTabs("projectred.core"):
        override def createIcon = new ItemStack(itemScrewdriver)

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        Configurator.loadConfig()
        CoreProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        CoreProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        CoreProxy.postinit()

    @Mod.EventHandler
    def onServerStarting(event: FMLServerStartingEvent): Unit = {}
