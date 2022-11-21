package mrtjp.projectred.fabrication.init;

import codechicken.multipart.api.MultiPartType;
import codechicken.multipart.api.SimpleMultiPartType;
import mrtjp.projectred.fabrication.part.FabricatedGatePart;
import mrtjp.projectred.integration.GateType;
import mrtjp.projectred.integration.item.GatePartItem;
import mrtjp.projectred.integration.part.GatePart;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;

import static mrtjp.projectred.fabrication.ProjectRedFabrication.ITEMS;
import static mrtjp.projectred.fabrication.ProjectRedFabrication.PARTS;

public class FabricationParts {

        public static final String ID_FABRICATED_GATE = "fabricated_gate";

        public static void register() {

            // Items
            RegistryObject<Item> registeredItem = ITEMS.register(ID_FABRICATED_GATE, () -> new GatePartItem(GateType.FABRICATED_GATE));

            // Parts
            RegistryObject<MultiPartType<GatePart>> registeredPartType = PARTS.register(ID_FABRICATED_GATE, () -> new SimpleMultiPartType<>(b -> new FabricatedGatePart()));

            // Add to GateType enum
            GateType.FABRICATED_GATE.inject(ID_FABRICATED_GATE, isClient -> new FabricatedGatePart(), registeredItem, registeredPartType);
        }
}
