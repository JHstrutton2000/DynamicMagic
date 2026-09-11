package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.compat.EternalCultivationBridge;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Splits a cast between mana and Eternal Cultivation qi without merging either resource pool. */
public final class SpellResourcePayment {
    private static final double MANA_PER_QI = 2.0;
    /** Rain supplies most of the raw water; the remaining cost represents shaping and control. */
    static final double RAIN_WATER_DISCOUNT = .75;

    private SpellResourcePayment() {}

    public enum Part { FORMATION, MAINTENANCE, RELEASE }

    public static boolean validate(ServerPlayer player, CraftedSpell spell, boolean notify) {
        boolean tracks = spell.allInstructions().stream().anyMatch(i -> i.targetMode() == TargetMode.TRACKED);
        if (tracks && !SkillKnowledge.knows(player, MagicSkill.TRACKING))
            return reject(player, notify, "Arcane Tracking is required to apply tracking.");
        boolean usesKi = usesKi(spell);
        if (usesKi && !SkillKnowledge.knows(player, MagicSkill.KI_MANA_FUSION))
            return reject(player, notify, "Ki-Mana Fusion is required to channel qi through a spell.");
        if (usesKi && !EternalCultivationBridge.installed())
            return reject(player, notify, "This Ki spell requires Eternal Cultivation to provide qi.");
        return true;
    }

    public static boolean pay(ServerPlayer player, CraftedSpell spell, SpellCost cost, Part part) {
        return pay(player, spell, cost, part, 1.0);
    }

    public static boolean pay(ServerPlayer player, CraftedSpell spell, SpellCost cost, Part part, double multiplier) {
        if (!validate(player, spell, true)) return false;
        double total = switch (part) {
            case FORMATION -> cost.formation();
            case MAINTENANCE -> cost.maintenancePerSecond();
            case RELEASE -> cost.release();
        } * Math.max(0, multiplier);
        if (SkillKnowledge.knows(player, MagicSkill.MANA_EFFICIENCY)) total *= .90;
        if (part == Part.MAINTENANCE && SkillKnowledge.knows(player, MagicSkill.CONCENTRATION)) total *= .75;
        total *= com.strutton.dynamicmagic.compat.SoloLevelingIntegration.castingCostMultiplier(player);
        double kiShare = kiShare(spell);
        boolean gatheringRain = player.level().isRainingAt(player.blockPosition());
        double manaCost = total * manaShare(spell, gatheringRain);
        double qiCost = total * kiShare / MANA_PER_QI;
        if ((!Mana.isUnlimited(player) && Mana.get(player) + 1.0e-6 < manaCost)
                || EternalCultivationBridge.current(player) + 1.0e-6 < Math.ceil(qiCost)) return false;
        boolean paid = Mana.consume(player, manaCost) && EternalCultivationBridge.consume(player, qiCost);
        if (paid) com.strutton.dynamicmagic.compat.SoloLevelingIntegration.onDynamicSpellCost(player, total);
        return paid;
    }

    public static double powerMultiplier(ServerPlayer player, CraftedSpell spell) {
        double result = 1 + kiShare(spell) * .75;
        result *= com.strutton.dynamicmagic.compat.SoloLevelingIntegration.spellPowerMultiplier(player, spell);
        if (SkillKnowledge.knows(player, MagicSkill.SPELL_AMPLIFICATION)) result *= 1.15;
        if (spell.delivery() == DeliveryType.PROJECTILE
                && spell.allInstructions().stream().anyMatch(instruction -> instruction.impact() == ImpactType.DAMAGE)
                && SkillKnowledge.knows(player, MagicSkill.SPELL_CRITICAL)
                && player.getRandom().nextDouble() < .10) {
            result *= 1.5;
            player.displayClientMessage(Component.literal("Spell critical!").withStyle(ChatFormatting.GOLD), true);
        }
        return result;
    }

    public static CraftedSpell applyRangeSkills(ServerPlayer player, CraftedSpell spell) {
        if (spell.delivery() != DeliveryType.PROJECTILE
                || !SkillKnowledge.knows(player, MagicSkill.PROJECTILE_MASTERY)) return spell;
        return spell.mapInstructions(instruction -> instruction.withRange(instruction.range() * 1.2));
    }

    public static double adjustedInstability(ServerPlayer player, double instability) {
        double adjusted = com.strutton.dynamicmagic.compat.SoloLevelingIntegration.adjustedInstability(player, instability);
        return SkillKnowledge.knows(player, MagicSkill.STABLE_CHANNELING) ? adjusted * .70 : adjusted;
    }

    public static double kiShare(CraftedSpell spell) {
        return elementShare(spell, Element.KI);
    }

    static double manaShare(CraftedSpell spell, boolean gatheringRain) {
        double share = 1 - kiShare(spell);
        if (gatheringRain) share -= elementShare(spell, Element.WATER) * RAIN_WATER_DISCOUNT;
        return Math.max(0, share);
    }

    static double elementShare(CraftedSpell spell, Element element) {
        double total = 0;
        double selected = 0;
        for (SpellInstruction instruction : spell.allInstructions()) {
            double weight = instruction.power() * instruction.repetitions();
            total += weight;
            if (instruction.element() == element) selected += weight;
        }
        return total <= 1.0e-9 ? 0 : Math.min(1, selected / total);
    }

    public static boolean usesKi(CraftedSpell spell) { return kiShare(spell) > 0; }

    private static boolean reject(ServerPlayer player, boolean notify, String message) {
        if (notify) player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
        return false;
    }
}
