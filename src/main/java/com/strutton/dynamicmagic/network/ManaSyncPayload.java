package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ManaSyncPayload(double current, double maximum, boolean unlimited) implements CustomPacketPayload {
    public static final Type<ManaSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManaSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeDouble(value.current); buffer.writeDouble(value.maximum); buffer.writeBoolean(value.unlimited); },
            buffer -> new ManaSyncPayload(buffer.readDouble(), buffer.readDouble(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
