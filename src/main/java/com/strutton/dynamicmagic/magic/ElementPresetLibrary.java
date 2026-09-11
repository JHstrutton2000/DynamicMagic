package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public final class ElementPresetLibrary {
    private static final String KEY = "DynamicMagicElementPresets";
    private ElementPresetLibrary() {}
    public static List<ElementPreset> presets(ServerPlayer player) {
        List<ElementPreset> result = new ArrayList<>();
        for (Tag tag : player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND)) if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            ElementPreset preset = ElementPreset.fromTag(compound); if (preset != null) result.add(preset);
        }
        return List.copyOf(result);
    }
    public static boolean save(ServerPlayer player, ElementPreset preset) {
        if (preset.entries().size() > 1 && !SkillKnowledge.knows(player, MagicSkill.MANA_COMBINING)) return false;
        if (preset.entries().stream().anyMatch(entry -> !com.strutton.dynamicmagic.knowledge.ElementKnowledge.knows(player, entry.element()))) return false;
        ListTag tags = player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy();
        for (int i = 0; i < tags.size(); i++) if (tags.getCompound(i).getString("Name").equalsIgnoreCase(preset.name())) { tags.set(i, preset.toTag()); player.getPersistentData().put(KEY, tags); return true; }
        if (tags.size() >= 24) tags.remove(0); tags.add(preset.toTag()); player.getPersistentData().put(KEY, tags); return true;
    }
    public static boolean remove(ServerPlayer player, String name) {
        ListTag tags = player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy();
        for (int i = 0; i < tags.size(); i++) if (tags.getCompound(i).getString("Name").equalsIgnoreCase(name)) { tags.remove(i); player.getPersistentData().put(KEY, tags); return true; }
        return false;
    }
    public static void copy(ServerPlayer from, ServerPlayer to) { if (from.getPersistentData().contains(KEY)) to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy()); }
}
