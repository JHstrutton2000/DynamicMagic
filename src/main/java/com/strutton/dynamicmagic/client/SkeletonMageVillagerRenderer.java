package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.entity.SkeletonMageVillager;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Uses villager geometry without profession overlays so exposed bones and torn robes remain visible. */
public final class SkeletonMageVillagerRenderer
        extends MobRenderer<SkeletonMageVillager, VillagerModel<SkeletonMageVillager>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DynamicMagic.MOD_ID, "textures/entity/skeleton_mage_villager.png");

    public SkeletonMageVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), .5f);
    }

    @Override public ResourceLocation getTextureLocation(SkeletonMageVillager entity) { return TEXTURE; }
}
