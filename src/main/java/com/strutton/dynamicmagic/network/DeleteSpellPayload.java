package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteSpellPayload(String spellName) implements CustomPacketPayload {
    public static final Type<DeleteSpellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "delete_spell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteSpellPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.spellName, 32), buffer -> new DeleteSpellPayload(buffer.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
