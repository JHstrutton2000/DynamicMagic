package com.strutton.dynamicmagic.mana;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Mana {
    private static final String CURRENT = "dynamicmagic.mana";
    private static final String MAXIMUM = "dynamicmagic.max_mana";
    private static final String UNLIMITED = "dynamicmagic.unlimited_mana";
    private static final String EXHAUSTED = "dynamicmagic.mana_exhausted";
    private static final String EXPANSION_READY_AT = "dynamicmagic.mana_expansion_ready_at";
    private static final String EXPANSION_HUNGER_DEBT = "dynamicmagic.mana_expansion_hunger_debt";
    private static final long EXPANSION_COOLDOWN_TICKS = 12_000L;
    private static final float EXPANSION_EXHAUSTION = 4.0f;
    public static final double DEFAULT_MAX = 100;
    private Mana() {}

    public static double get(ServerPlayer player) {
        if (isUnlimited(player)) return Double.POSITIVE_INFINITY;
        var data = player.getPersistentData();
        return data.contains(CURRENT) ? Math.min(data.getDouble(CURRENT), max(player)) : max(player);
    }

    public static double max(ServerPlayer player) {
        var data = player.getPersistentData();
        return data.contains(MAXIMUM) ? Math.max(1, data.getDouble(MAXIMUM)) : DEFAULT_MAX;
    }

    public static boolean isUnlimited(ServerPlayer player) { return player.getPersistentData().getBoolean(UNLIMITED); }

    public static void set(ServerPlayer player, double value) {
        if (!Double.isFinite(value)) return;
        player.getPersistentData().putDouble(CURRENT, Math.max(0, Math.min(max(player), value)));
        if (value > max(player) * .1) player.getPersistentData().putBoolean(EXHAUSTED, false);
    }

    public static void setMaximum(ServerPlayer player, double value) {
        if (!Double.isFinite(value)) return;
        player.getPersistentData().putDouble(MAXIMUM, Math.max(1, value));
        player.getPersistentData().putBoolean(UNLIMITED, false);
        set(player, Math.min(get(player), max(player)));
    }

    public static void setUnlimited(ServerPlayer player, boolean value) {
        player.getPersistentData().putBoolean(UNLIMITED, value);
        if (!value) set(player, max(player));
    }

    public static void refill(ServerPlayer player) { if (!isUnlimited(player)) set(player, max(player)); }
    public static String display(ServerPlayer player) {
        return isUnlimited(player) ? "∞" : (int) get(player) + "/" + (int) max(player);
    }

    public static boolean consume(ServerPlayer player, double amount) {
        if (amount <= 0 || isUnlimited(player)) return true;
        double current = get(player);
        if (current + 1.0e-6 < amount) {
            double shortfall = amount - current;
            set(player, 0);
            failedOverdraw(player, shortfall);
            return false;
        }
        set(player, current - amount);
        if (get(player) <= 1.0e-6) genuineExhaustion(player);
        return true;
    }

    private static void failedOverdraw(ServerPlayer player, double shortfall) {
        boolean alreadyExhausted = player.getPersistentData().getBoolean(EXHAUSTED);
        player.getPersistentData().putBoolean(EXHAUSTED, true);
        applyExhaustionPenalty(player);
        if (shortfall <= 50.0 + 1.0e-6 && !alreadyExhausted && expansionReady(player)) {
            double growth = growMaximum(player);
            player.displayClientMessage(Component.literal("Near exhaustion! The spell was short by "
                    + String.format(java.util.Locale.ROOT, "%.2f", shortfall)
                    + " mana, and your maximum mana grew by exactly 1% (+"
                    + String.format(java.util.Locale.ROOT, "%.2f", growth) + ").")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        } else if (shortfall <= 50.0 + 1.0e-6 && !alreadyExhausted) {
            player.displayClientMessage(Component.literal("Your mana pool needs another "
                    + expansionCooldownSeconds(player) + " seconds before it can expand again.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        } else {
            player.displayClientMessage(Component.literal(shortfall > 50.0 + 1.0e-6
                    ? "The spell exceeded your remaining mana by more than 50. Your pool was emptied without growing."
                    : "Your mana must recover above 10% before exhaustion can grow the pool again.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }

    private static void genuineExhaustion(ServerPlayer player) {
        boolean alreadyExhausted = player.getPersistentData().getBoolean(EXHAUSTED);
        player.getPersistentData().putBoolean(EXHAUSTED, true);
        applyExhaustionPenalty(player);
        if (alreadyExhausted) return;
        if (!expansionReady(player)) {
            player.displayClientMessage(Component.literal("Your mana pool needs another "
                    + expansionCooldownSeconds(player) + " seconds before it can expand again.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            return;
        }
        double growth = growMaximum(player);
        player.displayClientMessage(Component.literal("Mana exhaustion! Your maximum mana grew by exactly 1% (+"
                + String.format(java.util.Locale.ROOT, "%.2f", growth) + ").").withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    private static double growMaximum(ServerPlayer player) {
        double currentMaximum = max(player);
        double growth = currentMaximum * .01;
        player.getPersistentData().putDouble(MAXIMUM, currentMaximum + growth);
        player.getPersistentData().putLong(EXPANSION_READY_AT,
                player.serverLevel().getGameTime() + EXPANSION_COOLDOWN_TICKS);
        player.getFoodData().addExhaustion(EXPANSION_EXHAUSTION);
        if (com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.HUNGER_WARD)) {
            var data = player.getPersistentData();
            data.putInt(EXPANSION_HUNGER_DEBT, data.getInt(EXPANSION_HUNGER_DEBT) + 1);
        }
        return growth;
    }

    static boolean consumeExpansionHungerDebt(ServerPlayer player) {
        var data = player.getPersistentData();
        int debt = data.getInt(EXPANSION_HUNGER_DEBT);
        if (debt <= 0) return false;
        data.putInt(EXPANSION_HUNGER_DEBT, debt - 1);
        return true;
    }

    private static boolean expansionReady(ServerPlayer player) {
        return player.serverLevel().getGameTime()
                >= player.getPersistentData().getLong(EXPANSION_READY_AT);
    }

    private static long expansionCooldownSeconds(ServerPlayer player) {
        long remainingTicks = Math.max(0, player.getPersistentData().getLong(EXPANSION_READY_AT)
                - player.serverLevel().getGameTime());
        return (remainingTicks + 19) / 20;
    }

    private static void applyExhaustionPenalty(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        for (String key : new String[]{CURRENT, MAXIMUM, UNLIMITED, EXHAUSTED,
                EXPANSION_READY_AT, EXPANSION_HUNGER_DEBT})
            if (original.getPersistentData().contains(key))
                replacement.getPersistentData().put(key, original.getPersistentData().get(key).copy());
    }
}
