package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.knowledge.ElementAffinity;
import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ElementGrimoireItem extends Item {
    private final Element element;
    public ElementGrimoireItem(Element element, Properties properties) { super(properties); this.element = element; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            boolean firstLearning = !ElementKnowledge.knows(player, element);
            int baseCost = firstLearning ? learningCost(element) : 4 + ElementAffinity.get(player, element) * 2;
            int cost = com.strutton.dynamicmagic.skill.SkillKnowledge.knows(serverPlayer,
                    com.strutton.dynamicmagic.skill.MagicSkill.EFFICIENT_STUDY)
                    ? Math.max(1, (int) Math.ceil(baseCost * .75)) : baseCost;
            if (!player.getAbilities().instabuild && serverPlayer.experienceLevel < cost) {
                serverPlayer.displayClientMessage(Component.literal("Studying this grimoire requires " + cost
                        + " experience levels.").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            boolean consumed;
            if (ElementKnowledge.learn(player, element)) {
                serverPlayer.displayClientMessage(Component.literal("You learned the " + element.displayName() + " element.")
                        .withStyle(ChatFormatting.AQUA), false);
                consumed = true;
            } else if (ElementAffinity.increase(player, element)) {
                consumed = true;
                serverPlayer.displayClientMessage(Component.literal(element.displayName() + " affinity increased to "
                        + ElementAffinity.get(player, element) + '/' + ElementAffinity.MAX + '.')
                        .withStyle(ChatFormatting.GOLD), false);
            } else {
                consumed = false;
                serverPlayer.displayClientMessage(Component.literal("Your " + element.displayName()
                        + " affinity is already complete.").withStyle(ChatFormatting.GRAY), true);
            }
            if (consumed && !player.getAbilities().instabuild) {
                serverPlayer.giveExperienceLevels(-cost);
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static int learningCost(Element element) {
        return switch (element) {
            case FIRE, WATER, EARTH, AIR, LIGHTNING -> 6;
            case ICE, LIGHT, SHADOW, SAND, METAL, GLASS -> 12;
            default -> 18;
        };
    }
}
