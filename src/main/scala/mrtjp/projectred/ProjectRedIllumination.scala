package mrtjp.projectred

import mrtjp.projectred.illumination.*
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.{ItemBlock, ItemStack}
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-illumination", useMetadata = true, modLanguage = "scala")
object ProjectRedIllumination
{
    /** Blocks **/
    var blockLamp:BlockLamp = scala.compiletime.uninitialized
    var itemBlockLamp:ItemBlock = scala.compiletime.uninitialized
    var blockAirousLight:BlockAirousLight = scala.compiletime.uninitialized

//    /** Multipart items **/
    var itemPartIllumarButton:ItemPartButton = scala.compiletime.uninitialized
    var itemPartIllumarFButton:ItemPartFButton = scala.compiletime.uninitialized

    val tabLighting: CreativeTabs = new CreativeTabs("projectred.illumination") {
        override def createIcon = new ItemStack(LightFactoryCage.getItem(true))
    }

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit = {
        IlluminationProxy.preinit()
    }

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit = {
        IlluminationProxy.init()
    }

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit = {
        IlluminationProxy.postinit()
    }
}
