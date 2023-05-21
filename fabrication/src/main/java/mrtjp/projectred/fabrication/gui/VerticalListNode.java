package mrtjp.projectred.fabrication.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.lib.Rect;
import mrtjp.projectred.redui.AbstractGuiNode;
import mrtjp.projectred.redui.RedUINode;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Supplier;

import static net.minecraft.client.gui.AbstractGui.fill;

public class VerticalListNode extends AbstractGuiNode {

    private static final int TITLE_LINE_HEIGHT = 16;
    private static final int TEXT_LINE_HEIGHT = 12;
    private static final int TEXT_LEFT_PAD = 5;
    private static final int NODE_ROW_VERTICAL_PAD = 2;

    private int listHeight = 0;

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

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
        //Background
        fill(stack, getFrame().x(), getFrame().y(), getFrame().x() + getFrame().width(), getFrame().y() + getFrame().height(), 0x7F000000);

        //TODO render top/bottom gradients
    }

    public void sortElementsVertically() {
        positionElementsAt(0);
    }

    public boolean canScroll() {
        Rect subFrame = calculateChildrenFrame();
        return subFrame.height() <= calculateAccumulatedFrame().height();
    }

    public double getScrollPercentage() {
        Rect subFrame = calculateChildrenFrame();
        if (subFrame.height() <= calculateAccumulatedFrame().height()) {
            return 0;
        }

        int totalScroll = subFrame.height() - getFrame().height(); // How much scroll is possible
        int dist = subFrame.maxY() - getFrame().maxY(); // How close to the bottom we are

        return (double) (totalScroll - dist) / totalScroll;
    }

    public void setScrollPercentage(double scrollPercentage) {
        Rect subFrame = calculateChildrenFrame();
        if (subFrame.height() <= getFrame().height()) return;

        int totalScroll = subFrame.height() - getFrame().height(); // How much scroll is possible
        int dist = (int) (totalScroll * scrollPercentage); // How much we want to scroll

        positionElementsAt(-dist);
    }

    protected void positionChildNode(RedUINode node, int x, int y) {
        if (node instanceof AbstractGuiNode) {
            ((AbstractGuiNode) node).setPosition(x, y);
       }
    }

    private void positionElementsAt(int y) {
        for (RedUINode node : getOurChildren()) {
            positionChildNode(node, 0, y);
            y += node.calculateAccumulatedFrame().height();
        }
    }

    private void positionAndAddChildListElement(RedUINode node) {
        positionChildNode(node, 0, listHeight);
        listHeight += node.calculateAccumulatedFrame().height();
        addChild(node);
    }

    public void addTitleRow(ITextComponent title) {
        TitleRow titleNode = new TitleRow(title);
        titleNode.setSize(getFrame().width(), TITLE_LINE_HEIGHT);
        positionAndAddChildListElement(new RowNode(0, titleNode));
    }

    public void addKeyValueRow(ITextComponent key, Supplier<ITextComponent> valueSupplier) {

        TextNode keyNode = new TextNode(key);
        keyNode.setSize(getFrame().width() / 2, TEXT_LINE_HEIGHT);
        keyNode.setPadding(TEXT_LEFT_PAD);

        TextNode valueNode = new TextNode(valueSupplier);
        valueNode.setSize(getFrame().width() / 2, TEXT_LINE_HEIGHT);
        valueNode.setPadding(TEXT_LEFT_PAD);

        positionAndAddChildListElement(new RowNode(0, keyNode, valueNode));
    }

    public void addTextWithNodeRow(ITextComponent key, RedUINode node) {
        TextNode textNode = new TextNode(key);
        textNode.setSize(getFrame().width() / 2, Math.max(TEXT_LINE_HEIGHT, node.calculateAccumulatedFrame().height()));
        textNode.setPadding(TEXT_LEFT_PAD);

        positionAndAddChildListElement(new RowNode(NODE_ROW_VERTICAL_PAD, textNode, node));
    }

    public void addSingleNodeRow(RedUINode node) {
        positionAndAddChildListElement(new RowNode(NODE_ROW_VERTICAL_PAD, node));
    }

    private class RowNode extends AbstractGuiNode {

        public RowNode(int verticalPadding, RedUINode... rowElements) {
            int x = 0;
            int maxH = 0;
            for (RedUINode node : rowElements) {
                VerticalListNode.this.positionChildNode(node, x, verticalPadding);
                Rect childFrame = node.calculateAccumulatedFrame();
                maxH = Math.max(maxH, childFrame.height());
                x += childFrame.width();
                addChild(node);
            }

            setSize(x, maxH + verticalPadding * 2);
        }
    }

    // TODO move to redui lib
    private static class TextNode extends AbstractGuiNode {

        private final Supplier<ITextComponent> textSupplier;

        private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;
        private VerticalAlignment verticalAlignment = VerticalAlignment.CENTER;

        private int padding = 4;
        private int textColor = 0xFFFFFFFF;

        public TextNode(Supplier<ITextComponent> textSupplier) {
            this.textSupplier = textSupplier;
        }

        public TextNode(ITextComponent text) {
            this(() -> text);
        }

        public void setTextColor(int textColor) {
            this.textColor = textColor;
        }

        public void setPadding(int padding) {
            this.padding = padding;
        }

        public void setHorizontalAlignment(HorizontalAlignment horizontalAlignment) {
            this.horizontalAlignment = horizontalAlignment;
        }

        public void setVerticalAlignment(VerticalAlignment verticalAlignment) {
            this.verticalAlignment = verticalAlignment;
        }

        @Override
        public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
            FontRenderer fontRenderer = getRoot().getFontRenderer();
            ITextComponent text = textSupplier.get();

            int x = getXForAlignment(fontRenderer, text);
            int y = getYForAlignment(fontRenderer);
            fontRenderer.drawShadow(stack, text, x, y, textColor);
        }

        private Rect getPaddedFrame() {
            return getFrame().expand(-padding, -padding);
        }

        private int getXForAlignment(FontRenderer fontRenderer, ITextComponent text) {
            switch (horizontalAlignment) {
                case LEFT:
                    return getPaddedFrame().x();
                case CENTER:
                    return getPaddedFrame().midX() - fontRenderer.width(text) / 2;
                case RIGHT:
                    return getPaddedFrame().maxX() - fontRenderer.width(text);
                default:
                    return getPaddedFrame().x();
            }
        }

        private int getYForAlignment(FontRenderer fontRenderer) {
            switch (verticalAlignment) {
                case TOP:
                    return getPaddedFrame().y();
                case CENTER:
                    return getPaddedFrame().midY() - fontRenderer.lineHeight / 2;
                case BOTTOM:
                    return getPaddedFrame().maxY() - fontRenderer.lineHeight;
                default:
                    return getPaddedFrame().y();
            }
        }

        public enum HorizontalAlignment {
            LEFT,
            CENTER,
            RIGHT
        }

        public enum VerticalAlignment {
            TOP,
            CENTER,
            BOTTOM
        }
    }

    private static class TitleRow extends AbstractGuiNode {

        private static final int TITLE_COLOR = 0xFFFFFFFF;
        private final ITextComponent title;

        public TitleRow(ITextComponent title) {
            this.title = title;
        }

        @Override
        public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
            //Line
            fill(stack, getFrame().x(), getFrame().y(), getFrame().x() + getFrame().width(), getFrame().y() + 1, 0xFF000000);

            FontRenderer fontRenderer = getRoot().getFontRenderer();

            int x = getFrame().midX() - fontRenderer.width(title) / 2;
            int y = getFrame().midY() - fontRenderer.lineHeight / 2;
            fontRenderer.drawShadow(stack, title, x, y, TITLE_COLOR);

            //Line
            fill(stack, getFrame().x(), getFrame().y() + getFrame().height() - 1, getFrame().x() + getFrame().width(), getFrame().y() + getFrame().height(), 0xFF000000);
        }
    }
}
