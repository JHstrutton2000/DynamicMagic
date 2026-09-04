package com.strutton.dynamicmagic.vampire;

import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

public final class Vampirism {
    private static final String KEY = "DynamicMagicVampire";
    private Vampirism() {}

    public static boolean isVampire(ServerPlayer player) {
        return isInternal(player) || isExternalPlayerVampire(player);
    }
    public static boolean isVampire(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) return isVampire(player);
        if (entity.getPersistentData().getBoolean("DynamicMagicVampireEnemy")) return true;
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
                .toLowerCase(Locale.ROOT);
        String className = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return (id.startsWith("vampirism:") && (id.contains("vampire") || id.contains("converted")))
                || className.contains("vampire");
    }
    public static boolean isInternal(ServerPlayer player) { return player.getPersistentData().getBoolean(KEY); }
    public static boolean externalModLoaded() {
        try { return net.neoforged.fml.ModList.get().isLoaded("vampirism"); }
        catch (Throwable ignored) { return false; }
    }
    public static boolean infect(ServerPlayer player) {
        if (SkillKnowledge.knows(player, MagicSkill.BLOOD_WARD)
                || SkillKnowledge.knows(player, MagicSkill.VAMPIRE_IMMUNITY) || isVampire(player)) return false;
        if (!joinExternalFaction(player)) player.getPersistentData().putBoolean(KEY, true);
        return true;
    }
    public static boolean cure(ServerPlayer player) {
        boolean was = isVampire(player);
        player.getPersistentData().remove(KEY);
        leaveExternalFaction(player);
        return was;
    }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (isInternal(from)) to.getPersistentData().putBoolean(KEY, true);
    }

    /** Removes both our vampirism and Vampirism-mod infection/faction state for immune players. */
    public static boolean enforceImmunity(ServerPlayer player) {
        boolean changed = cure(player);
        for (var effect : java.util.List.copyOf(player.getActiveEffects())) {
            var id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null && id.getNamespace().equals("vampirism")
                    && (id.getPath().contains("sanguinare") || id.getPath().contains("vampir"))) {
                player.removeEffect(effect.getEffect());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isExternalPlayerVampire(ServerPlayer player) {
        if (!externalModLoaded()) return false;
        try {
            Object handler = api().getMethod("factionPlayerHandler", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            Class<?> handlerApi = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
            Object faction = handlerApi.getMethod("getCurrentFaction").invoke(handler);
            if (faction == null) return false;
            Class<?> factionApi = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFaction");
            return "vampirism:vampire".equals(String.valueOf(factionApi.getMethod("getID").invoke(faction)));
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    private static boolean joinExternalFaction(ServerPlayer player) {
        if (!externalModLoaded()) return false;
        try {
            Class<?> reference = Class.forName("de.teamlapen.vampirism.api.VReference");
            Object faction = reference.getField("VAMPIRE_FACTION").get(null);
            Object handler = api().getMethod("factionPlayerHandler", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            Class<?> handlerApi = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
            Class<?> playableFaction = Class.forName("de.teamlapen.vampirism.api.entity.factions.IPlayableFaction");
            return Boolean.TRUE.equals(handlerApi.getMethod("setFactionAndLevel", playableFaction, int.class)
                    .invoke(handler, faction, 1));
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return false;
    }

    private static void leaveExternalFaction(ServerPlayer player) {
        if (!isExternalPlayerVampire(player)) return;
        try {
            Object handler = api().getMethod("factionPlayerHandler", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            Class<?> handlerApi = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
            handlerApi.getMethod("leaveFaction", boolean.class).invoke(handler, true);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    private static Class<?> api() throws ClassNotFoundException {
        return Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
    }
}
