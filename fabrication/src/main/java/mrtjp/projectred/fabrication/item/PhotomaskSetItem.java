package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.fabrication.init.FabricationReferences;
import mrtjp.projectred.integration.GateType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.ITextComponent;
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

        ICBlueprintItem.buildTooltip(stack.getTag(), tooltipList);
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

    public static ItemStack createDieStack(ItemStack photomask, int count) {
        ItemStack validDieStack = new ItemStack(FabricationReferences.VALID_DIE_ITEM, count);
        validDieStack.setTag(photomask.getTag().copy()); //Nothing additional to add yet
        return validDieStack;
    }
}
