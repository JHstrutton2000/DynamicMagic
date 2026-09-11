package com.strutton.dynamicmagic.network;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.magic.ImpactType;
import com.strutton.dynamicmagic.knowledge.ComponentKnowledge;
import com.strutton.dynamicmagic.client.ClientPayloadHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.strutton.dynamicmagic.storage.MagicStorage;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import com.strutton.dynamicmagic.magic.CasterMastery;
import com.strutton.dynamicmagic.magic.SavedSpellLibrary;
import com.strutton.dynamicmagic.magic.ElementMastery;
import com.strutton.dynamicmagic.knowledge.ProgrammingKnowledge;

public final class SpellNetworking {
    private SpellNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9");
        registrar.playToServer(
                CreateSpellPayload.TYPE,
                CreateSpellPayload.STREAM_CODEC,
                SpellNetworking::handleCreateSpell);
        registrar.playToServer(OpenSpellcraftRequest.TYPE, OpenSpellcraftRequest.STREAM_CODEC,
                SpellNetworking::handleOpenSpellcraft);
        registrar.playToClient(
                OpenSpellbookPayload.TYPE,
                OpenSpellbookPayload.STREAM_CODEC,
                SpellNetworking::handleOpenSpellbookClient);
        registrar.playToClient(ManaSyncPayload.TYPE, ManaSyncPayload.STREAM_CODEC,
                SpellNetworking::handleManaSyncClient);
        registrar.playToClient(OpenEntityStoragePayload.TYPE, OpenEntityStoragePayload.STREAM_CODEC,
                SpellNetworking::handleOpenEntityStorageClient);
        registrar.playToClient(MorphMageSyncPayload.TYPE, MorphMageSyncPayload.STREAM_CODEC,
                SpellNetworking::handleMorphMageSyncClient);
        registrar.playToServer(SummonStoredEntityPayload.TYPE, SummonStoredEntityPayload.STREAM_CODEC,
                SpellNetworking::handleSummonEntity);
        registrar.playToServer(BindSpellPayload.TYPE, BindSpellPayload.STREAM_CODEC, SpellNetworking::handleBindSpell);
        registrar.playToServer(DeleteSpellPayload.TYPE, DeleteSpellPayload.STREAM_CODEC, SpellNetworking::handleDeleteSpell);
        registrar.playToServer(CastBoundSpellPayload.TYPE, CastBoundSpellPayload.STREAM_CODEC, SpellNetworking::handleCastBoundSpell);
        registrar.playToServer(SaveElementPresetPayload.TYPE, SaveElementPresetPayload.STREAM_CODEC, SpellNetworking::handleSaveElementPreset);
        registrar.playToServer(SpatialStorageHeartbeatPayload.TYPE, SpatialStorageHeartbeatPayload.STREAM_CODEC, SpellNetworking::handleSpatialHeartbeat);
        registrar.playToClient(CloseSpatialStoragePayload.TYPE, CloseSpatialStoragePayload.STREAM_CODEC, SpellNetworking::handleCloseSpatialClient);
        registrar.playToClient(OpenSpellNodePayload.TYPE, OpenSpellNodePayload.STREAM_CODEC, SpellNetworking::handleOpenSpellNodeClient);
        registrar.playToServer(ConfigureSpellNodePayload.TYPE, ConfigureSpellNodePayload.STREAM_CODEC, SpellNetworking::handleConfigureSpellNode);
    }

    public static void openSpellbook(ServerPlayer player, CraftedSpell editing) {
        ItemStack book = com.strutton.dynamicmagic.magic.SpellbookBindings.heldBook(player);
        java.util.List<Double> masteries = java.util.Arrays.stream(Element.values())
                .map(element -> ElementMastery.experience(player, element)).toList();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new OpenSpellbookPayload(ComponentKnowledge.snapshot(player).elements(),
                        ComponentKnowledge.snapshot(player).forms(), ComponentKnowledge.snapshot(player).deliveries(),
                        ComponentKnowledge.snapshot(player).impacts(), ComponentKnowledge.snapshot(player).directions(),
                        ComponentKnowledge.snapshot(player).conditions(), ComponentKnowledge.snapshot(player).programming(),
                        ComponentKnowledge.snapshot(player).programmingMastery(), ComponentKnowledge.snapshot(player).maxProgramBranches(),
                        CasterMastery.stats(player).control(), CasterMastery.stats(player).efficiency(),
                        SavedSpellLibrary.spells(player), editing, ElementMastery.forceCaps(player),
                        book.isEmpty() ? java.util.stream.IntStream.range(0, 6).mapToObj(i -> java.util.List.<String>of()).toList()
                                : com.strutton.dynamicmagic.magic.SpellbookBindings.names(book),
                        com.strutton.dynamicmagic.skill.SkillKnowledge.mask(player), masteries, !book.isEmpty(),
                        com.strutton.dynamicmagic.magic.ElementPresetLibrary.presets(player)));
    }

    private static void handleBindSpell(BindSpellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack book = com.strutton.dynamicmagic.magic.SpellbookBindings.heldBook(player);
            if (book.isEmpty()) return;
            boolean changed = payload.spellName().isBlank()
                    ? com.strutton.dynamicmagic.magic.SpellbookBindings.clear(book, payload.slot())
                    : com.strutton.dynamicmagic.magic.SpellbookBindings.bind(player, book, payload.slot(), payload.spellName(), payload.append());
            if (!changed) player.displayClientMessage(Component.literal("That binding is unavailable.").withStyle(ChatFormatting.RED), true);
            openSpellbook(player, null);
        });
    }

    private static void handleDeleteSpell(DeleteSpellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (SavedSpellLibrary.remove(player, payload.spellName())) {
                ItemStack book = com.strutton.dynamicmagic.magic.SpellbookBindings.heldBook(player);
                if (!book.isEmpty()) com.strutton.dynamicmagic.magic.SpellbookBindings.forget(book, payload.spellName());
                player.displayClientMessage(Component.literal("Forgot " + payload.spellName()).withStyle(ChatFormatting.GRAY), true);
            }
            openSpellbook(player, null);
        });
    }

    private static void handleCastBoundSpell(CastBoundSpellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || payload.slot() < 0 || payload.slot() >= 5) return;
            ItemStack book = com.strutton.dynamicmagic.magic.SpellbookBindings.heldBook(player);
            if (book.isEmpty()) return;
            if (payload.pressed()) com.strutton.dynamicmagic.magic.BoundSpellCasting.begin(player, book,
                    com.strutton.dynamicmagic.magic.SpellbookBindings.heldHand(player), payload.slot());
            else {
                com.strutton.dynamicmagic.magic.BoundSpellCasting.release(player);
                player.stopUsingItem();
            }
        });
    }

    private static void handleSaveElementPreset(SaveElementPresetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || payload.preset() == null) return;
            boolean changed = payload.delete()
                    ? com.strutton.dynamicmagic.magic.ElementPresetLibrary.remove(player, payload.preset().name())
                    : com.strutton.dynamicmagic.magic.ElementPresetLibrary.save(player, payload.preset());
            if (!changed) player.displayClientMessage(Component.literal("Learn Mana Combining and every selected element first.")
                    .withStyle(ChatFormatting.RED), true);
            openSpellbook(player, null);
        });
    }
    private static void handleSpatialHeartbeat(SpatialStorageHeartbeatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player && !com.strutton.dynamicmagic.mana.Mana.consume(player, .5))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CloseSpatialStoragePayload()); });
    }
    private static void handleCloseSpatialClient(CloseSpatialStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> net.minecraft.client.Minecraft.getInstance().setScreen(null));
    }
    private static void handleOpenSpellNodeClient(OpenSpellNodePayload payload, IPayloadContext context) { ClientPayloadHandler.openSpellNode(payload, context); }
    private static void handleConfigureSpellNode(ConfigureSpellNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(payload.pos().getX()+.5,payload.pos().getY()+.5,payload.pos().getZ()+.5)>64) return; if (player.serverLevel().getBlockEntity(payload.pos()) instanceof com.strutton.dynamicmagic.block.SpellAutomationBlockEntity node) { if (payload.ejectSlot() >= 0) node.eject(player,payload.ejectSlot()); else node.setSpellFromMemory(player,payload.spellName()); }});
    }

    private static void handleOpenSpellbookClient(OpenSpellbookPayload payload, IPayloadContext context) {
        ClientPayloadHandler.openSpellbook(payload, context);
    }

    private static void handleManaSyncClient(ManaSyncPayload payload, IPayloadContext context) {
        ClientPayloadHandler.syncMana(payload, context);
    }

    private static void handleOpenEntityStorageClient(OpenEntityStoragePayload payload, IPayloadContext context) {
        ClientPayloadHandler.openEntityStorage(payload, context);
    }

    private static void handleMorphMageSyncClient(MorphMageSyncPayload payload, IPayloadContext context) {
        ClientPayloadHandler.syncMorphMage(payload, context);
    }

    private static void handleOpenSpellcraft(OpenSpellcraftRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            CraftedSpell editing = null;
            if (payload.editHeld()) {
                editing = CraftedSpell.read(player.getMainHandItem());
                if (editing == null) editing = CraftedSpell.read(player.getOffhandItem());
                if (editing == null) {
                    player.displayClientMessage(Component.literal("Hold a crafted spell before pressing Edit Spell.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            openSpellbook(player, editing);
        });
    }

    private static void handleSummonEntity(SummonStoredEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && ElementKnowledge.knows(player, Element.SPACE))
                MagicStorage.release(player, payload.index());
        });
    }

    private static void handleCreateSpell(CreateSpellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            CraftedSpell spell = payload.spell();
            if (!com.strutton.dynamicmagic.magic.SpellResourcePayment.validate(player, spell, true)) return;
            if (spell.allInstructions().stream().map(com.strutton.dynamicmagic.magic.SpellInstruction::element)
                    .distinct().count() > 1 && !com.strutton.dynamicmagic.skill.SkillKnowledge.knows(player,
                    com.strutton.dynamicmagic.skill.MagicSkill.MANA_COMBINING)) {
                player.displayClientMessage(Component.literal("Combining elements requires the Mana Combining skill.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!ComponentKnowledge.canUse(player, spell)) {
                player.displayClientMessage(Component.literal("That spell uses magical knowledge you have not learned.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (spell.programmed() && spell.branches().size() > ProgrammingKnowledge.maxBranches(player)) {
                player.displayClientMessage(Component.literal("Your programming mastery supports only "
                        + ProgrammingKnowledge.maxBranches(player) + " branch(es).").withStyle(ChatFormatting.RED), true);
                return;
            }
            int timeOperations = 0;
            for (var instruction : spell.allInstructions()) {
                double forceCap = ElementMastery.maxForce(player, instruction.element());
                if (instruction.power() > forceCap + .001) {
                    player.displayClientMessage(Component.literal("Your " + instruction.element().displayName()
                            + " mastery cannot safely form force "
                            + String.format(java.util.Locale.ROOT, "%.1f", instruction.power()) + " (maximum "
                            + String.format(java.util.Locale.ROOT, "%.1f", forceCap) + ").")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (isTimeImpact(instruction.impact())) {
                    timeOperations++;
                    if (instruction.element() != Element.TIME) {
                        player.displayClientMessage(Component.literal("Time manipulation requires the Time element.")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                }
                if (isSpaceImpact(instruction.impact()) && instruction.element() != Element.SPACE) {
                    player.displayClientMessage(Component.literal("Magic storage requires the Space element.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                if ((instruction.impact() == ImpactType.HEAL || instruction.impact() == ImpactType.CLEANSE)
                        && instruction.element() != Element.DIVINE) {
                    player.displayClientMessage(Component.literal("Healing and cleansing require the Divine element.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (isUtilityImpact(instruction.impact()) && instruction.repetitions() > 1) {
                    player.displayClientMessage(Component.literal("Storage, contracts, conjuring, and time operations cannot repeat inside one step.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            if (timeOperations > 0 && (spell.branches().size() > 1 || spell.allInstructions().size() > 1)) {
                player.displayClientMessage(Component.literal("A global time operation must be the spell's only step.")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            ItemStack stack = ItemStack.EMPTY;
            if (payload.editHeld()) {
                stack = CraftedSpell.read(player.getMainHandItem()) != null ? player.getMainHandItem()
                        : CraftedSpell.read(player.getOffhandItem()) != null ? player.getOffhandItem() : ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    player.displayClientMessage(Component.literal("The spell being edited is no longer in your hand.")
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
                spell.writeTo(stack);
            } else if (!payload.memoryOnly()) {
                stack = new ItemStack(DynamicMagic.CRAFTED_SPELL.get());
                spell.writeTo(stack);
            }
            boolean newProgram = spell.programmed() && SavedSpellLibrary.spells(player).stream()
                    .noneMatch(saved -> saved.name().equalsIgnoreCase(spell.name()));
            if (payload.memoryOnly() && !payload.originalName().isBlank()
                    && !payload.originalName().equalsIgnoreCase(spell.name())) SavedSpellLibrary.remove(player, payload.originalName());
            SavedSpellLibrary.remember(player, spell);
            if (newProgram) ProgrammingKnowledge.practice(player, 2 + spell.branches().size());
            if (!payload.editHeld() && !payload.memoryOnly() && !player.getInventory().add(stack)) player.drop(stack, false);
            player.displayClientMessage(Component.literal((payload.editHeld() ? "Updated " : payload.memoryOnly() ? "Saved " : "Created ") + spell.name()).withStyle(ChatFormatting.AQUA), true);
        }).exceptionally(error -> {
            DynamicMagic.LOGGER.error("Failed to create spell", error);
            return null;
        });
    }

    private static boolean isTimeImpact(ImpactType impact) {
        return impact == ImpactType.STOP_TIME || impact == ImpactType.SPEED_TIME || impact == ImpactType.SLOW_TIME;
    }

    private static boolean isSpaceImpact(ImpactType impact) {
        return impact == ImpactType.STORE_ITEM || impact == ImpactType.STORE_ENTITY || impact == ImpactType.RELEASE_STORAGE
                || impact == ImpactType.TELEPORT || impact == ImpactType.CONTRACT_ENTITY || impact == ImpactType.SUMMON_CONTRACT;
    }

    private static boolean isUtilityImpact(ImpactType impact) {
        return isTimeImpact(impact) || isSpaceImpact(impact) || impact == ImpactType.CONJURE_ITEM;
    }
}
