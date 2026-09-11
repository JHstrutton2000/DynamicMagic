package com.strutton.dynamicmagic.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Opens the player's remotely accessible magical item chest. */
public final class MagicItemStorage {
    private static final String KEY = "DynamicMagicSpatialItems";
    private static final int CAPACITY = 54;
    private MagicItemStorage() {}
    public static void open(ServerPlayer player) {
        SimpleContainer storage = contents(player);
        storage.addListener(ignored -> save(player, storage));
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> ChestMenu.sixRows(containerId, inventory, storage),
                Component.literal("Spatial Backpack")));
    }

    /** Targeting yourself stores possessions, then ejects the caster into the void as warned by the spell. */
    public static boolean stashInventoryAndKill(ServerPlayer player) {
        SimpleContainer storage = contents(player);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = storage.addItem(stack.copy());
            if (!remainder.isEmpty()) {
                player.displayClientMessage(Component.literal("The spatial backpack cannot hold your complete inventory."), true);
                return false;
            }
        }
        player.getInventory().clearContent(); save(player, storage);
        player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        return true;
    }

    private static SimpleContainer contents(ServerPlayer player) {
        SimpleContainer result = new SimpleContainer(CAPACITY);
        for (Tag value : player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND)) if (value instanceof CompoundTag entry) {
            int slot = entry.getInt("Slot"); if (slot >= 0 && slot < CAPACITY)
                result.setItem(slot, ItemStack.parseOptional(player.registryAccess(), entry.getCompound("Item")));
        }
        return result;
    }
    private static void save(ServerPlayer player, SimpleContainer storage) {
        ListTag values = new ListTag();
        for (int slot = 0; slot < storage.getContainerSize(); slot++) if (!storage.getItem(slot).isEmpty()) {
            CompoundTag entry = new CompoundTag(); entry.putInt("Slot", slot); entry.put("Item", storage.getItem(slot).save(player.registryAccess())); values.add(entry);
        }
        player.getPersistentData().put(KEY, values);
    }
}
