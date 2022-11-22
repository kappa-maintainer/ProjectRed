package mrtjp.projectred.fabrication.gui;

import codechicken.lib.math.MathHelper;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.texture.TextureUtils;
import codechicken.lib.vec.Rotation;
import codechicken.lib.vec.Scale;
import codechicken.lib.vec.TransformationList;
import codechicken.lib.vec.Translation;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.editor.tools.GatePlacerTool;
import mrtjp.projectred.fabrication.engine.gates.ICGateTileType;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchScreen;
import mrtjp.projectred.integration.client.GateModelRenderer;
import mrtjp.projectred.lib.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

import static mrtjp.projectred.fabrication.init.FabricationUnlocal.*;

public class GatePlacerToolTab extends ICEditorToolTab {

    private final GatePlacerTool tool;

    public GatePlacerToolTab(ICEditorToolManager manager, GatePlacerTool tool) {
        super(manager, tool);
        this.tool = tool;
        construct();
    }

    private void addGateButton(ICGateTileType type) {
        ButtonController buttonController = new ButtonController() {
            @Override public void getTooltip(List<ITextProperties> tooltip) { tooltip.add(new TranslationTextComponent(type.tileType.getUnlocalizedName())); }
            @Override public void onClick() { tool.setGateType(type); }
            @Override public boolean isSelected() { return tool.getGateType() == type; }

            @Override
            public void renderIcon(MatrixStack stack, Point absPos, float partialFrame) {
                IRenderTypeBuffer.Impl getter = Minecraft.getInstance().renderBuffers().bufferSource();
                CCRenderState ccrs = CCRenderState.instance();
                ccrs.reset();
                ccrs.bind(RenderType.cutout(), getter, stack);
                ccrs.overlay = OverlayTexture.NO_OVERLAY;
                ccrs.brightness = 0xF000F0;

                double scale = 10/16D;
                TransformationList t = new TransformationList(
                        new Rotation(90.0F * MathHelper.torad, 1.0F, 0.0F, 0.0F),
                        new Scale(16.0F * scale, -16.0F * scale, 16.0F * scale),
                        new Translation(absPos.x + 8 - scale*8, absPos.y + 8 - scale*8, 0.0F)
                );

                //TODO dont use null?
                GateModelRenderer.instance().renderInventory(ccrs, null, type.renderIndex, 0, t);

                getter.endBatch();
            }
        };

        this.addSingleButton(buttonController);
    }

    private void construct() {

        addGroup(UL_TILEGROUP_IO);
        addGateButton(ICGateTileType.IO);

        addGroup(UL_TILEGROUP_BASIC);
        addGateButton(ICGateTileType.OR);
        addGateButton(ICGateTileType.NOR);
        addGateButton(ICGateTileType.NOT);
        addGateButton(ICGateTileType.AND);
        addGateButton(ICGateTileType.NAND);
        addGateButton(ICGateTileType.XOR);
        addGateButton(ICGateTileType.XNOR);
        addGateButton(ICGateTileType.BUFFER);
        addGateButton(ICGateTileType.MULTIPLEXER);

        addGroup(UL_TILEGROUP_TIMING);
        addGateButton(ICGateTileType.PULSE);
        addGateButton(ICGateTileType.REPEATER);
        addGateButton(ICGateTileType.RANDOMIZER);

        addGroup(UL_TILEGROUP_MEMORY);
        addGateButton(ICGateTileType.SR_LATCH);
        addGateButton(ICGateTileType.TOGGLE_LATCH);
        addGateButton(ICGateTileType.TRANSPARENT_LATCH);
    }

    @Override
    public TabButtonNode createButtonNode() {
        return new TabButtonNode(this, TabButtonNode.TabSide.LEFT) {
            @Override
            public void renderIcon(MatrixStack stack, Point mouse, float partialFrame) {
                TextureUtils.changeTexture(ICWorkbenchScreen.BACKGROUND);
                AbstractGui.blit(stack, getFrame().x() + 3, getFrame().y() + 3, 390, 31, 14, 14, 512, 512);
            }

            @Override
            public void buildTooltip(List<ITextProperties> tooltip) {
                tooltip.add(new TranslationTextComponent(UL_GATE_TOOL));
            }
        };
    }
}
