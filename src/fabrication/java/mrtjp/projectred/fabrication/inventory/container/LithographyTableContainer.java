package mrtjp.projectred.fabrication.inventory.container;

import codechicken.lib.inventory.container.ICCLContainerFactory;
import mrtjp.projectred.core.inventory.container.TakeOnlySlot;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import mrtjp.projectred.fabrication.item.PhotomaskSetItem;
import mrtjp.projectred.fabrication.item.RoughSiliconWaferItem;
import mrtjp.projectred.fabrication.tile.LithographyTableTile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class LithographyTableContainer extends FabricationMachineContainer {

    public static ICCLContainerFactory<LithographyTableContainer> FACTORY = (windowId, inventory, packet) -> {
        TileEntity tile = inventory.player.level.getBlockEntity(packet.readPos());
        if (!(tile instanceof LithographyTableTile)) return null;

        return new LithographyTableContainer(inventory, (LithographyTableTile) tile, windowId);
    };

    private final PlayerInventory playerInventory;
    private final LithographyTableTile tile;

    public LithographyTableContainer(PlayerInventory playerInventory, LithographyTableTile tile, int windowId) {
        super(FabricationReferences.LITHOGRAPHY_TABLE_CONTAINER, windowId, tile);
        this.playerInventory = playerInventory;
        this.tile = tile;

        addPlayerInventory(playerInventory, 8, 89);
        addLithographyTableInventory();
    }

    private void addLithographyTableInventory() {
        addSlot(new Slot(tile.getInventory(), 0, 56, 31)); // photomask set input
        addSlot(new Slot(tile.getInventory(), 1, 56, 49)); // wafer input
        addSlot(new TakeOnlySlot(tile.getInventory(), 2, 110, 31)); // valid die output
        addSlot(new TakeOnlySlot(tile.getInventory(), 3, 110, 49)); // invalid die output
    }

    public ItemStack quickMoveStack(PlayerEntity player, int slotIndex) {

        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack originalStack = stack.copy();

        if (isOutputs(slotIndex)) {
            if (!moveToEntireInventory(stack, true)) return ItemStack.EMPTY;

        } else if (isHotbar(slotIndex) || isPlayerInventory(slotIndex)) {
            if (isPhotomaskSet(stack)) {
                if (!moveToPhotomaskInput(stack, false)) return ItemStack.EMPTY;

            } else if (isSiliconWafer(stack)) {
                if (!moveToWaferInput(stack, false)) return ItemStack.EMPTY;

            } else if (isPlayerInventory(slotIndex)) {
                if (!moveToHotbar(stack, false)) return ItemStack.EMPTY;

            } else { // Hot bar
                if (!moveToPlayerInventory(stack, false)) return ItemStack.EMPTY;
            }

        } else if (isInputs(slotIndex)) {
            if (!moveToEntireInventory(stack, true)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == originalStack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);

        return originalStack;
    }

    //@formatter:off
    private boolean isHotbar(int slotIndex) {
        return slotIndex >= 27 && slotIndex < 36;
    }
    private boolean isPlayerInventory(int slotIndex) {
        return slotIndex >= 0 && slotIndex < 27;
    }
    private boolean isInputs(int slotIndex) {
        return slotIndex >= 36 && slotIndex < 38;
    }
    private boolean isOutputs(int slotIndex) {
        return slotIndex >= 38 && slotIndex < 40;
    }

    private boolean moveToHotbar(ItemStack stack, boolean reverse) {
        return moveItemStackTo(stack, 27, 36, reverse);
    }
    private boolean moveToPlayerInventory(ItemStack stack, boolean reverse) {
        return moveItemStackTo(stack, 0, 27, reverse);
    }
    private boolean moveToEntireInventory(ItemStack stack, boolean reverse) {
        return moveItemStackTo(stack, 0, 36, reverse);
    }
    private boolean moveToPhotomaskInput(ItemStack stack, boolean reverse) {
        return moveItemStackTo(stack, 36, 37, reverse);
    }
    private boolean moveToWaferInput(ItemStack stack, boolean reverse) {
        return moveItemStackTo(stack, 37, 38, reverse);
    }
    //@formatter:on

    private boolean isPhotomaskSet(ItemStack stack) {
        return stack.getItem() instanceof PhotomaskSetItem; //TODO check tag
    }

    private boolean isSiliconWafer(ItemStack stack) {
        return stack.getItem() instanceof RoughSiliconWaferItem;
    }
}
