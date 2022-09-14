package mrtjp.projectred.fabrication.block;

import codechicken.lib.vec.Rotation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;

import javax.annotation.Nullable;

public abstract class FabricationMachineBlock extends FabricationBaseBlock {

    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    protected static final VoxelShape BOTTOM_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D);

    public FabricationMachineBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        int r = Rotation.rotationTo(0, context.getHorizontalDirection().ordinal());
        return this.defaultBlockState()
                .setValue(ROTATION_PROPERTY, r)
                .setValue(CHARGED, false)
                .setValue(WORKING, false);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(ROTATION_PROPERTY);
        builder.add(CHARGED);
        builder.add(WORKING);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, IBlockReader world, BlockPos pos) {
        return BOTTOM_AABB;
    }
}
