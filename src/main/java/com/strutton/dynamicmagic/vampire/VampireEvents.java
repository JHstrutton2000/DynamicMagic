package com.strutton.dynamicmagic.vampire;

import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.minecraft.world.entity.MobSpawnType;

public final class VampireEvents {
    private static final String VAMPIRE = "DynamicMagicVampireEnemy";
    private static final String PROVOKED = "DynamicMagicVampireProvoked";
    private static final double EXTERNAL_NATURAL_SPAWN_KEEP_CHANCE = .10;
    private VampireEvents() {}

    public static boolean isVampireEnemy(Entity entity) {
        return entity.getPersistentData().getBoolean(VAMPIRE)
                || entity instanceof LivingEntity living && Vampirism.isVampire(living);
    }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Zombie zombie)
                || zombie.getPersistentData().contains(VAMPIRE) || zombie.isBaby() || Vampirism.externalModLoaded()) return;
        // Zombie spawn weight is roughly ten times Enderman weight; converting one in ten is comparable rarity.
        if (zombie.getRandom().nextDouble() < .10) {
            zombie.getPersistentData().putBoolean(VAMPIRE, true);
            zombie.setCustomName(Component.literal("Vampire"));
            zombie.setCustomNameVisible(true);
            zombie.setHealth(zombie.getMaxHealth());
        }
    }

    /** Vampirism's natural mobs are thinned to an Enderman-like encounter rate; eggs and commands are untouched. */
    @SubscribeEvent public static void onNaturalSpawn(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !Vampirism.externalModLoaded()
                || !Vampirism.isVampire(event.getEntity())) return;
        if (event.getEntity().getRandom().nextDouble() >= EXTERNAL_NATURAL_SPAWN_KEEP_CHANCE)
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof ServerPlayer player
                && SkillKnowledge.knows(player, MagicSkill.VAMPIRE_IMMUNITY)
                && attacker != null && isVampireEnemy(attacker)) {
            event.setAmount(0);
            return;
        }
        boolean internalVampire = victim.getPersistentData().getBoolean(VAMPIRE)
                || victim instanceof ServerPlayer player && Vampirism.isInternal(player);
        if (internalVampire && event.getSource().is(DamageTypeTags.IS_FIRE))
            event.setAmount(event.getAmount() * 1.75f);
        if (victim instanceof ServerPlayer vampire && Vampirism.isVampire(vampire)
                && (SkillKnowledge.knows(vampire, MagicSkill.SUN_WARD)
                || com.strutton.dynamicmagic.dragon.DragonIntegration.isTransformedPlayer(vampire))
                && event.getSource().getMsgId().toLowerCase(java.util.Locale.ROOT).contains("sun")) {
            event.setAmount(0);
            vampire.clearFire();
        }
        if (attacker != null && isVampireEnemy(attacker) && victim instanceof ServerPlayer player) {
            if (SkillKnowledge.knows(player, MagicSkill.BLOOD_WARD)) event.setAmount(event.getAmount() * .65f);
            else if (player.getRandom().nextDouble() < .12 && Vampirism.infect(player))
                player.displayClientMessage(Component.literal("Vampirism takes root in your blood."), false);
        }
        if (attacker instanceof ServerPlayer player && Vampirism.isVampire(player)
                && (victim instanceof Zombie || victim instanceof AbstractSkeleton))
            victim.getPersistentData().putBoolean(PROVOKED, true);
    }

    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer deadPlayer)
            VampireCureTreatment.cancelOnDeath(deadPlayer);
        if (event.getEntity().getPersistentData().getLong("DynamicMagicSoulTrappedUntil")
                < event.getEntity().level().getGameTime()
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        net.minecraft.world.item.ItemStack soul = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ECHO_SHARD);
        soul.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Captured " + event.getEntity().getName().getString() + " Soul"));
        if (!player.getInventory().add(soul)) player.drop(soul, false);
    }

    @SubscribeEvent public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % 10 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getPersistentData().contains("DynamicMagicSummonedUntil")
                        && entity.getPersistentData().getLong("DynamicMagicSummonedUntil") <= gameTime) {
                    entity.discard();
                    continue;
                }
                if (entity instanceof Mob mob && isVampireEnemy(mob)) tickEnemy(level, mob, gameTime);
            }
            for (ServerPlayer player : level.players()) {
                VampireCureTreatment.tick(player);
                if (SkillKnowledge.knows(player, MagicSkill.VAMPIRE_IMMUNITY)) {
                    if (Vampirism.enforceImmunity(player))
                        player.displayClientMessage(Component.literal("Your Vampire Immunity purges the infection."), false);
                } else if (Vampirism.isVampire(player)) tickPlayer(level, player, gameTime);
            }
        }
    }

    private static void tickEnemy(ServerLevel level, Mob vampire, long gameTime) {
        LivingEntity target = vampire.getTarget();
        if (target == null || !target.isAlive() || vampire.distanceToSqr(target) > 144 || gameTime % 60 != 0) return;
        if (target instanceof ServerPlayer player && SkillKnowledge.knows(player, MagicSkill.VAMPIRE_IMMUNITY)) return;
        float damage = 4;
        if (target instanceof ServerPlayer player && SkillKnowledge.knows(player, MagicSkill.BLOOD_WARD)) damage *= .35f;
        if (target.hurt(level.damageSources().magic(), damage)) vampire.heal(damage * .65f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + 1, target.getZ(), 12, .35, .5, .35, .04);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, long gameTime) {
        boolean transformedDragon = com.strutton.dynamicmagic.dragon.DragonIntegration.isTransformedPlayer(player);
        if (transformedDragon || SkillKnowledge.knows(player, MagicSkill.SUN_WARD)) player.clearFire();
        if (!transformedDragon && Vampirism.isInternal(player) && gameTime % 40 == 0
                && level.isDay() && level.canSeeSky(player.blockPosition())
                && !SkillKnowledge.knows(player, MagicSkill.SUN_WARD)) {
            player.igniteForSeconds(3);
            player.hurt(level.damageSources().onFire(), 2);
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), Mob::isAlive)) {
            if ((mob instanceof Zombie || mob instanceof AbstractSkeleton)
                    && mob.getTarget() == player && !mob.getPersistentData().getBoolean(PROVOKED)) mob.setTarget(null);
            if ((mob instanceof Wolf || mob instanceof IronGolem) && mob.getTarget() == null) mob.setTarget(player);
            if (mob instanceof Villager villager
                    && !com.strutton.dynamicmagic.mage.VillageMageEvents.isMage(villager)
                    && villager.distanceToSqr(player) < 100) {
                Vec3 away = villager.position().subtract(player.position());
                if (away.lengthSqr() > .01) {
                    Vec3 destination = villager.position().add(away.normalize().scale(8));
                    villager.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.25);
                }
            }
        }
    }
}
