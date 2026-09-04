package com.strutton.dynamicmagic.mage;

import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.vampire.Vampirism;
import com.strutton.dynamicmagic.entity.CreeperVillager;
import com.strutton.dynamicmagic.entity.EndermanVillager;
import com.strutton.dynamicmagic.entity.AlexVillager;
import com.strutton.dynamicmagic.entity.SkeletonMageVillager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class VillageMageEvents {
    public static final double VILLAGER_MAGE_CHANCE = .30;
    private static final String ELEMENTS = "DynamicMagicMageElements";
    private static final String ANGRY_AT = "DynamicMagicMageAngryAt";
    private static final String ANGRY_UNTIL = "DynamicMagicMageAngryUntil";
    private static final String SKILLS = "DynamicMagicMageSkills";
    private static final String SUPPRESS_HUNTER_OMEN = "DynamicMagicSuppressHunterOmenUntil";
    private static final String NECROMANCER = "DynamicMagicNecromancerTrader";
    private static final String SPECIALIST_CHECKED = "DynamicMagicSpecialistScholarChecked";
    private static final Element[] COMMON = {Element.FIRE, Element.WATER, Element.EARTH, Element.AIR, Element.LIGHTNING};
    private static final String[] NAMES = {
            "Aldren", "Amara", "Bram", "Cassia", "Corvin", "Elara", "Elias", "Fenna",
            "Galen", "Ilyra", "Joren", "Kael", "Liora", "Maren", "Nessa", "Orin",
            "Petra", "Quill", "Rowan", "Selene", "Tavian", "Vesper", "Wren", "Ysara"
    };
    private VillageMageEvents() {}

    public static boolean isMage(Villager villager) {
        return isMage((LivingEntity) villager);
    }

    public static boolean isMage(LivingEntity entity) { return entity.getPersistentData().getLong(ELEMENTS) != 0; }
    public static boolean isNecromancer(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(NECROMANCER);
    }
    public static long elementTradeMask(LivingEntity entity) {
        return entity.getPersistentData().getLong(ELEMENTS);
    }
    public static long skillTradeMask(LivingEntity entity) {
        return entity.getPersistentData().getLong(SKILLS);
    }

    public static void makeMage(Villager villager) {
        makeMage((LivingEntity) villager);
    }

    public static void makeMage(LivingEntity mage) {
        Element primary = COMMON[mage.getRandom().nextInt(COMMON.length)];
        long mask = bit(primary);
        double chance = .12;
        while (mage.getRandom().nextDouble() < chance && Long.bitCount(mask) < 4) {
            Element next = Element.values()[mage.getRandom().nextInt(Element.values().length)];
            mask |= bit(next);
            chance *= .25;
        }
        mage.getPersistentData().putLong(ELEMENTS, mask);
        long skillMask = 0;
        if (mage.getRandom().nextDouble() < .35) {
            MagicSkill[] skills = java.util.Arrays.stream(MagicSkill.values()).filter(MagicSkill::randomDrop)
                    .toArray(MagicSkill[]::new);
            skillMask |= 1L << skills[mage.getRandom().nextInt(skills.length)].ordinal();
            if (mage.getRandom().nextDouble() < .08)
                skillMask |= 1L << skills[mage.getRandom().nextInt(skills.length)].ordinal();
        }
        mage.getPersistentData().putLong(SKILLS, skillMask);
        String personalName = NAMES[mage.getRandom().nextInt(NAMES.length)];
        mage.setCustomName(Component.literal(personalName + " the " + title(primary)));
        mage.setCustomNameVisible(true);
    }

    public static void makeNecromancer(ZombieVillager necromancer) {
        necromancer.getPersistentData().putLong(ELEMENTS, bit(Element.UNDEAD));
        necromancer.getPersistentData().putLong(SKILLS, 0);
        necromancer.getPersistentData().putBoolean(NECROMANCER, true);
        String personalName = NAMES[necromancer.getRandom().nextInt(NAMES.length)];
        necromancer.setCustomName(Component.literal(personalName + " the Necromancer"));
        necromancer.setCustomNameVisible(true);
        necromancer.setPersistenceRequired();
        necromancer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false, true));
    }

    public static void makeSkeletonNecromancer(SkeletonMageVillager necromancer) {
        long elements = bit(Element.UNDEAD);
        if (necromancer.getRandom().nextDouble() < .25)
            elements |= bit(COMMON[necromancer.getRandom().nextInt(COMMON.length)]);
        necromancer.getPersistentData().putLong(ELEMENTS, elements);
        necromancer.getPersistentData().putLong(SKILLS, 0);
        necromancer.getPersistentData().putBoolean(NECROMANCER, true);
        String personalName = NAMES[necromancer.getRandom().nextInt(NAMES.length)];
        necromancer.setCustomName(Component.literal(personalName + " the Bone Sage"));
        necromancer.setCustomNameVisible(true);
        necromancer.setPersistenceRequired();
        necromancer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false, true));
    }

    public static void inheritAsNecromancer(Villager mage, ZombieVillager necromancer) {
        if (!isMage(mage)) return;
        long elements = mage.getPersistentData().getLong(ELEMENTS) | bit(Element.UNDEAD);
        necromancer.getPersistentData().putLong(ELEMENTS, elements);
        necromancer.getPersistentData().putLong(SKILLS, mage.getPersistentData().getLong(SKILLS));
        necromancer.getPersistentData().putBoolean(NECROMANCER, true);
        String previous = mage.getName().getString();
        int title = previous.indexOf(" the ");
        String personalName = title > 0 ? previous.substring(0, title) : previous;
        necromancer.setCustomName(Component.literal(personalName + " the Necromancer"));
        necromancer.setCustomNameVisible(true);
        necromancer.setPersistenceRequired();
        necromancer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 240, 0, false, false, true));
    }

    @SubscribeEvent public static void onConversion(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof Villager mage && event.getOutcome() instanceof ZombieVillager zombie)
            inheritAsNecromancer(mage, zombie);
    }

    /** Two percent of natural zombie villagers awaken as peaceful necromancer scholars. */
    @SubscribeEvent public static void onNaturalSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL
                || !(event.getEntity() instanceof ZombieVillager zombie)
                || zombie.getPersistentData().contains(ELEMENTS)) return;
        zombie.getPersistentData().putLong(ELEMENTS, 0);
        if (zombie.getRandom().nextDouble() < .02) makeNecromancer(zombie);
    }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || isSpecialist(event.getEntity())) return;
        if (event.getEntity() instanceof Villager villager
                && !villager.getPersistentData().getBoolean(SPECIALIST_CHECKED)) {
            villager.getPersistentData().putBoolean(SPECIALIST_CHECKED, true);
            double roll = villager.getRandom().nextDouble();
            if (roll < .04) {
                LivingEntity scholar = roll < .01
                        ? com.strutton.dynamicmagic.DynamicMagic.CREEPER_VILLAGER.get().create(event.getLevel())
                        : roll < .02
                        ? com.strutton.dynamicmagic.DynamicMagic.ENDERMAN_VILLAGER.get().create(event.getLevel())
                        : roll < .03
                        ? com.strutton.dynamicmagic.DynamicMagic.ALEX_VILLAGER.get().create(event.getLevel())
                        : com.strutton.dynamicmagic.DynamicMagic.SKELETON_MAGE_VILLAGER.get().create(event.getLevel());
                if (scholar != null) {
                    if (scholar instanceof SkeletonMageVillager skeleton) {
                        skeleton.setVillagerData(villager.getVillagerData());
                        skeleton.setAge(villager.getAge());
                        makeSkeletonNecromancer(skeleton);
                    } else if (scholar instanceof Villager specialist) {
                        specialist.setVillagerData(villager.getVillagerData());
                        specialist.setAge(villager.getAge());
                    }
                    scholar.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
                    if (scholar instanceof Mob mob) mob.setPersistenceRequired();
                    event.setCanceled(true);
                    event.getLevel().addFreshEntity(scholar);
                }
                return;
            }
        }
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity entity)
                || !(entity instanceof Villager || entity instanceof Witch)
                || entity.getPersistentData().contains(ELEMENTS)) return;
        entity.getPersistentData().putLong(ELEMENTS, 0);
        double chance = entity instanceof Witch ? .10 : VILLAGER_MAGE_CHANCE;
        if (entity.getRandom().nextDouble() < chance) makeMage(entity);
    }

    @SubscribeEvent public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() instanceof CreeperVillager scholar) {
            MageMerchant.openCreeperScholar(scholar, player);
            cancel(event);
            return;
        }
        if (event.getTarget() instanceof EndermanVillager scholar) {
            MageMerchant.openEndermanScholar(scholar, player);
            cancel(event);
            return;
        }
        if (event.getTarget() instanceof AlexVillager scholar) {
            MageMerchant.openAlexScholar(scholar, player);
            cancel(event);
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity mage) || !isMage(mage)) return;
        long mask = mage.getPersistentData().getLong(ELEMENTS);
        if (!isNecromancer(mage) && Vampirism.isVampire(player)) {
            mage.getPersistentData().putUUID(ANGRY_AT, player.getUUID());
            mage.getPersistentData().putLong(ANGRY_UNTIL, player.serverLevel().getGameTime() + 600);
            player.displayClientMessage(Component.literal("The village mage recognizes your vampirism!"), true);
            cancel(event);
            return;
        }
        new MageMerchant(mage, mask, mage.getPersistentData().getLong(SKILLS)).open(player);
        cancel(event);
    }

    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mage) || !isMage(mage)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        mage.getPersistentData().putUUID(ANGRY_AT, attacker.getUUID());
        mage.getPersistentData().putLong(ANGRY_UNTIL, attacker.serverLevel().getGameTime() + 600);
        attacker.getPersistentData().putLong(SUPPRESS_HUNTER_OMEN, attacker.serverLevel().getGameTime() + 20);
    }

    @SubscribeEvent public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mage) || !isMage(mage)) return;
        if (isNecromancer(mage)) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer target)) return;
        long now = target.serverLevel().getGameTime();
        boolean personallyAngry = mage.getPersistentData().hasUUID(ANGRY_AT)
                && mage.getPersistentData().getUUID(ANGRY_AT).equals(target.getUUID())
                && mage.getPersistentData().getLong(ANGRY_UNTIL) > now;
        if (!personallyAngry && !Vampirism.isVampire(target)) event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            if (player.getPersistentData().getLong(SUPPRESS_HUNTER_OMEN) >= now) removeHunterOmen(player);
        if (now % 40 != 0) return;
        for (var level : event.getServer().getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity mage) || !isMage(mage)) continue;
            if (isNecromancer(mage)) {
                mage.clearFire();
                mage.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, false, false, true));
                if (mage instanceof Mob mob) mob.setTarget(null);
                continue;
            }
            ServerPlayer target = nearestVisibleVampire(level, mage);
            if (target != null) {
                mage.getPersistentData().putUUID(ANGRY_AT, target.getUUID());
                mage.getPersistentData().putLong(ANGRY_UNTIL, now + 600);
            } else if (mage.getPersistentData().hasUUID(ANGRY_AT)
                    && mage.getPersistentData().getLong(ANGRY_UNTIL) > now) {
                target = event.getServer().getPlayerList().getPlayer(mage.getPersistentData().getUUID(ANGRY_AT));
            }
            if (target == null || target.level() != level || mage.distanceToSqr(target) > 256) continue;
            Element element = firstElement(mage.getPersistentData().getLong(ELEMENTS));
            target.hurt(level.damageSources().magic(), element == Element.FIRE ? 5 : 3);
            if (element == Element.FIRE) target.igniteForSeconds(3);
            else if (element == Element.ICE) target.setTicksFrozen(target.getTicksFrozen() + 100);
            else if (element == Element.AIR) target.push(target.getX() - mage.getX(), .2, target.getZ() - mage.getZ());
            level.sendParticles(element == Element.BLOOD ? net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR
                    : net.minecraft.core.particles.ParticleTypes.WITCH, target.getX(), target.getY() + 1,
                    target.getZ(), 12, .3, .5, .3, .05);
        }
    }

    private static ServerPlayer nearestVisibleVampire(net.minecraft.server.level.ServerLevel level, LivingEntity mage) {
        ServerPlayer closest = null;
        double closestDistance = 256;
        for (ServerPlayer player : level.players()) {
            double distance = mage.distanceToSqr(player);
            if (!Vampirism.isVampire(player) || distance > closestDistance || !mage.hasLineOfSight(player)) continue;
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    private static void removeHunterOmen(ServerPlayer player) {
        for (var effect : java.util.List.copyOf(player.getActiveEffects())) {
            var id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null && id.getNamespace().equals("vampirism") && id.getPath().equals("bad_omen_hunter"))
                player.removeEffect(effect.getEffect());
        }
    }
    private static void cancel(PlayerInteractEvent.EntityInteract event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
    private static boolean isSpecialist(net.minecraft.world.entity.Entity entity) {
        return entity instanceof CreeperVillager || entity instanceof EndermanVillager
                || entity instanceof AlexVillager || entity instanceof SkeletonMageVillager;
    }
    private static long bit(Element element) { return 1L << element.ordinal(); }
    private static String title(Element element) {
        return switch (element) {
            case FIRE -> "Flamecaller";
            case WATER -> "Tidecaller";
            case EARTH -> "Stoneweaver";
            case AIR -> "Windwalker";
            case LIGHTNING -> "Stormcaller";
            default -> element.displayName() + " Mage";
        };
    }
    private static Element firstElement(long mask) {
        for (Element element : Element.values()) if ((mask & bit(element)) != 0) return element;
        return Element.ARCANE;
    }
}
