package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import mrtjp.projectred.integration.GateType;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

import static mrtjp.projectred.fabrication.editor.EditorDataUtils.*;

public class ICBlueprintItem extends Item {

    public ICBlueprintItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP)
                .stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World p_77624_2_, List<ITextComponent> tooltipList, ITooltipFlag tooltipFlag) {
        buildTooltip(stack.getTag(), tooltipList);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {

        if (!context.getPlayer().isCreative()) return ActionResultType.PASS;

        // Creative mode bypass: Convert blueprint directly to gate block
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.getBlock() == FabricationReferences.IC_WORKBENCH_BLOCK) { return ActionResultType.PASS; }

        if (!canFabricate(stack.getTag())) {
            return ActionResultType.PASS;
        }

        ItemStack gate = GateType.FABRICATED_GATE.makeStack();
        gate.setTag(createFabricationCopy(stack.getTag()));

        context.getPlayer().addItem(gate);
        return ActionResultType.SUCCESS;
    }

    public static void buildTooltip(CompoundNBT blueprintTag, List<ITextComponent> tooltipList) {

        if (blueprintTag == null) return;

        if (!hasFabricationTarget(blueprintTag)) {
            tooltipList.add(new StringTextComponent("<!> ").withStyle(TextFormatting.RED)
                    .append(new StringTextComponent("Corrupted NBT data, please discard").withStyle(TextFormatting.GRAY)));
            return;
        }

        //TODO localize
        tooltipList.add(new StringTextComponent("Name: " + blueprintTag.getString(KEY_IC_NAME)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("Tile count: " + blueprintTag.getInt(KEY_TILE_COUNT)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("IO Types: ").withStyle(TextFormatting.GRAY));

        //TODO handle other types of IO
        byte bmask = blueprintTag.getByte(KEY_IO_BUNDLED);
        tooltipList.add(new StringTextComponent("  Top: " + getBundledIOString(bmask, 0)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("  Right: " + getBundledIOString(bmask, 1)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("  Bottom: " + getBundledIOString(bmask, 2)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("  Left: " + getBundledIOString(bmask, 3)).withStyle(TextFormatting.GRAY));

        //TODO errors
    }

    private static String getBundledIOString(byte bmask, int r) {
        int i = 0x01 << r;
        int o = 0x10 << r;
        return ((bmask & i) != 0 ? "Bundled input" : (bmask & o) != 0 ? "Bundled output" : "None");
    }

    public static ItemStack createPhotomaskStack(ItemStack blueprintStack) {

        ItemStack photomaskStack = new ItemStack(FabricationReferences.PHOTOMASK_SET_ITEM);
        CompoundNBT blueprintTag = blueprintStack.getTag();

        if (!hasFabricationTarget(blueprintTag)) return photomaskStack;

        CompoundNBT photomaskTag = createFabricationCopy(blueprintTag);
        photomaskStack.setTag(photomaskTag);

        return photomaskStack;
    }
}
