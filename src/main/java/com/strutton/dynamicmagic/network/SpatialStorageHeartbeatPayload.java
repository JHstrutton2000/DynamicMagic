package com.strutton.dynamicmagic.network;
import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record SpatialStorageHeartbeatPayload() implements CustomPacketPayload {
    public static final Type<SpatialStorageHeartbeatPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "spatial_storage_heartbeat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpatialStorageHeartbeatPayload> STREAM_CODEC = StreamCodec.unit(new SpatialStorageHeartbeatPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
