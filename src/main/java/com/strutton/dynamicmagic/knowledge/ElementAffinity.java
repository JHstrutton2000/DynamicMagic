package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Focus gained from consuming additional grimoires after an element is known. */
public final class ElementAffinity {
    private static final String KEY = "DynamicMagicElementAffinity";
    public static final int MAX = 10;
    private ElementAffinity() {}

    public static int get(Player player, Element element) {
        return Math.max(0, Math.min(MAX, player.getPersistentData().getCompound(KEY).getInt(element.name())));
    }

    public static boolean increase(Player player, Element element) {
        int before = get(player, element);
        if (before >= MAX) return false;
        CompoundTag affinities = player.getPersistentData().getCompound(KEY).copy();
        affinities.putInt(element.name(), before + 1);
        player.getPersistentData().put(KEY, affinities);
        return true;
    }

    public static void set(Player player, Element element, int value) {
        CompoundTag affinities = player.getPersistentData().getCompound(KEY).copy();
        affinities.putInt(element.name(), Math.max(0, Math.min(MAX, value)));
        player.getPersistentData().put(KEY, affinities);
    }

    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(KEY))
            to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy());
    }
    public static void reset(Player player) { player.getPersistentData().remove(KEY); }
}
