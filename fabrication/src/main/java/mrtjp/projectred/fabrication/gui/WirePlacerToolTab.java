package mrtjp.projectred.fabrication.gui;

import codechicken.lib.math.MathHelper;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.texture.TextureUtils;
import codechicken.lib.vec.Rotation;
import codechicken.lib.vec.Scale;
import codechicken.lib.vec.TransformationList;
import codechicken.lib.vec.Translation;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.projectred.fabrication.editor.tools.WirePlacerTool;
import mrtjp.projectred.fabrication.engine.wires.ICWireTileType;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchScreen;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.transmission.client.WireModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

import static mrtjp.projectred.fabrication.init.FabricationUnlocal.*;

public class WirePlacerToolTab extends ICEditorToolTab {

    private final WirePlacerTool tool;

    public WirePlacerToolTab(ICEditorToolManager manager, WirePlacerTool tool) {
        super(manager, tool);
        this.tool = tool;
        construct();
    }

    private void addWireButton(ICWireTileType type, boolean fullRow) {
        ButtonController buttonController = new ButtonController() {
            @Override public void getTooltip(List<ITextProperties> tooltip) { tooltip.add(new TranslationTextComponent(type.tileType.getUnlocalizedName())); }
            @Override public void onClick() { tool.setWireType(type); }
            @Override public boolean isSelected() { return tool.getWireType() == type; }

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

                WireModelRenderer.renderInventory(ccrs, type.multipartType.getThickness(), type.multipartType.getItemColour() << 8 | 0xFF, type.multipartType.getTextures().get(0), t);

                getter.endBatch();
            }
        };

        if (fullRow) {
            addFullRowButton(buttonController);
        } else {
            addSingleButton(buttonController);
        }
    }

    private void construct() {

        addGroup(UL_TILEGROUP_REDWIRE);

        // Alloy wire
        addWireButton(ICWireTileType.RED_ALLOY, true);

        // Insulated wires
        for (ICWireTileType type : ICWireTileType.INSULATED)
            addWireButton(type, false);

        addGroup(UL_TILEGROUP_BUNDLED);

        // Bundled neutral wire
        addWireButton(ICWireTileType.BUNDLED_NEUTRAL, true);

        // Bundled coloured wires
        for (ICWireTileType type : ICWireTileType.BUNDLED_COLOURED)
            addWireButton(type, false);

    }

    @Override
    public TabButtonNode createButtonNode() {
        return new TabButtonNode(this, TabButtonNode.TabSide.LEFT) {
            @Override
            public void renderIcon(MatrixStack stack, Point mouse, float partialFrame) {

                TextureUtils.changeTexture(ICWorkbenchScreen.BACKGROUND);
                AbstractGui.blit(stack, getFrame().x() + 3, getFrame().y() + 3, 390, 46, 14, 14, 512, 512);
            }

            @Override
            public void buildTooltip(List<ITextProperties> tooltip) {
                tooltip.add(new TranslationTextComponent(UL_WIRE_TOOL));
            }
        };
    }
}
