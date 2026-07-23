package mrtjp.projectred

import mrtjp.core.world.SimpleGenHandler
import mrtjp.projectred.exploration.*
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Blocks
import net.minecraft.item.Item.ToolMaterial
import net.minecraft.item.ItemArmor.ArmorMaterial
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.{FMLInitializationEvent, FMLPostInitializationEvent, FMLPreInitializationEvent}

@Mod(modid = "projectred-exploration", useMetadata = true, modLanguage = "scala")
object ProjectRedExploration
:
    /** Blocks **/
    var blockOres:BlockOre = scala.compiletime.uninitialized
    var blockDecorativeStone:BlockDecorativeStone = scala.compiletime.uninitialized
    var blockDecorativeWall:BlockDecorativeWall = scala.compiletime.uninitialized
    var blockBarrel:BlockBarrel = scala.compiletime.uninitialized

    /** Materials **/
    var toolMaterialRuby:ToolMaterial = scala.compiletime.uninitialized
    var toolMaterialSapphire:ToolMaterial = scala.compiletime.uninitialized
    var toolMaterialPeridot:ToolMaterial = scala.compiletime.uninitialized
    var armorMatrialRuby:ArmorMaterial = scala.compiletime.uninitialized
    var armorMatrialSapphire:ArmorMaterial = scala.compiletime.uninitialized
    var armorMatrialPeridot:ArmorMaterial = scala.compiletime.uninitialized

    /** Items **/
    var itemWoolGin:ItemWoolGin = scala.compiletime.uninitialized
    var itemBackpack:ItemBackpack = scala.compiletime.uninitialized
    var itemAthame:ItemAthame = scala.compiletime.uninitialized
    var itemRubyAxe:ItemGemAxe = scala.compiletime.uninitialized
    var itemSapphireAxe:ItemGemAxe = scala.compiletime.uninitialized
    var itemPeridotAxe:ItemGemAxe = scala.compiletime.uninitialized
    var itemRubyHoe:ItemGemHoe = scala.compiletime.uninitialized
    var itemSapphireHoe:ItemGemHoe = scala.compiletime.uninitialized
    var itemPeridotHoe:ItemGemHoe = scala.compiletime.uninitialized
    var itemRubyPickaxe:ItemGemPickaxe = scala.compiletime.uninitialized
    var itemSapphirePickaxe:ItemGemPickaxe = scala.compiletime.uninitialized
    var itemPeridotPickaxe:ItemGemPickaxe = scala.compiletime.uninitialized
    var itemRubyShovel:ItemGemShovel = scala.compiletime.uninitialized
    var itemSapphireShovel:ItemGemShovel = scala.compiletime.uninitialized
    var itemPeridotShovel:ItemGemShovel = scala.compiletime.uninitialized
    var itemRubySword:ItemGemSword = scala.compiletime.uninitialized
    var itemSapphireSword:ItemGemSword = scala.compiletime.uninitialized
    var itemPeridotSword:ItemGemSword = scala.compiletime.uninitialized
    var itemGoldSaw:ItemGemSaw = scala.compiletime.uninitialized
    var itemRubySaw:ItemGemSaw = scala.compiletime.uninitialized
    var itemSapphireSaw:ItemGemSaw = scala.compiletime.uninitialized
    var itemPeridotSaw:ItemGemSaw = scala.compiletime.uninitialized
    var itemWoodSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemStoneSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemIronSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemGoldSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemRubySickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemSapphireSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemPeridotSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemDiamondSickle:ItemGemSickle = scala.compiletime.uninitialized
    var itemRubyHelmet:ItemGemArmor = scala.compiletime.uninitialized
    var itemRubyChestplate:ItemGemArmor = scala.compiletime.uninitialized
    var itemRubyLeggings:ItemGemArmor = scala.compiletime.uninitialized
    var itemRubyBoots:ItemGemArmor = scala.compiletime.uninitialized
    var itemSapphireHelmet:ItemGemArmor = scala.compiletime.uninitialized
    var itemSapphireChestplate:ItemGemArmor = scala.compiletime.uninitialized
    var itemSapphireLeggings:ItemGemArmor = scala.compiletime.uninitialized
    var itemSapphireBoots:ItemGemArmor = scala.compiletime.uninitialized
    var itemPeridotHelmet:ItemGemArmor = scala.compiletime.uninitialized
    var itemPeridotChestplate:ItemGemArmor = scala.compiletime.uninitialized
    var itemPeridotLeggings:ItemGemArmor = scala.compiletime.uninitialized
    var itemPeridotBoots:ItemGemArmor = scala.compiletime.uninitialized

    val tabExploration:CreativeTabs = new CreativeTabs("projectred.exploration")
    :
        override def createIcon = new ItemStack(Blocks.GRASS)

    @Mod.EventHandler
    def preInit(event: FMLPreInitializationEvent): Unit =
        SimpleGenHandler.init()
        ExplorationProxy.preinit()

    @Mod.EventHandler
    def init(event: FMLInitializationEvent): Unit =
        ExplorationProxy.init()

    @Mod.EventHandler
    def postInit(event: FMLPostInitializationEvent): Unit =
        ExplorationProxy.postinit()
