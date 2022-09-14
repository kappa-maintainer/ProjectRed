package mrtjp.projectred.fabrication.block;

import mrtjp.projectred.fabrication.tile.PackagingTableTile;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

public class PackagingTableBlock extends FabricationMachineBlock {

    public PackagingTableBlock() {
        super(AbstractBlock.Properties.of(Material.STONE));
    }

    @Override
    protected TileEntity createTileEntityInstance(BlockState state, IBlockReader world) {
        return new PackagingTableTile();
    }
}
