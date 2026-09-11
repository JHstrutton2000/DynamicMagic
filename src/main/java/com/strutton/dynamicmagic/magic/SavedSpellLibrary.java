package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** The caster's reusable recipe book. Creating a spell updates its recipe by name. */
public final class SavedSpellLibrary {
    private static final String KEY = "DynamicMagicSavedSpells";
    public static final int CAPACITY = 64;
    private SavedSpellLibrary() {}

    public static void remember(ServerPlayer player, CraftedSpell spell) {
        ListTag tags = tags(player);
        for (int i = 0; i < tags.size(); i++) {
            if (tags.getCompound(i).getString("Name").equalsIgnoreCase(spell.name())) {
                tags.set(i, spell.toTag());
                player.getPersistentData().put(KEY, tags);
                return;
            }
        }
        if (tags.size() >= CAPACITY) tags.remove(0);
        tags.add(spell.toTag());
        player.getPersistentData().put(KEY, tags);
    }

    public static List<CraftedSpell> spells(ServerPlayer player) {
        List<CraftedSpell> result = new ArrayList<>();
        for (Tag tag : tags(player)) if (tag instanceof CompoundTag compound) {
            CraftedSpell spell = CraftedSpell.fromTag(compound);
            if (spell != null) result.add(spell);
        }
        return List.copyOf(result);
    }

    public static CraftedSpell find(ServerPlayer player, String name) {
        if (name == null) return null;
        return spells(player).stream().filter(spell -> spell.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static boolean remove(ServerPlayer player, String name) {
        if (name == null) return false;
        ListTag values = tags(player);
        for (int i = 0; i < values.size(); i++) {
            if (values.getCompound(i).getString("Name").equalsIgnoreCase(name)) {
                values.remove(i);
                player.getPersistentData().put(KEY, values);
                return true;
            }
        }
        return false;
    }

    public static void copy(ServerPlayer from, ServerPlayer to) { to.getPersistentData().put(KEY, tags(from)); }
    private static ListTag tags(ServerPlayer player) { return player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy(); }
}
