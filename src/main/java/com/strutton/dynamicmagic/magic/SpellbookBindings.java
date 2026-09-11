package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.item.SpellbookItem;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/** Six item-bound spell slots. Slots 0-4 are keys and slot 5 is right click. */
public final class SpellbookBindings {
    public static final int SLOT_COUNT = 6;
    public static final int MAX_MULTICAST = 4;
    private static final String ROOT = "DynamicMagicSpellbook";
    private static final String SLOTS = "Slots";

    private SpellbookBindings() {}

    public static ItemStack heldBook(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof SpellbookItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof SpellbookItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static InteractionHand heldHand(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof SpellbookItem
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static List<List<String>> names(ItemStack book) {
        List<List<String>> result = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) result.add(new ArrayList<>());
        CustomData data = book.get(DataComponents.CUSTOM_DATA);
        if (data == null) return immutable(result);
        ListTag slots = data.copyTag().getCompound(ROOT).getList(SLOTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag entry = slots.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= SLOT_COUNT) continue;
            ListTag values = entry.getList("Names", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(MAX_MULTICAST, values.size()); i++) {
                String name = values.getString(i);
                if (!name.isBlank()) result.get(slot).add(name);
            }
        }
        return immutable(result);
    }

    public static boolean bind(ServerPlayer player, ItemStack book, int slot, String spellName, boolean append) {
        if (slot < 0 || slot >= SLOT_COUNT || spellName == null || spellName.isBlank()) return false;
        CraftedSpell spell = SavedSpellLibrary.find(player, spellName);
        if (spell == null) return false;
        List<List<String>> slots = mutable(names(book));
        List<String> target = slots.get(slot);
        if (!append) target.clear();
        else if (!SkillKnowledge.knows(player, MagicSkill.MULTICAST)) return false;
        if (!target.stream().anyMatch(name -> name.equalsIgnoreCase(spell.name()))
                && target.size() < MAX_MULTICAST) target.add(spell.name());
        write(book, slots);
        return true;
    }

    public static boolean clear(ItemStack book, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        List<List<String>> slots = mutable(names(book));
        slots.get(slot).clear();
        write(book, slots);
        return true;
    }

    public static void forget(ItemStack book, String spellName) {
        List<List<String>> slots = mutable(names(book));
        for (List<String> slot : slots) slot.removeIf(name -> name.equalsIgnoreCase(spellName));
        write(book, slots);
    }

    public static List<CraftedSpell> resolve(ServerPlayer player, ItemStack book, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return List.of();
        List<CraftedSpell> result = new ArrayList<>();
        for (String name : names(book).get(slot)) {
            CraftedSpell spell = SavedSpellLibrary.find(player, name);
            if (spell != null) result.add(spell);
        }
        return List.copyOf(result);
    }

    private static void write(ItemStack book, List<List<String>> values) {
        CompoundTag root = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag bookTag = root.getCompound(ROOT).copy();
        ListTag slots = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (values.get(slot).isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            ListTag names = new ListTag();
            values.get(slot).stream().limit(MAX_MULTICAST).forEach(name -> names.add(StringTag.valueOf(name)));
            entry.put("Names", names);
            slots.add(entry);
        }
        bookTag.put(SLOTS, slots);
        root.put(ROOT, bookTag);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static List<List<String>> mutable(List<List<String>> values) {
        List<List<String>> result = new ArrayList<>(SLOT_COUNT);
        for (List<String> value : values) result.add(new ArrayList<>(value));
        return result;
    }

    private static List<List<String>> immutable(List<List<String>> values) {
        return values.stream().map(List::copyOf).toList();
    }
}
