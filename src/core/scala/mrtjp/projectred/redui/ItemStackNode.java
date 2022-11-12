package mrtjp.projectred.redui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mrtjp.core.vec.Point;
import mrtjp.core.vec.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;

import java.util.LinkedList;
import java.util.List;

public class ItemStackNode extends AbstractGuiNode {

    private ItemStack itemStack;

    public ItemStackNode(ItemStack itemStack) {
        this.itemStack = itemStack;
        setSize(16, 16);
    }

    public ItemStackNode() {
        this(ItemStack.EMPTY);
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {

        // Would be nice if renderGuiItem can take the matrix stack...
        Point screenPos = convertParentPointToScreen(getPosition());
        getRoot().getItemRenderer().renderGuiItem(itemStack, screenPos.x(), screenPos.y());

        if (isFirstHit(mouse)) {
            int slotColor = -2130706433;
            RenderSystem.disableDepthTest();
            RenderSystem.colorMask(true, true, true, false);
            Rect frame = getFrame();
            getRoot().fillGradient(stack, frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), slotColor, slotColor);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
        }
    }

    @Override
    public void drawFront(MatrixStack stack, Point mouse, float partialFrame) {
        if (isFirstHit(mouse)) {
            Minecraft minecraft = getRoot().getMinecraft();
            List<ITextComponent> tooltip = itemStack.getTooltipLines(minecraft.player, minecraft.options.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
            renderTooltip(stack, mouse, tooltip);
        }
    }
}
