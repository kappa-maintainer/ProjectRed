package mrtjp.projectred.fabrication.init;

import codechicken.multipart.api.MultiPartType;
import mrtjp.projectred.fabrication.block.FabricationBaseBlock;
import mrtjp.projectred.fabrication.block.LithographyTableBlock;
import mrtjp.projectred.fabrication.block.PackagingTableBlock;
import mrtjp.projectred.fabrication.block.PlottingTableBlock;
import mrtjp.projectred.fabrication.inventory.container.LithographyTableContainer;
import mrtjp.projectred.fabrication.inventory.container.PackagingTableContainer;
import mrtjp.projectred.fabrication.inventory.container.PlottingTableContainer;
import mrtjp.projectred.fabrication.item.*;
import mrtjp.projectred.fabrication.tile.ICWorkbenchTile;
import mrtjp.projectred.fabrication.tile.LithographyTableTile;
import mrtjp.projectred.fabrication.tile.PackagingTableTile;
import mrtjp.projectred.fabrication.tile.PlottingTableTile;
import mrtjp.projectred.integration.ItemPartGate;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.registries.ObjectHolder;

import static mrtjp.projectred.ProjectRedFabrication.MOD_ID;
import static mrtjp.projectred.fabrication.init.FabricationBlocks.*;
import static mrtjp.projectred.fabrication.init.FabricationItems.*;
import static mrtjp.projectred.fabrication.init.FabricationParts.ID_FABRICATED_GATE;

@ObjectHolder(MOD_ID)
public class FabricationReferences {

    // Blocks
    @ObjectHolder(ID_IC_WORKBENCH)
    public static FabricationBaseBlock IC_WORKBENCH_BLOCK = null;
    @ObjectHolder(ID_PLOTTING_TABLE)
    public static PlottingTableBlock PLOTTING_TABLE_BLOCK = null;
    @ObjectHolder(ID_LITHOGRAPHY_TABLE)
    public static LithographyTableBlock LITHOGRAPHY_TABLE_BLOCK = null;
    @ObjectHolder(ID_PACKAGING_TABLE)
    public static PackagingTableBlock PACKAGING_TABLE_BLOCK = null;

    // Tiles
    @ObjectHolder(ID_IC_WORKBENCH)
    public static TileEntityType<ICWorkbenchTile> IC_WORKBENCH_TILE = null;
    @ObjectHolder(ID_PLOTTING_TABLE)
    public static TileEntityType<PlottingTableTile> PLOTTING_TABLE_TILE = null;
    @ObjectHolder(ID_LITHOGRAPHY_TABLE)
    public static TileEntityType<LithographyTableTile> LITHOGRAPHY_TABLE_TILE = null;
    @ObjectHolder(ID_PACKAGING_TABLE)
    public static TileEntityType<PackagingTableTile> PACKAGING_TABLE_TILE = null;

    // Containers
    @ObjectHolder(ID_PLOTTING_TABLE)
    public static ContainerType<PlottingTableContainer> PLOTTING_TABLE_CONTAINER = null;
    @ObjectHolder(ID_LITHOGRAPHY_TABLE)
    public static ContainerType<LithographyTableContainer> LITHOGRAPHY_TABLE_CONTAINER = null;
    @ObjectHolder(ID_PACKAGING_TABLE)
    public static ContainerType<PackagingTableContainer> PACKAGING_TABLE_CONTAINER = null;

    // Items
    @ObjectHolder(ID_IC_BLUEPRINT)
    public static ICBlueprintItem IC_BLUEPRINT_ITEM = null;
    @ObjectHolder(ID_BLANK_PHOTOMASK)
    public static BlankPhotomaskItem BLANK_PHOTOMASK_ITEM = null;
    @ObjectHolder(ID_PHOTOMASK_SET)
    public static PhotomaskSetItem PHOTOMASK_SET_ITEM = null;
    @ObjectHolder(ID_ROUGH_SILICON_WAFER)
    public static RoughSiliconWaferItem ROUGH_SILICON_WAFER_ITEM = null;
    @ObjectHolder(ID_ETCHED_SILICON_WAFER)
    public static EtchedSiliconWaferItem ETCHED_SILICON_WAFER_ITEM = null;
    @ObjectHolder(ID_VALID_DIE)
    public static ValidDieItem VALID_DIE_ITEM = null;
    @ObjectHolder(ID_INVALID_DIE)
    public static InvalidDieItem INVALID_DIE_ITEM = null;

    // Parts
    @ObjectHolder(ID_FABRICATED_GATE)
    public static ItemPartGate FABRICATED_GATE_ITEM = null;
    @ObjectHolder(ID_FABRICATED_GATE)
    public static MultiPartType<?> FABRICATED_GATE_PART = null;
}
