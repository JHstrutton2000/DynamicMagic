package com.strutton.dynamicmagic.compat;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.magic.ElementMastery;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Optional AoA3 bridge. This class deliberately has no binary dependency on AoA classes. */
public final class AdventOfAscensionIntegration {
    private static final String RUNE_STUDY_KEY = "DynamicMagicAoARuneStudy";
    public static final int RUNES_TO_LEARN = 10;
    private static final TagKey<Item> STAVES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("aoa3", "staves"));
    private static final Map<String, RuneAttunement> RUNES = Map.ofEntries(
            Map.entry("charged_rune", new RuneAttunement(Element.ARCANE, 6, .50)),
            Map.entry("compass_rune", new RuneAttunement(Element.SPACE, 12, .75)),
            Map.entry("distortion_rune", new RuneAttunement(Element.SPACE, 15, .90)),
            Map.entry("energy_rune", new RuneAttunement(Element.ARCANE, 10, .75)),
            Map.entry("fire_rune", new RuneAttunement(Element.FIRE, 10, .75)),
            Map.entry("kinetic_rune", new RuneAttunement(Element.QUICK, 10, .75)),
            Map.entry("life_rune", new RuneAttunement(Element.SPIRIT, 12, .80)),
            Map.entry("lunar_rune", new RuneAttunement(Element.SHADOW, 12, .80)),
            Map.entry("poison_rune", new RuneAttunement(Element.SHADOW, 10, .75)),
            Map.entry("power_rune", new RuneAttunement(Element.ARCANE, 12, .80)),
            Map.entry("storm_rune", new RuneAttunement(Element.STORM, 14, .85)),
            Map.entry("strike_rune", new RuneAttunement(Element.LIGHTNING, 12, .80)),
            Map.entry("water_rune", new RuneAttunement(Element.WATER, 10, .75)),
            Map.entry("wind_rune", new RuneAttunement(Element.AIR, 10, .75)),
            Map.entry("wither_rune", new RuneAttunement(Element.UNDEAD, 12, .80)));
    private static final Map<UUID, PendingCast> PENDING_CASTS = new HashMap<>();

    private AdventOfAscensionIntegration() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());

        RuneAttunement rune = runeAttunement(id);
        if (rune != null) {
            consumeRune(player, stack, rune);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (!stack.is(STAVES) || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        StaffProfile profile = staffProfile(stack);
        if (!Mana.isUnlimited(player) && Mana.get(player) + 1.0e-6 < profile.manaCost()) {
            player.displayClientMessage(Component.literal("This staff requires "
                    + format(profile.manaCost()) + " mana (you have " + format(Mana.get(player)) + ").")
                    .withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (profile.storedCharges() <= 0) {
            Map<Item, Integer> deficits = runeDeficits(player, profile.runeCosts());
            if (!deficits.isEmpty() && canReplaceWithKnowledge(player, deficits)) {
                castWithElementalKnowledge(event, player, stack, profile, deficits);
                return;
            }
        }

        int usesBefore = player.getStats().getValue(Stats.ITEM_USED.get(stack.getItem()));
        PENDING_CASTS.put(player.getUUID(), new PendingCast(stack.getItem(), usesBefore, profile));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingCast pending = PENDING_CASTS.remove(player.getUUID());
        if (pending == null) return;
        int usesNow = player.getStats().getValue(Stats.ITEM_USED.get(pending.staff()));
        if (usesNow <= pending.usesBefore()) return;
        chargeSuccessfulCast(player, pending.profile());
    }

    private static void consumeRune(ServerPlayer player, ItemStack stack, RuneAttunement rune) {
        double before = Mana.get(player);
        if (!Mana.isUnlimited(player)) Mana.set(player, before + rune.manaRestore());
        double restored = Mana.isUnlimited(player) ? 0 : Mana.get(player) - before;
        ElementMastery.practice(player, rune.element(), rune.mastery());
        boolean learned = recordRuneStudy(player, rune.element());
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        String progress = learned ? " Learned " + rune.element().displayName() + "!"
                : ElementKnowledge.knows(player, rune.element()) ? " " + rune.element().displayName() + " mastery advanced."
                : " Element insight " + runeStudyProgress(player, rune.element()) + "/" + RUNES_TO_LEARN + ".";
        player.displayClientMessage(Component.literal("Absorbed " + rune.element().displayName() + " rune: +"
                + format(restored) + " mana." + progress).withStyle(learned ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA), true);
    }

    /** Records elemental insight from one absorbed rune and returns true only on the learning breakthrough. */
    public static boolean recordRuneStudy(ServerPlayer player, Element element) {
        if (ElementKnowledge.knows(player, element)) return false;
        var values = player.getPersistentData().getCompound(RUNE_STUDY_KEY).copy();
        int progress = Math.min(RUNES_TO_LEARN, values.getInt(element.name()) + 1);
        values.putInt(element.name(), progress);
        player.getPersistentData().put(RUNE_STUDY_KEY, values);
        return progress >= RUNES_TO_LEARN && ElementKnowledge.learn(player, element);
    }

    public static int runeStudyProgress(ServerPlayer player, Element element) {
        return Math.min(RUNES_TO_LEARN,
                Math.max(0, player.getPersistentData().getCompound(RUNE_STUDY_KEY).getInt(element.name())));
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(RUNE_STUDY_KEY))
            to.getPersistentData().put(RUNE_STUDY_KEY, from.getPersistentData().get(RUNE_STUDY_KEY).copy());
    }

    public static RuneAttunement runeAttunement(ResourceLocation id) {
        if (id == null || !id.getNamespace().equals("aoa3")) return null;
        return RUNES.get(id.getPath());
    }

    public static double staffManaCost(int runeCount, float magicDamage) {
        return Math.min(40, Math.max(3, 2 + Math.max(0, runeCount) * 1.25 + Math.max(0, magicDamage) * .35));
    }

    private static StaffProfile staffProfile(ItemStack stack) {
        EnumMap<Element, Integer> elements = new EnumMap<>(Element.class);
        Map<Item, Integer> runeCosts = new HashMap<>();
        int runeCount = 0;
        float magicDamage = 0;
        int storedCharges = -1;
        try {
            Method runeCostMethod = stack.getItem().getClass().getMethod("getRuneCost", ItemStack.class);
            Object rawCosts = runeCostMethod.invoke(stack.getItem(), stack);
            if (rawCosts instanceof Map<?, ?> costs) {
                for (Map.Entry<?, ?> entry : costs.entrySet()) {
                    if (!(entry.getKey() instanceof Item runeItem) || !(entry.getValue() instanceof Number amount)) continue;
                    int count = Math.max(0, amount.intValue());
                    runeCount += count;
                    if (count > 0) runeCosts.put(runeItem, count);
                    RuneAttunement rune = runeAttunement(BuiltInRegistries.ITEM.getKey(runeItem));
                    if (rune != null) elements.merge(rune.element(), count, Integer::sum);
                }
            }
            Method damageMethod = stack.getItem().getClass().getMethod("getMagicDamage", ItemStack.class);
            Object rawDamage = damageMethod.invoke(stack.getItem(), stack);
            if (rawDamage instanceof Number damage) magicDamage = damage.floatValue();
            Method chargesMethod = stack.getItem().getClass().getMethod("getStoredCharges", ItemStack.class);
            Object rawCharges = chargesMethod.invoke(stack.getItem(), stack);
            if (rawCharges instanceof Number charges) storedCharges = charges.intValue();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not inspect AoA staff stats for {}", stack, exception);
        }

        if (elements.isEmpty()) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            elements.put(fallbackStaffElement(path), 1);
        }
        return new StaffProfile(staffManaCost(runeCount, magicDamage), elements, Map.copyOf(runeCosts), storedCharges);
    }

    private static Map<Item, Integer> runeDeficits(ServerPlayer player, Map<Item, Integer> costs) {
        Map<Item, Integer> deficits = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : costs.entrySet()) {
            int missing = entry.getValue() - player.getInventory().countItem(entry.getKey());
            if (missing > 0) deficits.put(entry.getKey(), missing);
        }
        return deficits;
    }

    private static boolean canReplaceWithKnowledge(ServerPlayer player, Map<Item, Integer> deficits) {
        for (Item item : deficits.keySet()) {
            RuneAttunement rune = runeAttunement(BuiltInRegistries.ITEM.getKey(item));
            if (rune == null || !ElementKnowledge.knows(player, rune.element())) return false;
        }
        return true;
    }

    /** Lets AoA perform its own cast normally while temporary runes stand in for learned elemental knowledge. */
    private static void castWithElementalKnowledge(PlayerInteractEvent.RightClickItem event, ServerPlayer player,
                                                    ItemStack staff, StaffProfile profile, Map<Item, Integer> deficits) {
        Map<Item, Integer> supplied = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : deficits.entrySet()) {
            int before = player.getInventory().countItem(entry.getKey());
            ItemStack temporary = new ItemStack(entry.getKey(), entry.getValue());
            player.getInventory().add(temporary);
            int added = player.getInventory().countItem(entry.getKey()) - before;
            if (added > 0) supplied.put(entry.getKey(), added);
            if (added < entry.getValue()) {
                removeSuppliedRunes(player, supplied);
                player.displayClientMessage(Component.literal("Elemental rune substitution needs an open inventory slot.")
                        .withStyle(ChatFormatting.RED), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
        }

        int usesBefore = player.getStats().getValue(Stats.ITEM_USED.get(staff.getItem()));
        InteractionResultHolder<ItemStack> result;
        try {
            result = staff.use(event.getLevel(), player, event.getHand());
        } finally {
            removeSuppliedRunes(player, supplied);
        }
        player.setItemInHand(event.getHand(), result.getObject());
        int usesAfter = player.getStats().getValue(Stats.ITEM_USED.get(staff.getItem()));
        if (usesAfter > usesBefore) chargeSuccessfulCast(player, profile);
        event.setCanceled(true);
        event.setCancellationResult(result.getResult());
    }

    private static void removeSuppliedRunes(ServerPlayer player, Map<Item, Integer> supplied) {
        for (Map.Entry<Item, Integer> entry : supplied.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.is(entry.getKey())) continue;
                int remove = Math.min(remaining, stack.getCount());
                stack.shrink(remove);
                remaining -= remove;
            }
        }
    }

    private static void chargeSuccessfulCast(ServerPlayer player, StaffProfile profile) {
        Mana.consume(player, profile.manaCost());
        int totalWeight = profile.elements().values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<Element, Integer> entry : profile.elements().entrySet()) {
            double practice = profile.manaCost() * .08 * entry.getValue() / Math.max(1.0, totalWeight);
            ElementMastery.practice(player, entry.getKey(), practice);
        }
    }

    public static Element fallbackStaffElement(String path) {
        if (containsAny(path, "water", "aquatic", "atlantic", "coral", "reef")) return Element.WATER;
        if (containsAny(path, "fire", "ember", "meteor")) return Element.FIRE;
        if (containsAny(path, "lightning", "striker", "surge")) return Element.LIGHTNING;
        if (containsAny(path, "wind", "sky", "concussion")) return Element.AIR;
        if (containsAny(path, "sun", "celestial", "lightshine", "rejuvenation")) return Element.DIVINE;
        if (containsAny(path, "lunar", "moon", "nightmare", "shadow", "phantom")) return Element.SHADOW;
        if (containsAny(path, "wither", "ghoul", "haunter", "underworld")) return Element.UNDEAD;
        if (containsAny(path, "poison", "noxious", "web", "fungal")) return Element.SHADOW;
        if (containsAny(path, "nature", "tangle", "rosidian")) return Element.SPIRIT;
        if (containsAny(path, "mecha")) return Element.METAL;
        if (containsAny(path, "crystal", "crystik", "cryston")) return Element.GLASS;
        return Element.ARCANE;
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    public record RuneAttunement(Element element, double manaRestore, double mastery) {}
    private record StaffProfile(double manaCost, EnumMap<Element, Integer> elements,
                                Map<Item, Integer> runeCosts, int storedCharges) {}
    private record PendingCast(Item staff, int usesBefore, StaffProfile profile) {}
}
