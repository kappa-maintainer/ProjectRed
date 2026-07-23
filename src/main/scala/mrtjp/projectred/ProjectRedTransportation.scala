package mrtjp.projectred

import mrtjp.projectred.api.ProjectRedAPI
import mrtjp.projectred.transportation._
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent, FMLServerStoppingEvent}

@Mod(modid = "projectred-transportation", useMetadata = true, modLanguage = "scala")
object ProjectRedTransportation
{
    ProjectRedAPI.transportationAPI = new APIImpl_Transportation

    /** Items **/
    var itemRoutingChip:ItemRoutingChip = _
    var itemRouterUtility:ItemRouterUtility = _

    /** Multipart items **/
    var itemPartPipe:ItemPartPipe = _

    val tabTransportation: CreativeTabs = new CreativeTabs("projectred.transportation") {
        override def createIcon: ItemStack = RoutingChipDefs.ITEMSTOCKKEEPER.makeStack
    }

    @Mod.EventHandler
    def preInit(event:FMLPreInitializationEvent): Unit =
    {
        TransportationProxy.preinit()
    }

    @Mod.EventHandler
    def init(event:FMLInitializationEvent): Unit =
    {
        TransportationProxy.init()
    }

    @Mod.EventHandler
    def postInit(event:FMLPostInitializationEvent): Unit =
    {
        TransportationProxy.postinit()
    }

    @Mod.EventHandler
    def serverStopping(event:FMLServerStoppingEvent): Unit =
    {
        RouterServices.reboot()
    }
}
