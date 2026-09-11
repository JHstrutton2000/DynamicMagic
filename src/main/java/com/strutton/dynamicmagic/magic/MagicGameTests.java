package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.knowledge.StudyKnowledge;
import com.strutton.dynamicmagic.dragon.DragonIntegration;

/** Headless integration tests that exercise actual Minecraft entity state. */
@GameTestHolder(DynamicMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicGameTests {
    private MagicGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tenAoARunesTeachOnlyTheirAttunedElement(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        for (int i = 0; i < 9; i++)
            helper.assertTrue(!com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration
                            .recordRuneStudy(caster, Element.ARCANE),
                    "Arcane was learned before ten absorbed runes");
        helper.assertTrue(!ElementKnowledge.knows(caster, Element.ARCANE),
                "Nine absorbed runes incorrectly taught Arcane");
        helper.assertTrue(com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration
                        .recordRuneStudy(caster, Element.ARCANE),
                "The tenth absorbed rune did not trigger an Arcane breakthrough");
        helper.assertTrue(ElementKnowledge.knows(caster, Element.ARCANE),
                "The Arcane breakthrough did not persist elemental knowledge");
        helper.assertTrue(!ElementKnowledge.knows(caster, Element.TIME),
                "Arcane rune study incorrectly taught an unrelated element");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void divineHealAndCleanseAreSeparate(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Cow target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        target.setHealth(2);
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        SpellExecutor.applyDirect(caster, target, operation(Element.DIVINE, ImpactType.HEAL,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY));
        helper.assertTrue(target.getHealth() > 2, "Divine impact did not heal its target");
        helper.assertTrue(target.hasEffect(MobEffects.POISON), "Heal incorrectly performed Cleanse as a hidden side effect");
        float healedHealth = target.getHealth();
        SpellExecutor.applyDirect(caster, target, operation(Element.DIVINE, ImpactType.CLEANSE,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY));
        helper.assertTrue(!target.hasEffect(MobEffects.POISON), "Cleanse did not remove poison");
        helper.assertTrue(target.getHealth() == healedHealth, "Cleanse incorrectly performed Heal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void airMagicChangesMovementAndCancelsFallDamage(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Cow target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        target.setDeltaMovement(0, -.6, 0);
        target.fallDistance = 12;
        SpellExecutor.applyDirect(caster, target, operation(Element.AIR, ImpactType.PHYSICS,
                CastDirection.UP, PhysicsOperation.ADD_VELOCITY));
        SpellExecutor.applyDirect(caster, target, operation(Element.AIR, ImpactType.PHYSICS,
                CastDirection.UP, PhysicsOperation.RESET_FALL_DISTANCE));
        helper.assertTrue(target.getDeltaMovement().y > -.6, "Air operation did not alter vertical velocity");
        helper.assertTrue(target.fallDistance == 0, "Air operation did not clear accumulated fall distance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldFloatPushesUpAndMigratesLegacyDirection(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setDeltaMovement(0, -.4, 0);
        CraftedSpell heldFloat = new CraftedSpell("Float", SourceType.CREATE, Element.AIR, SpellForm.BURST,
                DeliveryType.CONTINUOUS, ImpactType.PHYSICS, 1.25, CastDirection.UP, false,
                ConditionType.ALWAYS, 8, 8, TargetMode.SELF, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY);
        SpellExecutor.cast(caster, heldFloat, 1.0);
        helper.assertTrue(caster.getDeltaMovement().y > 0, "Held Float did not produce upward movement");

        CraftedSpell legacy = new CraftedSpell("Wind Lift", SourceType.CREATE, Element.AIR, SpellForm.BURST,
                DeliveryType.CONTINUOUS, ImpactType.PHYSICS, 1.25, CastDirection.DOWN, false,
                ConditionType.ALWAYS, 8, 8, TargetMode.SELF, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY);
        CraftedSpell migrated = CraftedSpell.fromTag(legacy.toTag());
        helper.assertTrue(migrated != null && migrated.direction() == CastDirection.UP,
                "Legacy downward Wind Lift did not migrate to literal upward force");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enlightenmentIsSlowAndElementScoped(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ComponentKnowledge.reset(caster);
        ElementKnowledge.forgetAll(caster);
        ElementKnowledge.learn(caster, Element.AIR);
        ComponentKnowledge.ensureStarter(caster);
        CraftedSpell airPractice = new CraftedSpell("Air Practice", SourceType.CREATE, Element.AIR,
                SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.PHYSICS, 1,
                CastDirection.DOWN, false, ConditionType.ALWAYS, 20, 8, TargetMode.SELF,
                ProgramTargetMode.CASTER_AIM, PhysicsOperation.ADD_VELOCITY);
        for (int i = 0; i < 255; i++) ComponentKnowledge.recordSuccessfulCast(caster, airPractice);
        helper.assertTrue(!ComponentKnowledge.snapshot(caster).knows(CastDirection.UP),
                "Air breakthrough happened before the enlightenment requirement");
        ComponentKnowledge.recordSuccessfulCast(caster, airPractice);
        helper.assertTrue(ComponentKnowledge.snapshot(caster).knows(CastDirection.UP),
                "Air practice did not unlock an Air-relevant breakthrough at the requirement");
        helper.assertTrue(!ComponentKnowledge.snapshot(caster).knows(ImpactType.HEAL),
                "Air practice incorrectly unlocked Divine healing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void programConditionsReadLivePlayerState(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setOnGround(false);
        caster.setDeltaMovement(0, -.4, 0);
        SpellBranch falling = branch(ConditionType.FALLING, Element.AIR, ImpactType.PHYSICS);
        helper.assertTrue(SpellProgramRunner.conditionMet(caster, falling, null), "Falling condition ignored live velocity");
        caster.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        SpellBranch poisoned = branch(ConditionType.POISONED, Element.DIVINE, ImpactType.CLEANSE);
        helper.assertTrue(SpellProgramRunner.conditionMet(caster, poisoned, null), "Poison condition ignored active effects");
        CraftedSpell program = program(java.util.List.of(falling, poisoned));
        helper.assertTrue(SpellProgramRunner.matchingBranches(caster, program, 10).size() == 2,
                "Two simultaneously true branches did not both enter the execution plan");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void programmingMasteryUnlocksBranchCapacity(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ProgrammingKnowledge.reset(caster);
        helper.assertTrue(ProgrammingKnowledge.learn(caster), "Programming Grimoire did not teach programming");
        helper.assertTrue(ProgrammingKnowledge.maxBranches(caster) == 1, "A novice did not start with exactly one branch");
        ProgrammingKnowledge.practice(caster, 8);
        helper.assertTrue(ProgrammingKnowledge.maxBranches(caster) == 2, "Programming practice did not unlock branch two");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void programmedBranchesSurviveNbtRoundTrip(GameTestHelper helper) {
        CraftedSpell original = program(java.util.List.of(
                new SpellBranch(ConditionType.FALLING, 5, 6, ProgramTargetMode.CASTER_AIM,
                        java.util.List.of(operation(Element.AIR, ImpactType.PHYSICS,
                                CastDirection.UP, PhysicsOperation.ADD_VELOCITY))),
                new SpellBranch(ConditionType.POISONED, 10, 4, ProgramTargetMode.CASTER_AIM,
                        java.util.List.of(operation(Element.DIVINE, ImpactType.CLEANSE,
                                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY)))));
        CraftedSpell loaded = CraftedSpell.fromTag(original.toTag());
        helper.assertTrue(loaded != null, "NBT round-trip returned an invalid spell");
        helper.assertTrue(loaded.branches().size() == 2, "NBT round-trip lost a program branch");
        helper.assertTrue(loaded.branches().get(0).condition() == ConditionType.FALLING,
                "NBT round-trip changed the first condition");
        helper.assertTrue(loaded.branches().get(1).instructions().get(0).impact() == ImpactType.CLEANSE,
                "NBT round-trip changed a branch action");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void elementalManaTransformsBlocksByForceAndRatio(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        BlockPos lava = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
        SpellExecutor.applyWorldDirect(caster, lava, Direction.UP, worldSpell(Element.WATER, 3));
        helper.assertTrue(helper.getLevel().getBlockState(lava).is(Blocks.OBSIDIAN),
                "Enough Water mana did not turn lava into obsidian");

        BlockPos water = helper.absolutePos(new BlockPos(3, 2, 1));
        helper.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        SpellExecutor.applyWorldDirect(caster, water, Direction.UP, worldSpell(Element.FIRE, 3));
        helper.assertTrue(helper.getLevel().getBlockState(water).is(Blocks.COBBLESTONE),
                "Enough Fire mana did not turn water into cobblestone");

        BlockPos freeze = helper.absolutePos(new BlockPos(5, 2, 1));
        helper.getLevel().setBlockAndUpdate(freeze, Blocks.WATER.defaultBlockState());
        SpellExecutor.applyWorldDirect(caster, freeze, Direction.UP, worldSpell(Element.ICE, 3));
        helper.assertTrue(helper.getLevel().getBlockState(freeze).is(Blocks.ICE),
                "Enough Ice mana did not freeze water");

        BlockPos richWater = helper.absolutePos(new BlockPos(7, 2, 1));
        helper.getLevel().setBlockAndUpdate(richWater, Blocks.AIR.defaultBlockState());
        SpellExecutor.applyWorldDirect(caster, richWater, Direction.UP, combinedWorldSpell(4, 2, 0));
        helper.assertTrue(helper.getLevel().getBlockState(richWater).is(Blocks.OBSIDIAN),
                "Water-heavy combined spell did not produce obsidian");

        BlockPos richFire = helper.absolutePos(new BlockPos(9, 2, 1));
        helper.getLevel().setBlockAndUpdate(richFire, Blocks.AIR.defaultBlockState());
        SpellExecutor.applyWorldDirect(caster, richFire, Direction.UP, combinedWorldSpell(2, 4, 0));
        helper.assertTrue(helper.getLevel().getBlockState(richFire).is(Blocks.COBBLESTONE),
                "Fire-heavy combined spell did not produce cobblestone");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void divineAndSpiritLawsAffectLivingTargets(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Zombie undead = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        float before = undead.getHealth();
        SpellExecutor.applyDirect(caster, undead, operation(Element.DIVINE, ImpactType.HEAL,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY));
        helper.assertTrue(undead.getHealth() < before, "Divine healing did not damage undead");

        ZombieVillager zombieVillager = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(4, 2, 2));
        SpellExecutor.applyDirect(caster, zombieVillager, operation(Element.DIVINE, ImpactType.RESURRECT,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY).withPower(5));
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(Villager.class,
                zombieVillager.getBoundingBox().inflate(2)).isEmpty(), "Resurrect did not restore a zombie villager");

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 2));
        SpellExecutor.applyDirect(caster, pig, operation(Element.SPIRIT, ImpactType.FERTILITY,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY));
        helper.assertTrue(pig.isInLove(), "Spirit fertility did not place an animal in love mode");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void studyAndSynthesisTeachOnlyAfterRequiredPractice(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ElementKnowledge.forgetAll(caster);
        ComponentKnowledge.reset(caster);
        for (int i = 0; i < 79; i++) ComponentKnowledge.studyElement(caster, Element.WATER, 1);
        helper.assertTrue(!ElementKnowledge.knows(caster, Element.WATER), "Study taught Water too early");
        ComponentKnowledge.studyElement(caster, Element.WATER, 1);
        helper.assertTrue(ElementKnowledge.knows(caster, Element.WATER), "Study did not teach Water at its threshold");

        ElementKnowledge.learn(caster, Element.EARTH);
        ElementKnowledge.learn(caster, Element.LIGHTNING);
        CraftedSpell synthesis = combinedPracticeSpell(Element.EARTH, Element.LIGHTNING);
        for (int i = 0; i < 511; i++) ComponentKnowledge.recordSuccessfulCast(caster, synthesis);
        helper.assertTrue(!ElementKnowledge.knows(caster, Element.METAL), "Metal synthesis completed too early");
        ComponentKnowledge.recordSuccessfulCast(caster, synthesis);
        helper.assertTrue(ElementKnowledge.knows(caster, Element.METAL), "Earth and Lightning practice did not synthesize Metal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void projectionRestoresModeAndProjectilePracticeRaisesAccuracy(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setGameMode(GameType.SURVIVAL);
        SustainedMagicController.activate(caster, ImpactType.ASTRAL_PROJECTION);
        helper.assertTrue(caster.gameMode.getGameModeForPlayer() == GameType.SPECTATOR,
                "Spirit projection did not enter spectator mode");
        SustainedMagicController.deactivate(caster);
        helper.assertTrue(caster.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                "Spirit projection did not restore the previous game mode");

        double before = ProjectileAccuracy.value(caster);
        CraftedSpell projectile = new CraftedSpell("Accuracy practice", SourceType.CREATE, Element.AIR,
                SpellForm.BOLT, DeliveryType.PROJECTILE, ImpactType.DAMAGE, 1);
        for (int i = 0; i < 100; i++) ProjectileAccuracy.practice(caster, projectile);
        helper.assertTrue(ProjectileAccuracy.value(caster) > before,
                "Projectile practice did not improve accuracy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void studyCooldownIsPerElementAndLifeObservation(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        BlockPos water = new BlockPos(1, 1, 1);
        BlockPos otherWater = new BlockPos(3, 1, 1);
        BlockPos lava = new BlockPos(2, 1, 1);
        helper.assertTrue(StudyKnowledge.studyBlock(caster, caster.serverLevel(), water, Element.WATER, 1),
                "First study of an element was rejected");
        helper.assertTrue(StudyKnowledge.cooldownRemaining(caster, Element.WATER)
                        == StudyKnowledge.ELEMENT_COOLDOWN_TICKS,
                "Element study did not start the full ten-minute cooldown");
        helper.assertTrue(!StudyKnowledge.studyBlock(caster, caster.serverLevel(), otherWater, Element.WATER, 1),
                "A different block bypassed the per-element cooldown");
        helper.assertTrue(StudyKnowledge.studyBlock(caster, caster.serverLevel(), lava, Element.LAVA, 1),
                "Studying one element incorrectly blocked a different element");
        helper.assertTrue(StudyKnowledge.studyBlock(caster, caster.serverLevel(), water, Element.ICE, 1),
                "A different element at the same position was incorrectly blocked");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void portalsTeachSpaceAndUseItsStudyCooldown(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        BlockPos portal = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(portal, Blocks.NETHER_PORTAL.defaultBlockState());
        helper.assertTrue(SpellExecutor.isPortal(helper.getLevel().getBlockState(portal)),
                "Nether portal was not recognized as a study subject");
        helper.assertTrue(SpellExecutor.observedElement(helper.getLevel().getBlockState(portal)) == Element.SPACE,
                "Portal study was assigned to an unrelated element");
        helper.assertTrue(StudyKnowledge.studyPortal(caster, caster.serverLevel(), portal, 1),
                "First portal study was rejected");
        helper.assertTrue(StudyKnowledge.cooldownRemaining(caster, Element.SPACE)
                        == StudyKnowledge.ELEMENT_COOLDOWN_TICKS,
                "Portal study did not start the Space study cooldown");
        helper.assertTrue(!StudyKnowledge.studyPortal(caster, caster.serverLevel(), portal, 1),
                "The same portal could be studied repeatedly during cooldown");
        helper.assertTrue(StudyKnowledge.cooldownRemaining(caster, Element.EARTH) == 0,
                "Portal study incorrectly started the Earth cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enderDragonUsesResistanceAndOpposingElementRules(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        var dragon = EntityType.ENDER_DRAGON.create(caster.serverLevel());
        helper.assertTrue(dragon != null && DragonIntegration.isDragon(dragon),
                "Vanilla Ender Dragon was not recognized as a dragon");
        float resisted = DragonIntegration.adjustSpellDamage(caster, dragon, Element.WATER, 10);
        float opposed = DragonIntegration.adjustSpellDamage(caster, dragon, Element.DIVINE, 10);
        helper.assertTrue(resisted < 10, "Dragon did not resist ordinary magic");
        helper.assertTrue(opposed > 10, "Opposing elemental magic did not overcome dragon resistance");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void internalVampiresCountForElementalReactions(GameTestHelper helper) {
        Zombie vampire = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        vampire.getPersistentData().putBoolean("DynamicMagicVampireEnemy", true);
        helper.assertTrue(com.strutton.dynamicmagic.vampire.Vampirism.isVampire(vampire),
                "Internal vampire enemy was not exposed to holy/fire integration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void testingControlsExposeSkillsAndDragonProgress(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.skill.SkillKnowledge.forgetAll(caster);
        int learned = com.strutton.dynamicmagic.skill.SkillKnowledge.learnAll(caster);
        helper.assertTrue(learned == com.strutton.dynamicmagic.skill.MagicSkill.values().length,
                "Learn-all did not grant every skill");
        com.strutton.dynamicmagic.dragon.DragonProgression.setLore(caster, 144);
        com.strutton.dynamicmagic.dragon.DragonProgression.setAffinity(caster, Element.FIRE, 3);
        var progress = com.strutton.dynamicmagic.dragon.DragonProgression.progress(caster);
        helper.assertTrue(progress.lore() == 144, "Dragon progress did not expose configured lore");
        helper.assertTrue(com.strutton.dynamicmagic.dragon.DragonProgression.affinity(caster, Element.FIRE) == 3,
                "Dragon affinity testing control did not persist");
        com.strutton.dynamicmagic.dragon.DragonProgression.reset(caster);
        helper.assertTrue(com.strutton.dynamicmagic.dragon.DragonProgression.progress(caster).lore() == 0,
                "Dragon progress reset did not clear lore");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villageMageFactoryAlwaysCreatesMage(GameTestHelper helper) {
        Villager mage = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        com.strutton.dynamicmagic.mage.VillageMageEvents.makeMage(mage);
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isMage(mage),
                "Village mage summoning egg factory did not mark its villager as a mage");
        String name = mage.getName().getString();
        helper.assertTrue(java.util.Arrays.stream(new String[]{"Flamecaller", "Tidecaller", "Stoneweaver", "Windwalker", "Stormcaller"})
                        .anyMatch(name::endsWith),
                "Village mage summoning egg factory did not assign an elemental personal name");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void necromancerFactoryCreatesZombieVillagerTrader(GameTestHelper helper) {
        ZombieVillager necromancer = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(2, 2, 2));
        com.strutton.dynamicmagic.mage.VillageMageEvents.makeNecromancer(necromancer);
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isMage(necromancer),
                "Necromancer was not registered as a mage merchant");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isNecromancer(necromancer),
                "Necromancer variant marker was not persisted");
        helper.assertTrue(necromancer.getName().getString().endsWith("the Necromancer"),
                "Necromancer did not receive a persistent personal name");
        helper.assertTrue(necromancer.hasEffect(MobEffects.FIRE_RESISTANCE),
                "Necromancer was not protected from burning in daylight");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void skeletonNecromancerIsANeutralVillageMage(GameTestHelper helper) {
        var necromancer = DynamicMagic.SKELETON_MAGE_VILLAGER.get().create(helper.getLevel());
        helper.assertTrue(necromancer != null, "Skeleton mage villager entity type did not create an entity");
        necromancer.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0, 0);
        helper.getLevel().addFreshEntity(necromancer);
        com.strutton.dynamicmagic.mage.VillageMageEvents.makeSkeletonNecromancer(necromancer);
        helper.assertTrue(necromancer instanceof Villager,
                "Skeleton necromancer did not use the custom villager-bodied entity");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isMage(necromancer),
                "Skeleton necromancer was not registered as a mage merchant");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isNecromancer(necromancer),
                "Skeleton necromancer did not receive its neutral necromancer marker");
        helper.assertTrue((com.strutton.dynamicmagic.mage.VillageMageEvents.elementTradeMask(necromancer)
                        & (1L << Element.UNDEAD.ordinal())) != 0,
                "Skeleton necromancer did not receive its Necromancy Grimoire trade");
        helper.assertTrue(necromancer.getName().getString().endsWith("the Bone Sage"),
                "Skeleton necromancer did not receive its village-mage name");
        helper.assertTrue(necromancer.hasEffect(MobEffects.FIRE_RESISTANCE),
                "Skeleton necromancer was not protected from daylight");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.VILLAGER_MAGE_CHANCE >= .30,
                "Ordinary village mages are not configured to be common");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void infectedMageKeepsTradesAsNecromancer(GameTestHelper helper) {
        Villager mage = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        com.strutton.dynamicmagic.mage.VillageMageEvents.makeMage(mage);
        long oldElements = com.strutton.dynamicmagic.mage.VillageMageEvents.elementTradeMask(mage);
        long oldSkills = com.strutton.dynamicmagic.mage.VillageMageEvents.skillTradeMask(mage);
        ZombieVillager necromancer = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(3, 2, 2));
        com.strutton.dynamicmagic.mage.VillageMageEvents.inheritAsNecromancer(mage, necromancer);
        long inheritedElements = com.strutton.dynamicmagic.mage.VillageMageEvents.elementTradeMask(necromancer);
        helper.assertTrue((inheritedElements & oldElements) == oldElements,
                "Infected mage lost one or more old elemental book trades");
        helper.assertTrue((inheritedElements & (1L << Element.UNDEAD.ordinal())) != 0,
                "Infected mage did not gain the necromancy book trade");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.skillTradeMask(necromancer) == oldSkills,
                "Infected mage lost its old skill-book trades");
        helper.assertTrue(com.strutton.dynamicmagic.mage.VillageMageEvents.isNecromancer(necromancer),
                "Infected mage was not converted into a neutral necromancer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creeperVillagerAndExplosionEvolutionAreRegistered(GameTestHelper helper) {
        var scholar = com.strutton.dynamicmagic.DynamicMagic.CREEPER_VILLAGER.get().create(helper.getLevel());
        helper.assertTrue(scholar != null, "Creeper Villager entity type did not create an entity");
        helper.assertTrue(scholar instanceof Villager, "Creeper Villager does not use the villager entity model");
        helper.assertTrue(scholar.getName().getString().endsWith("the Blastkeeper"),
                "Creeper Villager did not receive its persistent name");

        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.skill.SkillKnowledge.learn(caster,
                com.strutton.dynamicmagic.skill.MagicSkill.EXPLOSION_RESISTANCE);
        boolean evolved = com.strutton.dynamicmagic.skill.ExplosionProgression.recordExposure(caster,
                com.strutton.dynamicmagic.skill.ExplosionProgression.IMMUNITY_REQUIREMENT);
        helper.assertTrue(evolved, "Explosion Resistance did not evolve at the exposure requirement");
        helper.assertTrue(!com.strutton.dynamicmagic.skill.SkillKnowledge.knows(caster,
                        com.strutton.dynamicmagic.skill.MagicSkill.EXPLOSION_RESISTANCE),
                "Explosion Resistance remained after evolving");
        helper.assertTrue(com.strutton.dynamicmagic.skill.SkillKnowledge.knows(caster,
                        com.strutton.dynamicmagic.skill.MagicSkill.EXPLOSION_IMMUNITY),
                "Explosion Immunity was not learned after evolution");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vampireImmunityPreventsInfection(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.skill.SkillKnowledge.learn(caster,
                com.strutton.dynamicmagic.skill.MagicSkill.VAMPIRE_IMMUNITY);
        helper.assertTrue(!com.strutton.dynamicmagic.vampire.Vampirism.infect(caster),
                "Vampire Immunity allowed an infection");
        helper.assertTrue(!com.strutton.dynamicmagic.vampire.Vampirism.isVampire(caster),
                "Vampire Immunity left the player vampiric");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void werewolfImmunityPreventsAddonInfection(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.skill.SkillKnowledge.learn(caster,
                com.strutton.dynamicmagic.skill.MagicSkill.WEREWOLF_IMMUNITY);
        helper.assertTrue(com.strutton.dynamicmagic.werewolf.Werewolves.isImmune(caster),
                "Werewolf Immunity was not recognized");
        helper.assertTrue(!com.strutton.dynamicmagic.werewolf.Werewolves.infect(caster),
                "Werewolf Immunity allowed the addon infection command");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void repeatedGrimoiresBuildElementAffinity(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ElementKnowledge.learn(caster, Element.WATER);
        helper.assertTrue(com.strutton.dynamicmagic.knowledge.ElementAffinity.increase(caster, Element.WATER),
                "A repeated elemental grimoire could not increase affinity");
        helper.assertTrue(com.strutton.dynamicmagic.knowledge.ElementAffinity.get(caster, Element.WATER) == 1,
                "Element affinity did not persist");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endermanStudyUnlocksTeleportation(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.knowledge.StudyKnowledge.addEndermanInsight(caster, 64);
        helper.assertTrue(ElementKnowledge.knows(caster, Element.SPACE),
                "Enderman study did not teach the Space element");
        helper.assertTrue(ComponentKnowledge.knowsImpact(caster, ImpactType.TELEPORT),
                "Enderman study did not unlock the Teleport effect");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endermanAndAlexScholarsUseVillagerEntities(GameTestHelper helper) {
        var endermanScholar = DynamicMagic.ENDERMAN_VILLAGER.get().create(helper.getLevel());
        var alexScholar = DynamicMagic.ALEX_VILLAGER.get().create(helper.getLevel());
        helper.assertTrue(endermanScholar instanceof Villager,
                "Enderman scholar does not use the villager entity model");
        helper.assertTrue(endermanScholar.getName().getString().endsWith("the Wayfarer"),
                "Enderman scholar did not receive a persistent name");
        helper.assertTrue(alexScholar instanceof Villager,
                "Alex fertility scholar does not use the villager entity model");
        helper.assertTrue(alexScholar.getName().getString().equals("Alex the Lifebringer"),
                "Alex fertility scholar did not receive the intended identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void waterCountersBlazesAndFootingMagicBridgesFluids(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        var blaze = helper.spawn(EntityType.BLAZE, new BlockPos(2, 2, 2));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        float blazeBefore = blaze.getHealth();
        float cowBefore = cow.getHealth();
        SpellInstruction waterDamage = operation(Element.WATER, ImpactType.DAMAGE,
                CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY);
        SpellExecutor.applyDirect(caster, blaze, waterDamage);
        SpellExecutor.applyDirect(caster, cow, waterDamage);
        helper.assertTrue(blazeBefore - blaze.getHealth() > cowBefore - cow.getHealth(),
                "Water magic did not deal increased damage to a blaze");

        BlockPos footing = helper.absolutePos(new BlockPos(6, 1, 2));
        caster.setPos(footing.getX() + .5, footing.getY() + 1, footing.getZ() + .5);
        helper.getLevel().setBlockAndUpdate(footing, Blocks.LAVA.defaultBlockState());
        helper.assertTrue(SpellExecutor.applyFootingMagic(caster, Element.WATER, 3) > 0,
                "Strong Water footing magic did not react with lava");
        helper.assertTrue(helper.getLevel().getBlockState(footing).is(Blocks.OBSIDIAN),
                "Water footing magic did not make an obsidian bridge");
        helper.getLevel().setBlockAndUpdate(footing, Blocks.WATER.defaultBlockState());
        helper.assertTrue(SpellExecutor.applyFootingMagic(caster, Element.ICE, 3) > 0,
                "Strong Ice footing magic did not react with water");
        helper.assertTrue(helper.getLevel().getBlockState(footing).is(Blocks.ICE),
                "Ice footing magic did not make a frozen bridge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manaMendingConsumesManaAndRepairsDurability(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        var enchantment = caster.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(com.strutton.dynamicmagic.mana.ManaMendingEvents.MANA_MENDING);
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        sword.enchant(enchantment, 1);
        sword.setDamageValue(5);
        caster.getInventory().setItem(0, sword);
        com.strutton.dynamicmagic.mana.Mana.set(caster, 10);

        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaMendingEvents.repairOne(caster),
                "Mana Mending did not repair an enchanted damaged item");
        helper.assertTrue(caster.getInventory().getItem(0).getDamageValue() == 4,
                "Mana Mending did not repair exactly one durability per pulse");
        helper.assertTrue(Math.abs(com.strutton.dynamicmagic.mana.Mana.get(caster) - 9) < 1.0e-6,
                "Mana Mending did not consume exactly one mana per durability");

        com.strutton.dynamicmagic.mana.Mana.set(caster, 0);
        helper.assertTrue(!com.strutton.dynamicmagic.mana.ManaMendingEvents.repairOne(caster),
                "Mana Mending repaired an item without enough mana");
        helper.assertTrue(caster.getInventory().getItem(0).getDamageValue() == 4,
                "Insufficient mana changed item durability");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manaBrewingPausesCompletesAndProgresses(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        for (var ingredient : java.util.List.of(net.minecraft.world.item.Items.AMETHYST_SHARD,
                net.minecraft.world.item.Items.CLOCK, net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.EMERALD_BLOCK,
                net.minecraft.world.item.Items.NETHER_STAR, net.minecraft.world.item.Items.DRAGON_EGG))
            helper.assertTrue(helper.getLevel().potionBrewing().isIngredient(new net.minecraft.world.item.ItemStack(ingredient)),
                    "A mana-brewing catalyst cannot be placed in the brewing stand: " + ingredient);
        var cureIngredient = com.strutton.dynamicmagic.mana.ManaBrewing.recipe("vampire_cure").ingredient();
        helper.assertTrue(helper.getLevel().potionBrewing().isIngredient(new net.minecraft.world.item.ItemStack(cureIngredient)),
                "The vampire cure catalyst cannot be placed in the brewing stand: " + cureIngredient);
        BlockPos relative = new BlockPos(2, 1, 2);
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.BREWING_STAND);
        var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity)
                helper.getLevel().getBlockEntity(absolute);
        stand.setItem(0, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD));
        stand.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD));

        com.strutton.dynamicmagic.mana.Mana.set(caster, 0);
        helper.assertTrue(!com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "Mana brewing progressed without mana");
        helper.assertTrue(stand.getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)
                        .is(net.minecraft.world.item.alchemy.Potions.AWKWARD),
                "A paused brew changed its potion input");

        com.strutton.dynamicmagic.mana.Mana.set(caster, 100);
        for (int pulse = 0; pulse < 60; pulse++)
            helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                    "Mana brew unexpectedly stopped with enough mana");
        var brewed = stand.getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        helper.assertTrue(brewed != null && brewed.is(DynamicMagic.MANA_POTION),
                "Mana brewing did not create a Mana Potion");
        helper.assertTrue(com.strutton.dynamicmagic.skill.SkillKnowledge.knows(caster,
                        com.strutton.dynamicmagic.skill.MagicSkill.MANA_BREWING),
                "Completing the first brew did not unlock Mana Brewing");
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.mastery(caster) > 0,
                "Completing a brew did not increase brewing mastery");
        helper.assertTrue(Math.abs(com.strutton.dynamicmagic.mana.Mana.get(caster) - 40) < 1.0e-5,
                "The basic Mana Potion brew did not drain its configured mana total");

        com.strutton.dynamicmagic.mana.Mana.set(caster, 0);
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.applyPotion(caster, DynamicMagic.MANA_POTION),
                "Mana Potion was not recognized when consumed");
        helper.assertTrue(Math.abs(com.strutton.dynamicmagic.mana.Mana.get(caster) - 35) < 1.0e-5,
                "Mana Potion did not restore 35% of maximum mana");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manaExpansionPotionsGrowPoolAndResetTimeout(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.mana.Mana.set(caster, 1);
        com.strutton.dynamicmagic.mana.Mana.consume(caster, 1);
        helper.assertTrue(com.strutton.dynamicmagic.mana.Mana.expansionCooldownRemainingTicks(caster) > 0,
                "Mana exhaustion did not establish an expansion timeout");
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.applyPotion(caster,
                        DynamicMagic.EXPANSION_RESET_POTION),
                "Expansion Reset Potion was not recognized");
        helper.assertTrue(com.strutton.dynamicmagic.mana.Mana.expansionCooldownRemainingTicks(caster) == 0,
                "Expansion Reset Potion did not clear the timeout");

        double before = com.strutton.dynamicmagic.mana.Mana.max(caster);
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.applyPotion(caster,
                        DynamicMagic.MANA_EXPANSION_50),
                "50% Mana Expansion Potion was not recognized");
        helper.assertTrue(Math.abs(com.strutton.dynamicmagic.mana.Mana.max(caster) - before * 1.5) < 1.0e-5,
                "50% Mana Expansion Potion did not increase maximum mana by exactly 50%");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manaBrewsShareDurationButExpensiveBrewsPause(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        int expected = com.strutton.dynamicmagic.mana.ManaBrewing.requiredSeconds(caster,
                com.strutton.dynamicmagic.mana.ManaBrewing.recipe("mana"));
        for (String id : java.util.List.of("vampire_cure", "reset", "expand_1", "expand_5", "expand_10", "expand_25", "expand_50"))
            helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.requiredSeconds(caster,
                            com.strutton.dynamicmagic.mana.ManaBrewing.recipe(id)) == expected,
                    "Mana brew recipes did not share the same base duration: " + id);

        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, Blocks.BREWING_STAND);
        var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        stand.setItem(0, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD));
        stand.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_EGG));
        com.strutton.dynamicmagic.mana.Mana.set(caster, 100);
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "An expensive brew did not begin with enough mana for one pulse");
        helper.assertTrue(!com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "An expensive brew did not pause to wait for naturally regenerating mana");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vampireCureBrewRequiresAndDrainsVampireBlood(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, Blocks.BREWING_STAND);
        var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity)
                helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        stand.setItem(0, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD));
        // Game tests run without the optional Vampirism JAR, so this resolves to the internal-vampire fallback.
        var cureRecipe = com.strutton.dynamicmagic.mana.ManaBrewing.recipe("vampire_cure");
        stand.setItem(3, new net.minecraft.world.item.ItemStack(cureRecipe.ingredient()));
        com.strutton.dynamicmagic.mana.Mana.set(caster, 100);

        helper.assertTrue(!com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "A human was able to power a vampire cure brew");
        com.strutton.dynamicmagic.vampire.Vampirism.infect(caster);
        caster.getFoodData().setFoodLevel(0);
        helper.assertTrue(!com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "Vampire cure brewing progressed with no vampire blood");
        caster.getFoodData().setFoodLevel(6);
        helper.assertTrue(com.strutton.dynamicmagic.mana.ManaBrewing.processStand(caster, stand),
                "Vampire cure brewing did not begin with blood and mana available");
        helper.assertTrue(caster.getFoodData().getFoodLevel() == 5,
                "Vampire cure brewing did not drain its first blood unit");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vampireCureSupportPotionsApplyOnlyOnce(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.vampire.Vampirism.infect(caster);
        caster.getFoodData().setFoodLevel(20);
        helper.assertTrue(com.strutton.dynamicmagic.vampire.VampireCureTreatment.start(caster),
                "Vampire cure treatment did not start");
        var weakness = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WEAKNESS)
                .get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        var regeneration = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.REGENERATION)
                .get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        helper.assertTrue(com.strutton.dynamicmagic.vampire.VampireCureTreatment.recordSupportPotion(caster, weakness),
                "Weakness potion did not strengthen the cure");
        helper.assertTrue(!com.strutton.dynamicmagic.vampire.VampireCureTreatment.recordSupportPotion(caster, weakness),
                "A second Weakness potion stacked its cure bonus");
        helper.assertTrue(com.strutton.dynamicmagic.vampire.VampireCureTreatment.recordSupportPotion(caster, regeneration),
                "Regeneration potion did not strengthen the cure");
        helper.assertTrue(!com.strutton.dynamicmagic.vampire.VampireCureTreatment.recordSupportPotion(caster, regeneration),
                "A second Regeneration potion stacked its cure bonus");
        helper.assertTrue(Math.abs(com.strutton.dynamicmagic.vampire.VampireCureTreatment.successChance(caster) - .14) < 1.0e-6,
                "Support potions did not add exactly 14 percentage points in total");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vampireCureMakesAnimalsFleeAndMobsAttack(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        com.strutton.dynamicmagic.vampire.Vampirism.infect(caster);
        var cow = net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(cow != null && zombie != null, "Could not create cure-aura test mobs");
        cow.setPos(caster.getX() + 2, caster.getY(), caster.getZ());
        zombie.setPos(caster.getX() + 4, caster.getY(), caster.getZ());
        helper.getLevel().addFreshEntity(cow);
        helper.getLevel().addFreshEntity(zombie);
        com.strutton.dynamicmagic.vampire.VampireCureTreatment.influenceNearbyMobs(caster);
        helper.assertTrue(cow.getPersistentData().hasUUID("DynamicMagicVampireCureForcedFlee"),
                "The cure did not force a nearby animal to flee");
        helper.assertTrue(cow.getTarget() == null, "The cure made an animal attack instead of flee");
        helper.assertTrue(zombie.getTarget() == caster, "The cure did not provoke a nearby hostile mob");
        helper.assertTrue(zombie.getPersistentData().hasUUID("DynamicMagicVampireCureForcedTarget"),
                "The cure did not mark its forced hostile target for later release");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void morphMagesPreserveTradesAndRevealNoName(GameTestHelper helper) {
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
        villager.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.WHEAT, 1),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), 4, 1, 0));
        helper.assertTrue(com.strutton.dynamicmagic.mage.MorphMageEvents.makeMorphMage(villager),
                "A generic villager could not become a morph mage");
        helper.assertTrue(villager.getCustomName() == null, "Morph mage received an identifying name");
        var offers = com.strutton.dynamicmagic.mage.MorphMageEvents.offers(villager);
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getResult().is(net.minecraft.world.item.Items.EMERALD)
                        && offer.getItemCostA().itemStack().is(net.minecraft.world.item.Items.WHEAT)),
                "Morph mage did not preserve its original trade");
        helper.assertTrue(offers.stream().anyMatch(offer -> offer.getResult().is(
                        DynamicMagic.SKILL_TOMES.get(com.strutton.dynamicmagic.skill.MagicSkill.MORPHING).get())),
                "Morph mage did not add the Morphing skill tome trade");

        var skeleton = net.minecraft.world.entity.EntityType.SKELETON.create(helper.getLevel());
        skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
        helper.assertTrue(com.strutton.dynamicmagic.mage.MorphMageEvents.makeMorphMage(skeleton),
                "A generic skeleton could not become a morph mage");
        helper.assertTrue(skeleton.getMainHandItem().isEmpty(),
                "A skeleton morph mage retained the bow that exposes its normal role");

        var wither = net.minecraft.world.entity.EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(!com.strutton.dynamicmagic.mage.MorphMageEvents.makeMorphMage(wither),
                "A boss was allowed to become a morph mage");
        helper.succeed();
    }

    private static SpellBranch branch(ConditionType condition, Element element, ImpactType impact) {
        return new SpellBranch(condition, 5, 8, ProgramTargetMode.CASTER_AIM,
                java.util.List.of(operation(element, impact, CastDirection.UP, PhysicsOperation.ADD_VELOCITY)));
    }

    private static SpellInstruction operation(Element element, ImpactType impact, CastDirection direction,
                                              PhysicsOperation physics) {
        return new SpellInstruction(element, impact, 1, direction, TargetMode.SELF, physics, 8, 0, 2, 1);
    }

    private static CraftedSpell program(java.util.List<SpellBranch> branches) {
        SpellBranch primary = branches.get(0);
        SpellInstruction first = primary.instructions().get(0);
        return new CraftedSpell("GameTest Program", SourceType.CREATE, first.element(), SpellForm.BURST,
                DeliveryType.SELF, first.impact(), first.power(), first.direction(), true, primary.condition(),
                primary.intervalTicks(), primary.detectionRange(), first.targetMode(), primary.targetMode(),
                first.physicsOperation(), primary.instructions(), branches);
    }

    private static CraftedSpell worldSpell(Element element, double power) {
        SpellInstruction instruction = new SpellInstruction(element, ImpactType.TRANSMUTE_BLOCK, power,
                CastDirection.LOOK, TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 8, 0, 1, 1);
        return new CraftedSpell("World law", SourceType.CREATE, element, SpellForm.BOLT,
                DeliveryType.PROJECTILE, ImpactType.TRANSMUTE_BLOCK, power, CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 8, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, java.util.List.of(instruction));
    }

    private static CraftedSpell combinedWorldSpell(double water, double fire, double ice) {
        java.util.List<SpellInstruction> instructions = new java.util.ArrayList<>();
        if (water > 0) instructions.add(new SpellInstruction(Element.WATER, ImpactType.TRANSMUTE_BLOCK, water,
                CastDirection.LOOK, TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 8, 0, 1, 1));
        if (fire > 0) instructions.add(new SpellInstruction(Element.FIRE, ImpactType.TRANSMUTE_BLOCK, fire,
                CastDirection.LOOK, TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 8, 0, 1, 1));
        if (ice > 0) instructions.add(new SpellInstruction(Element.ICE, ImpactType.TRANSMUTE_BLOCK, ice,
                CastDirection.LOOK, TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, 8, 0, 1, 1));
        SpellInstruction first = instructions.get(0);
        return new CraftedSpell("Combined world law", SourceType.CREATE, first.element(), SpellForm.BOLT,
                DeliveryType.PROJECTILE, first.impact(), first.power(), CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 8, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, instructions);
    }

    private static CraftedSpell combinedPracticeSpell(Element firstElement, Element secondElement) {
        java.util.List<SpellInstruction> instructions = java.util.List.of(
                operation(firstElement, ImpactType.DAMAGE, CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY),
                operation(secondElement, ImpactType.DAMAGE, CastDirection.LOOK, PhysicsOperation.ADD_VELOCITY));
        SpellInstruction first = instructions.get(0);
        return new CraftedSpell("Synthesis practice", SourceType.CREATE, first.element(), SpellForm.BOLT,
                DeliveryType.PROJECTILE, first.impact(), first.power(), CastDirection.LOOK, false,
                ConditionType.ALWAYS, 20, 8, TargetMode.AIM, ProgramTargetMode.CASTER_AIM,
                PhysicsOperation.ADD_VELOCITY, instructions);
    }
}
