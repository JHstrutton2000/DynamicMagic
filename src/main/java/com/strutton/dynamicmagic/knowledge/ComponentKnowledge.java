package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.magic.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Knowledge of spell grammar. New players receive exactly one random basic element and one simple technique. */
public final class ComponentKnowledge {
    private static final String STARTED = "DynamicMagicStarterGranted";
    private static final String FORMS = "DynamicMagicKnownForms";
    private static final String DELIVERIES = "DynamicMagicKnownDeliveries";
    private static final String IMPACTS = "DynamicMagicKnownImpacts";
    private static final String DIRECTIONS = "DynamicMagicKnownDirections";
    private static final String CONDITIONS = "DynamicMagicKnownConditions";
    private static final String CASTS = "DynamicMagicSuccessfulCasts";
    private static final String ENLIGHTENMENT = "DynamicMagicElementEnlightenment";
    private static final String BREAKTHROUGHS = "DynamicMagicElementBreakthroughs";
    private static final String STUDY = "DynamicMagicElementStudy";
    private static final double BASE_BREAKTHROUGH_REQUIREMENT = 256.0;
    private ComponentKnowledge() {}

    public static void ensureStarter(Player player) {
        if (player.getPersistentData().getBoolean(STARTED)) return;
        if (ElementKnowledge.mask(player) == 0) {
            Element[] basic = {Element.FIRE, Element.WATER, Element.AIR, Element.EARTH, Element.LIGHTNING};
            ElementKnowledge.learn(player, basic[Math.floorMod(player.getUUID().hashCode(), basic.length)]);
        }
        setBit(player, FORMS, SpellForm.BOLT.ordinal());
        setBit(player, DELIVERIES, DeliveryType.PROJECTILE.ordinal());
        setBit(player, IMPACTS, ImpactType.DAMAGE.ordinal());
        setBit(player, IMPACTS, ImpactType.STUDY.ordinal());
        setBit(player, IMPACTS, ImpactType.APPLY_EFFECT.ordinal());
        setBit(player, DIRECTIONS, CastDirection.LOOK.ordinal());
        setBit(player, CONDITIONS, ConditionType.ALWAYS.ordinal());
        player.getPersistentData().putBoolean(STARTED, true);
        for (Element known : ElementKnowledge.known(player)) learnIntrinsic(player, known);
    }

    public static KnowledgeSnapshot snapshot(Player player) {
        ensureStarter(player);
        return new KnowledgeSnapshot(ElementKnowledge.mask(player), mask(player, FORMS), mask(player, DELIVERIES),
                mask(player, IMPACTS), mask(player, DIRECTIONS), mask(player, CONDITIONS),
                ProgrammingKnowledge.knows(player), ProgrammingKnowledge.mastery(player), ProgrammingKnowledge.maxBranches(player));
    }

    public static boolean canUse(Player player, CraftedSpell spell) {
        KnowledgeSnapshot knowledge = snapshot(player);
        return knowledge.knows(spell.form()) && knowledge.knows(spell.delivery())
                && spell.allInstructions().stream().allMatch(instruction -> (instruction.impact() == ImpactType.STUDY
                        || knowledge.knows(instruction.element()))
                        && knowledge.knows(instruction.impact()) && knowledge.knows(instruction.direction())
                        && (instruction.impact() != ImpactType.APPLY_EFFECT
                        || instruction.potionEffect().element() == instruction.element()))
                && (!spell.programmed() || knowledge.programming()
                        && spell.branches().stream().allMatch(branch -> knowledge.knows(branch.condition())
                                && knowledge.knows(branch.targetMode())))
                && (spell.source() != SourceType.SUMMON || knowledge.canSummonDimension());
    }

    public static void learnAll(Player player) {
        ensureStarter(player);
        ElementKnowledge.learnAll(player);
        player.getPersistentData().putLong(FORMS, all(SpellForm.values().length));
        player.getPersistentData().putLong(DELIVERIES, all(DeliveryType.values().length));
        player.getPersistentData().putLong(IMPACTS, all(ImpactType.values().length));
        player.getPersistentData().putLong(DIRECTIONS, all(CastDirection.values().length));
        player.getPersistentData().putLong(CONDITIONS, all(ConditionType.values().length));
        ProgrammingKnowledge.learnAll(player);
    }

