package mrtjp.projectred.fabrication.block;

import mrtjp.projectred.fabrication.tile.ICWorkbenchTile;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

import javax.annotation.Nullable;

public class ICWorkbenchBlock extends FabricationBaseBlock {

    public static final BooleanProperty BLUEPRINT_PROPERTY = BooleanProperty.create("blueprint");

    public ICWorkbenchBlock() {
        super(AbstractBlock.Properties.of(Material.STONE));
    }

    @Override
    protected TileEntity createTileEntityInstance(BlockState state, IBlockReader world) {
        return new ICWorkbenchTile();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return defaultBlockState()
                .setValue(BLUEPRINT_PROPERTY, false);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BLUEPRINT_PROPERTY);
    }
}
