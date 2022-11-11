package mrtjp.projectred.fabrication.gui.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.fabrication.editor.ICWorkbenchEditor;
import mrtjp.projectred.fabrication.gui.SimpleUVTab;
import mrtjp.projectred.fabrication.gui.TabButtonNode;
import mrtjp.projectred.fabrication.gui.TabControllerNode;
import mrtjp.projectred.fabrication.tile.ICWorkbenchTile;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.lib.Rect;
import mrtjp.projectred.redui.RedUIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;

public class ICWorkbenchScreen extends RedUIScreen {

    public static final ResourceLocation BACKGROUND = new ResourceLocation(ProjectRedFabrication.MOD_ID, "textures/gui/ic_workbench.png");

    private final ICWorkbenchTile tile;
    private final ICWorkbenchEditor editor;

    public ICWorkbenchScreen(ICWorkbenchTile tile) {
        super(304, 222, new StringTextComponent(tile.getType().getRegistryName().toString()));

        this.tile = tile;
        this.editor = tile.getEditor();

        initSubNodes();
    }

    public static void openGuiOnClient(ICWorkbenchTile tile) {
        Minecraft.getInstance().setScreen(new ICWorkbenchScreen(tile));
    }

    private void initSubNodes() {
        ICWorkbenchInfoTab infoTab = new ICWorkbenchInfoTab(editor);
        addChild(infoTab);

        ICWorkbenchEditTab editTab = new ICWorkbenchEditTab(editor);
        addChild(editTab);

        ICWorkbenchCompileTab compileTab = new ICWorkbenchCompileTab(editor);
        addChild(compileTab);

        TabControllerNode tabControllerNode = new TabControllerNode();
        tabControllerNode.setPosition(-19, 4);
        tabControllerNode.setZPosition(0.1);
        addChild(tabControllerNode);

        tabControllerNode.addButtonForTab(new SimpleUVTab(infoTab, "Info", TabButtonNode.TabSide.LEFT, 420, 1));
        tabControllerNode.addButtonForTab(new SimpleUVTab(editTab, "Edit", TabButtonNode.TabSide.LEFT, 420, 16));
        tabControllerNode.addButtonForTab(new SimpleUVTab(compileTab, "Compile", TabButtonNode.TabSide.LEFT, 420, 31));

        tabControllerNode.selectInitialTab(1);
        tabControllerNode.spreadButtonsVertically(1);
    }

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
        super.drawBack(stack, mouse, partialFrame);

        if (!editor.isActive()) { //TODO this has to be drawn by a node above viewport node
            Rect frame = getFrame();
            fillGradient(stack, frame.x(), frame.y(), frame.width(), frame.height(), -1072689136, -804253680);

            FontRenderer fontRenderer = getFontRenderer();

            String message = "Editor is not active";
            int width = fontRenderer.width(message);
            int height = fontRenderer.lineHeight;

            getFontRenderer().draw(stack, message,
                    frame.x() + frame.width() / 2F - width / 2F,
                    frame.y() + frame.height() / 2F - height / 2F, 0xFFFFFFFF);
        }
    }

    @Override
    public void removed() {
        super.removed();
        tile.closeGuiFromClient();
        ProjectRedFabrication.LOGGER.info("ICWorkbenchScreen REMOVED");
    }

    @Override
    public void onClose() {
        super.onClose();
        ProjectRedFabrication.LOGGER.info("ICWorkbenchScreen ONCLOSE");
    }
}