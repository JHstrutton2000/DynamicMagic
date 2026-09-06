package com.strutton.dynamicmagic.vampire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** A risky cure ritual whose odds are earned by enduring daylight while blood-starved. */
public final class VampireCureTreatment {
    private static final String ACTIVE = "DynamicMagicVampireCureActive";
    private static final String END_TICK = "DynamicMagicVampireCureEndTick";
    private static final String SUN_TICKS = "DynamicMagicVampireCureSunTicks";
    private static final String SHADE_TICKS = "DynamicMagicVampireCureShadeTicks";
    private static final String NEXT_STATUS = "DynamicMagicVampireCureNextStatus";
    private static final String WEAKNESS_USED = "DynamicMagicVampireCureWeaknessUsed";
    private static final String REGENERATION_USED = "DynamicMagicVampireCureRegenerationUsed";
    private static final String FORCED_TARGET = "DynamicMagicVampireCureForcedTarget";
    private static final String FORCED_FLEE = "DynamicMagicVampireCureForcedFlee";
    static final long FULL_DAYLIGHT_TICKS = 12_000;
    static final long NIGHT_START = 13_000;
    static final double SUPPORT_POTION_BONUS = .07;
    private static final double REJECTION_RADIUS = 32;

    private VampireCureTreatment() {}

    public static boolean start(ServerPlayer player) {
        if (!Vampirism.isVampire(player)) {
            player.displayClientMessage(Component.literal("You are not afflicted with vampirism."), true);
            return false;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(ACTIVE)) {
            player.displayClientMessage(Component.literal("A vampire cure is already fighting through your blood."), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        data.putBoolean(ACTIVE, true);
        data.putLong(END_TICK, now + ticksUntilNextNight(level.getDayTime()));
        data.putLong(SUN_TICKS, 0);
        data.putLong(SHADE_TICKS, 0);
        data.putLong(NEXT_STATUS, now);
        data.remove(WEAKNESS_USED);
        data.remove(REGENERATION_USED);
        player.displayClientMessage(Component.literal(
                "The cure has begun. Endure direct sunlight until night; dying will end the treatment."), false);
        return true;
    }

    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(ACTIVE);
    }

    public static void tick(ServerPlayer player) {
        if (!isActive(player)) return;
        if (!Vampirism.isVampire(player)) {
            clear(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        if (level.isDay()) {
            boolean directSunlight = level.canSeeSky(player.blockPosition())
                    && !level.isRainingAt(player.blockPosition());
            String exposureKey = directSunlight ? SUN_TICKS : SHADE_TICKS;
            data.putLong(exposureKey, data.getLong(exposureKey) + 10);
        }

        applyTreatmentDebuffs(player);
        if (now % 20 == 0) influenceNearbyMobs(player);
        if (now >= data.getLong(END_TICK)) {
            resolve(player);
            return;
        }

        if (now >= data.getLong(NEXT_STATUS)) {
            data.putLong(NEXT_STATUS, now + 1_200);
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "Vampire cure: %.0f%% chance (shade loss %.0f%%); %s until night.",
                    successChance(player) * 100, shadePenalty(data.getLong(SHADE_TICKS)) * 100,
                    formatTicks(data.getLong(END_TICK) - now))), true);
        }
    }

