package mrtjp.projectred.expansion

import mrtjp.core.item.ItemKeyStack
import mrtjp.projectred.ProjectRedCore
import mrtjp.projectred.core.*
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraft.item.{ItemFood, ItemStack}
import net.minecraftforge.oredict.OreDictionary

object InductiveFurnaceRecipeLib
{
    var recipes = IndexedSeq[InductiveFurnaceRecipe]()

    def getRecipeFor(in:ItemStack):InductiveFurnaceRecipe =
    {
        val key = ItemKeyStack.get(in)
        for r <- recipes do if r.in.matches(key) then return r
        null
    }

    def getRecipeOf(out:ItemStack):InductiveFurnaceRecipe =
    {
        val key = ItemKeyStack.get(out)
        for r <- recipes do if r.out.matches(key) then return r
        null
    }

    def addRecipe(in:ItemStack, out:ItemStack, ticks:Int): Unit =
    {
        recipes :+= InductiveFurnaceRecipe(new ItemIn(in), new ItemOut(out), ticks)
    }

    def addOreRecipe(in:ItemStack, out:ItemStack, ticks:Int): Unit =
    {
        recipes :+= InductiveFurnaceRecipe(new OreIn(in), new ItemOut(out), ticks)
    }

    def addOreRecipe(in:String, out:ItemStack, ticks:Int): Unit =
    {
        recipes :+= InductiveFurnaceRecipe(new OreIn(in), new ItemOut(out), ticks)
    }

    def init(): Unit =
    {
        import scala.jdk.CollectionConverters.*

        def isDust(stack:ItemStack) = getOreName(stack).startsWith("dust")
        def isIngot(stack:ItemStack) = getOreName(stack).startsWith("ingot")
        def getOreName(stack:ItemStack) = {
            val IDs = OreDictionary.getOreIDs(stack)
            if IDs.isEmpty then "Unknown" else OreDictionary.getOreName(IDs(0))
        }

        val sl = FurnaceRecipes.instance.getSmeltingList
        for (in, out) <- sl.asScala do try  {
            if getRecipeFor(in) == null then
            {
                if in.getItem.isInstanceOf[ItemFood] then addRecipe(in, out, 40)
                else if isDust(in) && isIngot(out) then addOreRecipe(in, out, 80*10/16)
                else addRecipe(in, out, 80)
            }
        } catch {
            case e:Exception =>
                ProjectRedCore.log.warn(s"Failed to add Inductive Furnace recipe for IN: $in, OUT: $out")
        }
    }
}

case class InductiveFurnaceRecipe(in:RecipeInput, out:RecipeOutput, burnTime:Int)
{
    def createOutput = out.createOutput
}
