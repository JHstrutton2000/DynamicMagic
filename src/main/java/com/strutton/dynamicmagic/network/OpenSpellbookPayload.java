package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.knowledge.KnowledgeSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import java.util.ArrayList;
import java.util.List;

public record OpenSpellbookPayload(long elements, long forms, long deliveries, long impacts,
                                   long directions, long conditions, boolean programming,
                                   double programmingMastery, int maxProgramBranches,
                                   double control, double efficiency, List<CraftedSpell> savedSpells,
                                   CraftedSpell editingSpell, List<Double> forceCaps) implements CustomPacketPayload {
    public static final Type<OpenSpellbookPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "open_spellbook"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpellbookPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeLong(value.elements); buffer.writeLong(value.forms); buffer.writeLong(value.deliveries);
                buffer.writeLong(value.impacts); buffer.writeLong(value.directions); buffer.writeLong(value.conditions);
                buffer.writeBoolean(value.programming);
                buffer.writeDouble(value.programmingMastery); buffer.writeVarInt(value.maxProgramBranches);
                buffer.writeDouble(value.control); buffer.writeDouble(value.efficiency);
                buffer.writeVarInt(value.savedSpells.size());
                for (CraftedSpell spell : value.savedSpells) buffer.writeNbt(spell.toTag());
                buffer.writeBoolean(value.editingSpell != null);
                if (value.editingSpell != null) buffer.writeNbt(value.editingSpell.toTag());
                buffer.writeVarInt(value.forceCaps.size());
                for (double cap : value.forceCaps) buffer.writeDouble(cap);
            }, buffer -> {
                long elements = buffer.readLong(), forms = buffer.readLong(), deliveries = buffer.readLong();
                long impacts = buffer.readLong(), directions = buffer.readLong(), conditions = buffer.readLong();
                boolean programming = buffer.readBoolean();
                double programmingMastery = buffer.readDouble(); int maxProgramBranches = buffer.readVarInt();
                double control = buffer.readDouble(), efficiency = buffer.readDouble();
                int count = Math.min(64, buffer.readVarInt());
                List<CraftedSpell> saved = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    CraftedSpell spell = CraftedSpell.fromTag(buffer.readNbt());
                    if (spell != null) saved.add(spell);
                }
                CraftedSpell editing = buffer.readBoolean() ? CraftedSpell.fromTag(buffer.readNbt()) : null;
                int capCount = Math.min(com.strutton.dynamicmagic.magic.Element.values().length, buffer.readVarInt());
                List<Double> caps = new ArrayList<>();
                for (int i = 0; i < capCount; i++) caps.add(buffer.readDouble());
                return new OpenSpellbookPayload(elements, forms, deliveries, impacts, directions, conditions, programming,
                        programmingMastery, maxProgramBranches, control, efficiency, saved, editing, caps);
            });
    public OpenSpellbookPayload(KnowledgeSnapshot value, com.strutton.dynamicmagic.magic.CasterStats stats,
                                List<CraftedSpell> savedSpells, CraftedSpell editingSpell, List<Double> forceCaps) {
        this(value.elements(), value.forms(), value.deliveries(), value.impacts(), value.directions(), value.conditions(), value.programming(),
                value.programmingMastery(), value.maxProgramBranches(),
                stats.control(), stats.efficiency(), List.copyOf(savedSpells), editingSpell, List.copyOf(forceCaps));
    }
    public KnowledgeSnapshot snapshot() { return new KnowledgeSnapshot(elements, forms, deliveries, impacts, directions, conditions,
            programming, programmingMastery, maxProgramBranches); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
