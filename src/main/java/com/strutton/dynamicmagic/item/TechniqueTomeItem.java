package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.magic.ImpactType;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** An XP-gated book which teaches one spell-crafting effect without teaching an unrelated element. */
public final class TechniqueTomeItem extends Item {
    private final ImpactType impact;
    private final int experienceCost;

    public TechniqueTomeItem(ImpactType impact, int experienceCost, Properties properties) {
        super(properties);
        this.impact = impact;
        this.experienceCost = experienceCost;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.sidedSuccess(stack, true);
        if (ComponentKnowledge.knowsImpact(server, impact)) {
            server.displayClientMessage(Component.literal("You already understand the " + impact.displayName() + " effect.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        int cost = SkillKnowledge.knows(server, MagicSkill.EFFICIENT_STUDY)
                ? Math.max(1, (int) Math.ceil(experienceCost * .75)) : experienceCost;
        if (!server.getAbilities().instabuild && server.experienceLevel < cost) {
            server.displayClientMessage(Component.literal("Comprehending " + impact.displayName() + " requires "
                    + cost + " experience levels.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        ComponentKnowledge.learnImpact(server, impact);
        if (!server.getAbilities().instabuild) {
            server.giveExperienceLevels(-cost);
            stack.shrink(1);
        }
        server.displayClientMessage(Component.literal("Technique learned: " + impact.displayName())
                .withStyle(ChatFormatting.GOLD), false);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Teaches the " + impact.displayName() + " spell effect.")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + experienceCost + " experience levels")
                .withStyle(ChatFormatting.AQUA));
    }
}
