package mrtjp.projectred.fabrication.gui.screen;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.editor.ICWorkbenchEditor;
import mrtjp.projectred.fabrication.editor.tools.*;
import mrtjp.projectred.fabrication.gui.*;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.lib.Rect;
import mrtjp.projectred.redui.AbstractGuiNode;
import net.minecraft.client.gui.FontRenderer;

import static net.minecraft.client.gui.AbstractGui.blit;

public class ICWorkbenchEditTab extends AbstractGuiNode {

    private final ICWorkbenchEditor editor;
    private final ICEditorToolManager toolManager;

    public ICWorkbenchEditTab(ICWorkbenchEditor editor) {
        this.editor = editor;
        this.toolManager = new ICEditorToolManager(editor.getToolList());

        setSize(304, 222);
        initSubNodes();
    }

    private void initSubNodes() {

        TabControllerNode toolbarNode = new TabControllerNode();
        toolbarNode.setPosition(286, 24);
        toolbarNode.setZPosition(0.1);
        addChild(toolbarNode);

        for (IICEditorTool tool : editor.getToolList()) {

            ICEditorToolTab tab = getTabForTool(tool);
            tab.setPosition(305, 0);
            tab.setHidden(true);

            toolbarNode.addButtonForTab(tab);
            addChild(tab);
        }
        toolbarNode.selectInitialTab(0);
        toolbarNode.spreadButtonsVertically(1);

        ICRenderNode icRenderNode = new ICRenderNode(editor, toolManager);
        icRenderNode.setPosition(7, 18);
        icRenderNode.setSize(290, 197);
        addChild(icRenderNode);
    }

    private ICEditorToolTab getTabForTool(IICEditorTool tool) {

        if (tool instanceof InteractTool)
            return new InteractToolTab(toolManager, (InteractTool) tool);

        if (tool instanceof EraseTool)
            return new EraserToolTab(toolManager, (EraseTool) tool);

        if (tool instanceof GatePlacerTool)
            return new GatePlacerToolTab(toolManager, (GatePlacerTool) tool);

        if (tool instanceof WirePlacerTool)
            return new WirePlacerToolTab(toolManager, (WirePlacerTool) tool);

        return new ICEditorToolTab(toolManager, tool);
    }

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
        TextureUtils.changeTexture(ICWorkbenchScreen.BACKGROUND);

        Rect frame = getFrame();
        FontRenderer fontRenderer = getRoot().getFontRenderer();

        blit(stack, frame.x(), frame.y(), 0, 0, frame.width(), frame.height(), 512, 512);

        if (editor.isActive()) {
            fontRenderer.draw(stack, editor.getIcName(), frame.x() + 8, frame.y() + 6, EnumColour.GRAY.argb());
        }
    }

    @Override
    public boolean onKeyPressed(int glfwKeyCode, int glfwScanCode, int glfwFlags, boolean consumed) {
        if (!consumed && !isHidden()) {
            return toolManager.keyPressed(glfwKeyCode, glfwFlags);
        }
        return false;
    }

    @Override
    public boolean onKeyReleased(int glfwKeyCode, int glfwScanCode, int glfwFlags, boolean consumed) {
        return toolManager.keyReleased(glfwKeyCode, glfwFlags);
    }
}
