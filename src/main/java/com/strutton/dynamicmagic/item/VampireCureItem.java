package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.vampire.VampireCureTreatment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class VampireCureItem extends Item {
    public VampireCureItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (VampireCureTreatment.start(serverPlayer)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(Component.literal("You drink the cure and the treatment begins."), true);
                return InteractionResultHolder.consume(stack);
            }
            player.displayClientMessage(Component.literal("You are not afflicted with vampirism."), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
