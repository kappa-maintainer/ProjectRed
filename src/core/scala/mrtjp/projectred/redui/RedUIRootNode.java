package mrtjp.projectred.redui;

import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.core.vec.Point;
import mrtjp.core.vec.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ListIterator;

public interface RedUIRootNode extends RedUINode {

    Minecraft getMinecraft();

    ItemRenderer getItemRenderer();

    FontRenderer getFontRenderer();

    Rect getScreenFrame();

    @Override
    default RedUIRootNode getRoot() {
        return this;
    }

    @Override
    default RedUINode getParent() {
        throw new RuntimeException("Cannot get parent of root node");
    }

    @Override
    default void setRoot(@Nullable RedUIRootNode root) {
        throw new RuntimeException("Cannot set root of root node!");
    }

    @Override
    default void setParent(@Nullable RedUINode parent) {
        throw new RuntimeException("Cannot set parent of root node!");
    }

    @Override
    default double getRelativeZPosition() {
        return getZPosition(); // Assume screen's base z position is zero
    }

    default void drawBackForSubtree(MatrixStack stack, Point mouse, float partialFrame) {
        drawForSubtree(this, stack, mouse, partialFrame, RedUINode::drawBack);
    }

    default void drawFrontForSubtree(MatrixStack stack, Point mouse, float partialFrame) {
        drawForSubtree(this, stack, mouse, partialFrame, RedUINode::drawFront);
    }

    interface QuadConsumer<A, B, C, D> {

        void accept(A a, B b, C c, D d);
    }

    static void drawForSubtree(RedUINode node, MatrixStack stack, Point mouse, float partialFrame, QuadConsumer<RedUINode, MatrixStack, Point, Float> drawFunction) {
        List<RedUINode> nodes = node.getZOrderedSubtree(c -> !c.isHidden(), true);
        ListIterator<RedUINode> it = nodes.listIterator(nodes.size());

        // In reverse render order tell each node that nodes below it are about to be drawn
        while (it.hasPrevious()) {
            it.previous().onNodesBelowPreDraw();
        }

        // In render order, tell each node that under are done rendering, then draw the node
        while (it.hasNext()) {
            RedUINode n = it.next();
            n.onNodesBelowPostDraw();

            Point parentScreenPos = n.convertParentPointToScreen(Point.zeroPoint());
            stack.pushPose();
            stack.translate(parentScreenPos.x(), parentScreenPos.y(), 0); // Z pos is for sorting only

            Point relativeMousePos = n.convertScreenPointToParent(mouse);
            drawFunction.accept(n, stack, relativeMousePos, partialFrame);

            stack.popPose();
        }
    }
}
