package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Persistent, server-authoritative elemental knowledge. */
public final class ElementKnowledge {
    private static final String KEY = "DynamicMagicKnownElements";
    private ElementKnowledge() {}

    public static boolean knows(Player player, Element element) {
        return (mask(player) & bit(element)) != 0;
    }

    public static boolean learn(Player player, Element element) {
        long before = mask(player);
        player.getPersistentData().putLong(KEY, before | bit(element));
        ComponentKnowledge.learnIntrinsic(player, element);
        return before != mask(player);
    }

    public static void learnAll(Player player) {
        long value = 0;
        for (Element element : Element.values()) value |= bit(element);
        player.getPersistentData().putLong(KEY, value);
        for (Element element : Element.values()) ComponentKnowledge.learnIntrinsic(player, element);
    }

    public static void forgetAll(Player player) { player.getPersistentData().remove(KEY); }
    public static long mask(Player player) { return player.getPersistentData().getLong(KEY); }
    public static boolean inMask(long mask, Element element) { return (mask & bit(element)) != 0; }
    private static long bit(Element element) { return 1L << element.ordinal(); }

    public static List<Element> known(Player player) {
        List<Element> result = new ArrayList<>();
        for (Element element : Element.values()) if (knows(player, element)) result.add(element);
        return result;
    }

    public static void copy(Player original, Player replacement) {
        replacement.getPersistentData().putLong(KEY, mask(original));
    }
}
