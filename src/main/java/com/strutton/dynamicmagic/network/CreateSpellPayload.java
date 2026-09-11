package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Future-proof recipe payload: the complete ordered spell is encoded as bounded NBT. */
public record CreateSpellPayload(CraftedSpell spell, boolean editHeld, boolean memoryOnly, String originalName) implements CustomPacketPayload {
    public static final Type<CreateSpellPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "create_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateSpellPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeNbt(value.spell.toTag());
                buffer.writeBoolean(value.editHeld);
                buffer.writeBoolean(value.memoryOnly);
                buffer.writeUtf(value.originalName == null ? "" : value.originalName, 32);
            },
            buffer -> {
                CraftedSpell spell = CraftedSpell.fromTag(buffer.readNbt());
                if (spell == null) throw new IllegalArgumentException("Invalid spell recipe");
                return new CreateSpellPayload(spell, buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32));
            });

    public CreateSpellPayload(CraftedSpell spell, boolean editHeld) { this(spell, editHeld, false, ""); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
