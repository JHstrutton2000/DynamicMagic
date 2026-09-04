package com.strutton.dynamicmagic.magic;

import java.util.List;

public final class PremadeSpells {
    private PremadeSpells() {}

    public static final List<CraftedSpell> FAVORITES = List.of(
            new CraftedSpell("Study Element", SourceType.CREATE, Element.ARCANE, SpellForm.BOLT,
                    DeliveryType.PROJECTILE, ImpactType.STUDY, .5),
            new CraftedSpell("Fire Bolt", SourceType.SUMMON, Element.FIRE, SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.EXPLODE, 1.5),
            new CraftedSpell("Flame Stream", SourceType.SUMMON, Element.FIRE, SpellForm.BEAM, DeliveryType.CONTINUOUS, ImpactType.IGNITE, 1.25),
            new CraftedSpell("Air Blast", SourceType.CREATE, Element.AIR, SpellForm.BURST, DeliveryType.PROJECTILE, ImpactType.PHYSICS, 1.5),
            new CraftedSpell("Ice Lance", SourceType.CREATE, Element.ICE, SpellForm.WEAPON, DeliveryType.PROJECTILE, ImpactType.FREEZE, 1.75),
            new CraftedSpell("Wind Shield", SourceType.CREATE, Element.AIR, SpellForm.SHIELD, DeliveryType.SELF, ImpactType.PROTECT, 1.25),
            new CraftedSpell("Item Storage", SourceType.CREATE, Element.SPACE, SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.STORE_ITEM, 1.0),
            new CraftedSpell("Store Entity", SourceType.CREATE, Element.SPACE, SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.STORE_ENTITY, 1.5),
            new CraftedSpell("Entity Storage", SourceType.CREATE, Element.SPACE, SpellForm.BURST, DeliveryType.SELF, ImpactType.RELEASE_STORAGE, 1.0),
            new CraftedSpell("Stop Time", SourceType.CREATE, Element.TIME, SpellForm.SHIELD, DeliveryType.CONTINUOUS, ImpactType.STOP_TIME, 2.0),
            new CraftedSpell("Accelerate Time", SourceType.CREATE, Element.TIME, SpellForm.BURST, DeliveryType.CONTINUOUS, ImpactType.SPEED_TIME, 1.5),
            new CraftedSpell("Slow Time", SourceType.CREATE, Element.TIME, SpellForm.BURST, DeliveryType.CONTINUOUS, ImpactType.SLOW_TIME, 1.5),
            new CraftedSpell("Wind Lift", SourceType.CREATE, Element.AIR, SpellForm.BURST, DeliveryType.CONTINUOUS,
                    ImpactType.PHYSICS, 1.25, CastDirection.UP, false, ConditionType.ALWAYS, 8, 8,
                    TargetMode.SELF, ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Arcane Sentry", SourceType.CREATE, Element.ARCANE, SpellForm.BOLT, DeliveryType.PROJECTILE,
                    ImpactType.DAMAGE, 1.25, CastDirection.LOOK, true, ConditionType.HOSTILE_NEARBY, 10, 12,
                    TargetMode.AIM, ProgramTargetMode.DETECTED_ENTITY, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Divine Mend", SourceType.CREATE, Element.DIVINE, SpellForm.BOLT,
                    DeliveryType.PROJECTILE, ImpactType.HEAL, 1.5, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 10, 8, TargetMode.AIM, ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Divine Renewal", SourceType.CREATE, Element.DIVINE, SpellForm.BURST,
                    DeliveryType.SELF, ImpactType.HEAL, 1.25, CastDirection.LOOK, true,
                    ConditionType.HEALTH_BELOW_HALF, 20, 8, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Divine Cleanse", SourceType.CREATE, Element.DIVINE, SpellForm.BURST,
                    DeliveryType.SELF, ImpactType.CLEANSE, 1.0, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 4, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Call Rain", SourceType.CREATE, Element.WATER, SpellForm.BURST,
                    DeliveryType.CONTINUOUS, ImpactType.WEATHER_RAIN, 2.0, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 4, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Call Storm", SourceType.CREATE, Element.STORM, SpellForm.BURST,
                    DeliveryType.CONTINUOUS, ImpactType.WEATHER_STORM, 2.5, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 4, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Lightning Call", SourceType.SUMMON, Element.LIGHTNING, SpellForm.BOLT,
                    DeliveryType.PROJECTILE, ImpactType.SUMMON_LIGHTNING, 2.5),
            new CraftedSpell("Ore Sight", SourceType.CREATE, Element.METAL, SpellForm.BURST,
                    DeliveryType.PROJECTILE, ImpactType.DETECT_ORES, 1.5),
            new CraftedSpell("Spirit Walk", SourceType.CREATE, Element.SPIRIT, SpellForm.BURST,
                    DeliveryType.CONTINUOUS, ImpactType.ASTRAL_PROJECTION, 3.0, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 4, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            adaptiveSurvival(),
            new CraftedSpell("Hasten Time Toggle", SourceType.CREATE, Element.TIME, SpellForm.BURST,
                    DeliveryType.CONTINUOUS, ImpactType.SPEED_TIME, 1.5, CastDirection.LOOK, true,
                    ConditionType.ALWAYS, 20, 8, TargetMode.SELF,
                    ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY),
            new CraftedSpell("Storm Lance", SourceType.CREATE, Element.WATER, SpellForm.BEAM,
                    DeliveryType.PROJECTILE, ImpactType.DAMAGE, 1.25, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 12, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                    PhysicsOperation.ADD_VELOCITY, List.of(
                    new SpellInstruction(Element.WATER, ImpactType.DAMAGE, 1.25, CastDirection.LOOK,
                            TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 24, 2, 6, 1),
                    new SpellInstruction(Element.LIGHTNING, ImpactType.CHAIN, 1.5, CastDirection.LOOK,
                            TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 24, 2, 3, 1))),
            new CraftedSpell("Gravitic Snare", SourceType.CREATE, Element.EARTH, SpellForm.BURST,
                    DeliveryType.PROJECTILE, ImpactType.ROOT, 1.5, CastDirection.LOOK, false,
                    ConditionType.ALWAYS, 20, 12, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                    PhysicsOperation.ADD_VELOCITY, List.of(
                    new SpellInstruction(Element.EARTH, ImpactType.ROOT, 1.5, CastDirection.LOOK,
                            TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 18, 3, 8, 1),
                    new SpellInstruction(Element.AIR, ImpactType.PHYSICS, 1.0, CastDirection.LOOK,
                            TargetMode.AIM, PhysicsOperation.STOP_MOTION, 18, 3, 2, 1))));

    /** A ready-made example of several independent IF branches in one toggled program. */
    private static CraftedSpell adaptiveSurvival() {
        List<SpellBranch> branches = List.of(
                new SpellBranch(ConditionType.FALLING, 5, 4, ProgramTargetMode.CASTER_AIM, List.of(
                        new SpellInstruction(Element.AIR, ImpactType.PHYSICS, 1.0, CastDirection.UP,
                                TargetMode.SELF, PhysicsOperation.ADD_VELOCITY, 4, 0, 1, 1),
                        new SpellInstruction(Element.AIR, ImpactType.PHYSICS, .5, CastDirection.UP,
                                TargetMode.SELF, PhysicsOperation.RESET_FALL_DISTANCE, 4, 0, 1, 1))),
                new SpellBranch(ConditionType.HEALTH_BELOW_HALF, 20, 4, ProgramTargetMode.CASTER_AIM, List.of(
                        new SpellInstruction(Element.DIVINE, ImpactType.HEAL, 1.25, CastDirection.LOOK,
                                TargetMode.SELF, PhysicsOperation.ADD_VELOCITY, 4, 0, 1, 1))),
                new SpellBranch(ConditionType.POISONED, 10, 4, ProgramTargetMode.CASTER_AIM, List.of(
                        new SpellInstruction(Element.DIVINE, ImpactType.CLEANSE, .5, CastDirection.LOOK,
                                TargetMode.SELF, PhysicsOperation.ADD_VELOCITY, 4, 0, .5, 1))));
        SpellInstruction first = branches.get(0).instructions().get(0);
        return new CraftedSpell("Adaptive Survival", SourceType.CREATE, first.element(), SpellForm.BURST,
                DeliveryType.SELF, first.impact(), first.power(), first.direction(), true,
                branches.get(0).condition(), branches.get(0).intervalTicks(), branches.get(0).detectionRange(),
                first.targetMode(), branches.get(0).targetMode(), first.physicsOperation(), branches.get(0).instructions(), branches);
    }

    public static CraftedSpell byName(String name) {
        return FAVORITES.stream().filter(spell -> spell.name().equals(name)).findFirst().orElse(null);
    }
}
