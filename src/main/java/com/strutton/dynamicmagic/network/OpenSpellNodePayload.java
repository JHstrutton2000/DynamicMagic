package com.strutton.dynamicmagic.network;
import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
public record OpenSpellNodePayload(BlockPos pos, List<String> spells, String selected, List<String> crystals) implements CustomPacketPayload {
    public static final Type<OpenSpellNodePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "open_spell_node"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpellNodePayload> STREAM_CODEC = StreamCodec.of((b,v)->{ b.writeBlockPos(v.pos); b.writeVarInt(v.spells.size()); for(String s:v.spells)b.writeUtf(s,32); b.writeUtf(v.selected,32); b.writeVarInt(v.crystals.size()); for(String s:v.crystals)b.writeUtf(s,64); }, b->{ BlockPos p=b.readBlockPos(); int n=Math.min(64,b.readVarInt()); List<String>s=new ArrayList<>(); for(int i=0;i<n;i++)s.add(b.readUtf(32)); String selected=b.readUtf(32); int c=Math.min(4,b.readVarInt()); List<String>x=new ArrayList<>(); for(int i=0;i<c;i++)x.add(b.readUtf(64)); return new OpenSpellNodePayload(p,s,selected,x); });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
