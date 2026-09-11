package com.strutton.dynamicmagic.network;
import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record ConfigureSpellNodePayload(BlockPos pos, String spellName, int ejectSlot) implements CustomPacketPayload {
    public static final Type<ConfigureSpellNodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID,"configure_spell_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureSpellNodePayload> STREAM_CODEC=StreamCodec.of((b,v)->{b.writeBlockPos(v.pos);b.writeUtf(v.spellName,32);b.writeVarInt(v.ejectSlot+1);},b->new ConfigureSpellNodePayload(b.readBlockPos(),b.readUtf(32),b.readVarInt()-1));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
