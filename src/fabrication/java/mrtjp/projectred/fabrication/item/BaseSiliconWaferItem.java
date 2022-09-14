package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.ProjectRedFabrication;
import mrtjp.projectred.fabrication.lithography.WaferType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class BaseSiliconWaferItem extends Item {

    private final WaferType waferType;

    public BaseSiliconWaferItem(WaferType waferType) {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP));
        this.waferType = waferType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltipList, ITooltipFlag tooltipFlag) {
        super.appendHoverText(stack, world, tooltipList, tooltipFlag);

        //TODO localize
        tooltipList.add(new StringTextComponent("Size").append(": " + waferType.getWaferLen() + " nm").withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("Defect chance").append(": " + waferType.getDefectChancePerLen()*100 + "% / nm").withStyle(TextFormatting.GRAY));
    }

    public WaferType getWaferType() {
        return waferType;
    }
}
