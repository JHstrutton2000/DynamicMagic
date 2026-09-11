package com.strutton.dynamicmagic.network;
import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CloseSpatialStoragePayload() implements CustomPacketPayload {
    public static final Type<CloseSpatialStoragePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "close_spatial_storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseSpatialStoragePayload> STREAM_CODEC = StreamCodec.unit(new CloseSpatialStoragePayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
