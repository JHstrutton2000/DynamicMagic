package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.time.TimeMagicController;
import com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runs toggled spell programs independently of the player's hands or selected hotbar slot. */
public final class ProgramSpellController {
    private static final Map<MinecraftServer, Map<UUID, Map<String, ActiveProgram>>> ACTIVE = new WeakHashMap<>();
    private ProgramSpellController() {}

    public static void toggle(ServerPlayer player, CraftedSpell spell) {
        String key = spell.toTag().toString();
        Map<String, ActiveProgram> programs = ACTIVE.computeIfAbsent(player.server, ignored -> new HashMap<>())
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (programs.remove(key) != null) {
            if (spell.hasTimeMagic()) TimeMagicController.deactivate(player);
            if (spell.hasSustainedMagic()) SustainedMagicController.deactivate(player);
            player.displayClientMessage(Component.literal(spell.name() + " program released.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player));
        if (!Mana.consume(player, cost.formation())) {
            player.displayClientMessage(Component.literal("Not enough mana to activate " + spell.name()).withStyle(ChatFormatting.RED), true);
            return;
        }
        programs.put(key, new ActiveProgram(spell, cost));
        player.displayClientMessage(Component.literal(spell.name() + " program active. Click it again to release.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    public static boolean hasActiveTime(ServerPlayer player) {
        Map<UUID, Map<String, ActiveProgram>> players = ACTIVE.get(player.server);
        Map<String, ActiveProgram> programs = players == null ? null : players.get(player.getUUID());
        return programs != null && programs.values().stream().anyMatch(active -> active.spell().hasTimeMagic());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Map<UUID, Map<String, ActiveProgram>> players = ACTIVE.get(event.getServer());
        if (players == null) return;
        Iterator<Map.Entry<UUID, Map<String, ActiveProgram>>> playerIterator = players.entrySet().iterator();
        while (playerIterator.hasNext()) {
            var playerEntry = playerIterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) { playerIterator.remove(); continue; }
            Iterator<ActiveProgram> programs = playerEntry.getValue().values().iterator();
            while (programs.hasNext()) {
                ActiveProgram active = programs.next();
                CraftedSpell spell = active.spell();
                if (player.tickCount % 20 == 0 && !Mana.consume(player, active.cost().maintenancePerSecond())) {
                    stop(player, spell); programs.remove(); continue;
                }
                boolean ended = false;
                var matches = SpellProgramRunner.matchingBranches(player, spell, player.tickCount);
                if (player.tickCount % 20 == 0 && !matches.isEmpty())
                    ProgrammingKnowledge.practice(player, .2 + matches.size() * .05);
                if (spell.hasTimeMagic() && matches.isEmpty()) TimeMagicController.deactivate(player);
                if (spell.hasSustainedMagic() && matches.isEmpty()) SustainedMagicController.deactivate(player);
                for (SpellProgramRunner.BranchMatch match : matches) {
                    SpellBranch branch = match.branch();
                    LivingEntity detected = match.detected();
                    CraftedSpell branchSpell = spell.branchSpell(branch);
                    SpellCost branchCost = SpellCostCalculator.calculate(branchSpell.definition(), CasterMastery.stats(player));
                    if (!Mana.consume(player, branchCost.release())) {
                        stop(player, spell);
                        programs.remove();
                        ended = true;
                        break;
                    }
                    SpellInstruction time = branchSpell.timeInstruction();
                    if (time != null) TimeMagicController.activate(player, time.impact(), time.power());
                    else SpellExecutor.castProgram(player, spell, branch, 1.0, detected);
                    ComponentKnowledge.recordSuccessfulCast(player, branchSpell);
                    ProjectileAccuracy.practice(player, branchSpell);
                    CasterMastery.practice(player, branchSpell.definition(), branchCost.instability());
                    double complexity = branchSpell.definition().effects().size() * .2 / branch.instructions().size();
                    for (SpellInstruction instruction : branch.instructions())
                        ElementMastery.practice(player, instruction.element(), complexity
                                + instruction.power() * instruction.repetitions() * .1);
                }
                if (ended) continue;
            }
            if (playerEntry.getValue().isEmpty()) playerIterator.remove();
        }
        if (players.isEmpty()) ACTIVE.remove(event.getServer());
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Map<UUID, Map<String, ActiveProgram>> players = ACTIVE.get(player.server);
        if (players != null) players.remove(player.getUUID());
        TimeMagicController.deactivate(player);
        SustainedMagicController.deactivate(player);
    }

    private static void stop(ServerPlayer player, CraftedSpell spell) {
        if (spell.hasTimeMagic()) TimeMagicController.deactivate(player);
        if (spell.hasSustainedMagic()) SustainedMagicController.deactivate(player);
        player.displayClientMessage(Component.literal(spell.name() + " program ended: mana depleted.").withStyle(ChatFormatting.RED), true);
    }

    private record ActiveProgram(CraftedSpell spell, SpellCost cost) {}
}
