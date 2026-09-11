package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.ElementPreset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveElementPresetPayload(ElementPreset preset, boolean delete) implements CustomPacketPayload {
    public static final Type<SaveElementPresetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "save_element_preset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveElementPresetPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeNbt(value.preset.toTag()); buffer.writeBoolean(value.delete); },
            buffer -> new SaveElementPresetPayload(ElementPreset.fromTag(buffer.readNbt()), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
