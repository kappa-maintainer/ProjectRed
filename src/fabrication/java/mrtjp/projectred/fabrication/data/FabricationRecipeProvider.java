package mrtjp.projectred.fabrication.data;

import codechicken.lib.datagen.recipe.RecipeProvider;
import mrtjp.projectred.core.CoreContent;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import net.minecraft.block.Blocks;
import net.minecraft.data.DataGenerator;
import net.minecraft.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraftforge.common.Tags;

import static mrtjp.projectred.fabrication.init.FabricationReferences.*;
public class FabricationRecipeProvider extends RecipeProvider {

    public FabricationRecipeProvider(DataGenerator dataGenerator) {
        super(dataGenerator);
    }

    @Override
    public String getName() {
        return "ProjectRed-Fabrication Recipes";
    }

    @Override
    protected void registerRecipes() {

        shapedRecipe(IC_WORKBENCH_BLOCK)
                .key('s', Blocks.STONE)
                .key('w', ItemTags.PLANKS)
                .key('b', IC_BLUEPRINT_ITEM)
                .patternLine("sss")
                .patternLine("wbw")
                .patternLine("www");

        shapedRecipe(PLOTTING_TABLE_BLOCK)
                .key('g', Blocks.GLASS)
                .key('t', CoreContent.tagGemsSapphire())
                .key('s', Blocks.STONE)
                .key('p', BLANK_PHOTOMASK_ITEM)
                .key('w', ItemTags.PLANKS)
                .key('b', CoreContent.itemElectrotineIngot().get())
                .patternLine("gtg")
                .patternLine("sps")
                .patternLine("wbw");

        shapedRecipe(LITHOGRAPHY_TABLE_BLOCK)
                .key('g', Blocks.GLASS)
                .key('t', Tags.Items.GEMS_EMERALD)
                .key('o', Tags.Items.OBSIDIAN)
                .key('i', Tags.Items.INGOTS_IRON)
                .key('w', ItemTags.PLANKS)
                .key('b', CoreContent.itemElectrotineIngot().get())
                .patternLine("gtg")
                .patternLine("oio")
                .patternLine("wbw");

        shapedRecipe(PACKAGING_TABLE_BLOCK)
                .key('g', Blocks.GLASS)
                .key('t', Tags.Items.DUSTS_REDSTONE)
                .key('p', CoreContent.itemPlate().get())
                .key('w', ItemTags.PLANKS)
                .key('b', CoreContent.itemElectrotineIngot().get())
                .patternLine("gtg")
                .patternLine("ppp")
                .patternLine("wbw");

        shapedRecipe(IC_BLUEPRINT_ITEM)
                .key('p', Items.PAPER)
                .key('b', Tags.Items.DYES_BLUE)
                .key('r', Tags.Items.DUSTS_REDSTONE)
                .patternLine("pbp")
                .patternLine("brb")
                .patternLine("pbp");

        shapedRecipe(BLANK_PHOTOMASK_ITEM)
                .key('g', Tags.Items.GLASS_PANES)
                .key('q', Tags.Items.GEMS_QUARTZ)
                .patternLine("ggg")
                .patternLine("gqg")
                .patternLine("ggg");

        smelting(ROUGH_SILICON_WAFER_ITEM)
                .ingredient(CoreContent.itemSilicon().get());
    }
}
