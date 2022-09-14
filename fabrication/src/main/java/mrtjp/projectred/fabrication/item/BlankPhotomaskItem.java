package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.fabrication.ProjectRedFabrication;
import net.minecraft.item.Item;

public class BlankPhotomaskItem extends Item {

    public BlankPhotomaskItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP));
    }
}
