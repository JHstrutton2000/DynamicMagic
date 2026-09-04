package com.strutton.dynamicmagic.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class ExplosionProgression {
    private static final String EXPOSURE = "DynamicMagicExplosionResistanceExposure";
    public static final double IMMUNITY_REQUIREMENT = 120.0;
    private ExplosionProgression() {}

    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
        if (SkillKnowledge.knows(player, MagicSkill.EXPLOSION_IMMUNITY)) {
            event.setAmount(0);
            return;
        }
        if (!SkillKnowledge.knows(player, MagicSkill.EXPLOSION_RESISTANCE)) return;
        recordExposure(player, event.getAmount());
        event.setAmount(event.getAmount() * .5f);
    }

    public static double exposure(ServerPlayer player) {
        return Math.min(IMMUNITY_REQUIREMENT, Math.max(0, player.getPersistentData().getDouble(EXPOSURE)));
    }

    public static boolean recordExposure(ServerPlayer player, double amount) {
        if (!SkillKnowledge.knows(player, MagicSkill.EXPLOSION_RESISTANCE)
                || SkillKnowledge.knows(player, MagicSkill.EXPLOSION_IMMUNITY) || amount <= 0) return false;
        double total = Math.min(IMMUNITY_REQUIREMENT, exposure(player) + amount);
        player.getPersistentData().putDouble(EXPOSURE, total);
        if (total < IMMUNITY_REQUIREMENT) return false;
        SkillKnowledge.forget(player, MagicSkill.EXPLOSION_RESISTANCE);
        SkillKnowledge.learn(player, MagicSkill.EXPLOSION_IMMUNITY);
        player.displayClientMessage(Component.literal("Explosion Resistance evolved into Explosion Immunity!")
                .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(EXPOSURE))
            to.getPersistentData().putDouble(EXPOSURE, from.getPersistentData().getDouble(EXPOSURE));
    }
}
