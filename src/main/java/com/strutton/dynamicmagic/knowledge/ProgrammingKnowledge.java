package com.strutton.dynamicmagic.knowledge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Learned programming discipline and its separately trained branch capacity. */
public final class ProgrammingKnowledge {
    private static final String LEARNED = "DynamicMagicProgramming";
    private static final String MASTERY = "DynamicMagicProgrammingMastery";
    private static final double[] BRANCH_THRESHOLDS = {0, 8, 24, 50, 90, 150, 230, 330};
    private ProgrammingKnowledge() {}

    public static boolean knows(Player player) { return player.getPersistentData().getBoolean(LEARNED); }
    public static boolean learn(Player player) {
        if (knows(player)) return false;
        player.getPersistentData().putBoolean(LEARNED, true);
        player.getPersistentData().putDouble(MASTERY, 0);
        return true;
    }
    public static double mastery(Player player) { return Math.max(0, player.getPersistentData().getDouble(MASTERY)); }
    public static int maxBranches(Player player) {
        if (!knows(player)) return 0;
        double xp = mastery(player);
        int capacity = 1;
        while (capacity < BRANCH_THRESHOLDS.length && xp >= BRANCH_THRESHOLDS[capacity]) capacity++;
        return capacity;
    }
    public static void practice(ServerPlayer player, double amount) {
        if (!knows(player) || amount <= 0) return;
        int before = maxBranches(player);
        player.getPersistentData().putDouble(MASTERY, mastery(player) + amount);
        int after = maxBranches(player);
        if (after > before) player.displayClientMessage(Component.literal(
                "Programming mastery increased: you can now build " + after + " branches.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }
    public static void learnAll(Player player) {
        player.getPersistentData().putBoolean(LEARNED, true);
        player.getPersistentData().putDouble(MASTERY, BRANCH_THRESHOLDS[BRANCH_THRESHOLDS.length - 1]);
    }
    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(LEARNED)) to.getPersistentData().putBoolean(LEARNED, knows(from));
        if (from.getPersistentData().contains(MASTERY)) to.getPersistentData().putDouble(MASTERY, mastery(from));
    }
    public static void reset(Player player) { player.getPersistentData().remove(LEARNED); player.getPersistentData().remove(MASTERY); }
}
