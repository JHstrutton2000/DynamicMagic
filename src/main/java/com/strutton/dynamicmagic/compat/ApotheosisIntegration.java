package com.strutton.dynamicmagic.compat;

import com.strutton.dynamicmagic.magic.DeliveryType;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Optional hooks for the split Apotheosis 1.21 suite. */
public final class ApotheosisIntegration {
    private static final String APOTHIC_SPAWNER_TILE =
            "dev.shadowsoffire.apothic_spawners.block.ApothSpawnerTile";
    private static final String CHARGED_SPAWN = "DynamicMagicApothicSpawnerCharged";
    private static final String LAST_SPAWNER_WARNING = "DynamicMagicApothicSpawnerWarning";
    private static final int SOURCE_RADIUS = 32;
    private static final int PLAYER_RADIUS = 64;

    private ApotheosisIntegration() {}

    @SubscribeEvent
    public static void onSpawnerMobJoins(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob)
                || !MobSpawnType.isSpawner(mob.getSpawnType())
                || mob.getPersistentData().getBoolean(CHARGED_SPAWN)) return;

        BlockEntity spawner = findApothicSpawner(level, mob);
        if (spawner == null) return;

        ServerPlayer payer = nearestPlayer(level, spawner);
        if (payer == null) {
            // Even an Ignore Players spawner remains magical machinery and may not run unattended.
            event.setCanceled(true);
            return;
        }

        double cost = spawnManaCost(mob.getMaxHealth());
        if (!Mana.isUnlimited(payer) && Mana.get(payer) + 1.0e-6 < cost) {
            event.setCanceled(true);
            showInsufficientMana(payer, cost);
            return;
        }

        Mana.consume(payer, cost);
        mob.getPersistentData().putBoolean(CHARGED_SPAWN, true);
        mob.getPersistentData().putUUID("DynamicMagicApothicSpawnerPayer", payer.getUUID());
        payer.displayClientMessage(Component.literal("Apothic spawner used " + format(cost)
                + " mana (" + Mana.display(payer) + " remaining).")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    @SubscribeEvent
    public static void onSpawnerTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().getItem().getClass().getName()
                .startsWith("dev.shadowsoffire.apothic_spawners.block.ApothSpawnerItem")) return;
        event.getToolTip().add(Component.literal("Requires a player within 64 blocks; each spawn drains mana.")
                .withStyle(ChatFormatting.DARK_AQUA));
        event.getToolTip().add(Component.literal("Stronger creatures cost more mana.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static double spawnManaCost(double maximumHealth) {
        return Math.ceil(Math.max(2, Math.max(1, maximumHealth) * .20));
    }

    public static float adjustSpellDamage(ServerPlayer caster, Element element,
                                          DeliveryType delivery, float baseDamage) {
        double adjusted = baseDamage;
        String elementalAttribute = switch (element) {
            case FIRE, SCORCH, LAVA, PLASMA -> "fire_damage";
            case ICE -> "cold_damage";
            case KI -> null;
            default -> null;
        };
        if (elementalAttribute != null) {
            double rating = attributeValue(caster, elementalAttribute, 0);
            adjusted += Math.min(baseDamage * .25, Math.max(0, rating) * .25);
        }
        if (delivery == DeliveryType.PROJECTILE) {
            double multiplier = attributeValue(caster, "projectile_damage", 1);
            adjusted *= 1 + Math.min(.25, Math.max(0, multiplier - 1));
        }
        return (float) adjusted;
    }

    public static double cappedAttributeBonus(double baseDamage, double flatRating) {
        return Math.min(Math.max(0, baseDamage) * .25, Math.max(0, flatRating) * .25);
    }

    private static double attributeValue(ServerPlayer player, String path, double fallback) {
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("apothic_attributes", path);
            Holder.Reference<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
            return attribute == null ? fallback : player.getAttributeValue(attribute);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static BlockEntity findApothicSpawner(ServerLevel level, Mob mob) {
        BlockEntity closest = null;
        double closestDistance = SOURCE_RADIUS * SOURCE_RADIUS + 1;
        int centerX = mob.chunkPosition().x;
        int centerZ = mob.chunkPosition().z;
        int chunkRadius = (SOURCE_RADIUS + 15) / 16;
        for (int chunkX = centerX - chunkRadius; chunkX <= centerX + chunkRadius; chunkX++) {
            for (int chunkZ = centerZ - chunkRadius; chunkZ <= centerZ + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                for (BlockEntity candidate : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!candidate.getClass().getName().equals(APOTHIC_SPAWNER_TILE)) continue;
                    double distance = candidate.getBlockPos().distToCenterSqr(mob.position());
                    if (distance <= SOURCE_RADIUS * SOURCE_RADIUS && distance < closestDistance) {
                        closest = candidate;
                        closestDistance = distance;
                    }
                }
            }
        }
        return closest;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockEntity spawner) {
        ServerPlayer closest = null;
        double closestDistance = PLAYER_RADIUS * PLAYER_RADIUS + 1;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            double distance = player.distanceToSqr(spawner.getBlockPos().getCenter());
            if (distance <= PLAYER_RADIUS * PLAYER_RADIUS && distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static void showInsufficientMana(ServerPlayer player, double cost) {
        long now = player.serverLevel().getGameTime();
        if (now - player.getPersistentData().getLong(LAST_SPAWNER_WARNING) < 40) return;
        player.getPersistentData().putLong(LAST_SPAWNER_WARNING, now);
        player.displayClientMessage(Component.literal("Apothic spawner paused: " + format(cost)
                + " mana is required, but you have " + Mana.display(player) + ".")
                .withStyle(ChatFormatting.RED), true);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }
}
