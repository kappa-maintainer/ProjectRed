/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred

import codechicken.lib.model.bakery.sub.SubBlockBakery
import mrtjp.projectred.fabrication.{BlockFabricationMachine, BlockICMachine, FabricationProxy, ItemBlankPhotomask, ItemICBlueprint, ItemICChip, ItemInvalidDie, ItemPhotomaskSet, ItemRoughSiliconWafer, ItemValidDie}
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-fabrication", useMetadata = true, modLanguage = "scala")
object ProjectRedFabrication
:
    /** Blocks **/
    var icBlock:BlockICMachine = scala.compiletime.uninitialized
    var fabricationMachineBlock:BlockFabricationMachine = scala.compiletime.uninitialized

    /** Items **/
    var itemICBlueprint:ItemICBlueprint = scala.compiletime.uninitialized
    var itemICChip:ItemICChip = scala.compiletime.uninitialized
    var itemRoughSiliconWafer:ItemRoughSiliconWafer = scala.compiletime.uninitialized
    var itemBlankPhotomask:ItemBlankPhotomask = scala.compiletime.uninitialized
    var itemPhotomaskSet:ItemPhotomaskSet = scala.compiletime.uninitialized
    var itemValidDie:ItemValidDie = scala.compiletime.uninitialized
    var itemInvalidDie:ItemInvalidDie = scala.compiletime.uninitialized

    val icMachineBakery:SubBlockBakery = new SubBlockBakery
    val fabricationMachineBakery:SubBlockBakery = new SubBlockBakery

    val tabFabrication: CreativeTabs = new CreativeTabs("projectred.fabrication"):
        override def createIcon = new ItemStack(itemICChip)

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        FabricationProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        FabricationProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        FabricationProxy.postinit()
