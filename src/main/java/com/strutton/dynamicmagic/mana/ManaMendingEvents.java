package com.strutton.dynamicmagic.mana;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Converts mana into durability for items carrying the Mana Mending enchantment. */
public final class ManaMendingEvents {
    public static final ResourceKey<Enchantment> MANA_MENDING = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana_mending"));
    public static final double MANA_PER_DURABILITY = 1.0;
    private static final String NEXT_SLOT = "DynamicMagicManaMendingNextSlot";

    private ManaMendingEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) repairOne(player);
    }

    /** Repairs at most one point per pulse, rotating slots so several enchanted items share the mana fairly. */
    public static boolean repairOne(ServerPlayer player) {
        if (!Mana.isUnlimited(player) && Mana.get(player) + 1.0e-6 < MANA_PER_DURABILITY) return false;
        Holder<Enchantment> enchantment = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(MANA_MENDING);
        int size = player.getInventory().getContainerSize();
        int start = Math.floorMod(player.getPersistentData().getInt(NEXT_SLOT), size);
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isDamaged() || stack.getEnchantments().getLevel(enchantment) == 0) continue;
            if (!Mana.consume(player, MANA_PER_DURABILITY)) return false;
            stack.setDamageValue(stack.getDamageValue() - 1);
            player.getPersistentData().putInt(NEXT_SLOT, (slot + 1) % size);
            return true;
        }
        return false;
    }
}
