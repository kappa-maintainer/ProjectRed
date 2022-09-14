package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.integration.GateType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class ValidDieItem extends Item {

    public ValidDieItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltipList, ITooltipFlag tooltipFlag) {
        super.appendHoverText(stack, world, tooltipList, tooltipFlag);

        if (stack.getTag() == null)
            return;

        CompoundNBT tag = stack.getTag();

        // Blueprint data
        tooltipList.add(new StringTextComponent("Name: " + tag.getString("ic_name")).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("Tile count: " + tag.getInt("tilecount")).withStyle(TextFormatting.GRAY));
        byte bmask = tag.getByte("bmask");
        tooltipList.add(new StringTextComponent("Input mask: " + "0x" + Integer.toHexString(bmask & 0xF)).withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("Output mask: " + "0x" + Integer.toHexString((bmask >> 4) & 0xF)).withStyle(TextFormatting.GRAY));
    }

    public static ItemStack createGatePart(ItemStack die) {
        ItemStack gate = GateType.FABRICATED_GATE.makeStack();
        gate.setTag(die.getTag().copy());
        return gate;
    }
}
