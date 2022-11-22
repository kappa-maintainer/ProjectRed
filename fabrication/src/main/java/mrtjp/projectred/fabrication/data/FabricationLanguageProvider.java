package mrtjp.projectred.fabrication.data;

import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.LanguageProvider;

import static mrtjp.projectred.fabrication.ProjectRedFabrication.MOD_ID;
import static mrtjp.projectred.fabrication.init.FabricationReferences.*;
import static mrtjp.projectred.fabrication.init.FabricationUnlocal.*;

public class FabricationLanguageProvider extends LanguageProvider {

    public FabricationLanguageProvider(DataGenerator gen) {
        super(gen, MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {

        add(IC_WORKBENCH_BLOCK, "IC Workbench");
        add(PLOTTING_TABLE_BLOCK, "Plotting Table");
        add(LITHOGRAPHY_TABLE_BLOCK, "Lithography Table");
        add(PACKAGING_TABLE_BLOCK, "Packaging Table");

        add(IC_BLUEPRINT_ITEM, "IC Blueprint");
        add(BLANK_PHOTOMASK_ITEM, "Blank Photomask");
        add(PHOTOMASK_SET_ITEM, "Photomask Set");
        add(ROUGH_SILICON_WAFER_ITEM, "Rough Silicon Wafer");
        add(ETCHED_SILICON_WAFER_ITEM, "Etched Silicon Wafer");
        add(VALID_DIE_ITEM, "Valid Die");
        add(INVALID_DIE_ITEM, "Invalid Die");

        add(FABRICATED_GATE_ITEM, "Fabricated Gate");

        add(UL_TAB_INFO, "Info");
        add(UL_TAB_EDIT, "Edit");
        add(UL_TAB_COMPILE, "Compile");
        add(UL_TAB_STACK, "Stack");
        add(UL_TAB_TREE, "Tree");
        add(UL_TAB_PROBLEMS, "Problems");

        add(UL_ERASER_TOOL, "Erase");
        add(UL_INTERACT_TOOL, "Interact");
        add(UL_WIRE_TOOL, "Wires");
        add(UL_GATE_TOOL, "Gates");

        add(UL_TOGGLE_STATE, "Toggle state");
        add(UL_TOGGLE_IO_MODE, "Toggle IO mode");
        add(UL_IO_MODE_INPUT, "Input");
        add(UL_IO_MODE_OUTPUT, "Output");
        add(UL_TOGGLE_COLOUR, "Toggle colour");
        add(UL_TOGGLE_DELAY, "Toggle delay");
        add(UL_SIDE_ENABLED, "Side enabled");
        add(UL_SIDE_DISABLED, "Side disabled");
        add(UL_PLACE_BLUEPRINT, "Place blueprint");

        add(UL_BLUEPRINT_INFO, "Blueprint Info");
        add(UL_BLUEPRINT_NAME, "Name");
        add(UL_BLUEPRINT_OWNER, "Owner");
        add(UL_BLUEPRINT_DIM, "Dimensions");
        add(UL_BLUEPRINT_LAYERS, "Layers");

        add(UL_YIELD_CALCULATOR, "Yield Calculator");
        add(UL_LITHOGRAPHY_PIPELINE, "Lithography pipeline");
        add(UL_PROCESS_NODE, "Process node");
        add(UL_WAFER_TYPE, "Wafer type");
        add(UL_DIE_SIZE, "Die size");
        add(UL_WAFER_SIZE, "Wafer size");
        add(UL_DIES_PER_WAFER, "Dies per wafer");
        add(UL_SINGLE_LAYER_YIELD, "Single layer yield");
        add(UL_YIELD, "Yield");

        add(UL_SIZE, "Size");
        add(UL_DEFECT_CHANCE, "Defect chance");
        add(UL_CORRUPTED_DISCARD, "Corrupted NBT data, please discard");
        add(UL_NAME, "Name");
        add(UL_TILE_COUNT, "Tile count");
        add(UL_IO_TYPES, "IO types");
        add(UL_TOP, "Top");
        add(UL_RIGHT, "Right");
        add(UL_BOTTOM, "Bottom");
        add(UL_LEFT, "Left");
        add(UL_BUNDLED_INPUT, "Bundled input");
        add(UL_BUNDLED_OUTPUT, "Bundled output");
        add(UL_IO_NONE, "None");

        add(UL_IO_GATE_TILE, "IO Gate");

        add(UL_TILEGROUP_REDWIRE, "Redwire");
        add(UL_TILEGROUP_BUNDLED, "Bundled");
        add(UL_TILEGROUP_IO, "IO");
        add(UL_TILEGROUP_BASIC, "Basic");
        add(UL_TILEGROUP_TIMING, "Timing");
        add(UL_TILEGROUP_MEMORY, "Memory");

        add(UL_UNIT_TICKS, "%d ticks");
        add(UL_DIMENSIONS_NM, "%d nm x %d nm");
        add(UL_DIMENSIONS_TILES, "%d tiles x %d tiles");
        add(UL_DIMENSIONS_DIES, "%d dies x %d dies");
        add(UL_DIMENSIONS_NM_TOTAL, "%d nm x %d nm (%d nm^2)");
        add(UL_DIMENSIONS_TILES_TOTAL, "%d tiles x %d tiles (%d tiles^2)");
        add(UL_DIMENSIONS_DIES_TOTAL, "%d dies x %d dies (%d dies^2)");
    }
}
