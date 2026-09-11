package com.strutton.dynamicmagic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.stream.Collectors;
import com.strutton.dynamicmagic.storage.MagicStorage;
import com.strutton.dynamicmagic.mana.Mana;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.strutton.dynamicmagic.dragon.DragonProgression;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillKnowledge;

public final class MagicCommands {
    private MagicCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("magic");
        LiteralArgumentBuilder<CommandSourceStack> learn = Commands.literal("learn");
        learn.then(Commands.literal("all").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ElementKnowledge.learnAll(player);
            ComponentKnowledge.learnAll(player);
            context.getSource().sendSuccess(() -> Component.literal("You learned every element and spell technique."), false);
            return Element.values().length + com.strutton.dynamicmagic.magic.SpellForm.values().length
                    + com.strutton.dynamicmagic.magic.DeliveryType.values().length + com.strutton.dynamicmagic.magic.ImpactType.values().length;
        }));
        for (Element element : Element.values()) {
            learn.then(Commands.literal(element.name().toLowerCase(java.util.Locale.ROOT)).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                boolean learned = ElementKnowledge.learn(player, element);
                context.getSource().sendSuccess(() -> Component.literal(learned
                        ? "You learned the " + element.displayName() + " element."
                        : "You already know the " + element.displayName() + " element."), false);
                return learned ? 1 : 0;
            }));
        }
        root.then(learn);
        root.then(Commands.literal("known").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String known = ElementKnowledge.known(player).stream().map(Element::displayName).collect(Collectors.joining(", "));
            context.getSource().sendSuccess(() -> Component.literal(known.isEmpty() ? "You have not learned any elements." : "Known elements: " + known), false);
            return ElementKnowledge.known(player).size();
        }));
        root.then(Commands.literal("storage").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            int count = MagicStorage.size(player);
            context.getSource().sendSuccess(() -> Component.literal("Magic storage: " + count + "/" + MagicStorage.capacity(player)), false);
            return count;
        }));
        root.then(Commands.literal("mastery").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            var stats = com.strutton.dynamicmagic.magic.CasterMastery.stats(player);
            String caps = ElementKnowledge.known(player).stream().map(element -> element.displayName() + " "
                    + String.format(java.util.Locale.ROOT, "%.1f force, affinity %d/10, %.0f/%.0f enlightenment",
                    com.strutton.dynamicmagic.magic.ElementMastery.maxForce(player, element),
                    com.strutton.dynamicmagic.knowledge.ElementAffinity.get(player, element),
                    ComponentKnowledge.enlightenment(player, element),
                    ComponentKnowledge.breakthroughRequirement(player, element)))
                    .collect(Collectors.joining(", "));
            context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "Magic mastery — Control %.2f, Efficiency %.2f, Projectile accuracy %.1f%%; Programming %.1f (%d branches); elements: %s",
                    stats.control(), stats.efficiency(),
                    com.strutton.dynamicmagic.magic.ProjectileAccuracy.value(player) * 100,
                    com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge.mastery(player),
                    com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge.maxBranches(player), caps)), false);
            return (int)stats.control();
        }));
        root.then(Commands.literal("skills").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String skills = java.util.Arrays.stream(com.strutton.dynamicmagic.skill.MagicSkill.values())
                    .filter(skill -> com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player, skill))
                    .map(com.strutton.dynamicmagic.skill.MagicSkill::displayName).collect(Collectors.joining(", "));
            context.getSource().sendSuccess(() -> Component.literal(skills.isEmpty() ? "No discovered magic skills." : "Magic skills: " + skills), false);
            return skills.isEmpty() ? 0 : 1;
        }));
        root.then(Commands.literal("ki").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean installed = com.strutton.dynamicmagic.compat.EternalCultivationBridge.installed();
            boolean fusion = SkillKnowledge.knows(player, MagicSkill.KI_MANA_FUSION);
            int current = installed ? com.strutton.dynamicmagic.compat.EternalCultivationBridge.current(player) : 0;
            int maximum = installed ? com.strutton.dynamicmagic.compat.EternalCultivationBridge.maximum(player) : 0;
            context.getSource().sendSuccess(() -> Component.literal(installed
                    ? "Eternal Cultivation qi: " + current + '/' + maximum + "; Ki-Mana Fusion: "
                    + (fusion ? "learned" : "unknown") + ". Qi and mana remain separate."
                    : "Eternal Cultivation is not loaded; Ki spells cannot draw qi."), false);
            return installed ? current : 0;
        }));

        LiteralArgumentBuilder<CommandSourceStack> skillCommands = Commands.literal("skill");
        skillCommands.then(Commands.literal("list").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String skills = java.util.Arrays.stream(MagicSkill.values()).filter(skill -> SkillKnowledge.knows(player, skill))
                    .map(MagicSkill::displayName).collect(Collectors.joining(", "));
            context.getSource().sendSuccess(() -> Component.literal(skills.isEmpty()
                    ? "No discovered magic skills." : "Magic skills: " + skills), false);
            return skills.isEmpty() ? 0 : 1;
        }));
        LiteralArgumentBuilder<CommandSourceStack> giveSkill = Commands.literal("give")
                .requires(source -> source.hasPermission(2));
        giveSkill.then(Commands.literal("all").executes(context -> {
            int learned = SkillKnowledge.learnAll(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal("Granted every magic skill ("
                    + learned + " newly learned)."), false);
            return Math.max(1, learned);
        }));
        LiteralArgumentBuilder<CommandSourceStack> removeSkill = Commands.literal("remove")
                .requires(source -> source.hasPermission(2));
        removeSkill.then(Commands.literal("all").executes(context -> {
            SkillKnowledge.forgetAll(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal("Removed every magic skill."), false);
            return 1;
        }));
        for (MagicSkill skill : MagicSkill.values()) {
            String id = skill.name().toLowerCase(java.util.Locale.ROOT);
            giveSkill.then(Commands.literal(id).executes(context -> {
                boolean learned = SkillKnowledge.learn(context.getSource().getPlayerOrException(), skill);
                context.getSource().sendSuccess(() -> Component.literal(learned
                        ? "Granted " + skill.displayName() + '.' : "You already know " + skill.displayName() + '.'), false);
                return learned ? 1 : 0;
            }));
            removeSkill.then(Commands.literal(id).executes(context -> {
                boolean removed = SkillKnowledge.forget(context.getSource().getPlayerOrException(), skill);
                context.getSource().sendSuccess(() -> Component.literal(removed
                        ? "Removed " + skill.displayName() + '.' : "You did not know " + skill.displayName() + '.'), false);
                return removed ? 1 : 0;
            }));
        }
        skillCommands.then(giveSkill);
        skillCommands.then(removeSkill);
        root.then(skillCommands);

        LiteralArgumentBuilder<CommandSourceStack> elementalAffinity = Commands.literal("elemental_affinity")
                .requires(source -> source.hasPermission(2));
        for (Element element : Element.values()) {
            elementalAffinity.then(Commands.literal(element.name().toLowerCase(java.util.Locale.ROOT))
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 10)).executes(context -> {
                        int level = IntegerArgumentType.getInteger(context, "level");
                        com.strutton.dynamicmagic.knowledge.ElementAffinity.set(
                                context.getSource().getPlayerOrException(), element, level);
                        context.getSource().sendSuccess(() -> Component.literal(element.displayName()
                                + " affinity set to " + level + "/10."), false);
                        return 1;
                    })));
        }
        root.then(elementalAffinity);

        root.then(Commands.literal("mage").then(Commands.literal("spawn_necromancer")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    net.minecraft.world.entity.monster.ZombieVillager necromancer =
                            net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER.create(player.serverLevel());
                    if (necromancer == null) return 0;
                    necromancer.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1,
                            player.getYRot(), 0);
                    com.strutton.dynamicmagic.mage.VillageMageEvents.makeNecromancer(necromancer);
                    player.serverLevel().addFreshEntity(necromancer);
                    context.getSource().sendSuccess(() -> Component.literal("Summoned "
                            + necromancer.getName().getString() + '.'), false);
                    return 1;
                })));
        root.then(Commands.literal("mage").then(Commands.literal("spawn_skeleton_necromancer")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    com.strutton.dynamicmagic.entity.SkeletonMageVillager necromancer =
                            com.strutton.dynamicmagic.DynamicMagic.SKELETON_MAGE_VILLAGER.get()
                                    .create(player.serverLevel());
                    if (necromancer == null) return 0;
                    necromancer.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1,
                            player.getYRot(), 0);
                    com.strutton.dynamicmagic.mage.VillageMageEvents.makeSkeletonNecromancer(necromancer);
                    player.serverLevel().addFreshEntity(necromancer);
                    context.getSource().sendSuccess(() -> Component.literal("Summoned "
                            + necromancer.getName().getString() + '.'), false);
                    return 1;
                })));
        root.then(Commands.literal("mage").then(Commands.literal("spawn_creeper_villager")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    com.strutton.dynamicmagic.entity.CreeperVillager scholar =
                            com.strutton.dynamicmagic.DynamicMagic.CREEPER_VILLAGER.get().create(player.serverLevel());
                    if (scholar == null) return 0;
                    scholar.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1, player.getYRot(), 0);
                    scholar.setPersistenceRequired();
                    player.serverLevel().addFreshEntity(scholar);
                    context.getSource().sendSuccess(() -> Component.literal("Summoned "
                            + scholar.getName().getString() + '.'), false);
                    return 1;
                })));
        root.then(Commands.literal("mage").then(Commands.literal("spawn_enderman_villager")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var scholar = com.strutton.dynamicmagic.DynamicMagic.ENDERMAN_VILLAGER.get()
                            .create(player.serverLevel());
                    if (scholar == null) return 0;
                    scholar.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1, player.getYRot(), 0);
                    scholar.setPersistenceRequired();
                    player.serverLevel().addFreshEntity(scholar);
                    context.getSource().sendSuccess(() -> Component.literal("Summoned "
                            + scholar.getName().getString() + '.'), false);
                    return 1;
                })));
        root.then(Commands.literal("mage").then(Commands.literal("spawn_alex_villager")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var scholar = com.strutton.dynamicmagic.DynamicMagic.ALEX_VILLAGER.get()
                            .create(player.serverLevel());
                    if (scholar == null) return 0;
                    scholar.moveTo(player.getX() + 1, player.getY(), player.getZ() + 1, player.getYRot(), 0);
                    scholar.setPersistenceRequired();
                    player.serverLevel().addFreshEntity(scholar);
                    context.getSource().sendSuccess(() -> Component.literal("Summoned "
                            + scholar.getName().getString() + '.'), false);
                    return 1;
                })));
        root.then(Commands.literal("explosion_progress").executes(context -> {
            double exposure = com.strutton.dynamicmagic.skill.ExplosionProgression.exposure(
                    context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "Explosion resistance exposure: %.1f/120", exposure)), false);
            return (int) exposure;
        }));

        LiteralArgumentBuilder<CommandSourceStack> dragon = Commands.literal("dragon");
        dragon.then(Commands.literal("progress").executes(context -> {
            DragonProgression.Progress progress = DragonProgression.progress(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal(progress.summary()), false);
            return progress.kills();
        }));
        LiteralArgumentBuilder<CommandSourceStack> dragonLore = Commands.literal("lore")
                .requires(source -> source.hasPermission(2));
        dragonLore.then(Commands.literal("set").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DragonProgression.setLore(player, DoubleArgumentType.getDouble(context, "amount"));
                    context.getSource().sendSuccess(() -> Component.literal(DragonProgression.progress(player).summary()), false);
                    return 1;
                })));
        dragonLore.then(Commands.literal("add").then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DragonProgression.addTestingLore(player, DoubleArgumentType.getDouble(context, "amount"));
                    context.getSource().sendSuccess(() -> Component.literal(DragonProgression.progress(player).summary()), false);
                    return 1;
                })));
        dragon.then(dragonLore);
        LiteralArgumentBuilder<CommandSourceStack> affinity = Commands.literal("affinity")
                .requires(source -> source.hasPermission(2));
        for (Element element : Element.values()) {
            affinity.then(Commands.literal(element.name().toLowerCase(java.util.Locale.ROOT))
                    .then(Commands.argument("level", IntegerArgumentType.integer(0, 5)).executes(context -> {
                        int level = IntegerArgumentType.getInteger(context, "level");
                        DragonProgression.setAffinity(context.getSource().getPlayerOrException(), element, level);
                        context.getSource().sendSuccess(() -> Component.literal(element.displayName()
                                + " dragon affinity set to " + level + "/5."), false);
                        return 1;
                    })));
        }
        dragon.then(affinity);
        dragon.then(Commands.literal("reset").requires(source -> source.hasPermission(2)).executes(context -> {
            DragonProgression.reset(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal("Dragon lore, kills, and affinities reset."), false);
            return 1;
        }));
        root.then(dragon);

        LiteralArgumentBuilder<CommandSourceStack> vampire = Commands.literal("vampire");
        vampire.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean infected = com.strutton.dynamicmagic.vampire.Vampirism.isVampire(player);
            context.getSource().sendSuccess(() -> Component.literal(infected
                    ? "Vampire status: infected." : "Vampire status: human."), false);
            return infected ? 1 : 0;
        }));
        vampire.then(Commands.literal("infect").requires(source -> source.hasPermission(2)).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean infected = com.strutton.dynamicmagic.vampire.Vampirism.infect(player);
            context.getSource().sendSuccess(() -> Component.literal(infected
                    ? "Vampirism applied." : "Vampirism was blocked or already present."), false);
            return infected ? 1 : 0;
        }));
        vampire.then(Commands.literal("cure").requires(source -> source.hasPermission(2)).executes(context -> {
            boolean cured = com.strutton.dynamicmagic.vampire.Vampirism.cure(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal(cured
                    ? "Vampirism cured." : "No vampirism was present."), false);
            return cured ? 1 : 0;
        }));
        root.then(vampire);

        LiteralArgumentBuilder<CommandSourceStack> werewolf = Commands.literal("werewolf");
        werewolf.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean cursed = com.strutton.dynamicmagic.werewolf.Werewolves.isWerewolf(player);
            context.getSource().sendSuccess(() -> Component.literal(cursed
                    ? "Werewolf status: infected." : "Werewolf status: human."), false);
            return cursed ? 1 : 0;
        }));
        werewolf.then(Commands.literal("infect").requires(source -> source.hasPermission(2)).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean infected = com.strutton.dynamicmagic.werewolf.Werewolves.infect(player);
            context.getSource().sendSuccess(() -> Component.literal(infected
                    ? "Werewolf curse applied." : "Werewolf infection was blocked, already present, or the addon is absent."), false);
            return infected ? 1 : 0;
        }));
        werewolf.then(Commands.literal("cure").requires(source -> source.hasPermission(2)).executes(context -> {
            boolean cured = com.strutton.dynamicmagic.werewolf.Werewolves.cure(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal(cured
                    ? "Werewolf curse cured." : "No werewolf curse was present."), false);
            return cured ? 1 : 0;
        }));
        root.then(werewolf);
        LiteralArgumentBuilder<CommandSourceStack> morph = Commands.literal("morph");
        morph.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            boolean installed = com.strutton.dynamicmagic.morph.MorphV2Integration.installed();
            boolean active = installed && com.strutton.dynamicmagic.morph.MorphV2Integration.isMorphed(player);
            double upkeep = active ? com.strutton.dynamicmagic.morph.MorphV2Integration.currentMaintenance(player) : 0;
            long cooldown = com.strutton.dynamicmagic.morph.MorphV2Integration.cooldownTicks(player);
            context.getSource().sendSuccess(() -> Component.literal(installed
                    ? String.format(java.util.Locale.ROOT, "MorphV2: installed; skill: %s; transformed: %s; upkeep: %.2f mana/second; cooldown: %.1f seconds.",
                    SkillKnowledge.knows(player, MagicSkill.MORPHING) ? "learned" : "unknown", active, upkeep, cooldown / 20.0)
                    : "MorphV2 is not installed."), false);
            return active ? 1 : 0;
        }));
        morph.then(Commands.literal("make_mage").requires(source -> source.hasPermission(2)).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            var nearest = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                            player.getBoundingBox().inflate(8), entity ->
                                    !com.strutton.dynamicmagic.mage.MorphMageEvents.isMorphMage(entity))
                    .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            boolean changed = nearest != null && com.strutton.dynamicmagic.mage.MorphMageEvents.makeMorphMage(nearest);
            context.getSource().sendSuccess(() -> Component.literal(changed
                    ? "The nearest mob is now a disguised morph mage."
                    : "No eligible non-boss mob was found within eight blocks."), false);
            return changed ? 1 : 0;
        }));
        root.then(morph);
        LiteralArgumentBuilder<CommandSourceStack> teleport = Commands.literal("teleport");
        teleport.then(Commands.literal("menu").executes(context -> {
            com.strutton.dynamicmagic.magic.TeleportLocations.openMenu(context.getSource().getPlayerOrException());
            return 1;
        }));
        teleport.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(context, "name");
                    int slot = com.strutton.dynamicmagic.magic.TeleportLocations.save(player, name);
                    context.getSource().sendSuccess(() -> Component.literal(slot >= 0
                            ? "Saved teleport mark '" + name + "' in slot " + (slot + 1) + '.'
                            : "All nine teleport marks are occupied; reuse an existing name to update it."), false);
                    return slot >= 0 ? 1 : 0;
                })));
        teleport.then(Commands.literal("enderman_study").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double progress = com.strutton.dynamicmagic.knowledge.StudyKnowledge.endermanProgress(player);
            context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "Enderman teleport study: %.1f/64", progress)), false);
            return (int) progress;
        }));
        root.then(teleport);

        LiteralArgumentBuilder<CommandSourceStack> brewing = Commands.literal("brewing");
        brewing.then(Commands.literal("status").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double mastery = com.strutton.dynamicmagic.mana.ManaBrewing.mastery(player);
            int level = com.strutton.dynamicmagic.mana.ManaBrewing.masteryLevel(player);
            context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "Mana Brewing: %s; mastery %.1f (level %d/20); time reduction %.1f%%; mana reduction %.1f%%.",
                    SkillKnowledge.knows(player, MagicSkill.MANA_BREWING) ? "learned" : "undiscovered",
                    mastery, level, level * 2.5, level * 2.0)), false);
            return level;
        }));
        brewing.then(Commands.literal("mastery").requires(source -> source.hasPermission(2))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    double value = DoubleArgumentType.getDouble(context, "value");
                    com.strutton.dynamicmagic.mana.ManaBrewing.setMastery(player, value);
                    SkillKnowledge.learn(player, MagicSkill.MANA_BREWING);
                    int level = com.strutton.dynamicmagic.mana.ManaBrewing.masteryLevel(player);
                    context.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                            "Mana Brewing mastery set to %.1f (level %d/20).", value, level)), false);
                    return level;
                })));
        LiteralArgumentBuilder<CommandSourceStack> giveBrew = Commands.literal("give")
                .requires(source -> source.hasPermission(2));
        for (String id : java.util.List.of("mana", "vampire_cure", "reset", "expand_1", "expand_5", "expand_10", "expand_25", "expand_50")) {
            giveBrew.then(Commands.literal(id).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                var recipe = com.strutton.dynamicmagic.mana.ManaBrewing.recipe(id);
                var stack = com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(recipe.output());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                context.getSource().sendSuccess(() -> Component.literal("Granted mana-brewing potion: " + id + '.'), false);
                return 1;
            }));
        }
        brewing.then(giveBrew);
        root.then(brewing);

        LiteralArgumentBuilder<CommandSourceStack> mana = Commands.literal("mana");
        LiteralArgumentBuilder<CommandSourceStack> setMana = Commands.literal("set");
        setMana.then(Commands.literal("unlimited").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Mana.setUnlimited(player, true);
            context.getSource().sendSuccess(() -> Component.literal("Mana pool set to unlimited."), false);
            return 1;
        }));
        setMana.then(Commands.argument("maximum", DoubleArgumentType.doubleArg(1)).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double maximum = DoubleArgumentType.getDouble(context, "maximum");
            Mana.setMaximum(player, maximum);
            Mana.refill(player);
            context.getSource().sendSuccess(() -> Component.literal("Maximum mana set to " + maximum + "."), false);
            return 1;
        }));
        mana.then(setMana);
        mana.then(Commands.literal("refill").executes(context -> {
            Mana.refill(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal("Mana refilled."), false);
            return 1;
        }));
        root.then(mana);
        root.then(Commands.literal("forget_all").requires(source -> source.hasPermission(2)).executes(context -> {
            ElementKnowledge.forgetAll(context.getSource().getPlayerOrException());
            ComponentKnowledge.reset(context.getSource().getPlayerOrException());
            com.strutton.dynamicmagic.magic.ProjectileAccuracy.reset(context.getSource().getPlayerOrException());
            com.strutton.dynamicmagic.knowledge.RelatedElementKnowledge.reset(context.getSource().getPlayerOrException());
            com.strutton.dynamicmagic.knowledge.ElementAffinity.reset(context.getSource().getPlayerOrException());
            context.getSource().sendSuccess(() -> Component.literal("Your elemental knowledge was cleared."), false);
            return 1;
        }));
        var command = event.getDispatcher().register(root);
        event.getDispatcher().register(Commands.literal("dynamicmagic").redirect(command));
    }
}