    public static void learnProgrammingBasics(Player player) {
        setBit(player, CONDITIONS, ConditionType.ENTITY_NEARBY.ordinal());
    }

    public static boolean knowsImpact(Player player, ImpactType impact) {
        return (mask(player, IMPACTS) & (1L << impact.ordinal())) != 0;
    }

    public static boolean learnImpact(Player player, ImpactType impact) {
        if (knowsImpact(player, impact)) return false;
        setBit(player, IMPACTS, impact.ordinal());
        return true;
    }

    public static void learnIntrinsic(Player player, Element element) {
        if (element == Element.SPACE) {
            setBit(player, IMPACTS, ImpactType.TELEPORT.ordinal());
        } else if (element == Element.TIME) {
            setBit(player, IMPACTS, ImpactType.SLOW_TIME.ordinal());
        } else if (element == Element.AIR) {
            setBit(player, IMPACTS, ImpactType.PHYSICS.ordinal());
        } else if (element == Element.DIVINE) {
            setBit(player, IMPACTS, ImpactType.HEAL.ordinal());
        } else if (element == Element.WATER) {
            setBit(player, IMPACTS, ImpactType.EXTINGUISH.ordinal());
        } else if (element == Element.EARTH) {
            setBit(player, IMPACTS, ImpactType.ROOT.ordinal());
        } else if (element == Element.LIGHTNING) {
            setBit(player, IMPACTS, ImpactType.CHAIN.ordinal());
        } else if (element == Element.LIGHT) {
            setBit(player, IMPACTS, ImpactType.ILLUMINATE.ordinal());
        } else if (element == Element.SHADOW) {
            setBit(player, IMPACTS, ImpactType.BLIND.ordinal());
        } else if (element == Element.ARCANE) {
            setBit(player, IMPACTS, ImpactType.DISPEL.ordinal());
        } else if (element == Element.FIRE) {
            setBit(player, IMPACTS, ImpactType.IGNITE.ordinal());
        } else if (element == Element.ICE) {
            setBit(player, IMPACTS, ImpactType.FREEZE.ordinal());
        } else if (element == Element.METAL) {
            setBit(player, IMPACTS, ImpactType.DETECT_ORES.ordinal());
        } else if (element == Element.GLASS) {
            setBit(player, IMPACTS, ImpactType.PROTECT.ordinal());
        } else if (element == Element.PLASMA) {
            setBit(player, IMPACTS, ImpactType.DAMAGE.ordinal());
        } else if (element == Element.QUICK) {
            setBit(player, IMPACTS, ImpactType.PHYSICS.ordinal());
        } else if (element == Element.SCORCH) {
            setBit(player, IMPACTS, ImpactType.IGNITE.ordinal());
        } else if (element == Element.LAVA) {
            setBit(player, IMPACTS, ImpactType.TRANSMUTE_BLOCK.ordinal());
        } else if (element == Element.STORM) {
            setBit(player, IMPACTS, ImpactType.WEATHER_STORM.ordinal());
        } else if (element == Element.SPIRIT) {
            setBit(player, IMPACTS, ImpactType.DETECT_LIFE.ordinal());
        } else if (element == Element.UNDEAD) {
            setBit(player, IMPACTS, ImpactType.PACIFY_UNDEAD.ordinal());
        } else if (element == Element.SAND) {
            setBit(player, IMPACTS, ImpactType.PHYSICS.ordinal());
        } else if (element == Element.BLOOD) {
            setBit(player, IMPACTS, ImpactType.DRAIN_LIFE.ordinal());
        }
    }

    public static void recordSuccessfulCast(ServerPlayer player, CraftedSpell spell) {
        int casts = player.getPersistentData().getInt(CASTS) + 1;
        player.getPersistentData().putInt(CASTS, casts);
        EnumSet<Element> practiced = EnumSet.noneOf(Element.class);
        for (SpellInstruction instruction : spell.allInstructions())
            if (instruction.impact() != ImpactType.STUDY) practiced.add(instruction.element());
        for (Element element : practiced) addEnlightenment(player, element, 1.0);
        RelatedElementKnowledge.practice(player, practiced);
    }

