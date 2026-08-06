package mrtjp.projectred.transportation

import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundEvent
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object PneumaticSounds:
    val pressurizeId = new ResourceLocation("projectred-transportation", "pressurize")
    val depressurizeId = new ResourceLocation("projectred-transportation", "depressurize")

    val pressurize = new SoundEvent(pressurizeId).setRegistryName(pressurizeId)
    val depressurize = new SoundEvent(depressurizeId).setRegistryName(depressurizeId)

    @SubscribeEvent
    def registerSounds(event:RegistryEvent.Register[SoundEvent]):Unit =
        event.getRegistry.register(pressurize)
        event.getRegistry.register(depressurize)
