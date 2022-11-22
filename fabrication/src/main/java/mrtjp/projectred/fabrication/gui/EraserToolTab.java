package mrtjp.projectred.fabrication.gui;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.editor.tools.EraseTool;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchScreen;
import mrtjp.projectred.lib.Point;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_ERASER_TOOL;

public class EraserToolTab extends ICEditorToolTab {

    private final EraseTool tool;

    public EraserToolTab(ICEditorToolManager manager, EraseTool tool) {
        super(manager, tool);
        this.tool = tool;
        construct();
    }

    private void construct() {

    }

    @Override
    public TabButtonNode createButtonNode() {
        return new TabButtonNode(this, TabButtonNode.TabSide.LEFT) {
            @Override
            public void renderIcon(MatrixStack stack, Point mouse, float partialFrame) {
                TextureUtils.changeTexture(ICWorkbenchScreen.BACKGROUND);
                AbstractGui.blit(stack, getFrame().x() + 3, getFrame().y() + 3, 390, 16, 14, 14, 512, 512);
            }

            @Override
            public void buildTooltip(List<ITextProperties> tooltip) {
                tooltip.add(new TranslationTextComponent(UL_ERASER_TOOL));
            }

            @Override
            public boolean hasBody() {
                return false;
            }
        };
    }
}
