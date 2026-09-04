package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenSpellcraftRequest(boolean editHeld) implements CustomPacketPayload {
    public static final Type<OpenSpellcraftRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "open_spellcraft_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpellcraftRequest> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeBoolean(value.editHeld),
            buffer -> new OpenSpellcraftRequest(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
