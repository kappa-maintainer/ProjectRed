package mrtjp.projectred

import mrtjp.projectred.compatibility.CompatibilityProxy
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-compat", useMetadata = true, dependencies = "after:projectred-core", modLanguage = "scala")
object ProjectRedCompatibility
{
    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        CompatibilityProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        CompatibilityProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        CompatibilityProxy.postinit()
}