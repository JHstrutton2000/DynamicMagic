package com.strutton.dynamicmagic.item;

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

public final class SkillTomeItem extends Item {
    private final MagicSkill skill;
    public SkillTomeItem(MagicSkill skill, Properties properties) { super(properties); this.skill = skill; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.sidedSuccess(stack, true);
        if (SkillKnowledge.knows(server, skill)) {
            server.displayClientMessage(Component.literal("You already know " + skill.displayName() + '.').withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        int cost = effectiveCost(server, skill);
        if (!server.getAbilities().instabuild && server.experienceLevel < cost) {
            server.displayClientMessage(Component.literal("Comprehending this tome requires " + cost + " experience levels.")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (SkillKnowledge.learn(server, skill)) {
            if (!server.getAbilities().instabuild) server.giveExperienceLevels(-cost);
            server.displayClientMessage(Component.literal("Skill learned: " + skill.displayName()).withStyle(ChatFormatting.GOLD), false);
            if (!server.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        return InteractionResultHolder.pass(stack);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(skill.description()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + skill.experienceLevelCost() + " experience levels")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Rare hostile drop or village-mage trade").withStyle(ChatFormatting.DARK_PURPLE));
    }

    public static int effectiveCost(ServerPlayer player, MagicSkill skill) {
        double discount = SkillKnowledge.knows(player, MagicSkill.EFFICIENT_STUDY) ? .75 : 1.0;
        return Math.max(1, (int) Math.ceil(skill.experienceLevelCost() * discount));
    }
}
