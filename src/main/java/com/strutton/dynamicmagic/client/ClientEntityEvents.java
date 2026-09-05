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
    @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes())
            addMorphMageEyes(event, type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addMorphMageEyes(EntityRenderersEvent.AddLayers event,
                                         net.minecraft.world.entity.EntityType type) {
        net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getRenderer(type);
        if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer livingRenderer)
            livingRenderer.addLayer(new MorphMageEyesLayer(livingRenderer));
    }
}
