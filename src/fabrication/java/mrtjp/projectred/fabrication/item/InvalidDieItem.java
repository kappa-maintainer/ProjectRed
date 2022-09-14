package mrtjp.projectred.fabrication.item;

import mrtjp.projectred.ProjectRedFabrication;
import net.minecraft.item.Item;

public class InvalidDieItem extends Item {

    public InvalidDieItem() {
        super(new Item.Properties()
                .tab(ProjectRedFabrication.FABRICATION_GROUP));
    }
}
