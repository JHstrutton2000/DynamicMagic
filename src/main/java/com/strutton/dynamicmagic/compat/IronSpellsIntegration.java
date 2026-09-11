package com.strutton.dynamicmagic.compat;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.magic.ElementMastery;
import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;

/** Optional binary-free bridge to Iron's Spells 'n Spellbooks. */
public final class IronSpellsIntegration {
    private static final String NAMESPACE = "irons_spellbooks";
    private static final String MERGED = "dynamicmagic.irons_mana_merged";
    private static final ResourceLocation CAPACITY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "native_mana_capacity");
    private static final ThreadLocal<Boolean> SYNCHRONIZING = ThreadLocal.withInitial(() -> false);
    private static Method getMagicData;
    private static Method getIronMana;
    private static Method setIronMana;
    private static boolean active;

    private IronSpellsIntegration() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(IEventBus bus) {
        try {
            Class<? extends Event> manaEvent = (Class<? extends Event>) Class.forName(
                    "io.redspace.ironsspellbooks.api.events.ChangeManaEvent");
            Class<? extends Event> preCastEvent = (Class<? extends Event>) Class.forName(
                    "io.redspace.ironsspellbooks.api.events.SpellPreCastEvent");
            Class<? extends Event> castEvent = (Class<? extends Event>) Class.forName(
                    "io.redspace.ironsspellbooks.api.events.SpellOnCastEvent");
            bus.addListener(EventPriority.HIGHEST, (Class) manaEvent,
                    (Consumer) value -> onManaChanged((Event) value));
            bus.addListener(EventPriority.HIGHEST, (Class) preCastEvent,
                    (Consumer) value -> onPreCast((Event) value));
            bus.addListener(EventPriority.LOWEST, (Class) castEvent,
                    (Consumer) value -> onCast((Event) value));
            active = true;
            DynamicMagic.LOGGER.info("Enabled shared mana and elemental knowledge integration for Iron's Spellbooks");
        } catch (ReflectiveOperationException exception) {
            DynamicMagic.LOGGER.error("Iron's Spellbooks was loaded, but its integration API could not be attached", exception);
        }
    }

    public static void ensureMerged(ServerPlayer player, double nativeMaximum) {
        if (!active) return;
        AttributeInstance capacity = manaCapacity(player);
        if (capacity == null) return;
        double ironMaximumBefore = Math.max(1, capacity.getValue());
        double ironCurrentBefore = readIronMana(player, ironMaximumBefore);
        installCapacityModifier(capacity, nativeMaximum);
        if (player.getPersistentData().getBoolean(MERGED)) return;

        double dynamicCurrent = player.getPersistentData().contains("dynamicmagic.mana")
                ? player.getPersistentData().getDouble("dynamicmagic.mana") : nativeMaximum;
        double dynamicRatio = clamp01(dynamicCurrent / Math.max(1, nativeMaximum));
        double ironRatio = clamp01(ironCurrentBefore / ironMaximumBefore);
        double mergedMaximum = Math.max(1, capacity.getValue());
        player.getPersistentData().putDouble("dynamicmagic.mana",
                mergedMaximum * Math.min(dynamicRatio, ironRatio));
        player.getPersistentData().putBoolean(MERGED, true);
        syncFromDynamic(player);
    }

    public static double effectiveMaximum(ServerPlayer player, double nativeMaximum) {
        if (!active) return nativeMaximum;
        AttributeInstance capacity = manaCapacity(player);
        if (capacity == null) return nativeMaximum;
        installCapacityModifier(capacity, nativeMaximum);
        return Math.max(1, capacity.getValue());
    }

    public static void syncFromDynamic(ServerPlayer player) {
        if (!active || SYNCHRONIZING.get()) return;
        try {
            SYNCHRONIZING.set(true);
            Object data = magicData(player);
            if (data != null) setIronMana.invoke(data, (float) Math.min(Mana.get(player), Mana.max(player)));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not synchronize shared mana to Iron's Spellbooks", exception);
        } finally {
            SYNCHRONIZING.set(false);
        }
    }

    public static float adjustDynamicSpellDamage(ServerPlayer caster, LivingEntity target,
                                                  Element element, float amount) {
        String school = ironSchool(element);
        if (!active || school == null) return amount;
        double power = attributeValue(caster, school + "_spell_power", 1);
        double resistance = attributeValue(target, school + "_magic_resist", 1);
        return (float) (amount * clamp(power, .25, 3) / clamp(resistance, .25, 3));
    }

    public static Element elementForSchool(String schoolPath) {
        if (schoolPath == null) return Element.ARCANE;
        String path = schoolPath.toLowerCase(Locale.ROOT);
        return switch (path) {
            case "fire" -> Element.FIRE;
            case "ice" -> Element.ICE;
            case "lightning" -> Element.LIGHTNING;
            case "holy" -> Element.DIVINE;
            case "ender" -> Element.SPACE;
            case "blood" -> Element.BLOOD;
            case "nature" -> Element.SPIRIT;
            case "eldritch" -> Element.SHADOW;
            case "evocation" -> Element.ARCANE;
            default -> inferAddonSchool(path);
        };
    }

    private static void onManaChanged(Event event) {
        if (SYNCHRONIZING.get()) return;
        try {
            if (!(invoke(event, "getEntity") instanceof ServerPlayer player)) return;
            ensureMerged(player, Mana.nativeMaximum(player));
            float oldMana = ((Number) invoke(event, "getOldMana")).floatValue();
            float requestedMana = ((Number) invoke(event, "getNewMana")).floatValue();
            double delta = requestedMana - oldMana;
            SYNCHRONIZING.set(true);
            if (delta < 0) {
                if (!Mana.consume(player, -delta)) {
                    event.getClass().getMethod("setNewMana", float.class).invoke(event, oldMana);
                    return;
                }
            } else if (delta > 0) {
                Mana.set(player, Mana.get(player) + delta);
            }
            float shared = Mana.isUnlimited(player) ? (float) Mana.max(player) : (float) Mana.get(player);
            event.getClass().getMethod("setNewMana", float.class).invoke(event, shared);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not process an Iron's Spellbooks mana change", exception);
        } finally {
            SYNCHRONIZING.set(false);
        }
    }

    private static void onPreCast(Event event) {
        try {
            if (!(invoke(event, "getEntity") instanceof ServerPlayer player)) return;
            if (!"SPELLBOOK".equals(String.valueOf(invoke(event, "getCastSource")))) return;
            Element element = eventElement(event);
            if (ElementKnowledge.knows(player, element)) return;
            event.getClass().getMethod("setCanceled", boolean.class).invoke(event, true);
            player.displayClientMessage(Component.literal("You cannot understand this spellbook yet. Learn "
                    + element.displayName() + " first.").withStyle(ChatFormatting.RED), true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not validate Iron's Spellbooks elemental knowledge", exception);
        }
    }

    private static void onCast(Event event) {
        try {
            if (!(invoke(event, "getEntity") instanceof ServerPlayer player)) return;
            Element element = eventElement(event);
            if (!ElementKnowledge.knows(player, element)) return;
            int manaCost = ((Number) invoke(event, "getManaCost")).intValue();
            int level = ((Number) invoke(event, "getSpellLevel")).intValue();
            ElementMastery.practice(player, element, Math.max(.25, manaCost * .025 + level * .10));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            DynamicMagic.LOGGER.debug("Could not award mastery for an Iron's Spellbooks cast", exception);
        }
    }

    private static Element eventElement(Object event) throws ReflectiveOperationException {
        Object school = invoke(event, "getSchoolType");
        Object id = school.getClass().getMethod("getId").invoke(school);
        return elementForSchool(id instanceof ResourceLocation location ? location.getPath() : String.valueOf(id));
    }

    private static Object invoke(Object owner, String method) throws ReflectiveOperationException {
        return owner.getClass().getMethod(method).invoke(owner);
    }

    private static AttributeInstance manaCapacity(ServerPlayer player) {
        Holder.Reference<Attribute> attribute = attribute("max_mana");
        return attribute == null ? null : player.getAttribute(attribute);
    }

    private static void installCapacityModifier(AttributeInstance capacity, double nativeMaximum) {
        AttributeModifier current = capacity.getModifier(CAPACITY_MODIFIER);
        double amount = nativeMaximum - Mana.DEFAULT_MAX;
        if (current != null && Math.abs(current.amount() - amount) < 1.0e-6) return;
        capacity.removeModifier(CAPACITY_MODIFIER);
        capacity.addTransientModifier(new AttributeModifier(CAPACITY_MODIFIER, amount,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static double attributeValue(LivingEntity entity, String path, double fallback) {
        Holder.Reference<Attribute> attribute = attribute(path);
        if (attribute == null || !entity.getAttributes().hasAttribute(attribute)) return fallback;
        return entity.getAttributeValue(attribute);
    }

    private static Holder.Reference<Attribute> attribute(String path) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)).orElse(null);
    }

    private static Object magicData(ServerPlayer player) throws ReflectiveOperationException {
        if (getMagicData == null) {
            Class<?> type = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            getMagicData = type.getMethod("getPlayerMagicData", LivingEntity.class);
            getIronMana = type.getMethod("getMana");
            setIronMana = type.getMethod("setMana", float.class);
        }
        return getMagicData.invoke(null, player);
    }

    private static double readIronMana(ServerPlayer player, double fallback) {
        try {
            Object data = magicData(player);
            return data == null ? fallback : ((Number) getIronMana.invoke(data)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static String ironSchool(Element element) {
        return switch (element) {
            case FIRE, SCORCH, LAVA -> "fire";
            case ICE -> "ice";
            case LIGHTNING, STORM, PLASMA -> "lightning";
            case DIVINE, LIGHT -> "holy";
            case SPACE, QUICK -> "ender";
            case BLOOD, UNDEAD -> "blood";
            case SPIRIT -> "nature";
            case SHADOW -> "eldritch";
            case ARCANE -> "evocation";
            case KI -> null;
            default -> null;
        };
    }

    private static Element inferAddonSchool(String path) {
        if (path.contains("fire") || path.contains("flame") || path.contains("pyro")) return Element.FIRE;
        if (path.contains("ice") || path.contains("frost") || path.contains("cryo")) return Element.ICE;
        if (path.contains("storm") || path.contains("lightning")) return Element.LIGHTNING;
        if (path.contains("holy") || path.contains("divine")) return Element.DIVINE;
        if (path.contains("blood")) return Element.BLOOD;
        if (path.contains("nature") || path.contains("earth")) return Element.SPIRIT;
        if (path.contains("ender") || path.contains("space")) return Element.SPACE;
        if (path.contains("shadow") || path.contains("eldritch")) return Element.SHADOW;
        return Element.ARCANE;
    }

    private static double clamp01(double value) { return clamp(value, 0, 1); }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
