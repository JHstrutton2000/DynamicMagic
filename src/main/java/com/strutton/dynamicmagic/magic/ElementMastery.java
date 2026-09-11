package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.List;

/** Each element is practiced independently; mastery expands safe force instead of merely reducing cost. */
public final class ElementMastery {
    private static final String KEY = "DynamicMagicElementMastery";
    private ElementMastery() {}
    public static double experience(ServerPlayer player, Element element) {
        return player.getPersistentData().getCompound(KEY).getDouble(element.name());
    }
    public static double maxForce(ServerPlayer player, Element element) {
        return Math.min(12, 3.0 + Math.sqrt(experience(player, element)) * .22
                + com.strutton.dynamicmagic.compat.SoloLevelingIntegration.forceBonus(player, element));
    }
    public static void practice(ServerPlayer player, Element element, double complexity) {
        CompoundTag values = player.getPersistentData().getCompound(KEY).copy();
        double multiplier = com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.ELEMENTAL_SAVANT) ? 1.25 : 1;
        multiplier *= com.strutton.dynamicmagic.compat.SoloLevelingIntegration.learningMultiplier(player, element);
        values.putDouble(element.name(), values.getDouble(element.name()) + Math.max(.25, complexity) * multiplier);
        player.getPersistentData().put(KEY, values);
    }
    public static List<Double> forceCaps(ServerPlayer player) {
        return Arrays.stream(Element.values()).map(element -> maxForce(player, element)).toList();
    }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY)) to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy());
    }
}
