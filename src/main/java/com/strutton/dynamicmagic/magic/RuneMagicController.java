package com.strutton.dynamicmagic.magic;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

/** Persistent Skyrim-style runes: place now, trigger once when a creature enters the inscribed area. */
public final class RuneMagicController {
    private static final WeakHashMap<MinecraftServer, List<Rune>> RUNES = new WeakHashMap<>();
    private RuneMagicController() {}

    public static void place(ServerPlayer caster, CraftedSpell spell, Vec3 position) {
        int life = Math.max(200, (int) (spell.instructions().get(0).durationSeconds() * 20));
        RUNES.computeIfAbsent(caster.server, ignored -> new ArrayList<>()).add(new Rune(
                caster.level().dimension(), caster.getUUID(), spell, position,
                caster.serverLevel().getGameTime() + life));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        List<Rune> runes = RUNES.get(event.getServer());
        if (runes == null) return;
        long now = event.getServer().overworld().getGameTime();
        runes.removeIf(rune -> rune.expiry <= now || trigger(event.getServer(), rune));
        if (runes.isEmpty()) RUNES.remove(event.getServer());
    }

    private static boolean trigger(MinecraftServer server, Rune rune) {
        ServerLevel level = server.getLevel(rune.dimension);
        ServerPlayer caster = server.getPlayerList().getPlayer(rune.caster);
        if (level == null || caster == null) return true;
        double radius = Math.max(1.5, rune.spell.instructions().get(0).radius());
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(rune.position, rune.position).inflate(radius),
                entity -> entity != caster && entity.isAlive());
        if (targets.isEmpty()) return false;
        for (LivingEntity target : targets)
            for (SpellInstruction instruction : rune.spell.instructions()) SpellExecutor.applyDirect(caster, target, instruction);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                rune.position.x, rune.position.y, rune.position.z, 3, .3, .1, .3, 0);
        return true;
    }

    private record Rune(ResourceKey<Level> dimension, UUID caster, CraftedSpell spell, Vec3 position, long expiry) {}
}
