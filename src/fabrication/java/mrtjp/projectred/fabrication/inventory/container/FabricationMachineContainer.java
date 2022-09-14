package mrtjp.projectred.fabrication.inventory.container;

import mrtjp.projectred.fabrication.tile.FabricationMachineTile;
import mrtjp.projectred.core.inventory.container.BasePoweredTileContainer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.IContainerListener;
import net.minecraft.inventory.container.Slot;

import javax.annotation.Nullable;

public class FabricationMachineContainer extends BasePoweredTileContainer {

    private final FabricationMachineTile tile;

    private int remainingWork;
    private int totalWork;

    public FabricationMachineContainer(@Nullable ContainerType<?> containerType, int windowId, FabricationMachineTile tile) {
        super(containerType, windowId, tile);
        this.tile = tile;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        boolean needsRemainingWork = remainingWork != tile.getRemainingWork();
        boolean needsTotalWork = totalWork != tile.getTotalWork();

        remainingWork = tile.getRemainingWork();
        totalWork = tile.getTotalWork();

        for (IContainerListener listener : containerListeners) {
            if (needsRemainingWork) {
                listener.setContainerData(this, 110, remainingWork);
            }
            if (needsTotalWork) {
                listener.setContainerData(this, 111, totalWork);
            }
        }
    }

    @Override
    public void setData(int id, int value) {
        switch (id) {
            case 110:
                remainingWork = value;
                break;
            case 111:
                totalWork = value;
                break;
            default:
                super.setData(id, value);
        }
    }

    public int getProgressScaled(int scale) {
        return totalWork == 0 ? 0 : scale * (totalWork - remainingWork) / totalWork;
    }

    protected void addPlayerInventory(PlayerInventory playerInventory, int x, int y) {
        addInventory(playerInventory, 9, x, y, 9, 3); // Inventory (0 - 26)
        addInventory(playerInventory, 0, x, y + 58, 9, 1); // Hotbar slots (27 - 35)
    }

    protected void addInventory(IInventory inventory, int i, int x, int y, int columns, int rows) {
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                addSlot(new Slot(inventory, i + (r * columns + c), x + c * 18, y + r * 18));
            }
        }
    }
}
