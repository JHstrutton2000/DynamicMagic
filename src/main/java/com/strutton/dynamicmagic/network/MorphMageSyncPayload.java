package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronizes the visual tell of an otherwise vanilla morph-mage entity. */
public record MorphMageSyncPayload(int entityId, int eyeColor) implements CustomPacketPayload {
    public static final Type<MorphMageSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "morph_mage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphMageSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.entityId);
                buffer.writeVarInt(value.eyeColor);
            },
            buffer -> new MorphMageSyncPayload(buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
