package com.strutton.dynamicmagic.mana;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Player-powered brewing that pauses whenever its brewer or their mana is unavailable. */
public final class ManaBrewing {
    private static final String MASTERY = "DynamicMagicManaBrewingMastery";
    private static final String RECIPE = "DynamicMagicManaBrewRecipe";
    private static final String PROGRESS = "DynamicMagicManaBrewProgress";
    private static final String OWNER = "DynamicMagicManaBrewOwner";
    private static final String LAST_ACTIVE = "DynamicMagicManaBrewLastActive";
    private static final String BLOOD_PAID = "DynamicMagicManaBrewBloodPaid";
    private static final int HORIZONTAL_RANGE = 6;
    private static final int VERTICAL_RANGE = 4;

    private static final List<BrewRecipe> RECIPES = List.of(
            new BrewRecipe("mana", () -> Items.AMETHYST_SHARD, DynamicMagic.MANA_POTION, 60, 60, 0, 0, false, .35, 0x8A55FF),
            new BrewRecipe("vampire_cure", ManaBrewing::vampireFangOrFallback, DynamicMagic.VAMPIRE_CURE_POTION,
                    60, 90, 6, 0, false, 0, 0xB51F3C),
            new BrewRecipe("reset", () -> Items.CLOCK, DynamicMagic.EXPANSION_RESET_POTION, 60, 150, 0, 0, true, 0, 0xFFD65A),
            new BrewRecipe("expand_1", () -> Items.GOLD_INGOT, DynamicMagic.MANA_EXPANSION_1, 60, 90, 0, 1, false, 0, 0x62C7FF),
            new BrewRecipe("expand_5", () -> Items.DIAMOND, DynamicMagic.MANA_EXPANSION_5, 60, 280, 0, 5, false, 0, 0x36E8D4),
            new BrewRecipe("expand_10", () -> Items.EMERALD_BLOCK, DynamicMagic.MANA_EXPANSION_10, 60, 650, 0, 10, false, 0, 0x36E868),
            new BrewRecipe("expand_25", () -> Items.NETHER_STAR, DynamicMagic.MANA_EXPANSION_25, 60, 1_800, 0, 25, false, 0, 0xF2E8FF),
            new BrewRecipe("expand_50", () -> Items.DRAGON_EGG, DynamicMagic.MANA_EXPANSION_50, 60, 5_000, 0, 50, false, 0, 0xC040FF)
    );

    private ManaBrewing() {}

    private static Item vampireFangOrFallback() {
        Item fang = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vampirism", "vampire_fang"));
        // Internal vampires use the zombie fallback and therefore naturally drop rotten flesh.
        return fang == Items.AIR ? Items.ROTTEN_FLESH : fang;
    }

