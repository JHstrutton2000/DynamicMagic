package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SummonStoredEntityPayload(int index) implements CustomPacketPayload {
    public static final Type<SummonStoredEntityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "summon_entity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SummonStoredEntityPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.index),
            buffer -> new SummonStoredEntityPayload(buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
