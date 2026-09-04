package com.strutton.dynamicmagic.dragon;

import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

/** Slow, study-driven dragon lore, slayer, Dragonborn, and elemental inheritance progression. */
public final class DragonProgression {
    private static final String LORE = "DynamicMagicDragonLore";
    private static final String KILLS = "DynamicMagicDragonKills";
    private static final String AFFINITIES = "DynamicMagicDragonAffinities";
    private DragonProgression() {}

    public static void study(ServerPlayer player, DragonProfile profile, double power) {
        addLore(player, Math.max(.25, power * .4) * Math.max(.25, profile.ageScale()));
    }

    public static void killed(ServerPlayer player, DragonProfile profile) {
        double scale = Math.max(.1, profile.ageScale());
        player.getPersistentData().putInt(KILLS, player.getPersistentData().getInt(KILLS) + 1);
        double lore = addLore(player, 24 * scale);
        if (!SkillKnowledge.knows(player, MagicSkill.DRAGON_KILLER)
                && player.getRandom().nextDouble() < Math.min(.8, (.12 + lore / 180) * scale)) {
            learn(player, MagicSkill.DRAGON_KILLER);
        } else if (SkillKnowledge.knows(player, MagicSkill.DRAGON_KILLER)
                && !SkillKnowledge.knows(player, MagicSkill.DRAGON_SLAYER) && lore >= 120
                && player.getRandom().nextDouble() < Math.min(.7, (.10 + (lore - 120) / 360) * scale)) {
            learn(player, MagicSkill.DRAGON_SLAYER);
        } else if (SkillKnowledge.knows(player, MagicSkill.DRAGON_SLAYER)
                && !SkillKnowledge.knows(player, MagicSkill.DRAGONBORN) && lore >= 300
                && player.getRandom().nextDouble() < Math.min(.55, (.08 + (lore - 300) / 600) * scale)) {
            learn(player, MagicSkill.DRAGONBORN);
        }
        if (SkillKnowledge.knows(player, MagicSkill.DRAGONBORN)) absorb(player, profile, scale);
    }

    public static int affinity(ServerPlayer player, Element element) {
        return player.getPersistentData().getCompound(AFFINITIES).getInt(element.name());
    }

    public static Progress progress(ServerPlayer player) {
        double lore = player.getPersistentData().getDouble(LORE);
        int kills = player.getPersistentData().getInt(KILLS);
        MagicSkill next;
        double threshold;
        double chance;
        double afterAdultKill = lore + 24;
        if (!SkillKnowledge.knows(player, MagicSkill.DRAGON_KILLER)) {
            next = MagicSkill.DRAGON_KILLER;
            threshold = 0;
            chance = Math.min(.8, .12 + afterAdultKill / 180);
        } else if (!SkillKnowledge.knows(player, MagicSkill.DRAGON_SLAYER)) {
            next = MagicSkill.DRAGON_SLAYER;
            threshold = 120;
            chance = afterAdultKill < threshold ? 0 : Math.min(.7, .10 + (afterAdultKill - 120) / 360);
        } else if (!SkillKnowledge.knows(player, MagicSkill.DRAGONBORN)) {
            next = MagicSkill.DRAGONBORN;
            threshold = 300;
            chance = afterAdultKill < threshold ? 0 : Math.min(.55, .08 + (afterAdultKill - 300) / 600);
        } else {
            next = null;
            threshold = lore;
            chance = 0;
        }
        String affinities = java.util.Arrays.stream(Element.values()).filter(element -> affinity(player, element) > 0)
                .map(element -> element.displayName() + " " + affinity(player, element))
                .collect(Collectors.joining(", "));
        return new Progress(lore, kills, next, threshold, chance, affinities);
    }

    /** Administrative testing hook used by the in-game commands. */
    public static void setLore(ServerPlayer player, double amount) {
        player.getPersistentData().putDouble(LORE, Math.max(0, amount));
    }

    /** Administrative testing hook used by the in-game commands. */
    public static void addTestingLore(ServerPlayer player, double amount) {
        setLore(player, player.getPersistentData().getDouble(LORE) + amount);
    }

    /** Administrative testing hook used by the in-game commands. */
    public static void setAffinity(ServerPlayer player, Element element, int level) {
        CompoundTag values = player.getPersistentData().getCompound(AFFINITIES).copy();
        if (level <= 0) values.remove(element.name());
        else values.putInt(element.name(), Math.min(5, level));
        player.getPersistentData().put(AFFINITIES, values);
    }

    public static void reset(ServerPlayer player) {
        for (String key : new String[]{LORE, KILLS, AFFINITIES}) player.getPersistentData().remove(key);
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        for (String key : new String[]{LORE, KILLS, AFFINITIES})
            if (from.getPersistentData().contains(key)) to.getPersistentData().put(key, from.getPersistentData().get(key).copy());
    }

    private static double addLore(ServerPlayer player, double amount) {
        double lore = player.getPersistentData().getDouble(LORE) + amount;
        player.getPersistentData().putDouble(LORE, lore);
        return lore;
    }

    private static void absorb(ServerPlayer player, DragonProfile profile, double ageScale) {
        Element element = profile.elements().stream()
                .min(Comparator.comparingInt(value -> affinity(player, value))).orElse(Element.ARCANE);
        CompoundTag affinities = player.getPersistentData().getCompound(AFFINITIES).copy();
        int distinct = (int) affinities.getAllKeys().stream().filter(key -> affinities.getInt(key) > 0).count();
        int current = affinities.getInt(element.name());
        double chance = (.55 * ageScale) / Math.pow(2, Math.max(0, distinct - (current > 0 ? 1 : 0)));
        if (current >= 5 || player.getRandom().nextDouble() >= chance) return;
        affinities.putInt(element.name(), current + 1);
        player.getPersistentData().put(AFFINITIES, affinities);
        player.displayClientMessage(Component.literal("Dragonborn inheritance: " + element.displayName()
                + " affinity " + (current + 1)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void learn(ServerPlayer player, MagicSkill skill) {
        if (SkillKnowledge.learn(player, skill))
            player.displayClientMessage(Component.literal("Dragon breakthrough: " + skill.displayName())
                    .withStyle(ChatFormatting.GOLD), false);
    }

    public record Progress(double lore, int kills, MagicSkill nextSkill, double threshold,
                           double nextAdultKillChance, String affinities) {
        public String summary() {
            String affinityText = affinities.isBlank() ? "none" : affinities;
            if (nextSkill == null) return String.format(Locale.ROOT,
                    "Dragon progress — lore %.1f, kills %d, all dragon skills learned; affinities: %s.",
                    lore, kills, affinityText);
            String requirement = threshold <= 0 ? "no hard lore minimum"
                    : String.format(Locale.ROOT, "%.1f/%.0f lore", lore, threshold);
            return String.format(Locale.ROOT,
                    "Dragon progress — lore %.1f, kills %d; next: %s (%s); projected chance on next adult kill: %.1f%%; affinities: %s.",
                    lore, kills, nextSkill.displayName(), requirement, nextAdultKillChance * 100, affinityText);
        }
    }
}
