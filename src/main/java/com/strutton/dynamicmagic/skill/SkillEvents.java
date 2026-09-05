package com.strutton.dynamicmagic.skill;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class SkillEvents {
    private SkillEvents() {}
    @SubscribeEvent public static void experience(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player
                && SkillKnowledge.knows(player, MagicSkill.EXPERIENCE_HARVEST))
            event.setDroppedExperience((int) Math.ceil(event.getDroppedExperience() * 1.35));
    }

    @SubscribeEvent public static void permanentSkills(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0) return;
        if (SkillKnowledge.knows(player, MagicSkill.FIRE_RESISTANCE))
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 220, 0, false, false, true));
        if (SkillKnowledge.knows(player, MagicSkill.WATER_BREATHING))
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 220, 0, false, false, true));
    }
}
