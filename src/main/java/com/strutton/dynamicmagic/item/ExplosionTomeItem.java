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

public final class ExplosionTomeItem extends Item {
    private static final int BASE_COST = 24;
    public ExplosionTomeItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.sidedSuccess(stack, true);
        if (ComponentKnowledge.knowsImpact(server, ImpactType.EXPLODE)) {
            server.displayClientMessage(Component.literal("You already understand the Explosion effect.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        int cost = SkillKnowledge.knows(server, MagicSkill.EFFICIENT_STUDY) ? 18 : BASE_COST;
        if (!server.getAbilities().instabuild && server.experienceLevel < cost) {
            server.displayClientMessage(Component.literal("Comprehending detonation requires " + cost
                    + " experience levels.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        ComponentKnowledge.learnImpact(server, ImpactType.EXPLODE);
        if (!server.getAbilities().instabuild) {
            server.giveExperienceLevels(-cost);
            stack.shrink(1);
        }
        server.displayClientMessage(Component.literal("Technique learned: Explosion")
                .withStyle(ChatFormatting.GOLD), false);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Teaches the Explosion spell effect.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires 24 experience levels").withStyle(ChatFormatting.AQUA));
    }
}
