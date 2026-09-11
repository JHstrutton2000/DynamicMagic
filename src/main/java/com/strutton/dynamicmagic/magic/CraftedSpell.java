package com.strutton.dynamicmagic.magic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The complete, per-ItemStack recipe produced by the spellcrafting screen. */
public record CraftedSpell(
        String name,
        SourceType source,
        Element element,
        SpellForm form,
        DeliveryType delivery,
        ImpactType impact,
        double power,
        CastDirection direction,
        boolean programmed,
        ConditionType condition,
        int intervalTicks,
        double detectionRange,
        TargetMode targetMode,
        ProgramTargetMode programTargetMode,
        PhysicsOperation physicsOperation,
        List<SpellInstruction> instructions,
        List<SpellBranch> branches) {

    private static final String ROOT_KEY = "DynamicSpell";

    public CraftedSpell {
        name = name == null || name.isBlank() ? "Unnamed Spell" : name.strip().substring(0, Math.min(32, name.strip().length()));
        power = Double.isFinite(power) ? Math.max(0.5, Math.min(10.0, power)) : 1.0;
        direction = direction == null ? CastDirection.LOOK : direction;
        condition = condition == null ? ConditionType.ALWAYS : condition;
        intervalTicks = Math.max(2, Math.min(200, intervalTicks));
        detectionRange = Double.isFinite(detectionRange) ? Math.max(1, Math.min(32, detectionRange)) : 8;
        targetMode = targetMode == null ? TargetMode.AIM : targetMode;
        programTargetMode = programTargetMode == null ? ProgramTargetMode.CASTER_AIM : programTargetMode;
        physicsOperation = physicsOperation == null ? PhysicsOperation.ADD_VELOCITY : physicsOperation;
        if (instructions == null || instructions.isEmpty()) {
            instructions = List.of(SpellInstruction.legacy(element, impact, power, direction, targetMode,
                    physicsOperation, form, delivery));
        } else instructions = List.copyOf(instructions.subList(0, Math.min(8, instructions.size())));
        if (branches == null) branches = List.of();
        else branches = List.copyOf(branches.subList(0, Math.min(8, branches.size())));
        if (programmed && branches.isEmpty())
            branches = List.of(new SpellBranch(condition, intervalTicks, detectionRange, programTargetMode, instructions));
        if (programmed) {
            SpellBranch primaryBranch = branches.get(0);
            condition = primaryBranch.condition();
            intervalTicks = primaryBranch.intervalTicks();
            detectionRange = primaryBranch.detectionRange();
            programTargetMode = primaryBranch.targetMode();
            instructions = primaryBranch.instructions();
        }
        SpellInstruction primary = instructions.get(0);
        element = primary.element();
        impact = primary.impact();
        power = primary.power();
        direction = primary.direction();
        targetMode = primary.targetMode();
        physicsOperation = primary.physicsOperation();
    }

    public CraftedSpell(String name, SourceType source, Element element, SpellForm form,
                        DeliveryType delivery, ImpactType impact, double power, CastDirection direction,
                        boolean programmed, ConditionType condition, int intervalTicks, double detectionRange,
                        TargetMode targetMode, ProgramTargetMode programTargetMode, PhysicsOperation physicsOperation) {
        this(name, source, element, form, delivery, impact, power, direction, programmed, condition,
                intervalTicks, detectionRange, targetMode, programTargetMode, physicsOperation, List.of(), List.of());
    }

    public CraftedSpell(String name, SourceType source, Element element, SpellForm form,
                        DeliveryType delivery, ImpactType impact, double power, CastDirection direction,
                        boolean programmed, ConditionType condition, int intervalTicks, double detectionRange,
                        TargetMode targetMode, ProgramTargetMode programTargetMode, PhysicsOperation physicsOperation,
                        List<SpellInstruction> instructions) {
        this(name, source, element, form, delivery, impact, power, direction, programmed, condition,
                intervalTicks, detectionRange, targetMode, programTargetMode, physicsOperation, instructions, List.of());
    }

    public CraftedSpell(String name, SourceType source, Element element, SpellForm form,
                        DeliveryType delivery, ImpactType impact, double power) {
        this(name, source, element, form, delivery, impact, power,
                CastDirection.LOOK, false, ConditionType.ALWAYS, 10, 8, TargetMode.AIM,
                ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY, List.of(), List.of());
    }

    public SpellDefinition definition() {
        List<SpellEffect> effects = new ArrayList<>();
        List<SpellInstruction> operations = allInstructions().stream()
                .filter(instruction -> instruction.impact() != ImpactType.STUDY).toList();
        if (operations.isEmpty())
            return new SpellDefinition(name, List.of(new SpellEffect(EffectType.CREATE, element, 0, 0)));
        double totalForce = operations.stream().mapToDouble(instruction -> instruction.power() * instruction.repetitions()).sum();
        for (SpellInstruction instruction : operations)
            effects.add(new SpellEffect(source == SourceType.SUMMON ? EffectType.SUMMON_ELEMENT : EffectType.CREATE,
                    instruction.element(), instruction.power() * instruction.repetitions()
                    * (source == SourceType.SUMMON ? 2.5 : 3.5), 0.25));
        Element costingElement = operations.get(0).element();
        effects.add(new SpellEffect(EffectType.SHAPE, costingElement, totalForce * formMagnitude(), formComplexity()));
        if (form == SpellForm.SHIELD || form == SpellForm.WEAPON) {
            effects.add(new SpellEffect(EffectType.CONSTRUCT, element, totalForce * 2.5, 0.55));
            effects.add(new SpellEffect(EffectType.MAINTAIN, element, totalForce, 0.25));
        }
        effects.add(new SpellEffect(delivery == DeliveryType.CONTINUOUS ? EffectType.REPEAT : EffectType.PROJECT,
                costingElement, totalForce * deliveryMagnitude(), deliveryComplexity()));
        for (SpellInstruction instruction : operations) {
            double spatialLoad = 1 + instruction.range() / 40.0 + instruction.radius() / 8.0;
            double temporalLoad = 1 + instruction.durationSeconds() / 20.0;
            double repeatLoad = Math.pow(instruction.repetitions(), 1.18);
            effects.add(new SpellEffect(EffectType.IMPACT, instruction.element(),
                    instruction.power() * impactMagnitude(instruction.impact()) * spatialLoad * temporalLoad * repeatLoad,
                    impactComplexity(instruction.impact()) + .08 * (instruction.repetitions() - 1)));
            if (instruction.impact() == ImpactType.APPLY_EFFECT) {
                effects.add(new SpellEffect(EffectType.MAINTAIN, instruction.element(),
                        instruction.power() * Math.max(1, instruction.durationSeconds() / 5.0), .55));
            }
            if (isTimeImpact(instruction.impact())) {
                double timeLoad = switch (instruction.impact()) {
                    case STOP_TIME -> 4.5;
                    case SPEED_TIME -> 2.75;
                    case SLOW_TIME -> 2.0;
                    default -> 0;
                };
                effects.add(new SpellEffect(EffectType.MAINTAIN, Element.TIME, instruction.power() * timeLoad, 1.0));
            }
        }
        if (programmed) {
            for (SpellBranch branch : branches) {
                effects.add(new SpellEffect(EffectType.DETECT, Element.ARCANE, Math.max(1, branch.detectionRange() / 4), .55));
                effects.add(new SpellEffect(EffectType.CONDITIONAL, Element.ARCANE, 2, .65));
                effects.add(new SpellEffect(EffectType.PROGRAM, Element.ARCANE, Math.max(1, 20.0 / branch.intervalTicks()), .8));
                for (SpellInstruction instruction : branch.instructions())
                    if (instruction.impact() != ImpactType.STUDY)
                        effects.add(new SpellEffect(EffectType.MAINTAIN, instruction.element(),
                                Math.max(1, instruction.power() * instruction.repetitions() * .5), .35));
            }
        }
        return new SpellDefinition(name, effects);
    }

    public boolean isStudyOnly() {
        return allInstructions().stream().allMatch(instruction -> instruction.impact() == ImpactType.STUDY);
    }

    public void writeTo(ItemStack stack) {
        CompoundTag spell = toTag();
        CompoundTag root = new CompoundTag();
        root.put(ROOT_KEY, spell);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
    }

    public CompoundTag toTag() {
        CompoundTag spell = new CompoundTag();
        spell.putString("Name", name);
        spell.putString("Source", source.name());
        spell.putString("Element", element.name());
        spell.putString("Form", form.name());
        spell.putString("Delivery", delivery.name());
        spell.putString("Impact", impact.name());
        spell.putDouble("Power", power);
        spell.putString("Direction", direction.name());
        spell.putBoolean("Programmed", programmed);
        spell.putString("Condition", condition.name());
        spell.putInt("Interval", intervalTicks);
        spell.putDouble("DetectionRange", detectionRange);
        spell.putString("TargetMode", targetMode.name());
        spell.putString("ProgramTargetMode", programTargetMode.name());
        spell.putString("PhysicsOperation", physicsOperation.name());
        ListTag instructionTags = new ListTag();
        for (SpellInstruction instruction : instructions) instructionTags.add(instruction.toTag());
        spell.put("Instructions", instructionTags);
        ListTag branchTags = new ListTag();
        for (SpellBranch branch : branches) branchTags.add(branch.toTag());
        spell.put("Branches", branchTags);
        return spell;
    }

    public static CraftedSpell read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag root = data.copyTag();
        if (!root.contains(ROOT_KEY)) return null;
        return fromTag(root.getCompound(ROOT_KEY));
    }

    public static CraftedSpell fromTag(CompoundTag spell) {
        if (spell == null) return null;
        try {
            List<SpellInstruction> instructions = new ArrayList<>();
            if (spell.contains("Instructions", Tag.TAG_LIST)) {
                ListTag tags = spell.getList("Instructions", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(8, tags.size()); i++) {
                    SpellInstruction instruction = SpellInstruction.fromTag(tags.getCompound(i));
                    if (instruction != null) instructions.add(instruction);
                }
            }
            List<SpellBranch> branches = new ArrayList<>();
            if (spell.contains("Branches", Tag.TAG_LIST)) {
                ListTag tags = spell.getList("Branches", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(8, tags.size()); i++) {
                    SpellBranch branch = SpellBranch.fromTag(tags.getCompound(i));
                    if (branch != null) branches.add(branch);
                }
            }
            return migrateLegacyLift(new CraftedSpell(
                    spell.getString("Name"),
                    SourceType.valueOf(spell.getString("Source").toUpperCase(Locale.ROOT)),
                    Element.valueOf(spell.getString("Element").toUpperCase(Locale.ROOT)),
                    SpellForm.valueOf(spell.getString("Form").toUpperCase(Locale.ROOT)),
                    DeliveryType.valueOf(spell.getString("Delivery").toUpperCase(Locale.ROOT)),
                    parseImpact(spell.getString("Impact")),
                    spell.getDouble("Power"),
                    spell.contains("Direction") ? CastDirection.valueOf(spell.getString("Direction")) : CastDirection.LOOK,
                    spell.getBoolean("Programmed"),
                    spell.contains("Condition") ? ConditionType.valueOf(spell.getString("Condition")) : ConditionType.ALWAYS,
                    spell.contains("Interval") ? spell.getInt("Interval") : 10,
                    spell.contains("DetectionRange") ? spell.getDouble("DetectionRange") : 8,
                    spell.contains("TargetMode") ? TargetMode.valueOf(spell.getString("TargetMode")) : TargetMode.AIM,
                    spell.contains("ProgramTargetMode") ? ProgramTargetMode.valueOf(spell.getString("ProgramTargetMode")) : ProgramTargetMode.CASTER_AIM,
                    spell.contains("PhysicsOperation") ? PhysicsOperation.valueOf(spell.getString("PhysicsOperation")) : PhysicsOperation.ADD_VELOCITY,
                    instructions, branches));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static ImpactType parseImpact(String value) {
        if ("KNOCKBACK".equalsIgnoreCase(value)) return ImpactType.PHYSICS;
        return ImpactType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** v0.4 Wind Lift used DOWN to represent recoil; direction is literal in the generic physics engine now. */
    private static CraftedSpell migrateLegacyLift(CraftedSpell spell) {
        boolean legacyName = spell.name().equalsIgnoreCase("Wind Lift") || spell.name().equalsIgnoreCase("Float");
        if (!legacyName || spell.programmed() || spell.delivery() != DeliveryType.CONTINUOUS) return spell;
        List<SpellInstruction> migrated = spell.instructions().stream().map(instruction ->
                instruction.element() == Element.AIR && instruction.impact() == ImpactType.PHYSICS
                        && instruction.targetMode() == TargetMode.SELF
                        && instruction.physicsOperation() == PhysicsOperation.ADD_VELOCITY
                        && instruction.direction() == CastDirection.DOWN
                        ? instruction.withDirection(CastDirection.UP) : instruction).toList();
        if (migrated.equals(spell.instructions())) return spell;
        SpellInstruction first = migrated.get(0);
        return new CraftedSpell(spell.name(), spell.source(), first.element(), spell.form(), spell.delivery(),
                first.impact(), first.power(), first.direction(), false, spell.condition(), spell.intervalTicks(),
                spell.detectionRange(), first.targetMode(), spell.programTargetMode(), first.physicsOperation(), migrated);
    }

    private double formMagnitude() { return switch (form) { case BOLT -> 1.5; case BEAM -> 2; case BURST -> 2.5; case SHIELD -> 3; case WEAPON -> 2.25; case RUNE -> 3; case WALL -> 3.5; case CLOAK -> 3; }; }
    private double formComplexity() { return switch (form) { case BOLT -> .2; case BEAM -> .35; case BURST -> .3; case SHIELD -> .5; case WEAPON -> .45; case RUNE -> .7; case WALL -> .75; case CLOAK -> .65; }; }
    private double deliveryMagnitude() { return switch (delivery) { case PROJECTILE -> 2; case TOUCH -> .75; case SELF -> 1; case CONTINUOUS -> 2.75; }; }
    private double deliveryComplexity() { return delivery == DeliveryType.CONTINUOUS ? .6 : .3; }
    private static double impactMagnitude(ImpactType impact) { return switch (impact) {
        case DAMAGE -> 2; case EXPLODE -> 3.5; case IGNITE -> 2.25; case PHYSICS -> 2;
        case FREEZE -> 2.5; case PROTECT -> 3;
        case STORE_ITEM -> 2; case STORE_ENTITY -> 4; case RELEASE_STORAGE -> 1.5;
        case STOP_TIME -> 8; case SPEED_TIME, SLOW_TIME -> 5;
        case HEAL -> 3; case CLEANSE -> 2.25; case EXTINGUISH, ILLUMINATE -> 1.5; case ROOT, BLIND -> 2.5;
        case CHAIN, TELEPORT -> 4; case CONTRACT_ENTITY -> 6; case SUMMON_CONTRACT -> 4; case CONJURE_ITEM -> 3; case DISPEL -> 2.5;
        case TRANSMUTE_BLOCK -> 3; case WEATHER_RAIN -> 4; case WEATHER_STORM -> 6;
        case SUMMON_LIGHTNING -> 5; case DETECT_ORES -> 2.5; case STUDY -> 1;
        case RESURRECT -> 7; case ASTRAL_PROJECTION -> 8; case DETECT_LIFE, DETECT_UNDEAD -> 2.5;
        case FERTILITY, PACIFY_UNDEAD -> 3; case CORRUPT_LIFE -> 6;
        case APPLY_EFFECT -> 3; case DRAIN_LIFE -> 4; case TURN_UNDEAD -> 3.5;
        case REANIMATE, SUMMON_CREATURE -> 6; case SOUL_TRAP -> 4;
        case MIND_CALM, MIND_FEAR, MIND_FRENZY -> 3.5;
        case CONVERT_HEALTH_TO_MANA -> 3;
    }; }
    private static double impactComplexity(ImpactType impact) { return switch (impact) {
        case EXPLODE, PROTECT -> .55;
        case FREEZE, STORE_ITEM -> .45;
        case STORE_ENTITY, RELEASE_STORAGE -> .7;
        case STOP_TIME -> 1.2;
        case SPEED_TIME, SLOW_TIME -> .9;
        case HEAL, CLEANSE, ROOT, BLIND -> .55;
        case CHAIN, TELEPORT -> .8;
        case CONTRACT_ENTITY, SUMMON_CONTRACT -> 1.0;
        case CONJURE_ITEM -> .75;
        case DISPEL -> .6;
        case TRANSMUTE_BLOCK, DETECT_ORES -> .65;
        case WEATHER_RAIN, DETECT_LIFE, DETECT_UNDEAD -> .75;
        case WEATHER_STORM, SUMMON_LIGHTNING, FERTILITY, PACIFY_UNDEAD -> .9;
        case RESURRECT, CORRUPT_LIFE -> 1.15;
        case ASTRAL_PROJECTION -> 1.4;
        case APPLY_EFFECT, DRAIN_LIFE, TURN_UNDEAD, SOUL_TRAP -> .8;
        case REANIMATE, SUMMON_CREATURE -> 1.1;
        case MIND_CALM, MIND_FEAR, MIND_FRENZY -> .9;
        case CONVERT_HEALTH_TO_MANA -> .9;
        case STUDY -> .2;
        default -> .3;
    }; }

    public List<SpellInstruction> allInstructions() {
        return programmed ? branches.stream().flatMap(branch -> branch.instructions().stream()).toList() : instructions;
    }
    public boolean hasTimeMagic() { return allInstructions().stream().anyMatch(instruction -> isTimeImpact(instruction.impact())); }
    public boolean hasSustainedMagic() { return allInstructions().stream().anyMatch(instruction -> isSustainedImpact(instruction.impact())); }
    public SpellInstruction timeInstruction() { return allInstructions().stream().filter(instruction -> isTimeImpact(instruction.impact())).findFirst().orElse(null); }
    public boolean usesElement(Element value) { return allInstructions().stream().anyMatch(instruction -> instruction.element() == value); }
    public CraftedSpell mapInstructions(java.util.function.UnaryOperator<SpellInstruction> mapper) {
        List<SpellInstruction> mapped = instructions.stream().map(mapper).toList();
        List<SpellBranch> mappedBranches = branches.stream().map(branch -> new SpellBranch(branch.condition(),
                branch.intervalTicks(), branch.detectionRange(), branch.targetMode(),
                branch.instructions().stream().map(mapper).toList())).toList();
        SpellInstruction first = mapped.get(0);
        return new CraftedSpell(name, source, first.element(), form, delivery, first.impact(), first.power(),
                first.direction(), programmed, condition, intervalTicks, detectionRange, first.targetMode(),
                programTargetMode, first.physicsOperation(), mapped, mappedBranches);
    }
    public CraftedSpell withOnlyInstruction(SpellInstruction instruction) {
        return new CraftedSpell(name, source, instruction.element(), form, delivery, instruction.impact(), instruction.power(),
                instruction.direction(), false, ConditionType.ALWAYS, intervalTicks, detectionRange, instruction.targetMode(),
                ProgramTargetMode.CASTER_AIM, instruction.physicsOperation(), List.of(instruction), List.of());
    }
    public CraftedSpell branchSpell(SpellBranch branch) {
        SpellInstruction first = branch.instructions().get(0);
        return new CraftedSpell(name, source, first.element(), form, delivery, first.impact(), first.power(),
                first.direction(), false, ConditionType.ALWAYS, branch.intervalTicks(), branch.detectionRange(),
                first.targetMode(), branch.targetMode(), first.physicsOperation(), branch.instructions(), List.of());
    }
    private static boolean isTimeImpact(ImpactType value) {
        return value == ImpactType.STOP_TIME || value == ImpactType.SPEED_TIME || value == ImpactType.SLOW_TIME;
    }
    private static boolean isSustainedImpact(ImpactType value) {
        return value == ImpactType.WEATHER_RAIN || value == ImpactType.WEATHER_STORM
                || value == ImpactType.ASTRAL_PROJECTION;
    }
}
