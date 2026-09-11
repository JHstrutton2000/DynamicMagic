package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BindSpellPayload(int slot, String spellName, boolean append) implements CustomPacketPayload {
    public static final Type<BindSpellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "bind_spell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindSpellPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.slot); buffer.writeUtf(value.spellName, 32); buffer.writeBoolean(value.append); },
            buffer -> new BindSpellPayload(buffer.readVarInt(), buffer.readUtf(32), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
