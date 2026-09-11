package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.item.CraftedSpellItem;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maintains casts whose carrier is the held six-slot spellbook rather than a loose spell item. */
public final class BoundSpellCasting {
    private static final Map<UUID, ActiveCast> ACTIVE = new HashMap<>();
    private static final int USE_DURATION = 72_000;

    private BoundSpellCasting() {}

    public static boolean begin(ServerPlayer player, ItemStack book, InteractionHand hand, int slot) {
        List<CraftedSpell> spells = SpellbookBindings.resolve(player, book, slot);
        if (spells.isEmpty()) {
            player.displayClientMessage(Component.literal("Spellbook slot " + (slot + 1) + " is empty.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (spells.size() > 1 && !SkillKnowledge.knows(player, MagicSkill.MULTICAST)) {
            player.displayClientMessage(Component.literal("Multicast knowledge is required for this slot.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        for (CraftedSpell spell : spells) if (!CraftedSpellItem.validate(player, spell)) return false;
        if (spells.stream().anyMatch(CraftedSpell::programmed)) {
            for (CraftedSpell spell : spells) ProgramSpellController.toggle(player, spell);
            if (spells.size() > 1) payAll(player, spells, SpellResourcePayment.Part.FORMATION, .10);
            return true;
        }
        if (!payAll(player, spells, SpellResourcePayment.Part.FORMATION, multiplier(spells))) {
            player.displayClientMessage(Component.literal("Not enough mana or qi to form this spellbook slot.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ACTIVE.put(player.getUUID(), new ActiveCast(spells, player.tickCount));
        player.startUsingItem(hand);
        return true;
    }

    public static void tick(ServerPlayer player) {
        ActiveCast active = ACTIVE.get(player.getUUID());
        if (active == null) return;
        int usedTicks = player.tickCount - active.startedAt();
        if (usedTicks % 20 == 0 && !payAll(player, active.spells(),
                SpellResourcePayment.Part.MAINTENANCE, multiplier(active.spells()))) {
            player.displayClientMessage(Component.literal("Not enough mana or qi to maintain bound spells.")
                    .withStyle(ChatFormatting.RED), true);
            player.stopUsingItem();
            ACTIVE.remove(player.getUUID());
            return;
        }
        for (CraftedSpell spell : active.spells()) {
            if (spell.delivery() != DeliveryType.CONTINUOUS) continue;
            boolean lift = CraftedSpellItem.isHeldLift(spell);
            if (usedTicks < (lift ? 1 : 10) || !lift && usedTicks % 8 != 0) continue;
            double pulse = spell.isStudyOnly() ? 0 : SpellCostCalculator
                    .calculate(spell.definition(), CasterMastery.stats(player)).release() * .22 * multiplier(active.spells());
            if (usedTicks % 8 != 0 || SpellResourcePayment.pay(player, spell,
                    new SpellCost(0, 0, Math.max(.25, pulse), 0), SpellResourcePayment.Part.RELEASE)) {
                SpellExecutor.cast(player, spell, CraftedSpellItem.chargeMultiplier(player, usedTicks) * .65);
                if (usedTicks % 40 == 0) CraftedSpellItem.completedCast(player, spell,
                        SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player)));
            }
        }
    }

    public static void release(ServerPlayer player) {
        ActiveCast active = ACTIVE.remove(player.getUUID());
        if (active == null) return;
        int usedTicks = player.tickCount - active.startedAt();
        if (!payAll(player, active.spells(), SpellResourcePayment.Part.RELEASE, multiplier(active.spells()))) {
            player.displayClientMessage(Component.literal("Not enough mana or qi to release bound spells.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        for (CraftedSpell spell : active.spells()) {
            if (spell.hasSustainedMagic()) SustainedMagicController.deactivate(player);
            if (spell.hasTimeMagic()) {
                com.strutton.dynamicmagic.time.TimeMagicController.deactivate(player);
                continue;
            }
            if (spell.delivery() == DeliveryType.CONTINUOUS) continue;
            SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player));
            double charge = CraftedSpellItem.chargeMultiplier(player, usedTicks);
            if (!CraftedSpellItem.miscast(player, spell, cost)) SpellExecutor.cast(player, spell, charge);
            if (charge > 1.5) player.hurt(player.damageSources().magic(), (float) ((charge - 1.5) * 8));
            CraftedSpellItem.completedCast(player, spell, cost);
        }
        player.displayClientMessage(Component.literal("Bound spell released. Mana: " + Mana.display(player)), true);
    }

    public static void cancel(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static int useDuration() { return USE_DURATION; }

    private static double multiplier(List<CraftedSpell> spells) { return spells.size() > 1 ? 1.10 : 1; }
    private static boolean payAll(ServerPlayer player, List<CraftedSpell> spells,
                                  SpellResourcePayment.Part part, double multiplier) {
        for (CraftedSpell spell : spells) {
            SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player));
            if (!SpellResourcePayment.pay(player, spell, cost, part, multiplier)) return false;
        }
        return true;
    }
    private record ActiveCast(List<CraftedSpell> spells, int startedAt) {}
}
