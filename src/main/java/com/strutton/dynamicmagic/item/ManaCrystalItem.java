package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ManaCrystalItem extends Item {
    private static final String CHARGE = "DynamicMagicCrystalMana";
    private final int capacity;
    public ManaCrystalItem(int capacity, Properties properties) { super(properties); this.capacity = capacity; }
    public int capacity() { return capacity; }
    public static double charge(ItemStack stack) { CustomData data = stack.get(DataComponents.CUSTOM_DATA); return data == null ? 0 : data.copyTag().getDouble(CHARGE); }
    public static double drain(ItemStack stack, double requested) { double taken = Math.min(charge(stack), requested); set(stack, charge(stack) - taken); return taken; }
    private static void set(ItemStack stack, double amount) { CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); tag.putDouble(CHARGE, Math.max(0, amount)); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) { double room = capacity - charge(stack), transfer = Math.min(10, room); if (transfer > 0 && Mana.consume(server, transfer)) set(stack, charge(stack) + transfer); }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal((int)charge(stack) + " / " + capacity + " stored mana").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Right click to charge 10 mana").withStyle(ChatFormatting.GRAY));
    }
}
