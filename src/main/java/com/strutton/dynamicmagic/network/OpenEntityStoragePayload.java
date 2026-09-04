package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenEntityStoragePayload(List<String> names) implements CustomPacketPayload {
    public OpenEntityStoragePayload { names = List.copyOf(names); }
    public static final Type<OpenEntityStoragePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "entity_storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenEntityStoragePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.names.size());
                for (String name : value.names) buffer.writeUtf(name, 64);
            }, buffer -> {
                int count = Math.min(32, buffer.readVarInt());
                List<String> names = new ArrayList<>(count);
                for (int i = 0; i < count; i++) names.add(buffer.readUtf(64));
                return new OpenEntityStoragePayload(names);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
