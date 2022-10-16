package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.integration.GateType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
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
        ICBlueprintItem.buildTooltip(stack.getTag(), tooltipList);
    }

    public static ItemStack createGatePart(ItemStack die) {
        ItemStack gate = GateType.FABRICATED_GATE.makeStack();
        gate.setTag(die.getTag().copy());
        return gate;
    }
}
