package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Persistent projectile discipline: use gradually removes ray spread, but never grants perfect aim. */
public final class ProjectileAccuracy {
    private static final String KEY = "DynamicMagicProjectileAccuracyExperience";
    private ProjectileAccuracy() {}

    public static double experience(ServerPlayer player) { return player.getPersistentData().getDouble(KEY); }
    public static double value(ServerPlayer player) {
        return Math.min(.99, .55 + Math.sqrt(experience(player)) * .018);
    }
    public static void practice(ServerPlayer player, CraftedSpell spell) {
        if (spell.delivery() != DeliveryType.PROJECTILE) return;
        double difficulty = spell.allInstructions().stream().mapToDouble(SpellInstruction::range).average().orElse(8) / 16.0;
        player.getPersistentData().putDouble(KEY, experience(player) + Math.max(.25, difficulty));
    }
    public static Vec3 applySpread(ServerPlayer player, Vec3 direction) {
        double spread = (1.0 - value(player)) * .085;
        if (spread <= .001) return direction.normalize();
        return direction.add(player.getRandom().nextGaussian() * spread,
                player.getRandom().nextGaussian() * spread,
                player.getRandom().nextGaussian() * spread).normalize();
    }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY, Tag.TAG_DOUBLE))
            to.getPersistentData().putDouble(KEY, experience(from));
    }
    public static void reset(ServerPlayer player) { player.getPersistentData().remove(KEY); }
}
