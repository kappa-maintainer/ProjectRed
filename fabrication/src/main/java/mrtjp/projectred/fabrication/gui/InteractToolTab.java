package mrtjp.projectred.fabrication.gui;

import codechicken.lib.texture.TextureUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.editor.tools.InteractTool;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchScreen;
import mrtjp.projectred.lib.Point;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_INTERACT_TOOL;

public class InteractToolTab extends ICEditorToolTab {

    private final InteractTool tool;

    public InteractToolTab(ICEditorToolManager manager, InteractTool tool) {
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
                AbstractGui.blit(stack, getFrame().x() + 3, getFrame().y() + 3, 390, 1, 14, 14, 512, 512);
            }

            @Override
            public void buildTooltip(List<ITextProperties> tooltip) {
                tooltip.add(new TranslationTextComponent(UL_INTERACT_TOOL));
            }

            @Override
            public boolean hasBody() {
                return false;
            }
        };
    }
}
