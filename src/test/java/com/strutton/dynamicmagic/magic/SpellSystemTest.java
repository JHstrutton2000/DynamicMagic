package com.strutton.dynamicmagic.magic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.strutton.dynamicmagic.knowledge.KnowledgeSnapshot;

/** Fast deterministic tests that do not directly invoke Minecraft runtime classes. */
class SpellSystemTest {
    private static final CasterStats CASTER = new CasterStats(100, 1);

    @Test void adventRunesMapOnlyToTheirRelevantElement() {
        var fire = com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.runeAttunement(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aoa3", "fire_rune"));
        var water = com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.runeAttunement(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aoa3", "water_rune"));
        assertNotNull(fire);
        assertNotNull(water);
        assertEquals(Element.FIRE, fire.element());
        assertEquals(Element.WATER, water.element());
        assertNull(com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.runeAttunement(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "fire_rune")));
        assertNull(com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.runeAttunement(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aoa3", "unpowered_rune")));
    }

    @Test void adventStaffManaCostScalesAndElementFallbacksStayThematic() {
        double cheap = com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.staffManaCost(1, 2);
        double expensive = com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.staffManaCost(6, 18);
        assertTrue(expensive > cheap);
        assertTrue(expensive <= 40);
        assertEquals(Element.WATER,
                com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.fallbackStaffElement("aquatic_staff"));
        assertEquals(Element.LIGHTNING,
                com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.fallbackStaffElement("striker_staff"));
        assertEquals(Element.ARCANE,
                com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.fallbackStaffElement("wizards_staff"));
    }

    @Test void timeManipulationCostsFarMoreThanABasicFireBolt() {
        CraftedSpell fire = new CraftedSpell("Fire", SourceType.CREATE, Element.FIRE,
                SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.DAMAGE, 1);
        CraftedSpell time = new CraftedSpell("Time", SourceType.CREATE, Element.TIME,
                SpellForm.BURST, DeliveryType.CONTINUOUS, ImpactType.STOP_TIME, 1);
        SpellCost fireCost = SpellCostCalculator.calculate(fire.definition(), CASTER);
        SpellCost timeCost = SpellCostCalculator.calculate(time.definition(), CASTER);
        assertTrue(timeCost.initialCost() > fireCost.initialCost() * 3,
                "global time control must not be priced like a projectile");
        assertTrue(timeCost.maintenancePerSecond() > fireCost.maintenancePerSecond());
    }

    @Test void addingBranchesAndOperationsRaisesProgramUpkeep() {
        SpellInstruction heal = operation(Element.DIVINE, ImpactType.HEAL, TargetMode.SELF);
        CraftedSpell one = program(List.of(new SpellBranch(ConditionType.HEALTH_BELOW_HALF, 20, 4,
                ProgramTargetMode.CASTER_AIM, List.of(heal))));
        CraftedSpell three = program(List.of(
                new SpellBranch(ConditionType.HEALTH_BELOW_HALF, 20, 4, ProgramTargetMode.CASTER_AIM, List.of(heal)),
                new SpellBranch(ConditionType.POISONED, 10, 4, ProgramTargetMode.CASTER_AIM, List.of(heal)),
                new SpellBranch(ConditionType.FALLING, 5, 4, ProgramTargetMode.CASTER_AIM,
                        List.of(operation(Element.AIR, ImpactType.PHYSICS, TargetMode.SELF)))));
        assertTrue(SpellCostCalculator.calculate(three.definition(), CASTER).maintenancePerSecond()
                > SpellCostCalculator.calculate(one.definition(), CASTER).maintenancePerSecond() * 2);
    }

    @Test void advancedProgramTargetingIsMasteryGated() {
        KnowledgeSnapshot novice = new KnowledgeSnapshot(-1, -1, -1, -1, -1, -1, true, 0, 1);
        KnowledgeSnapshot intermediate = new KnowledgeSnapshot(-1, -1, -1, -1, -1, -1, true, 24, 3);
        assertTrue(novice.knows(ProgramTargetMode.CASTER_AIM));
        assertFalse(novice.knows(ProgramTargetMode.DIRECTION_TO_DETECTED));
        assertFalse(novice.knows(ProgramTargetMode.DETECTED_ENTITY));
        assertTrue(intermediate.knows(ProgramTargetMode.DIRECTION_TO_DETECTED));
        assertTrue(intermediate.knows(ProgramTargetMode.DETECTED_ENTITY));
    }

    @Test void potionEffectsCarryTheirElementAndMaintenanceCost() {
        for (PotionEffectType effect : PotionEffectType.values()) assertNotNull(effect.element());
        SpellInstruction effect = new SpellInstruction(Element.QUICK, ImpactType.APPLY_EFFECT, 2,
                CastDirection.LOOK, TargetMode.SELF, PhysicsOperation.ADD_VELOCITY, 8, 0, 20, 1,
                PotionEffectType.SPEED);
        CraftedSpell spell = new CraftedSpell("Haste", SourceType.CREATE, Element.QUICK, SpellForm.CLOAK,
                DeliveryType.SELF, ImpactType.APPLY_EFFECT, 2, CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 8, TargetMode.SELF, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, List.of(effect));
        assertTrue(SpellCostCalculator.calculate(spell.definition(), CASTER).maintenancePerSecond() > 0);
        assertEquals(PotionEffectType.SPEED, SpellInstruction.fromTag(effect.toTag()).potionEffect());
    }

    @Test void naturalAdvancedElementsAndSkyrimFormsAreCosted() {
        assertDoesNotThrow(() -> {
            for (Element element : List.of(Element.SAND, Element.GLASS, Element.LAVA, Element.BLOOD))
                for (SpellForm form : List.of(SpellForm.RUNE, SpellForm.WALL, SpellForm.CLOAK))
                    SpellCostCalculator.calculate(new CraftedSpell("Grammar", SourceType.CREATE, element,
                            form, DeliveryType.PROJECTILE, ImpactType.DAMAGE, 1).definition(), CASTER);
        });
    }

    @Test void powerfulMorphsHaveHigherManaUpkeep() {
        double passive = com.strutton.dynamicmagic.morph.MorphManaCost.calculate(10, .9, .9,
                false, false, false);
        double hostile = com.strutton.dynamicmagic.morph.MorphManaCost.calculate(20, .6, 1.95,
                true, false, false);
        double flyingBoss = com.strutton.dynamicmagic.morph.MorphManaCost.calculate(200, 16, 8,
                true, true, true);
        assertTrue(hostile > passive);
        assertTrue(flyingBoss > hostile * 4);
    }

    @Test void studyMagicIsAlwaysFree() {
        CraftedSpell study = new CraftedSpell("Study Element", SourceType.CREATE, Element.ARCANE,
                SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.STUDY, 10);
        SpellCost cost = SpellCostCalculator.calculate(study.definition(), CASTER);
        assertTrue(study.isStudyOnly());
        assertEquals(0, cost.formation());
        assertEquals(0, cost.maintenancePerSecond());
        assertEquals(0, cost.release());
        assertEquals(0, cost.instability());
    }

    @Test void vampireCureOddsRewardSunlightAndBloodHunger() {
        assertEquals(0, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(0, 0));
        assertEquals(.25, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(0, 1));
        assertEquals(.75, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(6_000, 1));
        assertEquals(1, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(12_000, 0));
        assertEquals(.14, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(0, 0, true, true));
        assertEquals(.64, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(6_000, 0, true, true));
        assertTrue(com.strutton.dynamicmagic.vampire.VampireCureTreatment.shadePenalty(100) < .001,
                "A few seconds outside direct sunlight should have negligible effect");
        assertEquals(.5, com.strutton.dynamicmagic.vampire.VampireCureTreatment.shadePenalty(1_200));
        assertEquals(1, com.strutton.dynamicmagic.vampire.VampireCureTreatment.shadePenalty(1_600));
        assertEquals(.25, com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(
                6_000, 1_200, 1, false, false));
        assertEquals(13_000, com.strutton.dynamicmagic.vampire.VampireCureTreatment.ticksUntilNextNight(0));
        assertEquals(7_000, com.strutton.dynamicmagic.vampire.VampireCureTreatment.ticksUntilNextNight(6_000));
        assertEquals(24_000, com.strutton.dynamicmagic.vampire.VampireCureTreatment.ticksUntilNextNight(13_000));
    }

    @Test void manaBrewingCountdownFormatsSecondsAndMinutes() {
        assertEquals("0s", com.strutton.dynamicmagic.mana.ManaBrewing.formatDuration(0));
        assertEquals("59s", com.strutton.dynamicmagic.mana.ManaBrewing.formatDuration(59));
        assertEquals("1:00", com.strutton.dynamicmagic.mana.ManaBrewing.formatDuration(60));
        assertEquals("2:05", com.strutton.dynamicmagic.mana.ManaBrewing.formatDuration(125));
    }

    @Test void apothicSpawnerAndAttributeScalingStayBounded() {
        assertEquals(2, com.strutton.dynamicmagic.compat.ApotheosisIntegration.spawnManaCost(1));
        assertEquals(4, com.strutton.dynamicmagic.compat.ApotheosisIntegration.spawnManaCost(20));
        assertEquals(40, com.strutton.dynamicmagic.compat.ApotheosisIntegration.spawnManaCost(200));
        assertEquals(1.25, com.strutton.dynamicmagic.compat.ApotheosisIntegration
                .cappedAttributeBonus(10, 5));
        assertEquals(2.5, com.strutton.dynamicmagic.compat.ApotheosisIntegration
                .cappedAttributeBonus(10, 100));
    }

    @Test void ironSpellSchoolsMapToRelevantDynamicElements() {
        assertEquals(Element.FIRE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("fire"));
        assertEquals(Element.ICE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("ice"));
        assertEquals(Element.LIGHTNING, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("lightning"));
        assertEquals(Element.DIVINE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("holy"));
        assertEquals(Element.SPACE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("ender"));
        assertEquals(Element.BLOOD, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("blood"));
        assertEquals(Element.SPIRIT, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("nature"));
        assertEquals(Element.SHADOW, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("eldritch"));
        assertEquals(Element.ARCANE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("evocation"));
        assertEquals(Element.FIRE, com.strutton.dynamicmagic.compat.IronSpellsIntegration.elementForSchool("pyromancy"));
    }

    @Test void opposingAndCooperativeElementRatiosFollowCombinationLaw() {
        SpellInstruction fire = new SpellInstruction(Element.FIRE, ImpactType.EXPLODE, 10,
                CastDirection.LOOK, TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 16, 3, 1, 1);
        SpellInstruction water = fire.withElement(Element.WATER).withPower(5);
        var opposed = ElementInteractions.effectiveForces(List.of(fire, water), 1);
        assertTrue(opposed.get(Element.FIRE) < 10, "Water must strongly weaken fire");
        assertTrue(opposed.get(Element.WATER) > 0 && opposed.get(Element.WATER) < 5,
                "The clash must weaken water by a smaller amount");
        SpellInstruction earth = fire.withElement(Element.EARTH).withPower(5);
        var bonded = ElementInteractions.effectiveForces(List.of(earth, water), 1);
        assertTrue(bonded.get(Element.EARTH) > 5, "Water must strengthen earth");
        assertTrue(bonded.get(Element.WATER) < 5, "Bonding into earth must consume some water force");
    }

    @Test void elementPresetsAreBoundedAndDiscardEmptyRatios() {
        ElementPreset preset = new ElementPreset("Steam Formula", List.of(
                new ElementPreset.Entry(Element.FIRE, 70), new ElementPreset.Entry(Element.WATER, 30),
                new ElementPreset.Entry(Element.AIR, 0)));
        assertEquals(2, preset.entries().size());
        assertEquals("Steam Formula", ElementPreset.fromTag(preset.toTag()).name());
    }

    @Test void kiRemainsASeparateWeightedSpellContribution() {
        SpellInstruction fire = operation(Element.FIRE, ImpactType.DAMAGE, TargetMode.TRACKED).withPower(3);
        SpellInstruction ki = operation(Element.KI, ImpactType.PHYSICS, TargetMode.SELF).withPower(1);
        CraftedSpell spell = new CraftedSpell("Ki Fire", SourceType.CREATE, Element.FIRE, SpellForm.BOLT,
                DeliveryType.PROJECTILE, ImpactType.DAMAGE, 3, CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 12, TargetMode.TRACKED, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, List.of(fire, ki));
        assertEquals(.25, SpellResourcePayment.kiShare(spell), 1.0e-9);
        assertEquals(TargetMode.TRACKED, SpellInstruction.fromTag(fire.toTag()).targetMode());
        assertTrue(com.strutton.dynamicmagic.skill.MagicSkill.values().length < 64,
                "The persistent skill mask must remain within one long");
    }

    @Test void rainOnlyDiscountsTheWaterShareOfSpellMana() {
        SpellInstruction water = operation(Element.WATER, ImpactType.DAMAGE, TargetMode.AIM).withPower(3);
        SpellInstruction fire = operation(Element.FIRE, ImpactType.DAMAGE, TargetMode.AIM).withPower(1);
        CraftedSpell mixed = new CraftedSpell("Rain-fed Steam", SourceType.CREATE, Element.WATER, SpellForm.BOLT,
                DeliveryType.PROJECTILE, ImpactType.DAMAGE, 3, CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 12, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, List.of(water, fire));
        assertEquals(1, SpellResourcePayment.manaShare(mixed, false), 1.0e-9);
        assertEquals(.4375, SpellResourcePayment.manaShare(mixed, true), 1.0e-9,
                "Rain should discount 75% of the spell's three-quarter water contribution");
        CraftedSpell fireOnly = mixed.withOnlyInstruction(fire);
        assertEquals(1, SpellResourcePayment.manaShare(fireOnly, true), 1.0e-9,
                "Rain must not discount unrelated elements");
    }

    private static SpellInstruction operation(Element element, ImpactType impact, TargetMode target) {
        return new SpellInstruction(element, impact, 1, CastDirection.UP, target,
                PhysicsOperation.ADD_VELOCITY, 8, 0, 2, 1);
    }

    private static CraftedSpell program(List<SpellBranch> branches) {
        SpellBranch primary = branches.get(0);
        SpellInstruction first = primary.instructions().get(0);
        return new CraftedSpell("Test Program", SourceType.CREATE, first.element(), SpellForm.BURST,
                DeliveryType.SELF, first.impact(), first.power(), first.direction(), true, primary.condition(),
                primary.intervalTicks(), primary.detectionRange(), first.targetMode(), primary.targetMode(),
                first.physicsOperation(), primary.instructions(), branches);
    }
}
