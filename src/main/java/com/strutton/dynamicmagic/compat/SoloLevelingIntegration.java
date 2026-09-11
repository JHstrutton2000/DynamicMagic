package com.strutton.dynamicmagic.compat;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.magic.ElementMastery;
import com.strutton.dynamicmagic.magic.ProjectileAccuracy;
import com.strutton.dynamicmagic.magic.TeleportLocations;
import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Optional, reflection-isolated bridge for Solo Leveling: Reawakening 1.3.x. */
public final class SoloLevelingIntegration {
    private static final String NAMESPACE = "sololeveling";
    private static final String MERGED = "dynamicmagic.slr_mana_merged";
    private static final String LAST_SHARED = "dynamicmagic.slr_last_shared_mana";
    private static final String LAST_CLASS = "dynamicmagic.slr_last_class_affinity";
    private static final String UNDEAD_OWNER = "DynamicMagicUndeadOwner";
    private static final ThreadLocal<Boolean> SYNCHRONIZING = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Profile> PROFILES = new ConcurrentHashMap<>();
    private static Method manaCurrent;
    private static Method manaMaximum;
    private static Method classOf;
    private static Method classIsAwakened;
    private static Method statsIntelligence;
    private static Method statsPerception;
    private static Method progressLevel;
    private static Method progressRank;
    private static Method skillsLearned;
    private static Method vesselOf;
    private static Method shadowMonarch;
    private static Object capability;
    private static Method getCapability;
    private static Class<?> playerVariablesType;
    private static boolean active;

