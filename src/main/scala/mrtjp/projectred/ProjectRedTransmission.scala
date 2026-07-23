package mrtjp.projectred

import mrtjp.projectred.api.ProjectRedAPI
import mrtjp.projectred.core.WirePropagator
import mrtjp.projectred.transmission._
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent, FMLServerAboutToStartEvent}

@Mod(modid = "projectred-transmission", useMetadata = true, modLanguage = "scala")
object ProjectRedTransmission
{
    ProjectRedAPI.transmissionAPI = new APIImpl_Transmission

    /** Multipart items **/
    var itemPartWire:ItemPartWire = _
    var itemPartFramedWire:ItemPartFramedWire = _

    val tabTransmission = new CreativeTabs("projectred.transmission")
    {
        override def createIcon = new ItemStack(ProjectRedTransmission.itemPartWire)
    }

    @Mod.EventHandler
    def preInit(event:FMLPreInitializationEvent): Unit =
    {
        TransmissionProxy.preinit()
    }

    @Mod.EventHandler
    def init(event:FMLInitializationEvent): Unit =
    {
        TransmissionProxy.init()
    }

    @Mod.EventHandler
    def postInit(event:FMLPostInitializationEvent): Unit =
    {
        TransmissionProxy.postinit()
    }

    @Mod.EventHandler
    def serverStopping(event:FMLServerAboutToStartEvent): Unit =
    {
        WirePropagator.reset()
    }
}
