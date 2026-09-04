package com.strutton.dynamicmagic.werewolf;

import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WerewolfEvents {
    // The addon declares two weight-80 werewolves plus a weight-5 lycanthrope. 1/16 reduces that near weight 10.
    private static final double NATURAL_SPAWN_KEEP_CHANCE = 1.0 / 16.0;
    private WerewolfEvents() {}

    @SubscribeEvent public static void onNaturalSpawn(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !Werewolves.modLoaded()
                || !Werewolves.isWerewolf(event.getEntity())) return;
        if (event.getEntity().getRandom().nextDouble() >= NATURAL_SPAWN_KEEP_CHANCE)
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !SkillKnowledge.knows(player, MagicSkill.WEREWOLF_IMMUNITY)) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof net.minecraft.world.entity.LivingEntity living && Werewolves.isWerewolf(living))
            event.setAmount(0);
    }

    @SubscribeEvent public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % 10 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (Werewolves.enforceImmunity(player))
                player.displayClientMessage(Component.literal("Your Werewolf Immunity purges the curse."), false);
        }
    }
}