    public static boolean recordSupportPotion(ServerPlayer player, PotionContents contents) {
        if (!isActive(player)) return false;
        boolean weakness = false;
        boolean regeneration = false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            weakness |= effect.getEffect().equals(MobEffects.WEAKNESS);
            regeneration |= effect.getEffect().equals(MobEffects.REGENERATION);
        }
        boolean changed = false;
        if (weakness) changed |= useSupportPotion(player, WEAKNESS_USED, "Weakness");
        if (regeneration) changed |= useSupportPotion(player, REGENERATION_USED, "Regeneration");
        return changed;
    }

    public static void cancelOnDeath(ServerPlayer player) {
        if (!isActive(player)) return;
        releaseNearbyMobs(player);
        clear(player);
        player.displayClientMessage(Component.literal(
                "Death ended the cure treatment. You will return as a vampire."), false);
    }

    public static double successChance(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return successChance(data.getLong(SUN_TICKS), data.getLong(SHADE_TICKS), Vampirism.bloodHunger(player),
                data.getBoolean(WEAKNESS_USED), data.getBoolean(REGENERATION_USED));
    }

    public static double successChance(long sunlightTicks, double bloodHunger) {
        return successChance(sunlightTicks, bloodHunger, false, false);
    }

    public static double successChance(long sunlightTicks, double bloodHunger,
                                       boolean weaknessUsed, boolean regenerationUsed) {
        return successChance(sunlightTicks, 0, bloodHunger, weaknessUsed, regenerationUsed);
    }

    public static double successChance(long sunlightTicks, long shadeTicks, double bloodHunger,
                                       boolean weaknessUsed, boolean regenerationUsed) {
        double sunlight = clamp(sunlightTicks / (double) FULL_DAYLIGHT_TICKS);
        double hungerBonus = clamp(bloodHunger) * .25;
        double potionBonus = (weaknessUsed ? SUPPORT_POTION_BONUS : 0)
                + (regenerationUsed ? SUPPORT_POTION_BONUS : 0);
        return clamp(sunlight + hungerBonus + potionBonus - shadePenalty(shadeTicks));
    }

    public static double shadePenalty(long shadeTicks) {
        double minutes = Math.max(0, shadeTicks) / 1_200.0;
        return clamp(.5 * minutes * minutes * minutes);
    }

    public static void influenceNearbyMobs(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(REJECTION_RADIUS), Mob::isAlive)) {
            if (shouldFlee(mob)) {
                mob.setTarget(null);
                mob.getPersistentData().putUUID(FORCED_FLEE, player.getUUID());
                Vec3 away = mob.position().subtract(player.position());
                if (away.lengthSqr() < .01) away = new Vec3(1, 0, 0);
                Vec3 destination = mob.position().add(away.normalize().scale(14));
                mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.45);
            } else {
                if (mob.getTarget() != player) {
                    mob.getPersistentData().putUUID(FORCED_TARGET, player.getUUID());
                    mob.setTarget(player);
                }
            }
        }
    }

    public static long ticksUntilNextNight(long dayTime) {
        long phase = Math.floorMod(dayTime, 24_000L);
        return phase < NIGHT_START ? NIGHT_START - phase : NIGHT_START + 24_000 - phase;
    }

    private static void resolve(ServerPlayer player) {
        double chance = successChance(player);
        releaseNearbyMobs(player);
        clear(player);
        if (player.getRandom().nextDouble() < chance) {
            Vampirism.cure(player);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 24_000, 1));
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "The cure succeeds (%.0f%% chance). You are human again, but weakened for one day.",
                    chance * 100)), false);
        } else {
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "The cure fails (%.0f%% chance). Vampirism still grips you.", chance * 100)), false);
        }
    }

    private static void applyTreatmentDebuffs(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 2, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, true, true));
    }

    private static void clear(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(ACTIVE);
        data.remove(END_TICK);
        data.remove(SUN_TICKS);
        data.remove(SHADE_TICKS);
        data.remove(NEXT_STATUS);
        data.remove(WEAKNESS_USED);
        data.remove(REGENERATION_USED);
    }

    private static boolean shouldFlee(Mob mob) {
        return mob instanceof Animal || mob instanceof AbstractVillager
                || mob instanceof AmbientCreature || mob instanceof WaterAnimal;
    }

    private static void releaseNearbyMobs(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                CompoundTag data = mob.getPersistentData();
                if (data.hasUUID(FORCED_TARGET) && data.getUUID(FORCED_TARGET).equals(player.getUUID())) {
                    if (mob.getTarget() == player) mob.setTarget(null);
                    data.remove(FORCED_TARGET);
                }
                if (data.hasUUID(FORCED_FLEE) && data.getUUID(FORCED_FLEE).equals(player.getUUID())) {
                    mob.getNavigation().stop();
                    data.remove(FORCED_FLEE);
                }
            }
        }
    }

    private static boolean useSupportPotion(ServerPlayer player, String key, String name) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(key)) {
            player.displayClientMessage(Component.literal(
                    name + " has already strengthened this cure; another dose adds no chance."), true);
            return false;
        }
        data.putBoolean(key, true);
        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "%s strengthens the cure by %.0f%%.", name, SUPPORT_POTION_BONUS * 100)), false);
        return true;
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
