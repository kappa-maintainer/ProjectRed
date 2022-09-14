package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import mrtjp.projectred.fabrication.init.FabricationReferences;
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

public class EtchedSiliconWaferItem extends Item {

    public EtchedSiliconWaferItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP)
                .stacksTo(1));
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

        // Wafer etching data
        tooltipList.add(new StringTextComponent("Yield: " + tag.getDouble("yield")*100 + "%").withStyle(TextFormatting.GRAY));
        tooltipList.add(new StringTextComponent("Defects: " + tag.getInt("defectCount")).withStyle(TextFormatting.GRAY));
        byte[] defects = tag.getByteArray("defects");
        int gridLen = tag.getInt("gridLen");
        for (int y = 0; y < gridLen; y++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < gridLen; x++) {
                int i = y * gridLen + x;
                s.append(defects[i] == 0 ? "[-]" : "[X]");
            }
            tooltipList.add(new StringTextComponent("  " + s).withStyle(TextFormatting.GRAY));
        }
    }

    public static ItemStack createFromPhotomaskSet(ItemStack photomaskSet, int waferLen, int dieLen, double defectChancePerLen) {

        // Create copy of photomask tag
        CompoundNBT tag = photomaskSet.getTag().copy();

        int gridLen = waferLen / dieLen;
        double defectChancePerDie = dieLen * defectChancePerLen;
        int totalDesigns = gridLen * gridLen;
        int totalDefects = 0;

        byte[] defects = new byte[totalDesigns];
        for (int i = 0; i < totalDesigns; i++) {
            if (Math.random() < defectChancePerDie) {
                defects[i] = 1;
                totalDefects++;
            }
        }

        double yield = (totalDesigns - totalDefects) / (double) totalDesigns;

        tag.putInt("gridLen", gridLen);
        tag.putInt("designCount", totalDesigns);
        tag.putInt("defectCount", totalDefects);
        tag.putDouble("yield", yield);
        tag.putByteArray("defects", defects);

        // Put NBT on new item stack and return
        ItemStack output = new ItemStack(FabricationReferences.ETCHED_SILICON_WAFER_ITEM);
        output.setTag(tag);
        return output;
    }
}
