package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public final class CreeperVillagerRenderer extends VillagerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            DynamicMagic.MOD_ID, "textures/entity/creeper_villager.png");
    public CreeperVillagerRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(Villager entity) { return TEXTURE; }
}