    public static void studyElement(ServerPlayer player, Element element, double amount) {
        com.strutton.dynamicmagic.magic.ElementMastery.practice(player, element, Math.max(.25, amount * .35));
        if (ElementKnowledge.knows(player, element)) {
            addEnlightenment(player, element, Math.max(.25, amount));
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag studies = root.getCompound(STUDY).copy();
        double total = studies.getDouble(element.name()) + Math.max(.25, amount);
        studies.putDouble(element.name(), total);
        root.put(STUDY, studies);
        double required = studyRequirement(element);
        if (total + 1.0e-8 < required) return;
        ElementKnowledge.learn(player, element);
        studies.remove(element.name());
        root.put(STUDY, studies);
        player.displayClientMessage(Component.literal("Study breakthrough: you learned the "
                + element.displayName() + " element.").withStyle(ChatFormatting.AQUA), false);
    }

    public static double enlightenment(Player player, Element element) {
        return player.getPersistentData().getCompound(ENLIGHTENMENT).getDouble(element.name());
    }

    public static double breakthroughRequirement(Player player, Element element) {
        int breakthroughs = player.getPersistentData().getCompound(BREAKTHROUGHS).getInt(element.name());
        return BASE_BREAKTHROUGH_REQUIREMENT * (breakthroughs + 1L) * elementDifficulty(element);
    }

    /** Consuming an element grimoire supplies enough focused study for the next relevant breakthrough. */
    public static String forceBreakthrough(ServerPlayer player, Element element) {
        if (!ElementKnowledge.knows(player, element) || nextRelevantTechnique(player, element, false) == null)
            return null;
        double required = breakthroughRequirement(player, element);
        return addEnlightenment(player, element, Math.max(0, required - enlightenment(player, element)));
    }

    private static String addEnlightenment(ServerPlayer player, Element element, double amount) {
        if (!ElementKnowledge.knows(player, element) || amount <= 0) return null;
        CompoundTag root = player.getPersistentData();
        CompoundTag values = root.getCompound(ENLIGHTENMENT).copy();
        double total = values.getDouble(element.name()) + amount;
        double required = breakthroughRequirement(player, element);
        values.putDouble(element.name(), total);
        root.put(ENLIGHTENMENT, values);
        if (total + 1.0e-8 < required) return null;

        String learned = nextRelevantTechnique(player, element, true);
        if (learned == null) return null;
        values.putDouble(element.name(), Math.max(0, total - required));
        root.put(ENLIGHTENMENT, values);
        CompoundTag counts = root.getCompound(BREAKTHROUGHS).copy();
        counts.putInt(element.name(), counts.getInt(element.name()) + 1);
        root.put(BREAKTHROUGHS, counts);
        player.displayClientMessage(Component.literal(element.displayName() + " breakthrough: " + learned)
                .withStyle(ChatFormatting.GOLD), false);
        return learned;
    }

    public static void copy(Player original, Player replacement) {
        for (String key : new String[]{STARTED, FORMS, DELIVERIES, IMPACTS, DIRECTIONS, CONDITIONS, CASTS,
                ENLIGHTENMENT, BREAKTHROUGHS, STUDY})
            if (original.getPersistentData().contains(key)) replacement.getPersistentData().put(key, original.getPersistentData().get(key).copy());
        ProgrammingKnowledge.copy(original, replacement);
    }

    public static void reset(Player player) {
        for (String key : new String[]{STARTED, FORMS, DELIVERIES, IMPACTS, DIRECTIONS, CONDITIONS, CASTS,
                ENLIGHTENMENT, BREAKTHROUGHS, STUDY})
            player.getPersistentData().remove(key);
        ProgrammingKnowledge.reset(player);
    }

    private static String nextRelevantTechnique(Player player, Element element, boolean unlock) {
        Object[] candidates = switch (element) {
            case FIRE -> new Object[]{ImpactType.IGNITE, ImpactType.EXPLODE, ConditionType.BURNING,
                    ImpactType.TRANSMUTE_BLOCK, SpellForm.BEAM, SpellForm.BURST, DeliveryType.CONTINUOUS, DeliveryType.TOUCH,
                    CastDirection.FORWARD, ImpactType.PROTECT, ImpactType.CONJURE_ITEM};
            case WATER -> new Object[]{ImpactType.EXTINGUISH, ImpactType.PHYSICS, ConditionType.IN_WATER,
                    ImpactType.TRANSMUTE_BLOCK, ImpactType.WEATHER_RAIN,
                    SpellForm.BURST, SpellForm.BEAM, DeliveryType.SELF, DeliveryType.CONTINUOUS,
                    ImpactType.PROTECT, ImpactType.CONJURE_ITEM};
            case AIR -> new Object[]{ImpactType.PHYSICS, CastDirection.UP, CastDirection.DOWN,
                    DeliveryType.SELF, DeliveryType.CONTINUOUS, SpellForm.BURST, ConditionType.FALLING,
                    ConditionType.ON_GROUND, CastDirection.FORWARD, CastDirection.BACKWARD,
                    SpellForm.SHIELD, ImpactType.PROTECT, ImpactType.CONJURE_ITEM};
            case EARTH -> new Object[]{ImpactType.ROOT, ImpactType.PHYSICS, SpellForm.SHIELD,
                    ImpactType.PROTECT, SpellForm.WEAPON, SpellForm.BURST, DeliveryType.TOUCH,
                    ConditionType.ON_GROUND, ImpactType.CONJURE_ITEM};
            case ICE -> new Object[]{ImpactType.FREEZE, SpellForm.WEAPON, SpellForm.SHIELD,
                    ImpactType.TRANSMUTE_BLOCK, ImpactType.PROTECT, DeliveryType.TOUCH, SpellForm.BURST, ImpactType.CONJURE_ITEM};
            case LIGHTNING -> new Object[]{ImpactType.CHAIN, SpellForm.BEAM, SpellForm.BURST,
                    ImpactType.SUMMON_LIGHTNING, DeliveryType.CONTINUOUS, ImpactType.PHYSICS, ImpactType.CONJURE_ITEM};
            case LIGHT -> new Object[]{ImpactType.ILLUMINATE, SpellForm.BEAM, SpellForm.SHIELD,
                    ImpactType.PROTECT, DeliveryType.CONTINUOUS, ImpactType.CONJURE_ITEM};
            case SHADOW -> new Object[]{ImpactType.BLIND, SpellForm.BEAM, SpellForm.BURST,
                    DeliveryType.CONTINUOUS, ImpactType.CONJURE_ITEM};
            case ARCANE -> new Object[]{ImpactType.DISPEL, SpellForm.SHIELD, ImpactType.PROTECT,
                    SpellForm.BEAM, DeliveryType.TOUCH, ImpactType.CONJURE_ITEM};
            case SPACE -> new Object[]{ImpactType.TELEPORT, ImpactType.STORE_ITEM, ImpactType.STORE_ENTITY,
                    ImpactType.RELEASE_STORAGE, ImpactType.CONTRACT_ENTITY, ImpactType.SUMMON_CONTRACT,
                    SpellForm.BURST, DeliveryType.SELF, DeliveryType.CONTINUOUS};
            case TIME -> new Object[]{DeliveryType.CONTINUOUS, ImpactType.SLOW_TIME, ImpactType.SPEED_TIME,
                    ImpactType.STOP_TIME, SpellForm.BURST, SpellForm.SHIELD, DeliveryType.SELF};
            case DIVINE -> new Object[]{ImpactType.HEAL, DeliveryType.SELF, ImpactType.CLEANSE,
                    ConditionType.HEALTH_BELOW_HALF, ConditionType.POISONED, ImpactType.RESURRECT, ImpactType.PROTECT,
                    SpellForm.BURST, DeliveryType.CONTINUOUS, ConditionType.HEALTH_BELOW_QUARTER};
            case METAL -> new Object[]{ImpactType.DETECT_ORES, SpellForm.BEAM, SpellForm.SHIELD,
                    ImpactType.PROTECT, ImpactType.PHYSICS, ImpactType.CONJURE_ITEM};
            case GLASS -> new Object[]{ImpactType.PROTECT, SpellForm.SHIELD, SpellForm.WEAPON,
                    ImpactType.PHYSICS, ImpactType.CONJURE_ITEM};
            case PLASMA -> new Object[]{SpellForm.BEAM, ImpactType.IGNITE, ImpactType.EXPLODE,
                    DeliveryType.CONTINUOUS, ImpactType.SUMMON_LIGHTNING};
            case QUICK -> new Object[]{ImpactType.PHYSICS, DeliveryType.CONTINUOUS, CastDirection.FORWARD,
                    CastDirection.BACKWARD, SpellForm.BURST};
            case SCORCH -> new Object[]{ImpactType.IGNITE, ImpactType.EXPLODE, ConditionType.BURNING,
                    SpellForm.BEAM, DeliveryType.CONTINUOUS};
            case LAVA -> new Object[]{ImpactType.TRANSMUTE_BLOCK, ImpactType.IGNITE, ImpactType.EXPLODE,
                    SpellForm.BURST, DeliveryType.CONTINUOUS};
            case STORM -> new Object[]{ImpactType.WEATHER_STORM, ImpactType.SUMMON_LIGHTNING,
                    ImpactType.CHAIN, SpellForm.BURST, DeliveryType.CONTINUOUS};
            case SPIRIT -> new Object[]{ImpactType.DETECT_LIFE, ImpactType.FERTILITY,
                    ImpactType.ASTRAL_PROJECTION, DeliveryType.CONTINUOUS, DeliveryType.SELF, SpellForm.BURST};
            case UNDEAD -> new Object[]{ImpactType.PACIFY_UNDEAD, ImpactType.DETECT_UNDEAD,
                    ImpactType.CORRUPT_LIFE, DeliveryType.CONTINUOUS, DeliveryType.SELF, SpellForm.BURST};
            case SAND -> new Object[]{ImpactType.PHYSICS, ImpactType.BLIND, SpellForm.WALL,
                    SpellForm.BURST, ImpactType.PROTECT, ImpactType.CONJURE_ITEM};
            case BLOOD -> new Object[]{ImpactType.DRAIN_LIFE, ImpactType.HEAL,
                    ImpactType.CONVERT_HEALTH_TO_MANA, ImpactType.APPLY_EFFECT, SpellForm.CLOAK,
                    DeliveryType.CONTINUOUS, ImpactType.PHYSICS, ImpactType.CONJURE_ITEM};
            case KI -> new Object[]{ImpactType.PHYSICS, ImpactType.PROTECT, ImpactType.DAMAGE,
                    SpellForm.BEAM, SpellForm.BURST, DeliveryType.TOUCH, DeliveryType.CONTINUOUS};
        };
        for (Object candidate : candidates) {
            String learned = technique(player, candidate, unlock);
            if (learned != null) return learned;
        }
        return null;
    }

    private static String technique(Player player, Object value, boolean unlock) {
        if (value instanceof SpellForm v) return technique(player, FORMS, v.ordinal(), v.displayName(), unlock);
        if (value instanceof DeliveryType v) return technique(player, DELIVERIES, v.ordinal(), v.displayName(), unlock);
        if (value instanceof ImpactType v) return technique(player, IMPACTS, v.ordinal(), v.displayName(), unlock);
        if (value instanceof CastDirection v) return technique(player, DIRECTIONS, v.ordinal(), v.displayName(), unlock);
        if (value instanceof ConditionType v) return technique(player, CONDITIONS, v.ordinal(), v.displayName(), unlock);
        return null;
    }

    private static String technique(Player player, String key, int ordinal, String displayName, boolean unlock) {
        if ((mask(player, key) & (1L << ordinal)) != 0) return null;
        if (unlock) setBit(player, key, ordinal);
        return displayName;
    }

    private static long mask(Player player, String key) { return player.getPersistentData().getLong(key); }
    private static double elementDifficulty(Element element) {
        return switch (element) {
            case FIRE, WATER, AIR, EARTH, LIGHTNING -> 1.0;
            case ICE, METAL, GLASS, QUICK, SCORCH, LAVA, SAND -> 1.5;
            case LIGHT, SHADOW, ARCANE, PLASMA, STORM -> 2.0;
            case DIVINE, SPIRIT, UNDEAD, BLOOD -> 2.5;
            case SPACE, TIME -> 3.0;
            case KI -> 2.5;
        };
    }
    private static double studyRequirement(Element element) {
        return 80.0 * elementDifficulty(element);
    }
    private static void setBit(Player player, String key, int ordinal) { player.getPersistentData().putLong(key, mask(player, key) | (1L << ordinal)); }
    private static long all(int count) { return count >= 64 ? -1L : (1L << count) - 1; }
}