    /**
     * Marks our catalysts as legal brewing-stand ingredients without giving the
     * vanilla brewer a recipe to run. ManaBrewing owns the actual conversion.
     */
    @SubscribeEvent
    public static void registerIngredientSlots(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(new IBrewingRecipe() {
            @Override
            public boolean isInput(ItemStack input) {
                return false;
            }

            @Override
            public boolean isIngredient(ItemStack ingredient) {
                return RECIPES.stream().anyMatch(recipe -> ingredient.is(recipe.ingredient()));
            }

            @Override
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                return ItemStack.EMPTY;
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HORIZONTAL_RANGE, -VERTICAL_RANGE, -HORIZONTAL_RANGE),
                center.offset(HORIZONTAL_RANGE, VERTICAL_RANGE, HORIZONTAL_RANGE))) {
            if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                    > HORIZONTAL_RANGE * HORIZONTAL_RANGE) continue;
            if (player.serverLevel().getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)
                processStand(player, stand);
        }
    }

    @SubscribeEvent
    public static void onPotionFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getItem().is(Items.POTION)) return;
        PotionContents contents = event.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;
        com.strutton.dynamicmagic.vampire.VampireCureTreatment.recordSupportPotion(player, contents);
        if (contents.potion().isEmpty()) return;
        applyPotion(player, contents.potion().get());
    }

    public static boolean applyPotion(ServerPlayer player, Holder<Potion> potion) {
        BrewRecipe recipe = RECIPES.stream().filter(candidate -> potion.is(candidate.output())).findFirst().orElse(null);
        if (recipe == null) return false;

        if (potion.is(DynamicMagic.VAMPIRE_CURE_POTION))
            return com.strutton.dynamicmagic.vampire.VampireCureTreatment.start(player);

        if (recipe.manaRestoreFraction() > 0) {
            double restored = Mana.max(player) * recipe.manaRestoreFraction();
            Mana.set(player, Mana.get(player) + restored);
            player.displayClientMessage(Component.literal("Restored " + (int) restored + " mana."), true);
        }
        if (recipe.resetsExpansion()) {
            Mana.resetExpansionCooldown(player);
            player.displayClientMessage(Component.literal("Your mana-expansion timeout has been reset."), false);
        }
        if (recipe.expansionPercent() > 0) {
            double growth = Mana.expandByPercent(player, recipe.expansionPercent());
            player.displayClientMessage(Component.literal("Your maximum mana expanded by "
                    + recipe.expansionPercent() + "% (+" + String.format(java.util.Locale.ROOT, "%.2f", growth) + ")."), false);
        }
        return true;
    }

    public static boolean processStand(ServerPlayer player, BrewingStandBlockEntity stand) {
        BrewRecipe recipe = recipeFor(stand);
        CompoundTag data = stand.getPersistentData();
        if (recipe == null) {
            if (data.contains(RECIPE)) clearProgress(data, stand);
            return false;
        }

        long now = player.serverLevel().getGameTime();
        if (!recipe.id().equals(data.getString(RECIPE))) {
            clearProgress(data, stand);
            data.putString(RECIPE, recipe.id());
            data.putUUID(OWNER, player.getUUID());
        } else if (data.hasUUID(OWNER) && !data.getUUID(OWNER).equals(player.getUUID())) {
            if (now - data.getLong(LAST_ACTIVE) <= 200) return false;
            data.putUUID(OWNER, player.getUUID());
        }
        data.putLong(LAST_ACTIVE, now);

        int requiredSeconds = requiredSeconds(player, recipe);
        double pulseCost = totalManaCost(player, recipe) / requiredSeconds;
        int nextProgress = data.getInt(PROGRESS) + 1;
        int paidBlood = data.getInt(BLOOD_PAID);
        int bloodDue = Math.max(0, (int) Math.ceil(recipe.vampireBloodCost()
                * nextProgress / (double) requiredSeconds) - paidBlood);
        if (recipe.vampireBloodCost() > 0 && (!com.strutton.dynamicmagic.vampire.Vampirism.isVampire(player)
                || com.strutton.dynamicmagic.vampire.Vampirism.bloodLevel(player) <= 0
                || com.strutton.dynamicmagic.vampire.Vampirism.bloodLevel(player) < bloodDue)) {
            if (now % 100 == 0) player.displayClientMessage(Component.literal(
                    "Vampire cure brewing paused: you need vampire blood in your body."), true);
            stand.setChanged();
            return false;
        }
        if (!Mana.isUnlimited(player) && Mana.get(player) + 1.0e-6 < pulseCost) {
            if (now % 100 == 0) player.displayClientMessage(Component.literal("Mana brewing paused: need "
                    + String.format(java.util.Locale.ROOT, "%.2f", pulseCost) + " mana for the next step."), true);
            stand.setChanged();
            return false;
        }
        if (!Mana.consume(player, pulseCost)) return false;
        if (bloodDue > 0) {
            if (!com.strutton.dynamicmagic.vampire.Vampirism.consumeBlood(player, bloodDue)) return false;
            data.putInt(BLOOD_PAID, paidBlood + bloodDue);
        }

        int progress = nextProgress;
        data.putInt(PROGRESS, progress);
        if (progress % 10 == 0)
            player.displayClientMessage(Component.literal("Mana brewing: " + progress + "/" + requiredSeconds
                    + "s (mastery " + masteryLevel(player) + "/20)"), true);
        if (progress < requiredSeconds) {
            stand.setChanged();
            return true;
        }

        complete(player, stand, recipe);
        return true;
    }

    private static void complete(ServerPlayer player, BrewingStandBlockEntity stand, BrewRecipe recipe) {
        int bottles = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (!isAwkward(stand.getItem(slot))) continue;
            stand.setItem(slot, potionStack(recipe.output()));
            bottles++;
        }
        ItemStack ingredient = stand.getItem(3);
        ingredient.shrink(1);
        if (ingredient.isEmpty()) stand.setItem(3, ItemStack.EMPTY);
        clearProgress(stand.getPersistentData(), stand);
        boolean learned = SkillKnowledge.learn(player, MagicSkill.MANA_BREWING);
        addMastery(player, recipe.masteryReward());
        player.displayClientMessage(Component.literal((learned ? "You discovered Mana Brewing! " : "")
                + "Completed " + bottles + " potion" + (bottles == 1 ? "" : "s")
                + "; mastery is now " + masteryLevel(player) + "/20."), false);
    }

    private static BrewRecipe recipeFor(BrewingStandBlockEntity stand) {
        ItemStack ingredient = stand.getItem(3);
        if (ingredient.isEmpty()) return null;
        boolean hasAwkward = false;
        for (int slot = 0; slot < 3; slot++) hasAwkward |= isAwkward(stand.getItem(slot));
        if (!hasAwkward) return null;
        return RECIPES.stream().filter(recipe -> ingredient.is(recipe.ingredient())).findFirst().orElse(null);
    }

    private static boolean isAwkward(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.AWKWARD);
    }

    public static ItemStack potionStack(Holder<Potion> potion) {
        int color = RECIPES.stream().filter(recipe -> potion.is(recipe.output()))
                .map(BrewRecipe::color).findFirst().orElse(0x8A55FF);
        ItemStack stack = PotionContents.createItemStack(Items.POTION, potion);
        stack.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.of(potion), Optional.of(color), List.of()));
        return stack;
    }

    public static double mastery(ServerPlayer player) { return player.getPersistentData().getDouble(MASTERY); }
    public static int masteryLevel(ServerPlayer player) { return Math.min(20, (int) Math.floor(Math.sqrt(mastery(player)))); }
    public static int requiredSeconds(ServerPlayer player, BrewRecipe recipe) {
        return Math.max(1, (int) Math.ceil(recipe.baseSeconds() * (1 - masteryLevel(player) * .025)));
    }
    public static double totalManaCost(ServerPlayer player, BrewRecipe recipe) {
        return recipe.baseMana() * (1 - masteryLevel(player) * .02);
    }
    public static BrewRecipe recipe(String id) {
        return RECIPES.stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElseThrow();
    }
    public static void setMastery(ServerPlayer player, double value) {
        player.getPersistentData().putDouble(MASTERY, Math.max(0, value));
    }
    private static void addMastery(ServerPlayer player, double value) { setMastery(player, mastery(player) + value); }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(MASTERY))
            to.getPersistentData().putDouble(MASTERY, from.getPersistentData().getDouble(MASTERY));
    }

    private static void clearProgress(CompoundTag data, BrewingStandBlockEntity stand) {
        data.remove(RECIPE);
        data.remove(PROGRESS);
        data.remove(OWNER);
        data.remove(LAST_ACTIVE);
        data.remove(BLOOD_PAID);
        stand.setChanged();
    }

    public record BrewRecipe(String id, Supplier<Item> ingredientSupplier, Holder<Potion> output,
                             int baseSeconds, double baseMana,
                             int vampireBloodCost, int expansionPercent, boolean resetsExpansion,
                             double manaRestoreFraction, int color) {
        public Item ingredient() { return ingredientSupplier.get(); }
        double masteryReward() { return 1 + expansionPercent * .5 + (resetsExpansion ? 2 : 0); }
    }
}
