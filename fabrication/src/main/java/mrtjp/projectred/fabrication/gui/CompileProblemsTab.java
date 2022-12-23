package mrtjp.projectred.fabrication.gui;

import codechicken.lib.colour.EnumColour;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.texture.TextureUtils;
import codechicken.lib.vec.Vector3;
import com.mojang.blaze3d.matrix.MatrixStack;
import mrtjp.fengine.TileCoord;
import mrtjp.projectred.fabrication.editor.ICWorkbenchEditor;
import mrtjp.projectred.fabrication.editor.tools.IICEditorTool;
import mrtjp.projectred.fabrication.engine.ICIssuesLog;
import mrtjp.projectred.fabrication.gui.screen.ICWorkbenchCompileTab;
import mrtjp.projectred.lib.Point;
import mrtjp.projectred.redui.AbstractGuiNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

import static mrtjp.projectred.fabrication.engine.ICIssuesLog.IssueSeverity.ERROR;
import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_MULTIPLE_DRIVERS_DESC;
import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_MULTIPLE_DRIVERS_TITLE;
import static net.minecraft.client.gui.AbstractGui.blit;

public class CompileProblemsTab extends AbstractGuiNode implements ICompileOverlayRenderer {

    private final ICWorkbenchEditor editor;

    public CompileProblemsTab(ICWorkbenchEditor editor) {
        this.editor = editor;
        setSize(91, 134);
        initSubNodes();
    }

    private void initSubNodes() {
    }

    @Override
    public void drawBack(MatrixStack stack, Point mouse, float partialFrame) {
        TextureUtils.changeTexture(ICWorkbenchCompileTab.TAB_BACKGROUND);
        blit(stack, getFrame().x(), getFrame().y(), 184, 223, getFrame().width(), getFrame().height(), 512, 512);
    }

    //region ICompileTabOverlayRenderer
    @Override
    public void renderOverlay(ICRenderNode renderNode, Vector3 mousePosition, boolean isFirstHit, CCRenderState ccrs, IRenderTypeBuffer getter, MatrixStack matrixStack) {
        // TODO - would be cleaner if issue classes handle their own overlays and tooltips
        ccrs.reset();
        ccrs.bind(ICRenderTypes.selectionRenderType, Minecraft.getInstance().renderBuffers().bufferSource(), matrixStack);
        Vector3 vec = new Vector3();

        for (ICIssuesLog.ICIssue issue : editor.getStateMachine().getCompilerLog().getIssuesLog().getIssues()) { // - JFC flatten these a lil
            switch (issue.type) {
                case MULTIPLE_DRIVERS:
                    ICIssuesLog.MultipleDriversIssue i = (ICIssuesLog.MultipleDriversIssue) issue;
                    ccrs.baseColour = issue.severity == ERROR ? EnumColour.RED.rgba(200) : EnumColour.YELLOW.rgba(200);
                    vec.set(i.coord.x, i.coord.y, i.coord.z).add(0.5);
                    ICRenderTypes.renderSelection(ccrs, vec, vec, 3 / 16D, 2 / 16D);
                    break;
                default:
            }
        }
    }

    @Override
    public void buildTooltip(ICRenderNode renderNode, Vector3 mousePosition, List<ITextProperties> tooltip) {

        TileCoord pos = IICEditorTool.toNearestPosition(mousePosition);

        //TODO would be cleaner if issue classes handle their own overlays and tooltips
        for (ICIssuesLog.ICIssue issue : editor.getStateMachine().getCompilerLog().getIssuesLog().getIssues()) { // - JFC flatten these a lil
            switch (issue.type) {
                case MULTIPLE_DRIVERS:
                    ICIssuesLog.MultipleDriversIssue i = (ICIssuesLog.MultipleDriversIssue) issue;
                    if (i.coord.equals(pos)) {
                        tooltip.add(new TranslationTextComponent(UL_MULTIPLE_DRIVERS_TITLE).withStyle(TextFormatting.GRAY));
                        tooltip.add(new StringTextComponent(" - ").withStyle(TextFormatting.GRAY).append(
                                new TranslationTextComponent(UL_MULTIPLE_DRIVERS_DESC).withStyle(TextFormatting.GRAY)));

                        StringBuilder s = new StringBuilder();
                        for (int r : i.registerList) {
                            s.append("R[").append(r).append("], ");
                        }
                        s.delete(s.length() - 2, s.length());
                        tooltip.add(new StringTextComponent("   ").withStyle(TextFormatting.GRAY).append(
                                new StringTextComponent(s.toString()).withStyle(TextFormatting.GRAY)));
                    }
                    break;
                default:
            }
        }
    }
    //endregion
}
