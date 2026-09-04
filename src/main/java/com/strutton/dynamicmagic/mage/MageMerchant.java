package com.strutton.dynamicmagic.mage;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.skill.MagicSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;
import java.util.Optional;

/** Merchant backing for both villager-bodied and witch-bodied village mages. */
final class MageMerchant implements Merchant {
    private final LivingEntity mage;
    private final MerchantOffers offers = new MerchantOffers();
    private Player tradingPlayer;
    private int xp;

    MageMerchant(LivingEntity mage, long elements, long skills) {
        this.mage = mage;
        for (Element element : Element.values()) if ((elements & bit(element.ordinal())) != 0) {
            int emeralds = switch (element) {
                case FIRE, WATER, EARTH, AIR, LIGHTNING -> 18;
                case ICE, LIGHT, SHADOW, SAND, METAL, GLASS -> 30;
                default -> 44;
            };
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds),
                    new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(element).get()), 12, 8, .05f));
        }
        for (MagicSkill skill : MagicSkill.values()) if ((skills & bit(skill.ordinal())) != 0) {
            int blocks = Math.max(2, Math.min(7, (skill.experienceLevelCost() + 9) / 10));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 48),
                    Optional.of(new ItemCost(Items.EMERALD_BLOCK, blocks)),
                    new ItemStack(DynamicMagic.SKILL_TOMES.get(skill).get()), 2, 24, .1f));
        }
    }

    static void openCreeperScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 36),
                new ItemStack(DynamicMagic.EXPLOSION_TOME.get()), 6, 16, .05f));
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 56),
                Optional.of(new ItemCost(Items.EMERALD_BLOCK, 4)),
                new ItemStack(DynamicMagic.SKILL_TOMES.get(MagicSkill.EXPLOSION_RESISTANCE).get()),
                2, 24, .1f));
        merchant.open(player);
    }

    static void openEndermanScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 48),
                Optional.of(new ItemCost(Items.ENDER_PEARL, 8)),
                new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(Element.SPACE).get()), 4, 20, .08f));
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 56),
                Optional.of(new ItemCost(Items.ENDER_EYE, 4)),
                new ItemStack(DynamicMagic.TELEPORT_TOME.get()), 2, 28, .12f));
        merchant.open(player);
    }

    static void openAlexScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 32),
                Optional.of(new ItemCost(Items.GOLDEN_CARROT, 8)),
                new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(Element.SPIRIT).get()), 6, 18, .06f));
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 44),
                Optional.of(new ItemCost(Items.GOLDEN_APPLE, 2)),
                new ItemStack(DynamicMagic.FERTILITY_TOME.get()), 3, 24, .1f));
        merchant.open(player);
    }

    void open(Player player) {
        setTradingPlayer(player);
        openTradingScreen(player, mage.getDisplayName(), 1);
    }

    private static long bit(int ordinal) { return 1L << ordinal; }
    @Override public void setTradingPlayer(@Nullable Player player) { tradingPlayer = player; }
    @Override public @Nullable Player getTradingPlayer() { return tradingPlayer; }
    @Override public MerchantOffers getOffers() { return offers; }
    @Override public void overrideOffers(MerchantOffers replacement) { offers.clear(); offers.addAll(replacement); }
    @Override public void notifyTrade(MerchantOffer offer) { mage.playSound(SoundEvents.VILLAGER_YES, 1, 1); xp += offer.getXp(); }
    @Override public void notifyTradeUpdated(ItemStack stack) {}
    @Override public int getVillagerXp() { return xp; }
    @Override public void overrideXp(int value) { xp = value; }
    @Override public boolean showProgressBar() { return false; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
    @Override public boolean isClientSide() { return mage.level().isClientSide(); }
}
