package mrtjp.projectred.transportation

import java.lang.{Character as JC}

import mrtjp.projectred.transportation.RoutingChipDefs.ChipVal
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.world.World
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.registries.IForgeRegistryEntry

class ChipResetRecipe extends IForgeRegistryEntry.Impl[IRecipe] with IRecipe
{
    override def matches(inv:InventoryCrafting, world:World) = !getCraftingResult(inv).isEmpty

    override def getCraftingResult(inv:InventoryCrafting):ItemStack =
    {
        val cdef = getType(inv)
        if cdef != null then if isTypeExclusive(cdef, inv) then return cdef.makeStack(countUnits(inv))
        ItemStack.EMPTY
    }

    def getType(inv:InventoryCrafting):ChipVal =
    {
        for i <- 0 until inv.getSizeInventory do
        {
            val cdef = RoutingChipDefs.getForStack(inv.getStackInSlot(i))
            if cdef != null then return cdef
        }
        null
    }

    def isTypeExclusive(cdef:ChipVal, inv:InventoryCrafting):Boolean =
    {
        for i <- 0 until inv.getSizeInventory do
        {
            val stack = inv.getStackInSlot(i)
            if !stack.isEmpty && !stack.getItem.isInstanceOf[ItemRoutingChip] then return false
            val type2 = RoutingChipDefs.getForStack(stack)
            if type2 != null && !(type2 == cdef) then return false
        }
        true
    }

    def countUnits(inv:InventoryCrafting):Int =
    {
        var count = 0
        for i <- 0 until inv.getSizeInventory do
            if !inv.getStackInSlot(i).isEmpty then count += 1
        count
    }

    override def canFit(width: Int, height: Int) = width * height >= 2
    def getRecipeOutput = ItemStack.EMPTY
    override def isDynamic: Boolean = true
}

object ChipResetRecipe
{
    @SubscribeEvent
    def registerRecipes(event: RegistryEvent.Register[IRecipe]): Unit =
    {
        event.getRegistry.register(new ChipResetRecipe().setRegistryName("chip_reset"))
    }
}
