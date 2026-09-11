package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.magic.*;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.time.TimeMagicController;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CraftedSpellItem extends Item {
    private static final int USE_DURATION = 72_000;

    public CraftedSpellItem(Properties properties) { super(properties); }

    @Override public boolean isFoil(ItemStack stack) { return CraftedSpell.read(stack) != null; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CraftedSpell spell = CraftedSpell.read(stack);
        if (spell == null) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!SpellResourcePayment.validate(serverPlayer, spell, true)) return InteractionResultHolder.fail(stack);
            if (!ComponentKnowledge.canUse(player, spell)) {
                serverPlayer.displayClientMessage(Component.literal("This spell contains knowledge you have not learned.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (spell.hasTimeMagic() && (spell.branches().size() > 1 || spell.allInstructions().size() > 1)) {
                serverPlayer.displayClientMessage(Component.literal("Global time magic must be the spell's only operation.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (spell.hasSustainedMagic() && spell.delivery() != DeliveryType.CONTINUOUS) {
                serverPlayer.displayClientMessage(Component.literal("Weather control and spirit projection require Continuous delivery.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            for (SpellInstruction instruction : spell.allInstructions()) {
                double cap = ElementMastery.maxForce(serverPlayer, instruction.element());
                if (instruction.power() > cap + .001) {
                    serverPlayer.displayClientMessage(Component.literal("Your " + instruction.element().displayName()
                            + " mastery only supports force " + String.format(java.util.Locale.ROOT, "%.1f", cap) + ".")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResultHolder.fail(stack);
                }
            }
            if (spell.programmed()) {
                ProgramSpellController.toggle(serverPlayer, spell);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(serverPlayer));
            if (!SpellResourcePayment.pay(serverPlayer, spell, cost, SpellResourcePayment.Part.FORMATION)) {
                serverPlayer.displayClientMessage(Component.literal("Not enough mana or qi to form " + spell.name())
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        if (spell.programmed()) return InteractionResultHolder.sidedSuccess(stack, true);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_DURATION; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof ServerPlayer player)) return;
        CraftedSpell spell = CraftedSpell.read(stack);
        if (spell == null) { player.stopUsingItem(); return; }
        int usedTicks = USE_DURATION - remainingTicks;
        SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player));
        if (usedTicks % 20 == 0 && !SpellResourcePayment.pay(player, spell, cost,
                SpellResourcePayment.Part.MAINTENANCE)) {
            player.displayClientMessage(Component.literal("Not enough mana or qi to maintain " + spell.name())
                    .withStyle(ChatFormatting.RED), true);
            TimeMagicController.deactivate(player);
            SustainedMagicController.deactivate(player);
            player.stopUsingItem();
            return;
        }
        if (spell.hasTimeMagic()) {
            SpellInstruction time = spell.timeInstruction();
            TimeMagicController.activate(player, time.impact(), time.power() * chargeMultiplier(player, usedTicks)
                    * SpellResourcePayment.powerMultiplier(player, spell));
            return;
        }
        boolean heldLift = isHeldLift(spell);
        if (spell.delivery() == DeliveryType.CONTINUOUS && usedTicks >= (heldLift ? 1 : 10)
                && (heldLift || usedTicks % 8 == 0)) {
            double pulseCost = spell.isStudyOnly() ? 0 : Math.max(0.25, cost.release() * 0.22);
            if (usedTicks % 8 != 0 || SpellResourcePayment.pay(player, spell,
                    new SpellCost(0, 0, pulseCost, cost.instability()), SpellResourcePayment.Part.RELEASE)) {
                SpellExecutor.cast(player, spell, chargeMultiplier(player, usedTicks) * .65);
                if (usedTicks % 40 == 0) completedCast(player, spell, cost);
            }
            else player.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player)) return;
        CraftedSpell spell = CraftedSpell.read(stack);
        if (spell == null) return;
        if (spell.hasSustainedMagic()) SustainedMagicController.deactivate(player);
        if (spell.hasTimeMagic()) {
            TimeMagicController.deactivate(player);
            player.displayClientMessage(Component.literal(spell.name() + " released. Mana: " + Mana.display(player)), true);
            return;
        }
        if (spell.delivery() == DeliveryType.CONTINUOUS) {
            player.displayClientMessage(Component.literal(spell.name() + " released. Mana: " + Mana.display(player)), true);
            return;
        }
        int usedTicks = USE_DURATION - timeLeft;
        SpellCost cost = SpellCostCalculator.calculate(spell.definition(), CasterMastery.stats(player));
        if (!SpellResourcePayment.pay(player, spell, cost, SpellResourcePayment.Part.RELEASE)) {
            player.displayClientMessage(Component.literal("Not enough mana or qi (mana " + (int) Mana.get(player) + ")")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        double charge = chargeMultiplier(player, usedTicks);
        if (!miscast(player, spell, cost)) SpellExecutor.cast(player, spell, charge);
        if (charge > 1.5) player.hurt(player.damageSources().magic(), (float) ((charge - 1.5) * 8));
        completedCast(player, spell, cost);
        player.displayClientMessage(Component.literal(spell.name() + "  Mana: " + Mana.display(player)), true);
    }

    public static double chargeMultiplier(int usedTicks) {
        return Math.max(.6, Math.min(1.5, .6 + usedTicks / 40.0));
    }

    public static double chargeMultiplier(ServerPlayer player, int usedTicks) {
        double speed = com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.QUICK_CASTING) ? 1.25 : 1;
        double maximum = com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.OVERCHANNEL) ? 1.8 : 1.5;
        return Math.max(.6, Math.min(maximum, .6 + usedTicks * speed / 40.0));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CraftedSpell spell = CraftedSpell.read(stack);
        if (spell == null) {
            tooltip.add(Component.literal("Invalid spell").withStyle(ChatFormatting.RED));
            return;
        }
        SpellCost cost = SpellCostCalculator.calculate(spell.definition(), new CasterStats(3, 1));
        tooltip.add(Component.literal(spell.source().displayName() + " • " + spell.form().displayName()
                + " • " + spell.delivery().displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal((spell.programmed() ? spell.branches().size() + " independent branch"
                + (spell.branches().size() == 1 ? "" : "es") + ", " : "")
                + spell.allInstructions().size() + " ordered operation"
                + (spell.allInstructions().size() == 1 ? "" : "s")).withStyle(ChatFormatting.GOLD));
        if (spell.programmed()) {
            int branchIndex = 1;
            for (SpellBranch branch : spell.branches())
                tooltip.add(Component.literal("IF " + branch.condition().displayName() + " → "
                        + branch.instructions().size() + " action(s), " + branch.targetMode().displayName()
                        + ", every " + format(branch.intervalTicks() / 20.0) + "s")
                        .withStyle(branchIndex++ == 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
        } else {
            int index = 1;
            for (SpellInstruction instruction : spell.instructions())
                tooltip.add(Component.literal(index++ + ". " + instruction.element().displayName() + " "
                        + instruction.impact().displayName() + " F" + format(instruction.power())
                        + " R" + format(instruction.range()) + " A" + format(instruction.radius())
                        + (instruction.repetitions() > 1 ? " ×" + instruction.repetitions() : ""))
                        .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("Form " + format(cost.formation()) + "  Release " + format(cost.release())
                + "  Upkeep " + format(cost.maintenancePerSecond()) + "/s").withStyle(ChatFormatting.AQUA));
        double kiShare = SpellResourcePayment.kiShare(spell);
        if (kiShare > 0) tooltip.add(Component.literal("Ki contribution " + format(kiShare * 100)
                + "% • requires Ki-Mana Fusion • 1 qi replaces 2 mana")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (cost.instability() > 0) tooltip.add(Component.literal("Instability " + format(cost.instability() * 100) + "%")
                .withStyle(ChatFormatting.RED));
    }

    public static boolean miscast(ServerPlayer player, CraftedSpell spell, SpellCost cost) {
        double instability = SpellResourcePayment.adjustedInstability(player, cost.instability());
        if (instability <= 0 || player.getRandom().nextDouble() >= instability) return false;
        double roll = player.getRandom().nextDouble();
        if (roll < .34) {
            player.displayClientMessage(Component.literal(spell.name() + " dissipated during formation.").withStyle(ChatFormatting.RED), true);
        } else if (roll < .67) {
            player.hurt(player.damageSources().magic(), (float)Math.max(1, spell.power()));
            player.displayClientMessage(Component.literal(spell.name() + " backfired!").withStyle(ChatFormatting.RED), true);
        } else {
            List<SpellInstruction> collapsed = spell.instructions().stream().map(instruction ->
                    instruction.withPower(instruction.power() * .65).withTargetMode(TargetMode.SELF)
                            .withDirection(CastDirection.LOOK)).toList();
            SpellInstruction first = collapsed.get(0);
            SpellExecutor.cast(player, new CraftedSpell(spell.name(), spell.source(), first.element(), spell.form(),
                    DeliveryType.SELF, first.impact(), first.power(), first.direction(), false,
                    ConditionType.ALWAYS, 10, 4, first.targetMode(), ProgramTargetMode.CASTER_AIM,
                    first.physicsOperation(), collapsed), .75);
            player.displayClientMessage(Component.literal(spell.name() + " collapsed onto its caster!").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    public static void completedCast(ServerPlayer player, CraftedSpell spell, SpellCost cost) {
        if (spell.isStudyOnly()) return;
        ComponentKnowledge.recordSuccessfulCast(player, spell);
        ProjectileAccuracy.practice(player, spell);
        CasterMastery.practice(player, spell.definition(), cost.instability());
        if (com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                com.strutton.dynamicmagic.skill.MagicSkill.ARCANE_RECOVERY) && !Mana.isUnlimited(player))
            Mana.set(player, Mana.get(player) + cost.release() * .05);
        double complexity = spell.definition().effects().size() * .2 / spell.allInstructions().size();
        for (SpellInstruction instruction : spell.allInstructions())
            if (instruction.impact() != ImpactType.STUDY)
                ElementMastery.practice(player, instruction.element(), complexity
                        + instruction.power() * instruction.repetitions() * .1);
    }

    public static boolean isHeldLift(CraftedSpell spell) {
        if (spell.programmed() || spell.delivery() != DeliveryType.CONTINUOUS || spell.instructions().size() != 1)
            return false;
        SpellInstruction instruction = spell.instructions().get(0);
        return instruction.element() == Element.AIR && instruction.impact() == ImpactType.PHYSICS
                && instruction.targetMode() == TargetMode.SELF && instruction.direction() == CastDirection.UP
                && (instruction.physicsOperation() == PhysicsOperation.ADD_VELOCITY
                || instruction.physicsOperation() == PhysicsOperation.SET_VELOCITY);
    }

    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }

    /** Shared validation for loose spell items and spells cast from a bound grimoire slot. */
    public static boolean validate(ServerPlayer player, CraftedSpell spell) {
        if (!SpellResourcePayment.validate(player, spell, true)) return false;
        if (!ComponentKnowledge.canUse(player, spell)) {
            player.displayClientMessage(Component.literal("This spell contains knowledge you have not learned.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (spell.hasTimeMagic() && (spell.branches().size() > 1 || spell.allInstructions().size() > 1)) {
            player.displayClientMessage(Component.literal("Global time magic must be the spell's only operation.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (spell.hasSustainedMagic() && spell.delivery() != DeliveryType.CONTINUOUS) {
            player.displayClientMessage(Component.literal("Weather control and spirit projection require Continuous delivery.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        for (SpellInstruction instruction : spell.allInstructions()) {
            double cap = ElementMastery.maxForce(player, instruction.element());
            if (instruction.power() > cap + .001) {
                player.displayClientMessage(Component.literal("Your " + instruction.element().displayName()
                        + " mastery only supports force " + String.format(java.util.Locale.ROOT, "%.1f", cap) + ".")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        return true;
    }
}
