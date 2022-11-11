package mrtjp.projectred.fabrication.block;

import mrtjp.projectred.fabrication.tile.LithographyTableTile;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

public class LithographyTableBlock extends FabricationMachineBlock {

    public LithographyTableBlock() {
        super(AbstractBlock.Properties.of(Material.STONE));
    }

    @Override
    protected TileEntity createTileEntityInstance(BlockState state, IBlockReader world) {
        return new LithographyTableTile();
    }
}