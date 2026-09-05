package com.strutton.dynamicmagic.mage;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.skill.MagicSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
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
    private static final String PRICE_SEED = "DynamicMagicMagePriceSeed";
    private final LivingEntity mage;
    private final MerchantOffers offers = new MerchantOffers();
    private final long priceSeed;
    private Player tradingPlayer;
    private int xp;

    MageMerchant(LivingEntity mage, long elements, long skills) {
        this.mage = mage;
        this.priceSeed = priceSeed(mage);
        for (Element element : Element.values()) if ((elements & bit(element.ordinal())) != 0) {
            int emeralds = varied("element:" + element.name(), elementValue(element), 8, 64);
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds),
                    new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(element).get()), 12, 8, .05f));
        }
        for (MagicSkill skill : MagicSkill.values())
            if ((skills & bit(skill.ordinal())) != 0) addSkillOffer(skill, 2, 24, .1f);
    }

    static void openCreeperScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,
                merchant.varied("technique:explosion", 36, 18, 64)),
                new ItemStack(DynamicMagic.EXPLOSION_TOME.get()), 6, 16, .05f));
        merchant.addSkillOffer(MagicSkill.EXPLOSION_RESISTANCE, 2, 24, .1f);
        merchant.open(player);
    }

    static void openEndermanScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,
                merchant.varied("element:space:emerald", 48, 24, 64)),
                Optional.of(new ItemCost(Items.ENDER_PEARL,
                        merchant.varied("element:space:pearls", 8, 3, 16))),
                new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(Element.SPACE).get()), 4, 20, .08f));
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,
                merchant.varied("technique:teleport:emerald", 56, 28, 64)),
                Optional.of(new ItemCost(Items.ENDER_EYE,
                        merchant.varied("technique:teleport:eyes", 4, 1, 10))),
                new ItemStack(DynamicMagic.TELEPORT_TOME.get()), 2, 28, .12f));
        merchant.open(player);
    }

    static void openAlexScholar(LivingEntity mage, Player player) {
        MageMerchant merchant = new MageMerchant(mage, 0, 0);
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,
                merchant.varied("element:spirit:emerald", 38, 18, 64)),
                Optional.of(new ItemCost(Items.GOLDEN_CARROT,
                        merchant.varied("element:spirit:carrots", 8, 3, 18))),
                new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(Element.SPIRIT).get()), 6, 18, .06f));
        merchant.offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,
                merchant.varied("technique:fertility:emerald", 44, 22, 64)),
                Optional.of(new ItemCost(Items.GOLDEN_APPLE,
                        merchant.varied("technique:fertility:apples", 2, 1, 5))),
                new ItemStack(DynamicMagic.FERTILITY_TOME.get()), 3, 24, .1f));
        merchant.open(player);
    }

    static MageMerchant forMorphMage(LivingEntity mage) {
        MageMerchant merchant = new MageMerchant(mage, 0, bit(MagicSkill.MORPHING.ordinal()));
        if (mage instanceof AbstractVillager villager && !villager.getOffers().isEmpty())
            merchant.offers.addAll(0, villager.getOffers());
        return merchant;
    }

    static MerchantOffers morphMageOffers(LivingEntity mage) {
        return forMorphMage(mage).getOffers();
    }

    static void copyPriceProfile(LivingEntity source, LivingEntity destination) {
        destination.getPersistentData().putLong(PRICE_SEED, priceSeed(source));
    }

    void open(Player player) {
        setTradingPlayer(player);
        openTradingScreen(player, mage.getDisplayName(), 1);
    }

    private void addSkillOffer(MagicSkill skill, int maxUses, int merchantXp, float priceMultiplier) {
        int rarityPremium = skill.randomDrop() ? 0 : 2;
        int emeraldBase = 28 + skill.experienceLevelCost() / 2;
        int blockBase = Math.max(2, (skill.experienceLevelCost() + 7) / 10) + rarityPremium;
        int emeralds = varied("skill:" + skill.name() + ":emerald", emeraldBase, 18, 64);
        int blocks = varied("skill:" + skill.name() + ":blocks", blockBase, 1, 18);
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds),
                Optional.of(new ItemCost(Items.EMERALD_BLOCK, blocks)),
                new ItemStack(DynamicMagic.SKILL_TOMES.get(skill).get()), maxUses, merchantXp, priceMultiplier));
    }

    /** Base value reflects how difficult and powerful an element is to acquire. */
    private static int elementValue(Element element) {
        return switch (element) {
            case FIRE, WATER, EARTH, AIR, LIGHTNING -> 16;
            case SAND -> 22;
            case ICE, METAL -> 27;
            case LIGHT, SHADOW, GLASS -> 31;
            case LAVA, STORM, SCORCH -> 38;
            case ARCANE, QUICK -> 42;
            case PLASMA -> 46;
            case SPIRIT, UNDEAD -> 48;
            case BLOOD -> 52;
            case SPACE -> 55;
            case DIVINE -> 58;
            case TIME -> 62;
        };
    }

    private int varied(String offerKey, int base, int minimum, int maximum) {
        // One factor makes a mage generally generous or expensive; another varies their individual stock.
        double personality = .72 + unit(mix(priceSeed ^ 0x632be59bd9b4e019L)) * .68;
        double stock = .78 + unit(mix(priceSeed ^ stableHash(offerKey))) * .50;
        return Math.clamp((int) Math.round(base * personality * stock), minimum, maximum);
    }

    private static long priceSeed(LivingEntity mage) {
        if (!mage.getPersistentData().contains(PRICE_SEED)) {
            long identity = mage.getUUID().getMostSignificantBits() ^ Long.rotateLeft(
                    mage.getUUID().getLeastSignificantBits(), 23);
            mage.getPersistentData().putLong(PRICE_SEED, mix(identity ^ mage.getRandom().nextLong()));
        }
        return mage.getPersistentData().getLong(PRICE_SEED);
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (double) (value >>> 11) * 0x1.0p-53;
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
