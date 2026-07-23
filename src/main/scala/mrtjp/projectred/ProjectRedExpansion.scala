package mrtjp.projectred

import codechicken.lib.model.bakery.sub.SubBlockBakery
import mrtjp.projectred.expansion.*
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.{Item, ItemStack}
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-expansion", useMetadata = true, modLanguage = "scala")
object ProjectRedExpansion
:
    /** Blocks **/
    var machine1:BlockMachine = scala.compiletime.uninitialized //machines
    var machine2:BlockMachine = scala.compiletime.uninitialized //devices

    /** Items **/
    var itemEmptybattery:ItemEmptyBattery = scala.compiletime.uninitialized
    var itemBattery:ItemBattery = scala.compiletime.uninitialized
    var itemJetpack:ItemJetpack = scala.compiletime.uninitialized
    var itemScrewdriver:ItemElectricScrewdriver = scala.compiletime.uninitialized
    var itemInfusedEnderPearl:ItemInfusedEnderPearl = scala.compiletime.uninitialized
    var itemPlan:ItemPlan = scala.compiletime.uninitialized

    /** Enchantments **/
    var enchantmentElectricEfficiency:EnchantmentElectricEfficiency = scala.compiletime.uninitialized

    /** Parts **/
    var itemSolar:ItemSolarPanel = scala.compiletime.uninitialized

    val tabExpansion: CreativeTabs = new CreativeTabs("projectred.expansion"):
        override def createIcon = new ItemStack(machine2)

    val machine1Bakery:SubBlockBakery = new SubBlockBakery
    val machine2Bakery:SubBlockBakery = new SubBlockBakery

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        ExpansionProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        ExpansionProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        ExpansionProxy.postinit()
