package mrtjp.projectred.fabrication.gui;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchScreen;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.redui.AbstractGuiNode;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;

import java.util.List;

public class SimpleUVTab implements TabControllerNode.IToolbarTab {

    private final AbstractGuiNode tabBodyNode;
    private final String tabName;
    private final TabButtonNode.TabSide tabSide;

    private final int u;
    private final int v;
    private final ResourceLocation texture;

    public SimpleUVTab(AbstractGuiNode tabBodyNode, String tabName, TabButtonNode.TabSide side, int u, int v, ResourceLocation texture) { //TODO Icon width/height?
        this.tabBodyNode = tabBodyNode;
        this.tabName = tabName;
        this.tabSide = side;
        this.u = u;
        this.v = v;
        this.texture = texture;
    }

    public SimpleUVTab(AbstractGuiNode tabBodyNode, String tabName, TabButtonNode.TabSide side, int u, int v) {
        this(tabBodyNode, tabName, side, u, v, ICWorkbenchScreen.BACKGROUND);
    }

    public AbstractGuiNode getTabBodyNode() {
        return tabBodyNode;
    }

    @Override
    public void onTabClosed() {
        tabBodyNode.setHidden(true);
    }

    @Override
    public void onTabOpened() {
        tabBodyNode.setHidden(false);
    }

    @Override
    public void onTabMinimized() {
        tabBodyNode.setHidden(true);
    }

    @Override
    public TabButtonNode createButtonNode() {
        return new TabButtonNode(this, tabSide) {
            @Override
            public void renderIcon(MatrixStack stack, Point mouse, float partialFrame) {
                TextureUtils.changeTexture(texture);
                AbstractGui.blit(stack, getFrame().x() + 3, getFrame().y() + 3, u, v, 14, 14, 512, 512);
            }

            @Override
            public void buildTooltip(List<ITextProperties> tooltip) {
                tooltip.add(new StringTextComponent(tabName));
            }
        };
    }
}
