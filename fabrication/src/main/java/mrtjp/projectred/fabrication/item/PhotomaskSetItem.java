package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.integration.GateType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class PhotomaskSetItem extends Item {

    public PhotomaskSetItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World p_77624_2_, List<ITextComponent> tooltipList, ITooltipFlag tooltipFlag) {

        if (stack.getTag() != null) {
            //TODO localize
            tooltipList.add(new StringTextComponent("Name: " + stack.getTag().getString("ic_name")).withStyle(TextFormatting.GRAY));
            tooltipList.add(new StringTextComponent("Tile count: " + stack.getTag().getInt("tilecount")).withStyle(TextFormatting.GRAY));

            byte bmask = stack.getTag().getByte("bmask");
            tooltipList.add(new StringTextComponent("Input mask: " + "0x" + Integer.toHexString(bmask & 0xF)).withStyle(TextFormatting.GRAY));
            tooltipList.add(new StringTextComponent("Output mask: " + "0x" + Integer.toHexString((bmask >> 4) & 0xF)).withStyle(TextFormatting.GRAY));
        }
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {

        if (stack.getTag() != null) {
            ItemStack gate = GateType.FABRICATED_GATE.makeStack();
            gate.setTag(stack.getTag());

            context.getPlayer().addItem(gate);
            return ActionResultType.SUCCESS;
        }

        return ActionResultType.PASS;
    }

    public static void transferNBTToDieItem(ItemStack photomask, ItemStack die) {
        die.setTag(photomask.getTag().copy());
    }
}
