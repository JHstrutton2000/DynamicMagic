package com.strutton.dynamicmagic.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.effect.MobEffects;
import com.strutton.dynamicmagic.mana.Mana;

import java.util.List;

/** Evaluates the sensor/condition portion of a running spell program. */
public final class SpellProgramRunner {
    private SpellProgramRunner() {}

    public static boolean conditionMet(ServerPlayer player, CraftedSpell spell) {
        SpellBranch branch = spell.branches().get(0);
        return conditionMet(player, branch, detectedTarget(player, branch));
    }

    public static boolean conditionMet(ServerPlayer player, CraftedSpell spell, LivingEntity detected) {
        return conditionMet(player, spell.branches().get(0), detected);
    }

    public static LivingEntity detectedTarget(ServerPlayer player, CraftedSpell spell) {
        return detectedTarget(player, spell.branches().get(0));
    }

    public static boolean conditionMet(ServerPlayer player, SpellBranch branch, LivingEntity detected) {
        return branch.condition() == ConditionType.ALWAYS || selfCondition(player, branch.condition()) || detected != null;
    }

    public static LivingEntity detectedTarget(ServerPlayer player, SpellBranch branch) {
        double range = branch.detectionRange();
        List<LivingEntity> nearby = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), entity -> entity != player && entity.isAlive());
        return switch (branch.condition()) {
            case ENTITY_NEARBY -> nearest(player, nearby.stream());
            case HOSTILE_NEARBY -> nearest(player, nearby.stream().filter(entity -> entity instanceof Enemy
                    || entity instanceof Mob mob && mob.getTarget() == player));
            case ENTITY_IN_SIGHT -> nearest(player, nearby.stream().filter(player::hasLineOfSight));
            default -> null;
        };
    }

    /** Returns every branch due on this tick whose condition currently passes, preserving authoring order. */
    public static List<BranchMatch> matchingBranches(ServerPlayer player, CraftedSpell spell, int tickCount) {
        java.util.ArrayList<BranchMatch> matches = new java.util.ArrayList<>();
        for (SpellBranch branch : spell.branches()) {
            if (tickCount % branch.intervalTicks() != 0) continue;
            LivingEntity detected = detectedTarget(player, branch);
            if (conditionMet(player, branch, detected)) matches.add(new BranchMatch(branch, detected));
        }
        return List.copyOf(matches);
    }

    public record BranchMatch(SpellBranch branch, LivingEntity detected) {}

    private static boolean selfCondition(ServerPlayer player, ConditionType condition) {
        return switch (condition) {
            case HEALTH_BELOW_HALF -> player.getHealth() < player.getMaxHealth() * .5f;
            case HEALTH_BELOW_QUARTER -> player.getHealth() < player.getMaxHealth() * .25f;
            case ON_GROUND -> player.onGround();
            case FALLING -> !player.onGround() && player.getDeltaMovement().y < -.08;
            case IN_WATER -> player.isInWater();
            case POISONED -> player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER);
            case BURNING -> player.isOnFire();
            case MANA_BELOW_HALF -> !Mana.isUnlimited(player) && Mana.get(player) < Mana.max(player) * .5;
            case HUNGER_BELOW_HALF -> player.getFoodData().getFoodLevel() < 10;
            default -> false;
        };
    }

    private static LivingEntity nearest(ServerPlayer player, java.util.stream.Stream<LivingEntity> values) {
        return values.min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }
}
