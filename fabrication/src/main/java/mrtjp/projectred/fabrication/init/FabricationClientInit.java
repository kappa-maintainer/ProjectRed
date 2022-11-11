package mrtjp.projectred.fabrication.init;

import codechicken.lib.model.ModelRegistryHelper;
import codechicken.lib.texture.SpriteRegistryHelper;
import mrtjp.projectred.fabrication.gui.ICRenderTypes;
import mrtjp.projectred.fabrication.gui.screen.inventory.LithographyTableScreen;
import mrtjp.projectred.fabrication.gui.screen.inventory.PackagingTableScreen;
import mrtjp.projectred.fabrication.gui.screen.inventory.PlottingTableScreen;
import mrtjp.projectred.integration.client.GatePartItemRenderer;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import static mrtjp.projectred.fabrication.init.FabricationReferences.*;

public class FabricationClientInit {

    public static void init() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(FabricationClientInit::clientSetup);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {

        // Register sprites
        SpriteRegistryHelper iconRegister = new SpriteRegistryHelper();
        iconRegister.addIIconRegister(ICRenderTypes::registerIcons);

        // Register models
        ModelRegistryHelper modelRegistryHelper = new ModelRegistryHelper();
        modelRegistryHelper.register(new ModelResourceLocation(FabricationReferences.FABRICATED_GATE_ITEM.getRegistryName(), "inventory"), GatePartItemRenderer.INSTANCE);

        // Register screens
        ScreenManager.register(PLOTTING_TABLE_CONTAINER, PlottingTableScreen::new);
        ScreenManager.register(LITHOGRAPHY_TABLE_CONTAINER, LithographyTableScreen::new);
        ScreenManager.register(PACKAGING_TABLE_CONTAINER, PackagingTableScreen::new);
    }
}