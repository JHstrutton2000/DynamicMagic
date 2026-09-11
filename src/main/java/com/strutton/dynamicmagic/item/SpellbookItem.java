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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;

import java.util.List;

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
                com.strutton.dynamicmagic.network.SpellNetworking.openSpellbook(serverPlayer, null);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            if (player.isShiftKeyDown()) {
                com.strutton.dynamicmagic.network.SpellNetworking.openSpellbook(serverPlayer, null);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            com.strutton.dynamicmagic.magic.BoundSpellCasting.begin(serverPlayer, stack, hand, 5);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return com.strutton.dynamicmagic.magic.BoundSpellCasting.useDuration();
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (entity instanceof ServerPlayer player)
            com.strutton.dynamicmagic.magic.BoundSpellCasting.tick(player);
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player)
            com.strutton.dynamicmagic.magic.BoundSpellCasting.release(player);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        List<List<String>> slots = com.strutton.dynamicmagic.magic.SpellbookBindings.names(stack);
        for (int slot = 0; slot < slots.size(); slot++) {
            String label = slot == 5 ? "Right click" : "Spell " + (slot + 1);
            String spells = slots.get(slot).isEmpty() ? "Empty" : String.join(" + ", slots.get(slot));
            tooltip.add(Component.literal(label + ": " + spells)
                    .withStyle(slots.get(slot).isEmpty() ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal("Sneak + right click: configure").withStyle(ChatFormatting.GRAY));
    }
}
