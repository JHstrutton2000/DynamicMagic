package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.network.OpenSpellbookPayload;
import com.strutton.dynamicmagic.network.ManaSyncPayload;
import com.strutton.dynamicmagic.network.OpenEntityStoragePayload;
import com.strutton.dynamicmagic.network.MorphMageSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}
    public static void openSpellbook(OpenSpellbookPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSpellbook.open(payload));
    }
    public static void syncMana(ManaSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientManaState.update(payload.current(), payload.maximum(), payload.unlimited()));
    }
    public static void openEntityStorage(OpenEntityStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new EntityStorageScreen(payload.names())));
    }
    public static void syncMorphMage(MorphMageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getEntity(payload.entityId()) instanceof net.minecraft.world.entity.LivingEntity entity)
                com.strutton.dynamicmagic.mage.MorphMageEvents.applyClientVisual(entity, payload.eyeColor());
        });
    }
    public static void openSpellNode(com.strutton.dynamicmagic.network.OpenSpellNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new SpellNodeScreen(payload)));
    }
}
