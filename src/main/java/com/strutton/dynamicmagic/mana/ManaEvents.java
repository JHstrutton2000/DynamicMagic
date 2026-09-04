package com.strutton.dynamicmagic.mana;

import com.strutton.dynamicmagic.network.ManaSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.Mob;

public final class ManaEvents {
    private ManaEvents() {}
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 == 0 && !player.isUsingItem() && !Mana.isUnlimited(player)
                && !com.strutton.dynamicmagic.morph.MorphV2Integration.isMorphed(player)) {
            double hungerFactor = player.getFoodData().getFoodLevel() / 20.0;
            double skillFactor = com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                    com.strutton.dynamicmagic.skill.MagicSkill.MANA_WELL) ? 1.75 : 1.0;
            skillFactor += elementalAttunementBonus(player);
            // Runs twice per second: 0.5% of maximum per pulse = 1%/second at full hunger.
            Mana.set(player, Mana.get(player) + Mana.max(player) * .005 * hungerFactor * skillFactor);
        }
        if (com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.HUNGER_WARD)) {
            var data = player.getPersistentData();
            int food = player.getFoodData().getFoodLevel();
            float saturation = player.getFoodData().getSaturationLevel();
            if (!data.contains("DynamicMagicWardFood")) {
                data.putInt("DynamicMagicWardFood", food);
                data.putFloat("DynamicMagicWardSaturation", saturation);
            } else {
                int wardFood = data.getInt("DynamicMagicWardFood");
                float wardSaturation = data.getFloat("DynamicMagicWardSaturation");
                boolean hungerDecreased = food < wardFood || saturation < wardSaturation;
                boolean acceptExpansionCost = hungerDecreased && Mana.consumeExpansionHungerDebt(player);
                int preservedFood = acceptExpansionCost ? food : Math.max(food, wardFood);
                float preservedSaturation = acceptExpansionCost ? saturation : Math.max(saturation, wardSaturation);
                player.getFoodData().setFoodLevel(preservedFood);
                player.getFoodData().setSaturation(preservedSaturation);
                data.putInt("DynamicMagicWardFood", preservedFood);
                data.putFloat("DynamicMagicWardSaturation", preservedSaturation);
            }
        }
        if (player.tickCount % 60 == 0 && player.getHealth() < player.getMaxHealth()
                && com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.DIVINE_VITALITY)) player.heal(1.0f);
        if (player.tickCount % 20 == 0) {
            com.strutton.dynamicmagic.magic.SeenItemMemory.observe(player, player.getMainHandItem());
            com.strutton.dynamicmagic.magic.SeenItemMemory.observe(player, player.getOffhandItem());
            com.strutton.dynamicmagic.magic.SeenItemMemory.expire(player);
        }
        if (player.tickCount % 5 == 0)
            PacketDistributor.sendToPlayer(player, new ManaSyncPayload(Mana.get(player), Mana.max(player), Mana.isUnlimited(player)));
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                mob -> mob.getPersistentData().getLong("DynamicMagicPacifiedUntil") > player.serverLevel().getGameTime()
                        && mob.getPersistentData().hasUUID("DynamicMagicPacifiedPlayer")
                        && mob.getPersistentData().getUUID("DynamicMagicPacifiedPlayer").equals(player.getUUID()))) {
            if (mob.getTarget() == player) mob.setTarget(null);
        }
        if (player.tickCount % 20 == 0) {
            for (net.minecraft.world.entity.Entity entity : player.serverLevel().getAllEntities()) {
                var data = entity.getPersistentData();
                if (data.hasUUID("DynamicMagicContractOwner")
                        && data.getUUID("DynamicMagicContractOwner").equals(player.getUUID())
                        && !Mana.consume(player, data.getDouble("DynamicMagicContractUpkeep"))) {
                    entity.discard();
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("A summoned contract dissolved when its mana tether broke."), true);
                }
            }
        }
    }

    /** Additive environmental regeneration: simultaneous qualifying elements intentionally stack. */
    public static double elementalAttunementBonus(ServerPlayer player) {
        if (!com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.ELEMENTAL_ATTUNEMENT)) return 0;
        var level = player.serverLevel();
        var pos = player.blockPosition();
        double bonus = 0;
        if (player.isInLava()) bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.LAVA, 1.50);
        else if (player.getRemainingFireTicks() > 0) bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.FIRE, .90);
        if (player.isInWaterOrRain()) bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.WATER, .75);

        String floor = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos.below()).getBlock()).getPath();
        if (floor.contains("stone") || floor.contains("dirt") || floor.contains("earth")
                || floor.contains("deepslate") || floor.contains("sand"))
            bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.EARTH, .45);
        if (!player.isInWaterOrRain() && !player.isInLava() && level.canSeeSky(pos.above()))
            bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.AIR, .35);

        int light = level.getMaxLocalRawBrightness(pos);
        if (!level.isDay())
            bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.SHADOW,
                    .90 * (15 - Math.min(15, light)) / 15.0);
        if (level.isDay() && level.canSeeSky(pos.above()))
            bonus += attunement(player, com.strutton.dynamicmagic.magic.Element.DIVINE,
                    .75 * Math.min(15, light) / 15.0);
        return bonus;
    }

    private static double attunement(ServerPlayer player, com.strutton.dynamicmagic.magic.Element element, double base) {
        if (!com.strutton.dynamicmagic.knowledge.ElementKnowledge.knows(player, element)) return 0;
        double mastery = com.strutton.dynamicmagic.magic.ElementMastery.experience(player, element);
        int affinity = com.strutton.dynamicmagic.knowledge.ElementAffinity.get(player, element);
        return base * (1.0 + Math.min(2.0, Math.sqrt(mastery) / 12.0) + affinity * .12);
    }
}
