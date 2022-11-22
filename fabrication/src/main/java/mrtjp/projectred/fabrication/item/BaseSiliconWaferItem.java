package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.fabrication.lithography.WaferType;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_DEFECT_CHANCE;
import static mrtjp.projectred.fabrication.init.FabricationUnlocal.UL_SIZE;

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

        tooltipList.add(new TranslationTextComponent(UL_SIZE).append(": " + waferType.getWaferWidth() + "nm x " + waferType.getWaferHeight() + "nm").withStyle(TextFormatting.GRAY));
        tooltipList.add(new TranslationTextComponent(UL_DEFECT_CHANCE).append(": " + waferType.getDefectRatePerUnitArea()*100 + "% / nm^2").withStyle(TextFormatting.GRAY));
    }

    public WaferType getWaferType() {
        return waferType;
    }
}
