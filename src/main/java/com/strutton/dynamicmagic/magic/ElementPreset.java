package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/** A reusable normalized recipe for distributing one spell's elemental mana. */
public record ElementPreset(String name, List<Entry> entries) {
    public ElementPreset {
        name = name == null || name.isBlank() ? "Element Preset" : name.substring(0, Math.min(32, name.length()));
        entries = List.copyOf(entries == null ? List.of() : entries.stream().filter(e -> e.ratio() > 0).limit(8).toList());
    }
    public record Entry(Element element, int ratio) {}
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag(); tag.putString("Name", name);
        ListTag values = new ListTag();
        for (Entry entry : entries) { CompoundTag value = new CompoundTag(); value.putString("Element", entry.element().name()); value.putInt("Ratio", entry.ratio()); values.add(value); }
        tag.put("Entries", values); return tag;
    }
    public static ElementPreset fromTag(CompoundTag tag) {
        List<Entry> entries = new ArrayList<>();
        for (Tag value : tag.getList("Entries", Tag.TAG_COMPOUND)) if (value instanceof CompoundTag entry) {
            try { entries.add(new Entry(Element.valueOf(entry.getString("Element")), Math.max(1, Math.min(100, entry.getInt("Ratio"))))); }
            catch (IllegalArgumentException ignored) {}
        }
        return entries.isEmpty() ? null : new ElementPreset(tag.getString("Name"), entries);
    }
}
