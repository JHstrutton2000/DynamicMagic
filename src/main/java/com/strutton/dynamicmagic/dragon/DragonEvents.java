package com.strutton.dynamicmagic.dragon;

import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class DragonEvents {
    private DragonEvents() {}

    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && DragonIntegration.isDragon(event.getEntity()))
            event.setAmount(event.getAmount() * DragonIntegration.dragonHunterMultiplier(attacker));
        if (event.getEntity() instanceof ServerPlayer player) {
            Element environmental = event.getSource().is(DamageTypeTags.IS_FIRE) ? Element.FIRE
                    : event.getSource().is(DamageTypeTags.IS_LIGHTNING) ? Element.LIGHTNING : null;
            if (environmental != null) {
                int affinity = DragonProgression.affinity(player, environmental);
                event.setAmount(event.getAmount() * (1f - Math.min(.5f, affinity * .08f)));
            }
        }
    }

    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        DragonProfile profile = DragonIntegration.profile(event.getEntity());
        if (profile.dragon()) DragonProgression.killed(player, profile);
    }
}
