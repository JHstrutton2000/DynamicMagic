package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Persistent practice-derived control and efficiency. Complicated casting trains both stats slowly. */
public final class CasterMastery {
    private static final String CONTROL = "DynamicMagicControl";
    private static final String EFFICIENCY = "DynamicMagicEfficiency";
    private static final String EXPERIENCE = "DynamicMagicMasteryExperience";
    private CasterMastery() {}

    public static CasterStats stats(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        double control = data.contains(CONTROL) ? data.getDouble(CONTROL) : 3.0;
        double efficiency = data.contains(EFFICIENCY) ? data.getDouble(EFFICIENCY) : 1.0;
        control += com.strutton.dynamicmagic.compat.SoloLevelingIntegration.controlBonus(player);
        efficiency *= com.strutton.dynamicmagic.compat.SoloLevelingIntegration.efficiencyMultiplier(player);
        return new CasterStats(control, efficiency);
    }

    public static void practice(ServerPlayer player, SpellDefinition definition, double instability) {
        CompoundTag data = player.getPersistentData();
        double experience = data.getDouble(EXPERIENCE) + .35 + definition.effects().size() * .08 + instability;
        data.putDouble(EXPERIENCE, experience);
        data.putDouble(CONTROL, Math.min(30, 3 + Math.sqrt(experience) * .42));
        data.putDouble(EFFICIENCY, Math.min(3, 1 + Math.log1p(experience) * .055));
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        for (String key : new String[]{CONTROL, EFFICIENCY, EXPERIENCE})
            if (from.getPersistentData().contains(key)) to.getPersistentData().put(key, from.getPersistentData().get(key).copy());
    }
}
