package com.strutton.dynamicmagic.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import com.strutton.dynamicmagic.storage.MagicStorage;
import com.strutton.dynamicmagic.storage.MagicItemStorage;
import com.strutton.dynamicmagic.storage.MagicContracts;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Interprets a CraftedSpell. New combinations use this instead of needing a new Java cast method. */
public final class SpellExecutor {
    private SpellExecutor() {}

    public static void cast(ServerPlayer player, CraftedSpell spell, double charge) {
        CraftedSpell ranged = SpellResourcePayment.applyRangeSkills(player, spell);
        castInternal(player, ranged, charge * SpellResourcePayment.powerMultiplier(player, ranged), null, false, false);
    }

    public static void castProgram(ServerPlayer player, CraftedSpell spell, SpellBranch branch,
                                   double charge, LivingEntity detected) {
        CraftedSpell branchSpell = spell.branchSpell(branch);
        boolean direct = branch.targetMode() == ProgramTargetMode.DETECTED_ENTITY;
        LivingEntity directed = branch.targetMode() == ProgramTargetMode.CASTER_AIM ? null : detected;
        branchSpell = SpellResourcePayment.applyRangeSkills(player, branchSpell);
        castInternal(player, branchSpell, charge * SpellResourcePayment.powerMultiplier(player, branchSpell), directed, direct, false);
    }

    /** Public operation hook used by GameTests and other data-driven runtimes. */
    public static void applyDirect(ServerPlayer player, LivingEntity target, SpellInstruction instruction) {
        CraftedSpell spell = new CraftedSpell("Direct operation", SourceType.CREATE, instruction.element(),
                SpellForm.BOLT, DeliveryType.SELF, instruction.impact(), instruction.power(), instruction.direction(),
                false, ConditionType.ALWAYS, 20, instruction.range(), instruction.targetMode(),
                ProgramTargetMode.CASTER_AIM, instruction.physicsOperation(), List.of(instruction));
        applyRepeatedImpact(player, target, spell, instruction.power());
    }

