package mrtjp.projectred.fabrication.tile;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.util.ServerUtils;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import mrtjp.projectred.fabrication.inventory.container.PlottingTableContainer;
import mrtjp.projectred.fabrication.item.BlankPhotomaskItem;
import mrtjp.projectred.fabrication.item.ICBlueprintItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlottingTableTile extends FabricationMachineTile {

    private final Inventory inventory = new Inventory(3) {

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            switch (slot) {
                case 0:
                    return stack.getItem() instanceof ICBlueprintItem;
                case 1:
                    return stack.getItem() instanceof BlankPhotomaskItem;
                default:
                    return false;
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
            cancelWorkIfNeeded();
        }
    };

    public PlottingTableTile() {
        super(FabricationReferences.PLOTTING_TABLE_TILE);
    }

    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void saveToNBT(CompoundNBT tag) {
        super.saveToNBT(tag);
        tag.put("inventory", inventory.createTag());
    }

    @Override
    public void loadFromNBT(CompoundNBT tag) {
        super.loadFromNBT(tag);
        inventory.fromTag(tag.getList("inventory", 10));
    }

    @Override
    public void writeDesc(MCDataOutput out) {
    }

    @Override
    public void readDesc(MCDataInput in) {
    }


    @Override
    public ActionResultType onBlockActivated(PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!getLevel().isClientSide) {
            ServerUtils.openContainer((ServerPlayerEntity) player,
                    new SimpleNamedContainerProvider((id, inv, p) -> new PlottingTableContainer(inv, this, id), new TranslationTextComponent(getBlockState().getBlock().getDescriptionId())),
                    p -> p.writePos(getBlockPos()));
        }

        return ActionResultType.SUCCESS;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return super.getCapability(cap, side); //TODO add capabilities
    }

    @Override
    protected boolean canStartWork() {

        ItemStack slot0 = inventory.getItem(0);
        ItemStack slot1 = inventory.getItem(1);

        if (slot0.isEmpty() || slot1.isEmpty()) {
            return false;
        }

        if (!(slot0.getItem() instanceof ICBlueprintItem)) return false;

        if (!(slot1.getItem() instanceof BlankPhotomaskItem)) return false;

        return inventory.getItem(2).isEmpty();
    }

    @Override
    protected int startWork() {
        return 20 * 10; // 10 seconds worth of work
    }

    @Override
    protected int tickWork(int remainingWork) {
        if (canConductorWork()) {
            conductor.applyPower(-1100); // draw at rate of 1.1kW
            return 1;
        }
        return 0;
    }

    @Override
    protected void finishWork() {
        ItemStack output = new ItemStack(FabricationReferences.PHOTOMASK_SET_ITEM);
        output.setTag(inventory.getItem(0).getTag().copy()); // copy the blueprint tag
        inventory.setItem(2, output);
        inventory.removeItem(1, 1); // delete 1 blank photomask
    }
}
