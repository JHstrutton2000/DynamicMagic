package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Remembers mundane item shapes the player has actually held, then produces temporary elemental imitations. */
public final class SeenItemMemory {
    private static final String KEY = "DynamicMagicSeenItems";
    private static final String CONSTRUCT = "DynamicMagicConstruct";
    private SeenItemMemory() {}

    public static void observe(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == DynamicMagic.CRAFTED_SPELL.get()
                || stack.getItem() == DynamicMagic.SPELLBOOK.get() || stack.getItem() == DynamicMagic.SPELL_FOCUS.get()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        ListTag seen = player.getPersistentData().getList(KEY, Tag.TAG_STRING).copy();
        for (Tag tag : seen) if (tag.getAsString().equals(id)) return;
        if (seen.size() >= 64) seen.remove(0);
        seen.add(StringTag.valueOf(id));
        player.getPersistentData().put(KEY, seen);
    }

    public static boolean conjure(ServerPlayer player, Element element, double power) {
        ListTag seen = player.getPersistentData().getList(KEY, Tag.TAG_STRING);
        if (seen.isEmpty()) {
            player.displayClientMessage(Component.literal("Hold a mundane item first so its shape can be remembered.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(seen.getString(seen.size() - 1));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        ItemStack made = BuiltInRegistries.ITEM.get(id).getDefaultInstance();
        made.set(DataComponents.CUSTOM_NAME, Component.literal(element.displayName() + " " + made.getHoverName().getString()).withStyle(ChatFormatting.AQUA));
        CompoundTag marker = new CompoundTag();
        marker.putLong("Expires", player.serverLevel().getGameTime() + (long)(200 + power * 200));
        CompoundTag root = new CompoundTag();
        root.put(CONSTRUCT, marker);
        made.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        if (!player.getInventory().add(made)) player.drop(made, false);
        return true;
    }

    public static void expire(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            CompoundTag root = data.copyTag();
            if (root.contains(CONSTRUCT) && root.getCompound(CONSTRUCT).getLong("Expires") <= now)
                player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }
}
