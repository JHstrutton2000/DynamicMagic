package com.strutton.dynamicmagic.werewolf;

import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/** Reflection-only bridge to Werewolves: Werebeasts so the addon remains optional. */
public final class Werewolves {
    private Werewolves() {}

    public static boolean modLoaded() {
        try { return net.neoforged.fml.ModList.get().isLoaded("werewolves"); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean isImmune(ServerPlayer player) {
        return SkillKnowledge.knows(player, MagicSkill.WEREWOLF_IMMUNITY);
    }

    public static boolean isWerewolf(ServerPlayer player) {
        if (!modLoaded()) return false;
        try {
            Object handler = factionHandler(player);
            Object current = factionHandlerApi().getMethod("getCurrentFaction").invoke(handler);
            return current != null && current.equals(werewolfFaction());
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    public static boolean isWerewolf(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) return isWerewolf(player);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
        return id.startsWith("werewolves:") && id.contains("werewolf")
                || entity.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("werewolf");
    }

    public static boolean infect(ServerPlayer player) {
        if (isImmune(player) || isWerewolf(player) || !modLoaded()) return false;
        try {
            Class<?> playableFaction = Class.forName("de.teamlapen.vampirism.api.entity.factions.IPlayableFaction");
            return Boolean.TRUE.equals(factionHandlerApi()
                    .getMethod("setFactionAndLevel", playableFaction, int.class)
                    .invoke(factionHandler(player), werewolfFaction(), 1));
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }

    public static boolean cure(ServerPlayer player) {
        boolean wasWerewolf = isWerewolf(player);
        if (wasWerewolf) {
            try {
                factionHandlerApi().getMethod("leaveFaction", boolean.class).invoke(factionHandler(player), true);
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        return removeInfectionEffects(player) || wasWerewolf;
    }

    public static boolean enforceImmunity(ServerPlayer player) {
        return isImmune(player) && cure(player);
    }

    private static boolean removeInfectionEffects(ServerPlayer player) {
        boolean changed = false;
        for (var effect : java.util.List.copyOf(player.getActiveEffects())) {
            var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (id != null && id.getNamespace().equals("werewolves")
                    && (id.getPath().contains("lupus_sanguinem") || id.getPath().contains("infection"))) {
                player.removeEffect(effect.getEffect());
                changed = true;
            }
        }
        return changed;
    }

    private static Object factionHandler(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> api = Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
        return api.getMethod("factionPlayerHandler", net.minecraft.world.entity.player.Player.class).invoke(null, player);
    }

    private static Class<?> factionHandlerApi() throws ClassNotFoundException {
        return Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
    }

    private static Object werewolfFaction() throws ReflectiveOperationException {
        return Class.forName("de.teamlapen.werewolves.api.WReference").getField("WEREWOLF_FACTION").get(null);
    }
}
