package mrtjp.projectred.fabrication.gui.screen.inventory;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.core.gui.GuiLib$;
import mrtjp.core.vec.Point;
import mrtjp.projectred.ProjectRedFabrication;
import mrtjp.projectred.fabrication.inventory.container.PlottingTableContainer;
import mrtjp.projectred.redui.RedUIContainerScreen;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class PlottingTableScreen extends RedUIContainerScreen<PlottingTableContainer> implements IHasContainer<PlottingTableContainer> {

    public static final ResourceLocation BACKGROUND = new ResourceLocation(ProjectRedFabrication.MOD_ID, "textures/gui/plotting_table.png");

    public PlottingTableScreen(PlottingTableContainer container, PlayerInventory playerInventory, ITextComponent title) {
        super(176, 171, container, playerInventory, title); //TODO size

        inventoryLabelX = 8;
        inventoryLabelY = 79;
    }

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
        super.drawBack(stack, mouse, partialFrame);

        TextureUtils.changeTexture(BACKGROUND);
        int x = getFrame().x();
        int y = getFrame().y();

        blit(stack, x, y, 0, 0, getFrame().width(), getFrame().height());

        int s = getMenu().getProgressScaled(24);
        blit(stack, x + 80, y + 40, 176, 0, s + 1, 16);

        if (getMenu().canConductorWork())
            blit(stack, x + 16, y + 16, 177, 18, 7, 9);

        GuiLib$.MODULE$.drawVerticalTank(stack, this, x + 16, y + 26, 177, 27, 7, 48, getMenu().getChargeScaled(48));

        if (getMenu().isFlowFull())
            blit(stack, x + 27, y + 16, 185, 18, 7, 9);

        GuiLib$.MODULE$.drawVerticalTank(stack, this, x + 27, y + 26, 185, 27, 7, 48, getMenu().getFlowScaled(48));
    }
}
