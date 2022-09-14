package mrtjp.projectred.fabrication.tile;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.util.ServerUtils;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import mrtjp.projectred.fabrication.inventory.container.PackagingTableContainer;
import mrtjp.projectred.fabrication.item.ValidDieItem;
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

public class PackagingTableTile extends FabricationMachineTile {

    private final Inventory inventory = new Inventory(5) {

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            switch (slot) {
                case 0:
                    return stack.getItem() instanceof ValidDieItem;
                case 1:
                case 2:
                case 3:
                    //TODO
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
    public PackagingTableTile() {
        super(FabricationReferences.PACKAGING_TABLE_TILE);
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
                    new SimpleNamedContainerProvider((id, inv, p) -> new PackagingTableContainer(inv, this, id), new TranslationTextComponent(getBlockState().getBlock().getDescriptionId())),
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

        if (slot0.isEmpty()) return false;

        if (!(slot0.getItem() instanceof ValidDieItem)) return false;

        return inventory.getItem(4).isEmpty(); //TODO or output can be stackable (made from same blueprint)
    }

    @Override
    protected int startWork() {
        return 20 * 20; // 20 seconds worth of work
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
        ItemStack gatePart = ValidDieItem.createGatePart(inventory.getItem(0));
        inventory.setItem(4, gatePart); //TODO or stack

        // Consume inputs
        inventory.removeItem(0, 1);
    }
}