    private SoloLevelingIntegration() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus bus) {
        try {
            Class<?> entity = Entity.class;
            Class<?> hunterMana = Class.forName("net.solocraft.api.HunterMana");
            Class<?> hunterStats = Class.forName("net.solocraft.api.HunterStats");
            Class<?> hunterProgress = Class.forName("net.solocraft.api.HunterProgress");
            Class<?> hunterClasses = Class.forName("net.solocraft.api.hunter.HunterClassRegistry");
            Class<?> hunterSkills = Class.forName("net.solocraft.api.skill.HunterSkills");
            Class<?> vessels = Class.forName("net.solocraft.api.vessel.VesselRegistry");
            manaCurrent = hunterMana.getMethod("current", entity);
            manaMaximum = hunterMana.getMethod("maximum", entity);
            classOf = hunterClasses.getMethod("of", entity);
            classIsAwakened = hunterClasses.getMethod("isAwakened", entity);
            statsIntelligence = hunterStats.getMethod("intelligence", entity);
            statsPerception = hunterStats.getMethod("perception", entity);
            progressLevel = hunterProgress.getMethod("level", entity);
            progressRank = hunterProgress.getMethod("rankNumber", entity);
            skillsLearned = hunterSkills.getMethod("learned", entity);
            vesselOf = vessels.getMethod("of", entity);
            shadowMonarch = Class.forName("net.solocraft.util.VesselProgressionManager")
                    .getMethod("isShadowMonarch", entity);
            initialisePlayerVariables();
            registerManaCapacityProvider();
            Class<? extends Event> castEvent = (Class<? extends Event>) Class.forName(
                    "net.solocraft.api.skill.HunterSkillCastEvent");
            bus.addListener(EventPriority.LOWEST, (Class) castEvent,
                    (Consumer) value -> onHunterSkillCast((Event) value));
            active = true;
            DynamicMagic.LOGGER.info("Enabled Solo Leveling shared mana, Hunter progression, gate, and necromancy integration");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.error("Solo Leveling was loaded, but its 1.3.x integration API could not be attached", exception);
        }
    }

    public static boolean active() { return active; }

    public static void ensureMerged(ServerPlayer player, double fallbackMaximum) {
        if (!active || !isAwakened(player) || SYNCHRONIZING.get()) return;
        try {
            double slrMaximum = slrMaximum(player, fallbackMaximum);
            if (player.getPersistentData().getBoolean(MERGED)) return;
            double dynamicCurrent = player.getPersistentData().contains("dynamicmagic.mana")
                    ? player.getPersistentData().getDouble("dynamicmagic.mana") : fallbackMaximum;
            double dynamicRatio = clamp01(dynamicCurrent / Math.max(1, fallbackMaximum));
            double slrCurrent = slrCurrent(player, slrMaximum);
            double slrRatio = clamp01(slrCurrent / Math.max(1, slrMaximum));
            double mergedMaximum = Math.max(fallbackMaximum, slrMaximum);
            double merged = mergedMaximum * Math.min(dynamicRatio, slrRatio);
            player.getPersistentData().putDouble("dynamicmagic.mana", merged);
            player.getPersistentData().putBoolean(MERGED, true);
            setSlrMana(player, merged);
            player.getPersistentData().putDouble(LAST_SHARED, merged);
        } catch (RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not initialise shared Solo Leveling mana", exception);
        }
    }

    public static double effectiveMaximum(ServerPlayer player, double fallback) {
        return active && isAwakened(player) ? Math.max(fallback, slrMaximum(player, fallback)) : fallback;
    }

    public static void syncFromDynamic(ServerPlayer player) {
        if (!active || !isAwakened(player) || SYNCHRONIZING.get()) return;
        try {
            SYNCHRONIZING.set(true);
            double value = player.getPersistentData().getDouble("dynamicmagic.mana");
            setSlrMana(player, value);
            player.getPersistentData().putDouble(LAST_SHARED, value);
        } finally {
            SYNCHRONIZING.set(false);
        }
    }

    public static double spellPowerMultiplier(ServerPlayer player, CraftedSpell spell) {
        Profile profile = profile(player);
        if (!active || !profile.awakened) return 1;
        double weightedAffinity = 0;
        double total = 0;
        for (var instruction : spell.allInstructions()) {
            double weight = instruction.power() * instruction.repetitions();
            weightedAffinity += externalAffinity(player, instruction.element()) * weight;
            total += weight;
        }
        double affinity = total <= 0 ? 0 : weightedAffinity / total;
        double intelligence = Math.min(.60, Math.sqrt(Math.max(0, profile.intelligence)) / 25.0);
        double magicalSkills = Math.min(.30, profile.magicalSkills * .035);
        double fatiguePenalty = Math.min(.30, profile.fatigue / 333.0);
        return Math.max(.65, (1 + intelligence + magicalSkills + affinity * .055) * (1 - fatiguePenalty));
    }

    public static double castingCostMultiplier(ServerPlayer player) {
        Profile profile = profile(player);
        if (!active || !profile.awakened) return 1;
        double efficiency = Math.min(.25, profile.magicalSkills * .025
                + Math.sqrt(Math.max(0, profile.intelligence)) * .004);
        double fatigue = Math.min(.25, profile.fatigue / 400.0);
        return (1 - efficiency) * (1 + fatigue);
    }

    public static double controlBonus(ServerPlayer player) {
        Profile profile = profile(player);
        return !active || !profile.awakened ? 0
                : Math.min(6, Math.sqrt(Math.max(0, profile.perception)) * .30 + profile.rank * .25);
    }

    public static double efficiencyMultiplier(ServerPlayer player) {
        Profile profile = profile(player);
        return !active || !profile.awakened ? 1 : 1 + Math.min(.45,
                Math.sqrt(Math.max(0, profile.intelligence)) * .018 + profile.magicalSkills * .02);
    }

    public static double regenerationMultiplier(ServerPlayer player) {
        Profile profile = profile(player);
        return !active || !profile.awakened ? 1 : Math.max(.35, 1 - Math.min(.65, profile.fatigue / 155.0));
    }

    public static double learningMultiplier(ServerPlayer player, Element element) {
        Profile profile = profile(player);
        return !active || !profile.awakened ? 1 : 1 + Math.min(1.5,
                externalAffinity(player, element) * .12 + profile.magicalSkills * .035);
    }

    public static double forceBonus(ServerPlayer player, Element element) {
        return active ? externalAffinity(player, element) * .22 + profile(player).rank * .08 : 0;
    }

    public static double accuracyBonus(ServerPlayer player) {
        Profile profile = profile(player);
        return !active || !profile.awakened ? 0 : Math.min(.25,
                Math.sqrt(Math.max(0, profile.perception)) * .012 + (classPath(profile).contains("ranger") ? .06 : 0));
    }

    public static double adjustedInstability(ServerPlayer player, double value) {
        Profile profile = profile(player);
        if (!active || !profile.awakened) return value;
        return value * (1 + Math.min(.75, profile.fatigue / 135.0));
    }

    public static void onDynamicSpellCost(ServerPlayer player, double cost) {
        if (!active || !isAwakened(player) || cost <= 0) return;
        Object variables = playerVariables(player);
        if (variables == null) return;
        try {
            Field fatigue = playerVariablesType.getField("Fatigue");
            fatigue.setDouble(variables, Math.min(100, fatigue.getDouble(variables) + Math.min(2.5, cost / 240.0)));
            syncVariables(variables, player);
            refreshProfile(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not add Solo Leveling fatigue for a Dynamic Magic cast", exception);
        }
    }

    public static double externalAffinity(ServerPlayer player, Element element) {
        Profile profile = profile(player);
        if (!active || !profile.awakened) return 0;
        double affinity = classAffinity(profile, element) + vesselAffinity(profile, element);
        for (String skill : profile.skills) if (elementForName(skill) == element) affinity += .45;
        if (element == Element.UNDEAD) affinity += profile.shadowTier * 1.35;
        if (element == Element.SHADOW) affinity += profile.shadowTier * .80;
        return Math.min(8, affinity);
    }

    public static Element elementForEntity(LivingEntity entity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !id.getNamespace().equals(NAMESPACE)) return null;
        return elementForName(id.getPath());
    }

    public static double studyMultiplier(LivingEntity entity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !id.getNamespace().equals(NAMESPACE)) return 1;
        String path = id.getPath();
        return containsAny(path, "boss", "igris", "beru", "baran", "antares", "monarch", "sillad", "kargalgan") ? 3
                : containsAny(path, "shadow", "mage", "shaman", "gate") ? 1.75 : 1.25;
    }

    public static boolean isProtectedEntity(Entity entity) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && id.getNamespace().equals(NAMESPACE);
    }

    public static boolean canMorph(Entity entity) { return !isProtectedEntity(entity); }

    public static boolean isGateDimension(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        if (!id.getNamespace().equals(NAMESPACE)) return false;
        String path = id.getPath();
        return !path.contains("system_void") && (path.contains("dungeon") || path.contains("cartenon")
                || path.contains("rift") || path.contains("survival"));
    }

    public static int shadowTier(ServerPlayer player) { return profile(player).shadowTier; }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        for (String key : new String[]{MERGED, LAST_SHARED, LAST_CLASS})
            if (from.getPersistentData().contains(key))
                to.getPersistentData().put(key, from.getPersistentData().get(key).copy());
        PROFILES.remove(to.getUUID());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!active || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 5 == 0) synchronizeMana(player);
        if (player.tickCount % 20 != 0) return;
        refreshProfile(player);
        grantClassAndShadowKnowledge(player);
        if (isGateDimension(player.serverLevel())) TeleportLocations.recordGateAnchor(player);
        commandOwnedUndead(player);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGH)
    public static void tameUndead(PlayerInteractEvent.EntityInteract event) {
        if (!active || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty() || !(event.getTarget() instanceof Mob mob)
                || (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton))) return;
        int tier = shadowTier(player);
        if (tier < 2) {
            player.displayClientMessage(Component.literal(
                    "Your developing necromancy is not yet strong enough to bind undead.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        if (mob.getPersistentData().hasUUID(UNDEAD_OWNER)) return;
        int capacity = tier >= 5 ? 12 : tier * 2;
        if (ownedUndead(player) >= capacity) {
            player.displayClientMessage(Component.literal("Your undead command limit is " + capacity + '.')
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        double cost = 25 + mob.getMaxHealth();
        if (!Mana.consume(player, cost)) return;
        mob.getPersistentData().putUUID(UNDEAD_OWNER, player.getUUID());
        mob.setPersistenceRequired();
        mob.setTarget(null);
        player.displayClientMessage(Component.literal("The " + mob.getName().getString()
                + " submits to your necromancy (" + (ownedUndead(player)) + '/' + capacity + ").")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGH)
    public static void protectOwnedUndead(LivingIncomingDamageEvent event) {
        if (!active || !(event.getEntity() instanceof Mob victim)
                || !victim.getPersistentData().hasUUID(UNDEAD_OWNER)) return;
        Entity attacker = event.getSource().getEntity();
        UUID owner = victim.getPersistentData().getUUID(UNDEAD_OWNER);
        if (attacker != null && (attacker.getUUID().equals(owner)
                || attacker instanceof Mob ally && ally.getPersistentData().hasUUID(UNDEAD_OWNER)
                && ally.getPersistentData().getUUID(UNDEAD_OWNER).equals(owner))) event.setAmount(0);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGH)
    public static void keepOwnedUndeadFriendly(LivingChangeTargetEvent event) {
        if (!active || !(event.getEntity() instanceof Mob mob)
                || !mob.getPersistentData().hasUUID(UNDEAD_OWNER)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        UUID owner = mob.getPersistentData().getUUID(UNDEAD_OWNER);
        if (target != null && (target.getUUID().equals(owner)
                || target instanceof Mob ally && ally.getPersistentData().hasUUID(UNDEAD_OWNER)
                && ally.getPersistentData().getUUID(UNDEAD_OWNER).equals(owner)))
            event.setNewAboutToBeSetTarget(null);
    }

    private static void synchronizeMana(ServerPlayer player) {
        if (SYNCHRONIZING.get() || !isAwakened(player)) return;
        ensureMerged(player, Mana.nativeMaximum(player));
        if (!player.getPersistentData().getBoolean(MERGED)) return;
        try {
            SYNCHRONIZING.set(true);
            double maximum = effectiveMaximum(player,
                    IronSpellsIntegration.effectiveMaximum(player, Mana.nativeMaximum(player)));
            double slr = Math.min(maximum, slrCurrent(player, maximum));
            double dynamic = Math.min(maximum, player.getPersistentData().getDouble("dynamicmagic.mana"));
            double last = player.getPersistentData().getDouble(LAST_SHARED);
            if (Math.abs(slr - last) > .01) {
                player.getPersistentData().putDouble("dynamicmagic.mana", slr);
                dynamic = slr;
                IronSpellsIntegration.syncFromDynamic(player);
            } else if (Math.abs(dynamic - slr) > .01) setSlrMana(player, dynamic);
            player.getPersistentData().putDouble(LAST_SHARED, dynamic);
        } finally {
            SYNCHRONIZING.set(false);
        }
    }

    private static void grantClassAndShadowKnowledge(ServerPlayer player) {
        Profile profile = profile(player);
        if (!profile.awakened) return;
        String path = classPath(profile);
        if (!path.isBlank() && !path.equals(player.getPersistentData().getString(LAST_CLASS))) {
            Element element = switch (path) {
                case "assassin" -> Element.SHADOW;
                case "combat_mage" -> elementForName(profile.specialization);
                case "fighter" -> Element.METAL;
                case "tanker" -> Element.EARTH;
                case "support_mage" -> Element.DIVINE;
                case "ranger" -> Element.AIR;
                default -> null;
            };
            if (element != null && ElementKnowledge.learn(player, element))
                player.displayClientMessage(Component.literal("Your Hunter awakening resonates with "
                        + element.displayName() + " magic.").withStyle(ChatFormatting.AQUA), false);
            player.getPersistentData().putString(LAST_CLASS, path);
        }
        if (profile.shadowTier >= 1 && ElementKnowledge.learn(player, Element.UNDEAD))
            player.displayClientMessage(Component.literal(
                    "Your path toward the Shadow Monarch has awakened Necromancy magic.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        if (profile.shadowTier >= 3) ElementKnowledge.learn(player, Element.SHADOW);
    }

    private static void commandOwnedUndead(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(64),
                    candidate -> candidate.getPersistentData().hasUUID(UNDEAD_OWNER)
                            && candidate.getPersistentData().getUUID(UNDEAD_OWNER).equals(owner.getUUID()))) {
                mob.setRemainingFireTicks(0);
                mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false));
                LivingEntity target = owner.getLastHurtMob();
                if (target != null && target.isAlive() && target != mob && owner.distanceToSqr(target) < 1024)
                    mob.setTarget(target);
                else if (mob.getTarget() == owner) mob.setTarget(null);
                if (mob.distanceToSqr(owner) > 36) mob.getNavigation().moveTo(owner, 1.15);
                if (mob.distanceToSqr(owner) > 1024 && mob.level() == owner.level())
                    mob.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
        }
    }

    private static int ownedUndead(ServerPlayer owner) {
        int count = 0;
        for (ServerLevel level : owner.server.getAllLevels())
            for (Entity entity : level.getAllEntities())
                if (entity.getPersistentData().hasUUID(UNDEAD_OWNER)
                        && entity.getPersistentData().getUUID(UNDEAD_OWNER).equals(owner.getUUID())) count++;
        return count;
    }

    private static void onHunterSkillCast(Event event) {
        try {
            Object caster = event.getClass().getMethod("getCaster").invoke(event);
            String skill = String.valueOf(event.getClass().getMethod("getSkill").invoke(event));
            if (!(caster instanceof ServerPlayer player)) return;
            Element element = elementForName(skill);
            if (ElementKnowledge.knows(player, element))
                ElementMastery.practice(player, element, 1 + profile(player).rank * .15);
            String lower = skill.toLowerCase(Locale.ROOT);
            if (containsAny(lower, "arrow", "missile", "projectile", "throw", "shot")) {
                CraftedSpell practice = new CraftedSpell("Hunter Skill", com.strutton.dynamicmagic.magic.SourceType.CREATE,
                        element, com.strutton.dynamicmagic.magic.SpellForm.BOLT,
                        com.strutton.dynamicmagic.magic.DeliveryType.PROJECTILE,
                        com.strutton.dynamicmagic.magic.ImpactType.DAMAGE, 1);
                ProjectileAccuracy.practice(player, practice);
            }
            refreshProfile(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not translate a Solo Leveling skill cast into Dynamic mastery", exception);
        }
    }

    private static Profile profile(ServerPlayer player) {
        if (!active) return Profile.EMPTY;
        return PROFILES.computeIfAbsent(player.getUUID(), ignored -> readProfile(player));
    }

    private static void refreshProfile(ServerPlayer player) { PROFILES.put(player.getUUID(), readProfile(player)); }

    @SuppressWarnings("unchecked")
    private static Profile readProfile(ServerPlayer player) {
        try {
            boolean awakened = isAwakened(player);
            String hunterClass = idOf(classOf.invoke(null, player));
            Object vesselResult = vesselOf.invoke(null, player);
            String vessel = vesselResult instanceof Optional<?> optional ? optional.map(SoloLevelingIntegration::idOf).orElse("") : "";
            List<String> learned = new ArrayList<>();
            Object rawSkills = skillsLearned.invoke(null, player);
            if (rawSkills instanceof List<?> list)
                for (Object value : list) learned.add(String.valueOf(value).toLowerCase(Locale.ROOT));
            Object variables = playerVariables(player);
            double fatigue = fieldDouble(variables, "Fatigue");
            double advancement = fieldDouble(variables, "jobadvpoint");
            String specialization = fieldString(variables, "mageSpecialization");
            boolean monarch = Boolean.TRUE.equals(shadowMonarch.invoke(null, player));
            int shadowSkills = (int) learned.stream().filter(skill -> containsAny(skill,
                    "shadow", "arise", "necroman", "extraction", "monarch")).count();
            int shadowTier = monarch || vessel.contains("shadow") ? 5
                    : Math.min(4, Math.max(shadowSkills > 0 ? 1 : 0, (int) Math.floor(advancement / 12.5)));
            int magicalSkills = (int) learned.stream().filter(skill -> containsAny(skill,
                    "magic", "mana", "spell", "mage", "arcane", "sorcer", "element", "barrier", "healing")).count();
            return new Profile(awakened,
                    number(statsIntelligence.invoke(null, player)), number(statsPerception.invoke(null, player)),
                    number(progressLevel.invoke(null, player)), (int) number(progressRank.invoke(null, player)),
                    fatigue, hunterClass, vessel, specialization, List.copyOf(learned), magicalSkills, shadowTier);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not read Solo Leveling Hunter profile", exception);
            return Profile.EMPTY;
        }
    }

    private static double classAffinity(Profile profile, Element element) {
        String path = classPath(profile);
        return switch (path) {
            case "assassin" -> element == Element.SHADOW || element == Element.SPACE ? 1.5 : 0;
            case "combat_mage" -> element == Element.ARCANE || element == elementForName(profile.specialization) ? 2 : 0;
            case "fighter" -> element == Element.METAL ? 1.25 : 0;
            case "tanker" -> element == Element.EARTH || element == Element.METAL ? 1.5 : 0;
            case "support_mage" -> element == Element.DIVINE || element == Element.SPIRIT ? 2 : 0;
            case "ranger" -> element == Element.AIR ? 1.5 : 0;
            default -> 0;
        };
    }

    private static double vesselAffinity(Profile profile, Element element) {
        String vessel = profile.vessel;
        if (vessel.contains("shadow") && (element == Element.SHADOW || element == Element.UNDEAD)) return 3;
        if (containsAny(vessel, "frost", "ice", "sillad") && element == Element.ICE) return 3;
        if (containsAny(vessel, "flame", "fire", "baran") && element == Element.FIRE) return 3;
        if (containsAny(vessel, "storm", "lightning") && (element == Element.STORM || element == Element.LIGHTNING)) return 3;
        if (vessel.contains("beast") && element == Element.SPIRIT) return 2.5;
        return 0;
    }

    private static Element elementForName(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (containsAny(value, "arise", "necroman", "undead", "skeleton", "soul")) return Element.UNDEAD;
        if (value.contains("shadow")) return Element.SHADOW;
        if (containsAny(value, "frost", "ice", "snow", "freeze", "sillad")) return Element.ICE;
        if (containsAny(value, "fire", "flame", "burn", "cremation", "inferno")) return Element.FIRE;
        if (containsAny(value, "storm", "lightning", "thunder", "volt")) return Element.LIGHTNING;
        if (containsAny(value, "barrier", "shield", "protection", "light")) return Element.LIGHT;
        if (containsAny(value, "heal", "support", "holy", "divine")) return Element.DIVINE;
        if (containsAny(value, "gate", "rift", "exchange", "teleport", "space")) return Element.SPACE;
        if (containsAny(value, "water", "aqua", "tide")) return Element.WATER;
        if (containsAny(value, "earth", "stone", "golem")) return Element.EARTH;
        if (containsAny(value, "wind", "air", "arrow", "quiver")) return Element.AIR;
        if (containsAny(value, "blood", "vamp")) return Element.BLOOD;
        if (containsAny(value, "nature", "beast", "wolf", "bear")) return Element.SPIRIT;
        return Element.ARCANE;
    }

    private static boolean isAwakened(ServerPlayer player) {
        if (!active && classIsAwakened == null) return false;
        try { return Boolean.TRUE.equals(classIsAwakened.invoke(null, player)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static void initialisePlayerVariables() throws ReflectiveOperationException {
        Class<?> variables = Class.forName("net.solocraft.network.SololevelingModVariables");
        playerVariablesType = Class.forName("net.solocraft.network.SololevelingModVariables$PlayerVariables");
        capability = variables.getField("PLAYER_VARIABLES_CAPABILITY").get(null);
        for (Method method : Entity.class.getMethods())
            if (method.getName().equals("getCapability") && method.getParameterCount() == 2) {
                getCapability = method;
                break;
            }
        if (getCapability == null) throw new NoSuchMethodException("Entity.getCapability(EntityCapability, context)");
    }

    private static Object playerVariables(ServerPlayer player) {
        if (capability == null || getCapability == null) return null;
        try {
            Object result = getCapability.invoke(player, capability, null);
            return result instanceof Optional<?> optional ? optional.orElse(null) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static void syncVariables(Object variables, Entity entity) throws ReflectiveOperationException {
        playerVariablesType.getMethod("syncPlayerVariables", Entity.class).invoke(variables, entity);
    }

    private static void setSlrMana(ServerPlayer player, double value) {
        Object variables = playerVariables(player);
        if (variables == null) return;
        try {
            playerVariablesType.getField("MP").setDouble(variables,
                    Math.max(0, Math.min(slrMaximum(player, value), value)));
            syncVariables(variables, player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not synchronize mana into Solo Leveling", exception);
        }
    }

    private static double slrCurrent(ServerPlayer player, double fallback) {
        try { return number(manaCurrent.invoke(null, player)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return fallback; }
    }

    private static double slrMaximum(ServerPlayer player, double fallback) {
        try { return Math.max(1, number(manaMaximum.invoke(null, player))); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return fallback; }
    }

    private static void registerManaCapacityProvider() throws ReflectiveOperationException {
        Class<?> manager = Class.forName("net.solocraft.util.TemporaryStatBonusManager");
        Class<?> providerType = Class.forName("net.solocraft.util.TemporaryStatBonusManager$BonusProvider");
        Object provider = Proxy.newProxyInstance(providerType.getClassLoader(), new Class[]{providerType},
                (proxy, method, arguments) -> {
                    if (!method.getName().equals("collect") || arguments == null || arguments.length != 4) return null;
                    if (!(arguments[0] instanceof ServerPlayer player) || !String.valueOf(arguments[1]).equals("INTELLIGENCE")) return null;
                    double bonus = Math.max(0, Mana.nativeMaximum(player) - Mana.DEFAULT_MAX) / 100.0;
                    if (bonus <= 0) return null;
                    Method add = arguments[3].getClass().getMethod("add", ResourceLocation.class, Component.class, double.class);
                    add.invoke(arguments[3], ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana_expansion"),
                            Component.literal("Dynamic Mana Expansion"), bonus);
                    return null;
                });
        manager.getMethod("registerProvider", ResourceLocation.class, providerType).invoke(null,
                ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana_expansion"), provider);
    }

    private static String idOf(Object value) {
        if (value == null) return "";
        try { return String.valueOf(value.getClass().getMethod("id").invoke(value)).toLowerCase(Locale.ROOT); }
        catch (ReflectiveOperationException ignored) { return String.valueOf(value).toLowerCase(Locale.ROOT); }
    }

    private static String classPath(Profile profile) {
        int split = profile.hunterClass.indexOf(':');
        return split >= 0 ? profile.hunterClass.substring(split + 1) : profile.hunterClass;
    }

    private static double fieldDouble(Object owner, String name) {
        if (owner == null) return 0;
        try { return playerVariablesType.getField(name).getDouble(owner); }
        catch (ReflectiveOperationException ignored) { return 0; }
    }

    private static String fieldString(Object owner, String name) {
        if (owner == null) return "";
        try { return String.valueOf(playerVariablesType.getField(name).get(owner)); }
        catch (ReflectiveOperationException ignored) { return ""; }
    }

    private static double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0; }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private record Profile(boolean awakened, double intelligence, double perception, double level, int rank,
                           double fatigue, String hunterClass, String vessel, String specialization,
                           List<String> skills, int magicalSkills, int shadowTier) {
        private static final Profile EMPTY = new Profile(false, 0, 0, 0, 0,
                0, "", "", "", List.of(), 0, 0);
    }
}
