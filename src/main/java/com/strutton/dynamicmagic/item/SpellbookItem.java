package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class SpellbookItem extends Item {
    public SpellbookItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (ProgrammingKnowledge.learn(player)) {
                ComponentKnowledge.learnProgrammingBasics(player);
                serverPlayer.displayClientMessage(Component.literal(
                        "You learned spell programming. Practice building and running programs to unlock more branches.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            } else serverPlayer.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                    "Programming mastery %.1f — maximum branches: %d", ProgrammingKnowledge.mastery(player),
                    ProgrammingKnowledge.maxBranches(player))).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
