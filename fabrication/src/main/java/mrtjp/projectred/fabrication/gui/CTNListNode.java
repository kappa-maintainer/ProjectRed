package mrtjp.projectred.fabrication.gui;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mrtjp.projectred.fabrication.engine.ICCompilerLog.CompileTreeNode;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchCompileTab;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.lib.Rect;
import mrtjp.projectred.redui.AbstractGuiNode;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;

import java.util.LinkedList;
import java.util.List;

import static net.minecraft.client.gui.AbstractGui.blit;

public class CTNListNode extends AbstractGuiNode {

    private final List<CompileTreeNode> nodeList = new LinkedList<>();

    private final AbstractGuiNode listParent = new AbstractGuiNode() {

    };

    public CTNListNode() {
        initSubNodes();
    }

    private void initSubNodes() {
        addChild(listParent);
    }

    public void setNodeList(List<CompileTreeNode> nodeList) {
        this.nodeList.clear();
        this.nodeList.addAll(nodeList);
        refreshListItems();
    }

    private void refreshListItems() {
        listParent.removeAllChildren();
        listParent.setPosition(0, 0);

        int y = 0;
        for (CompileTreeNode node : nodeList) {
            CompileTreeNodeListItem item = new CompileTreeNodeListItem(node);
            item.setPosition(0, y);
            listParent.addChild(item);
            y += item.calculateAccumulatedFrame().height();
        }
    }

    @Override
    public void onSubTreePreDrawBack() {
        // This node's frame converted to GL11 window coordinates
        Rect gl11Rect = calculateGL11Frame();

        // Enable scissor using the calculated rect
        RenderSystem.enableScissor(gl11Rect.x(), gl11Rect.y(), gl11Rect.width(), gl11Rect.height());
    }

    @Override
    public void onSubTreePostDrawBack() {
        // Disable scissor
        RenderSystem.disableScissor();
    }

    private class CompileTreeNodeListItem extends AbstractGuiNode {

        private final CompileTreeNode node;

        public CompileTreeNodeListItem(CompileTreeNode node) {
            this.node = node;

            setSize(67, 16);
        }

        @Override
        public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
            TextureUtils.changeTexture(ICWorkbenchCompileTab.TAB_BACKGROUND);

            blit(stack, getFrame().x(), getFrame().y(), 1, 358, getFrame().width(), getFrame().height(), 512, 512);

            String s = node.step.toString();
            if (s.length() > 10) {
                s = s.substring(s.length() - 10);
            }
            getRoot().getFontRenderer().draw(stack, s, getFrame().x() + 2, getFrame().y() + 2, 0xFFFFFF);
        }

        @Override
        public void drawFront(MatrixStack stack, Point mouse, float partialFrame) {

            if (!isFirstHit(mouse)) return;

            List<ITextProperties> toolTip = new LinkedList<>();
            toolTip.add(new StringTextComponent(node.step.toString())); //TODO localize
            toolTip.add(new StringTextComponent("Positions: " + node.tileCoords.size()));
            toolTip.add(new StringTextComponent("Registers: " + node.registerIds.size()));
            toolTip.add(new StringTextComponent("Gates: " + node.gateIds.size()));
            toolTip.add(new StringTextComponent("Remaps: " + node.registerRemaps.size()));

            renderTooltip(stack, mouse, toolTip);
        }

        @Override
        public boolean checkHit(Point absPoint) {
            return super.checkHit(absPoint) && CTNListNode.this.convertParentRectToScreen(CTNListNode.this.getFrame()).contains(absPoint);
        }
    }
}
