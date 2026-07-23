package mrtjp.projectred.expansion

import mezz.jei.api.*
import mezz.jei.api.recipe.VanillaRecipeCategoryUid.*
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo
import mrtjp.projectred.ProjectRedExpansion.*
import net.minecraft.item.ItemStack

import scala.jdk.CollectionConverters.*

@JEIPlugin
class ExpansionJEIPlugin extends IModPlugin
{
    override def register(registry:IModRegistry): Unit =
    {
        //Inductive furnace
        registry.addRecipeCatalyst(new ItemStack(machine1, 1, 0), SMELTING)
        registry.addRecipeClickArea(classOf[GuiInductiveFurnace], 76, 36, 30, 23, SMELTING)
        registry.getRecipeTransferRegistry.addRecipeTransferHandler(classOf[ContainerFurnace], SMELTING, 0, 1, 2, 36)

        //Project bench
        registry.addRecipeCatalyst(new ItemStack(machine2, 1, 10), CRAFTING)
        registry.addRecipeClickArea(classOf[GuiProjectBench], 104, 32, 30, 23, CRAFTING)
        registry.getRecipeTransferRegistry.addRecipeTransferHandler(ProjectBenchRecipeTransferInfo)

        //Auto crafting table
        registry.addRecipeCatalyst(new ItemStack(machine2, 1, 11), CRAFTING) //auto crafting bench
    }

    override def onRuntimeAvailable(jeiRuntime:IJeiRuntime): Unit ={}
}

object ProjectBenchRecipeTransferInfo extends IRecipeTransferInfo[ContainerProjectBench]
{
    override def getRecipeCategoryUid = CRAFTING

    override def getContainerClass = classOf[ContainerProjectBench]

    override def getRecipeSlots(container:ContainerProjectBench) = (0 until 9 map container.getSlot).asJava

    override def getInventorySlots(container:ContainerProjectBench) = ((9 until 27) ++ (29 until 65) map container.getSlot).asJava

    override def canHandle(container: ContainerProjectBench) = true//TODO??
}
