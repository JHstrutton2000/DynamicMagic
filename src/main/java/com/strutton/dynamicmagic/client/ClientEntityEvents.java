package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.DynamicMagic;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEntityEvents {
    private ClientEntityEvents() {}
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DynamicMagic.CREEPER_VILLAGER.get(), CreeperVillagerRenderer::new);
        event.registerEntityRenderer(DynamicMagic.ENDERMAN_VILLAGER.get(), EndermanVillagerRenderer::new);
        event.registerEntityRenderer(DynamicMagic.ALEX_VILLAGER.get(), AlexVillagerRenderer::new);
        event.registerEntityRenderer(DynamicMagic.SKELETON_MAGE_VILLAGER.get(), SkeletonMageVillagerRenderer::new);
    }
}
