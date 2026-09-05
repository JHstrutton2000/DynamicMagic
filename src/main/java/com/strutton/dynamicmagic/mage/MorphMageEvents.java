package com.strutton.dynamicmagic.mage;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Rare mages hiding in the unmodified body and behavior set of ordinary mobs. */
public final class MorphMageEvents {
    public static final double MORPH_MAGE_CHANCE = .01;
    private static final String CHECKED = "DynamicMagicMorphMageChecked";
    private static final String MORPH_MAGE = "DynamicMagicMorphMage";
    private static final String TELL = "DynamicMagicMorphMageTell";
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    private static final Vector3f[] EYE_COLORS = {
            new Vector3f(.15f, 1f, 1f), new Vector3f(1f, .1f, .75f),
            new Vector3f(.65f, 1f, .05f), new Vector3f(1f, .35f, .05f),
            new Vector3f(.55f, .2f, 1f), new Vector3f(1f, 1f, .1f)
    };

    private MorphMageEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!net.neoforged.fml.ModList.get().isLoaded("morph") || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof Mob mob)
                || mob.getPersistentData().getBoolean(CHECKED)) return;
        mob.getPersistentData().putBoolean(CHECKED, true);
        if (eligible(mob) && mob.getRandom().nextDouble() < MORPH_MAGE_CHANCE) makeMorphMage(mob);
    }

    public static boolean makeMorphMage(LivingEntity entity) {
        if (!eligible(entity)) return false;
        entity.getPersistentData().putBoolean(CHECKED, true);
        entity.getPersistentData().putBoolean(MORPH_MAGE, true);
        entity.getPersistentData().putInt(TELL, entity.getRandom().nextInt(6));
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        // An unarmed skeleton is one of the possible physical mistakes in the disguise.
        if (entity instanceof AbstractSkeleton skeleton) skeleton.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        if (!entity.level().isClientSide()) syncVisual(entity);
        return true;
    }

    public static boolean isMorphMage(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(MORPH_MAGE);
    }

    public static int eyeColor(LivingEntity entity) {
        return Math.floorMod(entity.getPersistentData().getInt(TELL), EYE_COLORS.length);
    }

    /** Applies server-authoritative render data after the target entity reaches the client. */
    public static void applyClientVisual(LivingEntity entity, int eyeColor) {
        entity.getPersistentData().putBoolean(MORPH_MAGE, true);
        entity.getPersistentData().putInt(TELL, Math.floorMod(eyeColor, EYE_COLORS.length));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target && isMorphMage(target))
            PacketDistributor.sendToPlayer(player, visualPayload(target));
    }

    public static MerchantOffers offers(LivingEntity entity) {
        return MageMerchant.morphMageOffers(entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity target) || !isMorphMage(target)) return;
        MageMerchant.forMorphMage(target).open(player);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Disguised mages remain neutral even when wearing a hostile creature's form. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && isMorphMage(entity)
                && event.getNewAboutToBeSetTarget() instanceof Player)
            event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % 10 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels())
            for (var raw : level.getAllEntities())
                if (raw instanceof LivingEntity entity && isMorphMage(entity)) showDisguiseErrors(level, entity, now);
    }

    private static void showDisguiseErrors(ServerLevel level, LivingEntity entity, long now) {
        int tell = eyeColor(entity);
        double yaw = Math.toRadians(entity.getYHeadRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double front = entity.getBbWidth() * .42 + .04;
        double side = Math.max(.035, Math.min(.22, entity.getBbWidth() * .16));
        double eyeY = entity.getEyeY() - Math.min(.10, entity.getBbHeight() * .04);
        double eyeX = entity.getX() + forwardX * front;
        double eyeZ = entity.getZ() + forwardZ * front;
        if (now % 20 != 0) return;
        switch (tell) {
            case 0 -> level.sendParticles(ParticleTypes.WITCH, entity.getX(),
                    entity.getY() + entity.getBbHeight() + .15, entity.getZ(), 2, .22, .03, .22, 0);
            case 1 -> level.sendParticles(new DustParticleOptions(new Vector3f(.3f, .05f, .55f), .8f),
                    entity.getX() - forwardX * entity.getBbWidth() * .55, entity.getY() + entity.getBbHeight() * .55,
                    entity.getZ() - forwardZ * entity.getBbWidth() * .55, 3, .12, .28, .05, 0);
            case 2 -> level.sendParticles(ParticleTypes.SMOKE, eyeX, eyeY, eyeZ, 2, side, .02, side, 0);
            case 3 -> level.sendParticles(new DustParticleOptions(new Vector3f(1f, .2f, .65f), .7f),
                    eyeX, eyeY - entity.getBbHeight() * .10, eyeZ, 3, side, .01, .01, 0);
            case 4 -> level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + entity.getBbHeight() * .6,
                    entity.getZ(), 3, entity.getBbWidth() * .35, entity.getBbHeight() * .2, entity.getBbWidth() * .35, 0);
            default -> level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() * .65,
                    entity.getZ(), 2, entity.getBbWidth() * .25, entity.getBbHeight() * .15, entity.getBbWidth() * .25, 0);
        }
    }

    private static void syncVisual(LivingEntity entity) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, visualPayload(entity));
    }

    private static com.strutton.dynamicmagic.network.MorphMageSyncPayload visualPayload(LivingEntity entity) {
        return new com.strutton.dynamicmagic.network.MorphMageSyncPayload(entity.getId(), eyeColor(entity));
    }

    private static boolean eligible(LivingEntity entity) {
        return entity instanceof Mob && !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss)
                && !entity.getType().is(BOSSES) && !VillageMageEvents.isMage(entity)
                && entity.getType() != DynamicMagic.CREEPER_VILLAGER.get()
                && entity.getType() != DynamicMagic.ENDERMAN_VILLAGER.get()
                && entity.getType() != DynamicMagic.ALEX_VILLAGER.get()
                && entity.getType() != DynamicMagic.SKELETON_MAGE_VILLAGER.get();
    }
}