    /** Direct block hook for GameTests; production casts reach the same laws through ray targeting. */
    public static void applyWorldDirect(ServerPlayer player, BlockPos position, Direction face, CraftedSpell spell) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), face, position, false);
        if (spell.instructions().size() > 1) applyCombinedAt(player, spell, 1.0, hit);
        else applyWorldMagic(player, spell, hit, spell.power());
    }

    private static void castInternal(ServerPlayer player, CraftedSpell spell, double charge, LivingEntity detected,
                                     boolean direct, boolean suppressWorldMagic) {
        if (spell.instructions().size() > 1) {
            boolean combinedWorldMagic = !suppressWorldMagic && applyCombinedWorldMagic(player, spell, charge);
            boolean combinedExplosion = !suppressWorldMagic && spell.instructions().stream().anyMatch(i -> i.impact() == ImpactType.EXPLODE)
                    && applyCombinedExplosion(player, spell, charge);
            for (SpellInstruction instruction : spell.instructions())
                castInternal(player, spell.withOnlyInstruction(instruction), charge, detected, direct,
                        suppressWorldMagic || combinedWorldMagic || combinedExplosion);
            return;
        }
        ServerLevel level = player.serverLevel();
        SpellInstruction instruction = spell.instructions().get(0);
        double power = spell.power() * charge;
        if (spell.hasSustainedMagic()) SustainedMagicController.activate(player, spell.impact());
        if (spell.impact() == ImpactType.RELEASE_STORAGE) {
            MagicStorage.openMenu(player);
            return;
        }
        if (spell.impact() == ImpactType.STORE_ITEM) {
            MagicItemStorage.open(player);
            return;
        }
        if (spell.impact() == ImpactType.SUMMON_CONTRACT) {
            MagicContracts.summon(player, spell.direction());
            return;
        }
        if (spell.impact() == ImpactType.CONJURE_ITEM) {
            SeenItemMemory.conjure(player, spell.element(), power);
            return;
        }
        if (spell.impact() == ImpactType.TELEPORT) {
            if (spell.source() == SourceType.SUMMON && spell.element() == Element.SPACE)
                DimensionDoorController.create(player, instruction.range(), Math.max(10, instruction.durationSeconds()));
            else if (player.isShiftKeyDown()) TeleportLocations.openMenu(player);
            else TeleportLocations.teleportWhereLooking(player, instruction.range());
            return;
        }
        if (com.strutton.dynamicmagic.time.TimeMagicController.isTimeMode(spell.impact())) return;
        if (!suppressWorldMagic && spell.impact() == ImpactType.TRANSMUTE_BLOCK
                && (spell.element() == Element.WATER || spell.element() == Element.ICE)
                && (spell.direction() == CastDirection.DOWN || instruction.targetMode() == TargetMode.SELF
                || spell.delivery() == DeliveryType.SELF))
            applyFootingMagic(player, spell.element(), power);
        if (instruction.targetMode() == TargetMode.SELF || spell.delivery() == DeliveryType.SELF
                || spell.form() == SpellForm.CLOAK) {
            if (spell.impact() == ImpactType.STORE_ENTITY) {
                MagicItemStorage.stashInventoryAndKill(player);
                return;
            }
            castAroundCaster(player, spell, power);
            return;
        }
        if (direct && detected != null) {
            if (spell.impact() == ImpactType.STORE_ENTITY) MagicStorage.store(player, detected, false);
            else {
                applyRepeatedImpact(player, detected, spell, power);
                if (instruction.radius() > 0) {
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                            detected.getBoundingBox().inflate(instruction.radius()),
                            entity -> entity != player && entity != detected && entity.isAlive()))
                        applyRepeatedImpact(player, nearby, spell, power);
                }
                if (!suppressWorldMagic && spell.impact() == ImpactType.EXPLODE && spell.element() != Element.DIVINE) {
                    for (int i = 0; i < instruction.repetitions(); i++)
                        level.explode(player, detected.getX(), detected.getY(), detected.getZ(),
                                (float) Math.min(6, .5 + power * .55), Level.ExplosionInteraction.NONE);
                }
                level.sendParticles(particle(spell.element()), detected.getX(), detected.getY() + detected.getBbHeight() * .5,
                        detected.getZ(), 24, detected.getBbWidth() * .4, detected.getBbHeight() * .3, detected.getBbWidth() * .4, .04);
                playSound(level, player, spell.element());
            }
            return;
        }
        if (spell.impact() == ImpactType.STORE_ENTITY) {
            storeTarget(player, spell, false, power);
            return;
        }
        if (spell.impact() == ImpactType.CONTRACT_ENTITY) {
            MagicContracts.contractTarget(player, spell, power);
            return;
        }
        double range = instruction.range();
        Vec3 start = player.getEyePosition();
        LivingEntity aimedTarget = detected;
        if (instruction.targetMode() == TargetMode.TRACKED && aimedTarget == null)
            aimedTarget = trackingTarget(player, instruction.range());
        Vec3 direction = aimedTarget == null ? direction(player, spell.direction())
                : aimedTarget.getEyePosition().subtract(start).normalize();
        if (aimedTarget == null && spell.delivery() == DeliveryType.PROJECTILE)
            direction = ProjectileAccuracy.applySpread(player, direction);
        Vec3 rayEnd = start.add(direction.scale(range));
        ClipContext.Fluid fluidMode = isWorldMagic(spell.impact()) ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        ClipContext.Block blockMode = spell.impact() == ImpactType.STUDY
                ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER;
        HitResult hit = level.clip(new ClipContext(start, rayEnd, blockMode, fluidMode, player));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, rayEnd,
                player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1),
                entity -> entity != player && entity.isAlive(), (float) (range * range));
        if (entityHit != null && start.distanceToSqr(entityHit.getLocation()) < start.distanceToSqr(hit.getLocation())) hit = entityHit;
        Vec3 end = hit.getLocation();
        drawPath(level, start, end, particle(spell.element()), spell.form() == SpellForm.BEAM ? 3 : 1);
        if (spell.form() == SpellForm.RUNE) {
            RuneMagicController.place(player, spell, end);
            playSound(level, player, spell.element());
            return;
        }

        double radius = instruction.radius();
        LivingEntity hitTarget = hit instanceof EntityHitResult entityResult
                && entityResult.getEntity() instanceof LivingEntity living ? living : null;
        Set<Integer> affected = new HashSet<>();
        if (hitTarget != null) {
            applyRepeatedImpact(player, hitTarget, spell, power);
            affected.add(hitTarget.getId());
        }
        if (spell.form() == SpellForm.BEAM || spell.form() == SpellForm.WALL) {
            double beamRadius = Math.max(.35, radius);
            AABB beamArea = new AABB(start, end).inflate(beamRadius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, beamArea,
                    target -> target != player && target.isAlive() && !affected.contains(target.getId()))) {
                double allowed = beamRadius + target.getBbWidth() * .5;
                if (distanceToSegmentSqr(target.getBoundingBox().getCenter(), start, end) <= allowed * allowed) {
                    applyRepeatedImpact(player, target, spell, power);
                    affected.add(target.getId());
                }
            }
        }
        if (radius > 0) {
            AABB area = new AABB(end, end).inflate(radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    target -> target != player && target.isAlive() && !affected.contains(target.getId()))) {
                applyRepeatedImpact(player, target, spell, power);
                affected.add(target.getId());
            }
        }

        if (!suppressWorldMagic && spell.impact() == ImpactType.EXPLODE && spell.element() != Element.DIVINE) {
            for (int i = 0; i < instruction.repetitions(); i++)
                level.explode(player, end.x, end.y, end.z, (float) Math.min(6, .5 + power * .55), Level.ExplosionInteraction.NONE);
        }
        if (!suppressWorldMagic && hit instanceof BlockHitResult blockHit)
            applyWorldMagic(player, spell, blockHit, power);
        if (spell.impact() == ImpactType.IGNITE && isHot(spell.element()) && hit instanceof BlockHitResult blockHit) {
            BlockPos firePos = blockHit.getBlockPos().relative(blockHit.getDirection());
            BlockState struck = level.getBlockState(blockHit.getBlockPos());
            if (struck.ignitedByLava() && level.getBlockState(firePos).isAir()
                    && Blocks.FIRE.defaultBlockState().canSurvive(level, firePos))
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
        }
        playSound(level, player, spell.element());
    }

    private static LivingEntity trackingTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        LivingEntity best = null; double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), entity -> entity != player && entity.isAlive())) {
            Vec3 offset = candidate.getEyePosition().subtract(eye); double distance = offset.length();
            if (distance <= 0 || distance > range) continue;
            double alignment = look.dot(offset.scale(1.0 / distance));
            if (alignment < .72) continue;
            double score = distance * (2.0 - alignment);
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private static void castAroundCaster(ServerPlayer player, CraftedSpell spell, double power) {
        ServerLevel level = player.serverLevel();
        if (spell.targetMode() == TargetMode.SELF || spell.delivery() == DeliveryType.SELF)
            applyRepeatedImpact(player, player, spell, power);
        double radius = spell.instructions().get(0).radius();
        level.sendParticles(particle(spell.element()), player.getX(), player.getY() + 1, player.getZ(),
                30, radius * .5, 1, radius * .5, .04);
        if (radius > 0) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius), target -> target != player))
                applyRepeatedImpact(player, target, spell, power);
        }
        if (spell.impact() == ImpactType.EXPLODE && spell.element() != Element.DIVINE) {
            for (int i = 0; i < spell.instructions().get(0).repetitions(); i++)
                level.explode(player, player.getX(), player.getY(), player.getZ(),
                        (float) Math.min(6, .5 + power * .55), Level.ExplosionInteraction.NONE);
        }
        playSound(level, player, spell.element());
    }

    private static void applyRepeatedImpact(ServerPlayer player, LivingEntity target, CraftedSpell spell, double power) {
        for (int i = 0; i < spell.instructions().get(0).repetitions(); i++) applyImpact(player, target, spell, power);
    }

    private static void applyImpact(ServerPlayer player, LivingEntity target, CraftedSpell spell, double power) {
        SpellInstruction instruction = spell.instructions().get(0);
        int durationTicks = Math.max(1, (int) Math.round(instruction.durationSeconds() * 20));
        long now = player.serverLevel().getGameTime();
        boolean wet = target.isInWaterOrRain() || target.getPersistentData().getLong("DynamicMagicWetUntil") > now;
        if (instruction.element() == Element.WATER) {
            target.clearFire();
            target.getPersistentData().putLong("DynamicMagicWetUntil", now + durationTicks);
        } else if (instruction.element() == Element.LIGHTNING && wet) {
            power *= 1.5;
            target.getPersistentData().remove("DynamicMagicWetUntil");
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1,
                    target.getZ(), 24, .45, .7, .45, .16);
        } else if (instruction.element() == Element.ICE && wet) {
            durationTicks *= 2;
            target.getPersistentData().remove("DynamicMagicWetUntil");
        } else if (instruction.element() == Element.FIRE && (wet || target.getTicksFrozen() > 0)) {
            target.getPersistentData().remove("DynamicMagicWetUntil");
            target.setTicksFrozen(0);
            target.hurt(player.damageSources().magic(), elementalDamage(player, target, instruction.element(), spell.delivery(), (float) Math.max(1, power)));
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1,
                    target.getZ(), 20, .4, .6, .4, .08);
        }
        if (spell.element() == Element.DIVINE && isUndead(target)
                && spell.impact() != ImpactType.RESURRECT) {
            target.hurt(player.damageSources().magic(), elementalDamage(player, target, Element.DIVINE, spell.delivery(), (float) (3 + power * 3)));
            return;
        }
        // Divine energy cannot be weaponized against living creatures, but reacts violently with undead.
        if (spell.element() == Element.DIVINE && (spell.impact() == ImpactType.DAMAGE
                || spell.impact() == ImpactType.EXPLODE || spell.impact() == ImpactType.IGNITE
                || spell.impact() == ImpactType.FREEZE || spell.impact() == ImpactType.CHAIN
                || spell.impact() == ImpactType.BLIND || spell.impact() == ImpactType.ROOT)) {
            target.heal((float) (2 + power * 3));
            return;
        }
        switch (spell.impact()) {
            case DAMAGE, EXPLODE -> target.hurt(player.damageSources().playerAttack(player),
                    elementalDamage(player, target, instruction.element(), spell.delivery(), (float) (2 + power * 2)));
            case IGNITE -> {
                target.hurt(player.damageSources().playerAttack(player),
                        elementalDamage(player, target, instruction.element(), spell.delivery(), (float) (1 + power)));
                target.igniteForSeconds((float) instruction.durationSeconds());
            }
            case PHYSICS -> {
                Vec3 vector = direction(player, spell.direction());
                if (spell.programTargetMode() != ProgramTargetMode.CASTER_AIM && target != player
                        && spell.direction() == CastDirection.LOOK)
                    vector = target.position().subtract(player.position()).normalize();
                double strength = .12 + power * .22;
                switch (spell.physicsOperation()) {
                    case ADD_VELOCITY -> {
                        if (target == player && spell.delivery() == DeliveryType.CONTINUOUS && vector.y > 0) {
                            Vec3 current = target.getDeltaMovement();
                            target.setDeltaMovement(current.x, Math.max(current.y, Math.min(.35, strength)), current.z);
                        } else target.push(vector.x * strength, vector.y * strength, vector.z * strength);
                    }
                    case SET_VELOCITY -> target.setDeltaMovement(vector.scale(strength));
                    case MULTIPLY_VELOCITY -> target.setDeltaMovement(target.getDeltaMovement().scale(1 + power * .15));
                    case REVERSE_VELOCITY -> target.setDeltaMovement(target.getDeltaMovement().reverse());
                    case STOP_MOTION -> target.setDeltaMovement(Vec3.ZERO);
                    case RESET_FALL_DISTANCE -> target.fallDistance = 0;
                    case DISABLE_GRAVITY -> target.setNoGravity(true);
                    case ENABLE_GRAVITY -> target.setNoGravity(false);
                }
                target.hurtMarked = true;
                if (vector.y > 0 || spell.physicsOperation() == PhysicsOperation.DISABLE_GRAVITY) target.fallDistance = 0;
            }
            case FREEZE -> {
                target.hurt(player.damageSources().playerAttack(player),
                        elementalDamage(player, target, instruction.element(), spell.delivery(), (float) (1 + power)));
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + durationTicks,
                        target.getTicksFrozen() + (int) (durationTicks * power)));
            }
            case PROTECT -> {
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, durationTicks, Math.max(0, (int) power - 1)));
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
            }
            case HEAL -> target.heal((float) (2 + power * 3));
            case CLEANSE -> {
                cleanse(target);
                if (target instanceof ServerPlayer serverPlayer)
                    com.strutton.dynamicmagic.vampire.Vampirism.cure(serverPlayer);
            }
            case EXTINGUISH -> { target.clearFire(); target.setTicksFrozen(Math.max(0, target.getTicksFrozen() - (int)(80 * power))); }
            case ROOT -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 9));
            case CHAIN -> {
                target.hurt(player.damageSources().lightningBolt(),
                        elementalDamage(player, target, instruction.element(), spell.delivery(), (float)(2 + power * 2.5)));
                player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(), 18, .4, .7, .4, .12);
                int chained = 0;
                for (LivingEntity next : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(3 + power), entity -> entity != player && entity != target && entity.isAlive())) {
                    next.hurt(player.damageSources().lightningBolt(),
                            elementalDamage(player, next, instruction.element(), spell.delivery(), (float)(1 + power * 1.25)));
                    player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1, next.getZ(), 10, .3, .5, .3, .1);
                    if (++chained >= Math.max(1, (int)power)) break;
                }
            }
            case ILLUMINATE -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0));
            case BLIND -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, 0));
            case TELEPORT -> {
                Vec3 destination = player.position().add(direction(player, spell.direction()).scale(instruction.range()));
                target.teleportTo(destination.x, destination.y, destination.z);
            }
            case DISPEL -> target.removeAllEffects();
            case APPLY_EFFECT -> target.addEffect(new MobEffectInstance(instruction.potionEffect().effect(),
                    durationTicks, Math.max(0, Math.min(4, (int) Math.floor(power / 2)))));
            case DRAIN_LIFE -> {
                float amount = (float) (2 + power * 2.5);
                if (target instanceof ServerPlayer victim
                        && com.strutton.dynamicmagic.skill.SkillKnowledge.knows(victim,
                        com.strutton.dynamicmagic.skill.MagicSkill.BLOOD_WARD)) amount *= .35f;
                amount = elementalDamage(player, target, instruction.element(), spell.delivery(), amount);
                if (target.hurt(player.damageSources().magic(), amount)) player.heal(amount * .65f);
            }
            case TURN_UNDEAD -> {
                if (isUndead(target)) {
                    if (target instanceof Mob mob) mob.setTarget(null);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, durationTicks, 1));
                    target.push(target.getX() - player.getX(), .15, target.getZ() - player.getZ());
                }
            }
            case MIND_CALM -> { if (target instanceof Mob mob) mob.setTarget(null); }
            case MIND_FEAR -> {
                if (target instanceof Mob mob) mob.setTarget(null);
                target.push(target.getX() - player.getX(), .1, target.getZ() - player.getZ());
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, durationTicks, 0));
            }
            case MIND_FRENZY -> {
                if (target instanceof Mob mob) {
                    List<LivingEntity> candidates = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                            target.getBoundingBox().inflate(12),
                            candidate -> candidate != player && candidate != target && candidate.isAlive());
                    LivingEntity victim = player.serverLevel().getNearestEntity(candidates,
                            net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT,
                            mob, target.getX(), target.getY(), target.getZ());
                    if (victim != null) mob.setTarget(victim);
                }
            }
            case SOUL_TRAP -> target.getPersistentData().putLong("DynamicMagicSoulTrappedUntil", now + durationTicks);
            case REANIMATE -> summon(player, Element.UNDEAD, target.position(), durationTicks);
            case SUMMON_CREATURE -> summon(player, instruction.element(), target.position(), durationTicks);
            case CONVERT_HEALTH_TO_MANA -> {
                if (target == player) {
                    float health = (float) Math.min(Math.max(1, power * 2), Math.max(0, player.getHealth() - 1));
                    if (health > 0 && player.hurt(player.damageSources().magic(), health))
                        com.strutton.dynamicmagic.mana.Mana.set(player,
                                com.strutton.dynamicmagic.mana.Mana.get(player) + health * 8);
                }
            }
            case STUDY -> com.strutton.dynamicmagic.knowledge.StudyKnowledge.studyEntity(
                    player, target, observedElement(target), power);
            case RESURRECT -> { if (power >= 4) resurrect(player, target); }
            case DETECT_LIFE -> {
                if (!isUndead(target))
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0));
            }
            case FERTILITY -> {
                if (target instanceof Animal animal && animal.canFallInLove()) animal.setInLove(player);
            }
            case PACIFY_UNDEAD -> {
                if (isUndead(target)) {
                    if (target instanceof Mob mob) mob.setTarget(null);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 4));
                    target.getPersistentData().putLong("DynamicMagicPacifiedUntil", now + durationTicks);
                    target.getPersistentData().putUUID("DynamicMagicPacifiedPlayer", player.getUUID());
                }
            }
            case CORRUPT_LIFE -> { if (power >= 3) corrupt(player, target); }
            case DETECT_UNDEAD -> {
                if (isUndead(target))
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0));
            }
            case STORE_ITEM, STORE_ENTITY, RELEASE_STORAGE, STOP_TIME, SPEED_TIME, SLOW_TIME,
                 CONTRACT_ENTITY, SUMMON_CONTRACT, CONJURE_ITEM, TRANSMUTE_BLOCK, WEATHER_RAIN,
                 WEATHER_STORM, SUMMON_LIGHTNING, DETECT_ORES, ASTRAL_PROJECTION -> { }
        }
    }

    private static void cleanse(LivingEntity target) {
        target.removeEffect(MobEffects.POISON);
        target.removeEffect(MobEffects.WITHER);
        target.removeEffect(MobEffects.WEAKNESS);
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.removeEffect(MobEffects.BLINDNESS);
        target.removeEffect(MobEffects.CONFUSION);
        target.removeEffect(MobEffects.HUNGER);
    }

    private static boolean isWorldMagic(ImpactType impact) {
        return impact == ImpactType.TRANSMUTE_BLOCK || impact == ImpactType.EXTINGUISH
                || impact == ImpactType.STUDY || impact == ImpactType.DETECT_ORES
                || impact == ImpactType.SUMMON_LIGHTNING;
    }

    private static boolean isHot(Element element) {
        return element == Element.FIRE || element == Element.SCORCH
                || element == Element.LAVA || element == Element.PLASMA;
    }

    /** Creates temporary travel footing when sufficiently strong Water or Ice transmutation is cast at the caster's feet. */
    public static int applyFootingMagic(ServerPlayer player, Element element, double power) {
        if (power < 2 || element != Element.WATER && element != Element.ICE) return 0;
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition().below();
        int radius = Math.min(2, Math.max(0, (int) Math.floor(power - 2)));
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, 0, -radius),
                center.offset(radius, 0, radius))) {
            BlockState state = level.getBlockState(pos);
            if (element == Element.WATER && state.getFluidState().is(FluidTags.LAVA)) {
                level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                changed++;
            } else if (element == Element.ICE && state.getFluidState().is(FluidTags.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                changed++;
            }
        }
        return changed;
    }

    private static void applyWorldMagic(ServerPlayer player, CraftedSpell spell, BlockHitResult hit, double power) {
        ServerLevel level = player.serverLevel();
        BlockPos struck = hit.getBlockPos();
        BlockState state = level.getBlockState(struck);
        if (spell.impact() == ImpactType.STUDY) {
            if (isPortal(state))
                com.strutton.dynamicmagic.knowledge.StudyKnowledge.studyPortal(player, level, struck, power);
            else com.strutton.dynamicmagic.knowledge.StudyKnowledge.studyBlock(
                    player, level, struck, observedElement(state), power);
            return;
        }
        if (spell.impact() == ImpactType.DETECT_ORES) {
            detectOres(player, struck, power);
            return;
        }
        if (spell.impact() == ImpactType.SUMMON_LIGHTNING && power >= 2) {
            var bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(struck.relative(hit.getDirection())));
                bolt.setCause(player);
                level.addFreshEntity(bolt);
            }
            return;
        }
        if (spell.element() == Element.WATER && spell.impact() == ImpactType.EXTINGUISH) {
            if (state.is(Blocks.FIRE)) level.removeBlock(struck, false);
            BlockPos adjacent = struck.relative(hit.getDirection());
            if (level.getBlockState(adjacent).is(Blocks.FIRE)) level.removeBlock(adjacent, false);
            return;
        }
        if (isHot(spell.element())) {
            trySmelt(level, struck, state, power);
            tryIgnite(level, hit, power);
        }
        if (spell.impact() != ImpactType.TRANSMUTE_BLOCK) return;
        BlockPos placement = placement(level, hit);
        if (spell.element() == Element.WATER) {
            if (state.getFluidState().is(FluidTags.LAVA) && power >= 2)
                level.setBlockAndUpdate(struck, Blocks.OBSIDIAN.defaultBlockState());
            else if (power >= 3 && level.getBlockState(placement).canBeReplaced())
                level.setBlockAndUpdate(placement, Blocks.WATER.defaultBlockState());
        } else if (isHot(spell.element()) && state.getFluidState().is(FluidTags.WATER) && power >= 2.5) {
            level.setBlockAndUpdate(struck, Blocks.COBBLESTONE.defaultBlockState());
        } else if (spell.element() == Element.ICE && state.getFluidState().is(FluidTags.WATER) && power >= 2) {
            level.setBlockAndUpdate(struck, Blocks.ICE.defaultBlockState());
        }
    }

    private static boolean applyCombinedExplosion(ServerPlayer player, CraftedSpell spell, double charge) {
        SpellInstruction explosion = spell.instructions().stream().filter(i -> i.impact() == ImpactType.EXPLODE).findFirst().orElse(null);
        if (explosion == null) return false;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(direction(player, explosion.direction()).scale(explosion.range()));
        HitResult hit = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 center = hit.getLocation();
        var forces = ElementInteractions.effectiveForces(spell.instructions(), charge);
        double blast = forces.values().stream().mapToDouble(Double::doubleValue).sum();
        player.serverLevel().explode(player, center.x, center.y, center.z, (float) Math.min(7, .5 + explosion.power() * charge * .6), Level.ExplosionInteraction.NONE);
        applyElementalExplosionTerrain(player, center, forces, Math.min(8, 1.5 + explosion.radius() + Math.sqrt(blast) * .55));
        return true;
    }

    private static void applyElementalExplosionTerrain(ServerPlayer player, Vec3 center, java.util.Map<Element, Double> forces, double radius) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = BlockPos.containing(center);
        double fire = forces.getOrDefault(Element.FIRE, 0.0) + forces.getOrDefault(Element.SCORCH, 0.0)
                + forces.getOrDefault(Element.LAVA, 0.0) + forces.getOrDefault(Element.PLASMA, 0.0);
        double water = forces.getOrDefault(Element.WATER, 0.0), earth = forces.getOrDefault(Element.EARTH, 0.0);
        double air = forces.getOrDefault(Element.AIR, 0.0);
        int r = (int) Math.ceil(radius);
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            BlockPos pos = cursor.immutable();
            if (Vec3.atCenterOf(pos).distanceToSqr(center) > radius * radius || level.random.nextDouble() > .72) continue;
            BlockState state = level.getBlockState(pos);
            if (water > 0 && state.is(Blocks.FIRE)) level.removeBlock(pos, false);
            if (fire > 0 && !state.isAir()) { trySmelt(level, pos, state, fire); if (fire >= 2.5 && level.random.nextDouble() < Math.min(.9, fire * .09)) tryIgniteAbove(level, pos); }
            if (water >= 5 && state.canBeReplaced() && level.random.nextDouble() < Math.min(.55, water * .035)) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            if (fire >= 8 && state.canBeReplaced() && level.random.nextDouble() < Math.min(.18, fire * .008)) level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
            if (earth >= 2 && state.canBeReplaced() && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below()))
                level.setBlockAndUpdate(pos, level.random.nextDouble() < .55 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
            if (air >= 3 && !state.isAir() && state.getDestroySpeed(level, pos) >= 0 && state.getDestroySpeed(level, pos) <= 1.5f
                    && level.random.nextDouble() < Math.min(.45, air * .04)) level.destroyBlock(pos, false, player);
            level.sendParticles(particle(dominant(forces)), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, .2, .2, .2, .02);
        }
        if (air > 0) for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), e -> e != player)) {
            Vec3 push = entity.position().subtract(center).normalize().scale(.15 + air * .09); entity.push(push.x, Math.max(.08, push.y), push.z); entity.hurtMarked = true;
        }
    }

    private static Element dominant(java.util.Map<Element, Double> forces) {
        return forces.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(Element.ARCANE);
    }

    private static void trySmelt(ServerLevel level, BlockPos pos, BlockState state, double power) {
        if (power < 1 || level.random.nextDouble() > Math.min(.95, .10 + power * .11)) return;
        BlockState result = state.is(Blocks.COBBLESTONE) ? Blocks.STONE.defaultBlockState()
                : state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) ? Blocks.GLASS.defaultBlockState()
                : state.is(Blocks.CLAY) ? Blocks.TERRACOTTA.defaultBlockState()
                : state.is(Blocks.STONE_BRICKS) ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : null;
        if (result != null) level.setBlockAndUpdate(pos, result);
    }

    private static void tryIgnite(ServerLevel level, BlockHitResult hit, double power) {
        if (power < .5 || level.random.nextDouble() > Math.min(.95, .3 + power * .16)) return;
        BlockPos struck = hit.getBlockPos(), fire = struck.relative(hit.getDirection());
        if ((level.getBlockState(struck).ignitedByLava() || level.getBlockState(struck).isFlammable(level, struck, hit.getDirection()))
                && level.getBlockState(fire).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, fire)) level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
    }
    private static void tryIgniteAbove(ServerLevel level, BlockPos struck) {
        BlockPos fire = struck.above();
        if (level.getBlockState(struck).ignitedByLava() && level.getBlockState(fire).isAir()
                && Blocks.FIRE.defaultBlockState().canSurvive(level, fire)) level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
    }

    /** Resolves Fire/Water/Ice together so the result depends on force ratio instead of operation order. */
    private static boolean applyCombinedWorldMagic(ServerPlayer player, CraftedSpell spell, double charge) {
        double water = 0, fire = 0, ice = 0;
        for (SpellInstruction instruction : spell.instructions()) {
            if (instruction.impact() != ImpactType.TRANSMUTE_BLOCK) continue;
            double force = instruction.power() * instruction.repetitions() * charge;
            if (instruction.element() == Element.WATER) water += force;
            else if (isHot(instruction.element())) fire += force;
            else if (instruction.element() == Element.ICE) ice += force;
        }
        if (water <= 0 || fire <= 0 && ice <= 0) return false;
        SpellInstruction first = spell.instructions().get(0);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(direction(player, first.direction()).scale(first.range()));
        HitResult result = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        if (!(result instanceof BlockHitResult hit)) return false;
        return applyCombinedAt(player, spell, charge, hit);
    }

    private static boolean applyCombinedAt(ServerPlayer player, CraftedSpell spell, double charge, BlockHitResult hit) {
        double water = 0, fire = 0, ice = 0;
        for (SpellInstruction instruction : spell.instructions()) {
            if (instruction.impact() != ImpactType.TRANSMUTE_BLOCK) continue;
            double force = instruction.power() * instruction.repetitions() * charge;
            if (instruction.element() == Element.WATER) water += force;
            else if (isHot(instruction.element())) fire += force;
            else if (instruction.element() == Element.ICE) ice += force;
        }
        ServerLevel level = player.serverLevel();
        BlockPos struck = hit.getBlockPos();
        BlockState state = level.getBlockState(struck);
        BlockPos placement = placement(level, hit);
        if (water >= 2 && state.getFluidState().is(FluidTags.LAVA)) {
            level.setBlockAndUpdate(struck, Blocks.OBSIDIAN.defaultBlockState());
            return true;
        }
        if (ice >= 2 && water >= 2) {
            level.setBlockAndUpdate(state.canBeReplaced() || !state.getFluidState().isEmpty() ? struck : placement,
                    Blocks.ICE.defaultBlockState());
            return true;
        }
        if (water + fire >= 4) {
            BlockState resultState = water >= fire * 1.25
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            level.setBlockAndUpdate(state.canBeReplaced() || !state.getFluidState().isEmpty() ? struck : placement,
                    resultState);
            return true;
        }
        return false;
    }

    private static BlockPos placement(ServerLevel level, BlockHitResult hit) {
        BlockPos struck = hit.getBlockPos();
        BlockState state = level.getBlockState(struck);
        return state.canBeReplaced() || !state.getFluidState().isEmpty() ? struck : struck.relative(hit.getDirection());
    }

    static Element observedElement(BlockState state) {
        if (isPortal(state)) return Element.SPACE;
        if (state.getFluidState().is(FluidTags.WATER)) return Element.WATER;
        if (state.getFluidState().is(FluidTags.LAVA)) return Element.LAVA;
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return Element.ICE;
        if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)) return Element.GLASS;
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return Element.FIRE;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.contains("ore") || id.equals("ancient_debris") || id.contains("metal")) return Element.METAL;
        if (id.contains("soul") || id.contains("sculk")) return Element.SPIRIT;
        if (id.contains("lightning_rod")) return Element.LIGHTNING;
        return Element.EARTH;
    }

    static boolean isPortal(BlockState state) {
        if (state.is(BlockTags.PORTALS) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().contains("portal");
    }

    private static Element observedElement(LivingEntity target) {
        Element soloElement = com.strutton.dynamicmagic.compat.SoloLevelingIntegration.elementForEntity(target);
        if (soloElement != null) return soloElement;
        if (target.getType() == EntityType.ENDERMAN) return Element.SPACE;
        if (isUndead(target)) return Element.UNDEAD;
        if (target instanceof Animal || target instanceof Villager) return Element.SPIRIT;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        if (id.contains("blaze") || id.contains("magma")) return Element.FIRE;
        if (id.contains("drowned") || id.contains("guardian")) return Element.WATER;
        if (id.contains("breeze")) return Element.AIR;
        return Element.SPIRIT;
    }

    private static void detectOres(ServerPlayer player, BlockPos center, double power) {
        ServerLevel level = player.serverLevel();
        int radius = Math.min(16, 4 + (int) Math.floor(power * 2
                + Math.sqrt(ElementMastery.experience(player, Element.METAL)) * .15));
        int shown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
            if (!id.contains("ore") && !id.equals("ancient_debris")) continue;
            level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3, .2, .2, .2, .01);
            if (++shown >= 256) break;
        }
    }

    private static void resurrect(ServerPlayer player, LivingEntity target) {
        if (target == null) return;
        ServerLevel level = player.serverLevel();
        if (target instanceof ZombieVillager zombie) {
            Villager restored = EntityType.VILLAGER.create(level);
            if (restored == null) return;
            restored.setVillagerData(zombie.getVillagerData());
            restored.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
            level.addFreshEntity(restored);
            zombie.discard();
        } else if (target instanceof ZombifiedPiglin zombie) {
            Pig restored = EntityType.PIG.create(level);
            if (restored == null) return;
            restored.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
            level.addFreshEntity(restored);
            zombie.discard();
        }
    }

    private static void corrupt(ServerPlayer player, LivingEntity target) {
        if (!(target instanceof Villager villager)) return;
        ServerLevel level = player.serverLevel();
        ZombieVillager corrupted = EntityType.ZOMBIE_VILLAGER.create(level);
        if (corrupted == null) return;
        corrupted.setVillagerData(villager.getVillagerData());
        corrupted.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        com.strutton.dynamicmagic.mage.VillageMageEvents.inheritAsNecromancer(villager, corrupted);
        level.addFreshEntity(corrupted);
        villager.discard();
    }

    private static void drawPath(ServerLevel level, Vec3 start, Vec3 end, ParticleOptions particle, int width) {
        Vec3 delta = end.subtract(start);
        int count = Math.max(5, (int) (delta.length() * 3));
        for (int i = 1; i <= count; i++) {
            Vec3 point = start.add(delta.scale(i / (double) count));
            level.sendParticles(particle, point.x, point.y, point.z, width, .025 * width, .025 * width, .025 * width, .002);
        }
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0e-8) return point.distanceToSqr(start);
        double position = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / lengthSqr));
        return point.distanceToSqr(start.add(segment.scale(position)));
    }

    private static ParticleOptions particle(Element element) {
        return switch (element) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case AIR -> ParticleTypes.CLOUD;
            case EARTH -> ParticleTypes.POOF;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case LIGHT -> ParticleTypes.END_ROD;
            case SHADOW -> ParticleTypes.PORTAL;
            case ARCANE -> ParticleTypes.WITCH;
            case SPACE -> ParticleTypes.REVERSE_PORTAL;
            case TIME -> ParticleTypes.ENCHANT;
            case DIVINE -> ParticleTypes.TOTEM_OF_UNDYING;
            case METAL, GLASS -> ParticleTypes.CRIT;
            case PLASMA, STORM -> ParticleTypes.ELECTRIC_SPARK;
            case QUICK, SCORCH -> ParticleTypes.CLOUD;
            case LAVA -> ParticleTypes.LAVA;
            case SPIRIT -> ParticleTypes.SOUL;
            case UNDEAD -> ParticleTypes.SCULK_SOUL;
            case SAND -> ParticleTypes.POOF;
            case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
            case KI -> ParticleTypes.ENCHANTED_HIT;
        };
    }

    private static void playSound(ServerLevel level, ServerPlayer player, Element element) {
        var sound = switch (element) {
            case FIRE -> SoundEvents.FIRECHARGE_USE;
            case WATER, ICE -> SoundEvents.PLAYER_SPLASH;
            case AIR -> SoundEvents.BREEZE_SHOOT;
            case EARTH -> SoundEvents.STONE_BREAK;
            case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case LIGHT -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case SHADOW, ARCANE -> SoundEvents.EVOKER_CAST_SPELL;
            case SPACE -> SoundEvents.ENDERMAN_TELEPORT;
            case TIME -> SoundEvents.BEACON_AMBIENT;
            case DIVINE -> SoundEvents.PLAYER_LEVELUP;
            case METAL, GLASS -> SoundEvents.ANVIL_PLACE;
            case PLASMA, STORM -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case QUICK, SCORCH -> SoundEvents.BREEZE_SHOOT;
            case LAVA -> SoundEvents.LAVA_POP;
            case SPIRIT -> SoundEvents.EVOKER_CAST_SPELL;
            case UNDEAD -> SoundEvents.ZOMBIE_AMBIENT;
            case SAND -> SoundEvents.SAND_BREAK;
            case BLOOD -> SoundEvents.WITHER_HURT;
            case KI -> SoundEvents.PLAYER_ATTACK_STRONG;
        };
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, .8f, 1f);
    }

    private static void storeTarget(ServerPlayer player, CraftedSpell spell, boolean itemsOnly, double power) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 direction = direction(player, spell.direction());
        double range = spell.instructions().get(0).range();
        Vec3 end = start.add(direction.scale(range));
        AABB search = player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.25);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, start, end, search,
                entity -> entity != player && entity.isAlive() && (!itemsOnly || entity instanceof ItemEntity),
                (float) start.distanceToSqr(end));
        if (hit == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(itemsOnly
                    ? "No dropped item is in the spell's path." : "No entity is in the spell's path.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        Entity target = hit.getEntity();
        if (MagicStorage.store(player, target, itemsOnly)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * .5,
                    target.getZ(), 35, target.getBbWidth(), target.getBbHeight() * .5, target.getBbWidth(), .08);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .8f, 1.2f);
        }
    }

    private static Vec3 direction(ServerPlayer player, CastDirection direction) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < .0001) horizontal = new Vec3(0, 0, 1);
        else horizontal = horizontal.normalize();
        return switch (direction) {
            case LOOK -> look.normalize();
            case UP -> new Vec3(0, 1, 0);
            case DOWN -> new Vec3(0, -1, 0);
            case FORWARD -> horizontal;
            case BACKWARD -> horizontal.reverse();
        };
    }

    private static boolean isUndead(LivingEntity target) {
        return target.getType().is(EntityTypeTags.UNDEAD)
                || com.strutton.dynamicmagic.vampire.Vampirism.isVampire(target);
    }

    private static float elementalDamage(ServerPlayer caster, LivingEntity target, Element element,
                                         DeliveryType delivery, float amount) {
        if (element == Element.WATER && target.getType() == EntityType.BLAZE) amount *= 2.0f;
        if (com.strutton.dynamicmagic.vampire.Vampirism.isVampire(target)) {
            if (isHot(element)) amount *= 1.75f;
            else if (element == Element.DIVINE || element == Element.LIGHT) amount *= 1.5f;
        }
        amount = com.strutton.dynamicmagic.dragon.DragonIntegration.adjustSpellDamage(caster, target, element, amount);
        amount = com.strutton.dynamicmagic.compat.ApotheosisIntegration.adjustSpellDamage(caster, element, delivery, amount);
        return com.strutton.dynamicmagic.compat.IronSpellsIntegration.adjustDynamicSpellDamage(caster, target, element, amount);
    }

    private static void summon(ServerPlayer player, Element element, Vec3 position, int durationTicks) {
        EntityType<?> type = switch (element) {
            case FIRE, SCORCH, LAVA, PLASMA -> EntityType.BLAZE;
            case ICE -> EntityType.STRAY;
            case WATER -> EntityType.DROWNED;
            case AIR, STORM -> EntityType.BREEZE;
            case SPIRIT, DIVINE, LIGHT -> EntityType.WOLF;
            case UNDEAD, BLOOD, SHADOW -> EntityType.ZOMBIE;
            case EARTH, SAND, METAL, GLASS -> EntityType.IRON_GOLEM;
            default -> EntityType.ALLAY;
        };
        Entity entity = type.create(player.serverLevel());
        if (entity == null) return;
        entity.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        entity.getPersistentData().putUUID("DynamicMagicSummoner", player.getUUID());
        entity.getPersistentData().putLong("DynamicMagicSummonedUntil",
                player.serverLevel().getGameTime() + Math.max(40, durationTicks));
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        player.serverLevel().addFreshEntity(entity);
    }

}
