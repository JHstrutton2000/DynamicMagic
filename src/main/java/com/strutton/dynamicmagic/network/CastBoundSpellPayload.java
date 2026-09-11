package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CastBoundSpellPayload(int slot, boolean pressed) implements CustomPacketPayload {
    public static final Type<CastBoundSpellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "cast_bound_spell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastBoundSpellPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.slot); buffer.writeBoolean(value.pressed); },
            buffer -> new CastBoundSpellPayload(buffer.readVarInt(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
