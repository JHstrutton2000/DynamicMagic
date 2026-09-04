package com.strutton.dynamicmagic.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;

/** Opens the player's remotely accessible magical item chest. */
public final class MagicItemStorage {
    private MagicItemStorage() {}
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> ChestMenu.threeRows(containerId, inventory, player.getEnderChestInventory()),
                Component.literal("Magic Item Storage")));
    }
}
