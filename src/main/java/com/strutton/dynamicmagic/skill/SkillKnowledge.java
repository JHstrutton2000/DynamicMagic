package com.strutton.dynamicmagic.skill;

import net.minecraft.server.level.ServerPlayer;

public final class SkillKnowledge {
    private static final String KEY = "DynamicMagicSkills";
    private SkillKnowledge() {}
    public static boolean knows(ServerPlayer player, MagicSkill skill) {
        return (player.getPersistentData().getLong(KEY) & (1L << skill.ordinal())) != 0;
    }
    public static long mask(ServerPlayer player) { return player.getPersistentData().getLong(KEY); }
    public static boolean learn(ServerPlayer player, MagicSkill skill) {
        long before = player.getPersistentData().getLong(KEY);
        player.getPersistentData().putLong(KEY, before | (1L << skill.ordinal()));
        return (before & (1L << skill.ordinal())) == 0;
    }
    public static int learnAll(ServerPlayer player) {
        int learned = 0;
        for (MagicSkill skill : MagicSkill.values()) if (learn(player, skill)) learned++;
        return learned;
    }
    public static boolean forget(ServerPlayer player, MagicSkill skill) {
        long before = player.getPersistentData().getLong(KEY);
        player.getPersistentData().putLong(KEY, before & ~(1L << skill.ordinal()));
        return (before & (1L << skill.ordinal())) != 0;
    }
    public static void forgetAll(ServerPlayer player) { player.getPersistentData().remove(KEY); }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY)) to.getPersistentData().putLong(KEY, from.getPersistentData().getLong(KEY));
    }
}
