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
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

import static mrtjp.projectred.fabrication.editor.EditorDataUtils.*;
import static mrtjp.projectred.fabrication.init.FabricationUnlocal.*;

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
                    .append(new TranslationTextComponent(UL_CORRUPTED_DISCARD).withStyle(TextFormatting.GRAY)));
            return;
        }

        tooltipList.add(new TranslationTextComponent(UL_NAME).append(": " + blueprintTag.getString(KEY_IC_NAME)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new TranslationTextComponent(UL_TILE_COUNT).append(": " + blueprintTag.getInt(KEY_TILE_COUNT)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new TranslationTextComponent(UL_IO_TYPES).append(": ").withStyle(TextFormatting.GRAY));

        //TODO handle other types of IO
        byte bmask = blueprintTag.getByte(KEY_IO_BUNDLED);
        StringTextComponent indent = new StringTextComponent("  ");
        tooltipList.add(indent.copy().append(new TranslationTextComponent(UL_TOP)).append(": ").append(getBundledIOTextComponent(bmask, 0)).withStyle(TextFormatting.GRAY));
        tooltipList.add(indent.copy().append(new TranslationTextComponent(UL_RIGHT)).append(": ").append(getBundledIOTextComponent(bmask, 1)).withStyle(TextFormatting.GRAY));
        tooltipList.add(indent.copy().append(new TranslationTextComponent(UL_BOTTOM)).append(": ").append(getBundledIOTextComponent(bmask, 2)).withStyle(TextFormatting.GRAY));
        tooltipList.add(indent.copy().append(new TranslationTextComponent(UL_LEFT)).append(": ").append(getBundledIOTextComponent(bmask, 3)).withStyle(TextFormatting.GRAY));

        //TODO errors
    }

    private static TranslationTextComponent getBundledIOTextComponent(byte bmask, int r) {
        int i = 0x01 << r;
        int o = 0x10 << r;
        return new TranslationTextComponent((bmask & i) != 0 ? UL_BUNDLED_INPUT : (bmask & o) != 0 ? UL_BUNDLED_OUTPUT : UL_IO_NONE);
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
