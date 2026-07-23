package mrtjp.projectred.fabrication

import mrtjp.projectred.ProjectRedFabrication.*
import mrtjp.projectred.core.PartDefs
import mrtjp.projectred.integration.GateDefinition
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.util.NonNullList
import net.minecraft.world.World
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.registries.IForgeRegistryEntry

object FabricationRecipes
{
    @SubscribeEvent
    def registerRecipes(event: RegistryEvent.Register[IRecipe]): Unit =
    {
        event.getRegistry.register(new ICBlueprintResetRecipe().setRegistryName("ic_blueprint_reset"))
        event.getRegistry.register(new ICBlueprintCopyRecipe().setRegistryName("ic_blueprint_copy"))
        event.getRegistry.register(new ICGateRecipe().setRegistryName("ic_gate"))
    }
}

class ICBlueprintResetRecipe extends TBaseRecipe {
    override def getRecipeSize = 2
    override def getRecipeOutput = new ItemStack(itemICBlueprint)

    override def getCraftingResult(inv:InventoryCrafting):ItemStack =
    {
        val inputs = (0 until inv.getSizeInventory).map(inv.getStackInSlot).filter(!_.isEmpty)
        if inputs.size == 1 && inputs.head.getItem == itemICBlueprint && ItemICBlueprint.hasICInside(inputs.head) then
            new ItemStack(itemICBlueprint)
        else ItemStack.EMPTY
    }
}

class ICBlueprintCopyRecipe extends TBaseRecipe {
    override def getRecipeOutput = new ItemStack(itemICBlueprint, 2)
    override def getRecipeSize = 2
    override def getCraftingResult(inv:InventoryCrafting):ItemStack =
    {
        var bp:ItemStack = ItemStack.EMPTY
        var emptyCount = 0
        var valid = true
        for i <- 0 until inv.getSizeInventory do {
            val s = inv.getStackInSlot(i)
            if !s.isEmpty then {
                if s.getItem != itemICBlueprint then valid = false
                else if ItemICBlueprint.hasICInside(s) then
                    if !bp.isEmpty then valid = false else bp = s
                else emptyCount += 1
            }
        }
        if valid && !bp.isEmpty && emptyCount == 1 then {
            val out = new ItemStack(itemICBlueprint)
            out.setCount(emptyCount)
            ItemICBlueprint.copyIC(bp, out)
            out
        }
        else ItemStack.EMPTY
    }

    override def getRemainingItems(inv:InventoryCrafting):NonNullList[ItemStack] = {
        val remaining = NonNullList.withSize(inv.getSizeInventory, ItemStack.EMPTY)
        (0 until inv.getSizeInventory).find { i =>
            val s = inv.getStackInSlot(i)
            !s.isEmpty && s.getItem == itemICBlueprint && ItemICBlueprint.hasICInside(s)
        }.foreach(i => remaining.set(i, inv.getStackInSlot(i).copy))
        remaining
    }
}

class ICGateRecipe extends TBaseRecipe {
    override def getRecipeOutput = GateDefinition.ICGate.makeStack
    override def getRecipeSize = 9
    override def getCraftingResult(inv:InventoryCrafting):ItemStack =
    {
        val valid = (0 until 9).forall { i =>
            val stack = inv.getStackInSlot(i)
            if stack.isEmpty then false
            else i match {
                case 4 => stack.getItem == itemICChip && ItemICBlueprint.hasICInside(stack)
                case _ => stack.isItemEqual(PartDefs.PLATE.makeStack)
            }
        }
        if !valid then ItemStack.EMPTY
        else {
        val out = GateDefinition.ICGate.makeStack
        ItemICBlueprint.copyToGate(inv.getStackInSlot(4), out)
        out
        }
    }
}

trait TBaseRecipe extends IForgeRegistryEntry.Impl[IRecipe] with IRecipe {
    def getRecipeSize:Int
    override def canFit(width: Int, height: Int) = width * height >= getRecipeSize
    override def matches(inv: InventoryCrafting, worldIn: World) = !getCraftingResult(inv).isEmpty
    override def isDynamic: Boolean = true
}
