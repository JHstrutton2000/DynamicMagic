package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.knowledge.KnowledgeSnapshot;
import com.strutton.dynamicmagic.magic.*;
import com.strutton.dynamicmagic.network.CreateSpellPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Visual editor for a carrier and either a normal action chain or independent IF/THEN branches. */
public final class SpellcraftScreen extends Screen {
    private final CasterStats previewStats;
    private final KnowledgeSnapshot knowledge;
    private final List<CraftedSpell> savedSpells;
    private final boolean editMode;
    private final boolean memoryOnly;
    private final String originalName;
    private final List<Double> forceCaps;
    private final List<SpellInstruction> standalone = new ArrayList<>();
    private final List<BranchDraft> branches = new ArrayList<>();
    private SourceType source;
    private SpellForm form;
    private DeliveryType delivery;
    private boolean programmed;
    private int selectedBranch;
    private int selectedInstruction;
    private EditBox nameBox;
    private Button conditionButton, intervalButton, sensorButton, programTargetButton, physicsButton;
    private String draftName;

    public SpellcraftScreen(KnowledgeSnapshot knowledge, CasterStats previewStats,
                            List<CraftedSpell> savedSpells, CraftedSpell editing, List<Double> forceCaps) {
        this(knowledge, previewStats, savedSpells, editing, forceCaps, editing != null, false);
    }
    public SpellcraftScreen(KnowledgeSnapshot knowledge, CasterStats previewStats,
                            List<CraftedSpell> savedSpells, CraftedSpell editing, List<Double> forceCaps,
                            boolean editHeld, boolean memoryOnly) {
        super(Component.literal("Spellcrafting"));
        this.knowledge = knowledge;
        this.previewStats = previewStats;
        this.savedSpells = List.copyOf(savedSpells);
        this.editMode = editHeld;
        this.memoryOnly = memoryOnly;
        this.originalName = editing == null ? "" : editing.name();
        this.draftName = editing == null ? "Custom Spell" : editing.name();
        this.forceCaps = List.copyOf(forceCaps);
        source = editing == null ? SourceType.CREATE : editing.source();
        form = editing == null ? first(SpellForm.values(), knowledge::knows) : editing.form();
        delivery = editing == null ? first(DeliveryType.values(), knowledge::knows) : editing.delivery();
        if (editing == null) {
            Element element = first(Element.values(), knowledge::knows);
            ImpactType impact = first(allowedImpactsFor(element), knowledge::knows);
            standalone.add(SpellInstruction.legacy(element, impact, 1, CastDirection.LOOK,
                    TargetMode.AIM, PhysicsOperation.ADD_VELOCITY, form, delivery));
        } else if (editing.programmed()) {
            programmed = true;
            editing.branches().forEach(branch -> branches.add(new BranchDraft(branch)));
            standalone.addAll(editing.instructions());
        } else standalone.addAll(editing.instructions());
    }

    @Override protected void init() {
        int panelWidth = Math.min(780, width - 20), left = (width - panelWidth) / 2;
        int favoritesWidth = 230, builderLeft = left + favoritesWidth + 14;
        int builderWidth = panelWidth - favoritesWidth - 14, half = builderWidth / 2 - 2;
        int top = Math.max(6, (height - 438) / 2);
        addRenderableWidget(Button.builder(Component.literal("Slots"), b -> ClientSpellbook.openLoadout()).bounds(left, top, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Craft"), b -> {}).bounds(left + 62, top, 58, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal("Elements"), b -> ClientSpellbook.openKnowledge()).bounds(left + 124, top, 76, 20).build());
        int favoriteIndex = 0;
        for (CraftedSpell favorite : concat(PremadeSpells.FAVORITES, savedSpells)) {
            if (!canUse(favorite) || favoriteIndex >= 20) continue;
            int x = left + (favoriteIndex % 2) * 113, y = top + 30 + (favoriteIndex / 2) * 23;
            String marker = PremadeSpells.FAVORITES.contains(favorite) ? "★ " : "◆ ";
            addRenderableWidget(Button.builder(Component.literal(marker + favorite.name()), b -> request(favorite))
                    .bounds(x, y, 108, 20).build());
            favoriteIndex++;
        }
        nameBox = new EditBox(font, builderLeft, top + 30, builderWidth, 20, Component.literal("Spell name"));
        nameBox.setMaxLength(32); nameBox.setValue(draftName); addRenderableWidget(nameBox);
        addRenderableWidget(cycleButton(builderLeft, top + 53, half, () -> "Source: " + source.displayName(), () -> source = next(availableSources(), source)));
        addRenderableWidget(cycleButton(builderLeft + half + 4, top + 53, half, () -> "Form: " + form.displayName(), () -> form = next(filter(SpellForm.values(), knowledge::knows), form)));
        addRenderableWidget(cycleButton(builderLeft, top + 76, half, () -> "Delivery: " + delivery.displayName(), () -> delivery = next(filter(DeliveryType.values(), knowledge::knows), delivery)));
        Button program = cycleButton(builderLeft + half + 4, top + 76, half, () -> "Program: " + (programmed ? "ON" : "OFF"), this::toggleProgram);
        program.active = knowledge.programming(); addRenderableWidget(program);

        conditionButton = cycleButton(builderLeft, top + 99, half, () -> "IF: " + branch().condition.displayName(), () -> branch().condition = next(filter(ConditionType.values(), knowledge::knows), branch().condition));
        programTargetButton = cycleButton(builderLeft + half + 4, top + 99, half, () -> "THEN: " + branch().targetMode.displayName(), () ->
                branch().targetMode = next(filter(ProgramTargetMode.values(), knowledge::knows), branch().targetMode));
        addRenderableWidget(conditionButton); addRenderableWidget(programTargetButton);

        int quarter = (builderWidth - 12) / 4;
        intervalButton = cycleButton(builderLeft, top + 122, quarter, () -> format(branch().intervalTicks / 20.0) + "s", () -> branch().intervalTicks = switch (branch().intervalTicks) { case 5 -> 10; case 10 -> 20; case 20 -> 40; case 40 -> 100; default -> 5; });
        sensorButton = cycleButton(builderLeft + quarter + 4, top + 122, quarter, () -> "Sense " + format(branch().detectionRange) + "m", () -> { branch().detectionRange += 4; if (branch().detectionRange > 32) branch().detectionRange = 4; });
        Button addBranch = Button.builder(Component.literal("+ Branch"), b -> addBranch()).bounds(builderLeft + (quarter + 4) * 2, top + 122, quarter, 20).build();
        Button removeBranch = Button.builder(Component.literal("− Branch"), b -> removeBranch()).bounds(builderLeft + (quarter + 4) * 3, top + 122, quarter, 20).build();
        addBranch.active = programmed && branches.size() < Math.min(8, knowledge.maxProgramBranches());
        removeBranch.active = programmed && branches.size() > 1;
        addRenderableWidget(intervalButton); addRenderableWidget(sensorButton); addRenderableWidget(addBranch); addRenderableWidget(removeBranch);

        navRow(builderLeft, top + 145, builderWidth, this::previousBranch, this::nextBranch, branchLabel());
        navRow(builderLeft, top + 168, builderWidth, this::previousStep, this::nextStep, stepLabel());

        addRenderableWidget(cycleButton(builderLeft, top + 194, half, () -> "Element: " + current().element().displayName(), this::cycleElement));
        addRenderableWidget(cycleButton(builderLeft + half + 4, top + 194, half, () -> "Impact: " + current().impact().displayName(), this::cycleImpact));
        addRenderableWidget(cycleButton(builderLeft, top + 217, half, () -> "Direction: " + current().direction().displayName(), () -> replace(current().withDirection(next(filter(CastDirection.values(), knowledge::knows), current().direction())))));
        addRenderableWidget(cycleButton(builderLeft + half + 4, top + 217, half, () -> "Target: " + current().targetMode().displayName(), () -> replace(current().withTargetMode(next(availableTargetModes(), current().targetMode())))));
        addRenderableWidget(valueSlider(builderLeft, top + 240, half, .5, maxForce(), () -> current().power(),
                value -> replace(current().withPower(value)), () -> (current().impact() == ImpactType.EXPLODE ? "Explosion force: " : "Mana force: ") + format(current().power())));
        addRenderableWidget(cycleButton(builderLeft + half + 4, top + 240, half, () -> "Repeat: " + current().repetitions() + "×", () -> replace(current().withRepetitions(isUtility(current().impact()) ? 1 : current().repetitions() % 8 + 1))));
        addRenderableWidget(valueSlider(builderLeft, top + 263, half, 4, 64, () -> current().range(), value -> replace(current().withRange(value)), () -> "Range: " + format(current().range()) + "m"));
        addRenderableWidget(valueSlider(builderLeft + half + 4, top + 263, half, 0, 12, () -> current().radius(), value -> replace(current().withRadius(value)), () -> "Radius: " + format(current().radius()) + "m"));
        addRenderableWidget(valueSlider(builderLeft, top + 286, half, .25, 60, () -> current().durationSeconds(), value -> replace(current().withDuration(value)), () -> "Duration: " + format(current().durationSeconds()) + "s"));
        physicsButton = cycleButton(builderLeft + half + 4, top + 286, half,
                () -> current().impact() == ImpactType.APPLY_EFFECT ? "Effect: " + current().potionEffect().displayName()
                        : "Physics: " + current().physicsOperation().displayName(),
                () -> replace(current().impact() == ImpactType.APPLY_EFFECT
                        ? current().withPotionEffect(next(Arrays.stream(PotionEffectType.values())
                        .filter(effect -> effect.element() == current().element()).toArray(PotionEffectType[]::new), current().potionEffect()))
                        : current().withPhysicsOperation(next(PhysicsOperation.values(), current().physicsOperation()))));
        addRenderableWidget(physicsButton);

        int third = (builderWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Add Action"), b -> addStep()).bounds(builderLeft, top + 309, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> duplicateStep()).bounds(builderLeft + third + 4, top + 309, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeStep()).bounds(builderLeft + (third + 4) * 2, top + 309, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal(editMode || memoryOnly ? "Save Changes" : "Create Spell"), b -> request(currentSpell())).bounds(builderLeft, top + 407, builderWidth, 20).build());
        updateButtons();
    }

    private void navRow(int x, int y, int w, Runnable previous, Runnable next, Component label) {
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> previous.run()).bounds(x, y, 38, 20).build());
        Button center = Button.builder(label, b -> {}).bounds(x + 42, y, w - 84, 20).build(); center.active = false; addRenderableWidget(center);
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> next.run()).bounds(x + w - 38, y, 38, 20).build());
    }
    private Button cycleButton(int x, int y, int w, java.util.function.Supplier<String> label, Runnable cycle) {
        return Button.builder(Component.literal(label.get()), b -> { cycle.run(); refreshScreen(); }).bounds(x, y, w, 20).build();
    }
    private AbstractSliderButton valueSlider(int x, int y, int w, double min, double max,
                                             java.util.function.DoubleSupplier getter,
                                             java.util.function.DoubleConsumer setter,
                                             java.util.function.Supplier<String> label) {
        double initial = Math.max(0, Math.min(1, (getter.getAsDouble() - min) / Math.max(.001, max - min)));
        return new AbstractSliderButton(x, y, w, 20, Component.literal(label.get()), initial) {
            @Override protected void updateMessage() { setMessage(Component.literal(label.get())); }
            @Override protected void applyValue() {
                double stepped = Math.round((min + value * (max - min)) * 4) / 4.0;
                setter.accept(stepped); updateMessage();
            }
        };
    }
    private void toggleProgram() {
        if (programmed) programmed = false;
        else {
            programmed = true;
            branches.clear();
            branches.add(new BranchDraft(first(ConditionType.values(), knowledge::knows), 20, 8,
                    ProgramTargetMode.CASTER_AIM, new ArrayList<>(standalone)));
        }
        selectedBranch = 0; selectedInstruction = 0;
    }
    private void addBranch() {
        if (!programmed || branches.size() >= Math.min(8, knowledge.maxProgramBranches())) return;
        branches.add(new BranchDraft(first(ConditionType.values(), knowledge::knows), 20, 8, ProgramTargetMode.CASTER_AIM, new ArrayList<>(List.of(current()))));
        selectedBranch = branches.size() - 1; selectedInstruction = 0; refreshScreen();
    }
    private void removeBranch() { if (programmed && branches.size() > 1) { branches.remove(selectedBranch); selectedBranch = Math.min(selectedBranch, branches.size() - 1); selectedInstruction = 0; refreshScreen(); } }
    private void previousBranch() { selectBranch(-1); }
    private void nextBranch() { selectBranch(1); }
    private void selectBranch(int delta) { if (programmed) { selectedBranch = Math.floorMod(selectedBranch + delta, branches.size()); selectedInstruction = 0; refreshScreen(); } }
    private void previousStep() { selectStep(-1); }
    private void nextStep() { selectStep(1); }
    private void selectStep(int delta) { selectedInstruction = Math.floorMod(selectedInstruction + delta, actions().size()); refreshScreen(); }
    private void cycleElement() {
        Element element = next(filter(Element.values(), knowledge::knows), current().element());
        if (actions().stream().anyMatch(action -> action != current() && action.element() != element)
                && !knowsSkill(com.strutton.dynamicmagic.skill.MagicSkill.MANA_COMBINING)) return;
        SpellInstruction changed = current().withElement(element);
        if (!Arrays.asList(allowedImpactsFor(element)).contains(changed.impact()) || !knowledge.knows(changed.impact())) changed = changed.withImpact(first(allowedImpactsFor(element), knowledge::knows));
        replace(changed.withPower(Math.min(changed.power(), forceCap(element))));
    }
    private void cycleImpact() { ImpactType impact = next(filter(allowedImpactsFor(current().element()), knowledge::knows), current().impact()); replace(current().withImpact(impact).withRepetitions(isUtility(impact) ? 1 : current().repetitions())); }
    private void addStep() { if (actions().size() < 8) { actions().add(current().withRepetitions(1)); selectedInstruction = actions().size() - 1; refreshScreen(); } }
    private void duplicateStep() { if (actions().size() < 8) { actions().add(selectedInstruction + 1, current()); selectedInstruction++; refreshScreen(); } }
    private void removeStep() { if (actions().size() > 1) { actions().remove(selectedInstruction); selectedInstruction = Math.min(selectedInstruction, actions().size() - 1); refreshScreen(); } }
    private void refreshScreen() { if (nameBox != null) draftName = nameBox.getValue(); clearWidgets(); init(); }
    private List<SpellInstruction> actions() { return programmed ? branch().instructions : standalone; }
    private BranchDraft branch() {
        if (branches.isEmpty()) branches.add(new BranchDraft(ConditionType.ALWAYS, 20, 8, ProgramTargetMode.CASTER_AIM, new ArrayList<>(standalone)));
        return branches.get(Math.min(selectedBranch, branches.size() - 1));
    }
    private SpellInstruction current() { return actions().get(selectedInstruction); }
    private void replace(SpellInstruction value) { actions().set(selectedInstruction, value); }
    private Component branchLabel() { return Component.literal(programmed ? "Branch " + (selectedBranch + 1) + " of " + branches.size() : "Normal spell (no branches)"); }
    private Component stepLabel() { return Component.literal("Action " + (selectedInstruction + 1) + " of " + actions().size()); }
    private void updateButtons() {
        conditionButton.active = programmed; intervalButton.active = programmed; sensorButton.active = programmed; programTargetButton.active = programmed;
        physicsButton.active = current().impact() == ImpactType.PHYSICS || current().impact() == ImpactType.APPLY_EFFECT;
    }

    private CraftedSpell currentSpell() {
        List<SpellBranch> finished = programmed ? branches.stream().map(BranchDraft::finish).toList() : List.of();
        List<SpellInstruction> operations = programmed ? finished.get(0).instructions() : standalone;
        SpellBranch primary = programmed ? finished.get(0) : new SpellBranch(ConditionType.ALWAYS, 20, 8, ProgramTargetMode.CASTER_AIM, operations);
        SpellInstruction first = operations.get(0);
        return new CraftedSpell(nameBox.getValue(), source, first.element(), form, delivery, first.impact(), first.power(), first.direction(), programmed,
                primary.condition(), primary.intervalTicks(), primary.detectionRange(), first.targetMode(), primary.targetMode(), first.physicsOperation(), operations, finished);
    }
    private boolean canUse(CraftedSpell spell) {
        return knowledge.knows(spell.form()) && knowledge.knows(spell.delivery())
                && spell.allInstructions().stream().allMatch(i -> (i.impact() == ImpactType.STUDY || knowledge.knows(i.element()))
                        && knowledge.knows(i.impact()) && knowledge.knows(i.direction())
                        && (i.impact() != ImpactType.APPLY_EFFECT || i.potionEffect().element() == i.element()))
                && (!spell.programmed() || knowledge.programming() && spell.branches().stream().allMatch(b ->
                        knowledge.knows(b.condition()) && knowledge.knows(b.targetMode())))
                && (spell.source() != SourceType.SUMMON || knowledge.canSummonDimension());
    }
    private void request(CraftedSpell spell) { PacketDistributor.sendToServer(new CreateSpellPayload(spell, editMode, memoryOnly, originalName)); }
    private boolean knowsSkill(com.strutton.dynamicmagic.skill.MagicSkill skill) {
        var data = ClientSpellbook.data();
        return data != null && (data.skills() & (1L << skill.ordinal())) != 0;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101620);
        int panelWidth = Math.min(780, width - 20), left = (width - panelWidth) / 2, builderLeft = left + 244, builderWidth = panelWidth - 244;
        int top = Math.max(6, (height - 438) / 2);
        graphics.fill(left - 7, top - 8, left + panelWidth + 7, top + 435, 0xF0101620);
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        graphics.drawString(font, "Learned Recipes", left, top + 17, 0xFFD27F, false);
        graphics.drawString(font, programmed ? "Program mastery " + format(knowledge.programmingMastery())
                + " • branch cap " + knowledge.maxProgramBranches() : "Carrier + ordered actions", builderLeft, top + 17, 0x7FE7FF, false);
        int row = 0;
        if (programmed) for (int b = 0; b < branches.size() && row < 6; b++) {
            BranchDraft branch = branches.get(b);
            graphics.drawString(font, (b == selectedBranch ? "▶ " : "  ") + "IF " + branch.condition.displayName() + " → " + branch.instructions.size() + " action(s)",
                    builderLeft, top + 336 + row++ * 10, b == selectedBranch ? 0xFFE08A : 0xB8C8D8, false);
        } else for (int i = 0; i < standalone.size() && row < 6; i++) {
            SpellInstruction action = standalone.get(i);
            graphics.drawString(font, (i == selectedInstruction ? "▶ " : "  ") + (i + 1) + ": " + action.element().displayName() + " " + action.impact().displayName() + " F" + format(action.power()),
                    builderLeft, top + 336 + row++ * 10, i == selectedInstruction ? 0xFFE08A : 0xB8C8D8, false);
        }
        SpellCost cost = SpellCostCalculator.calculate(currentSpell().definition(), previewStats);
        double kiShare = SpellResourcePayment.kiShare(currentSpell());
        String resourceHint = kiShare > 0 ? "  • Ki share " + format(kiShare * 100) + "%" : "";
        graphics.drawCenteredString(font, "Form " + format(cost.formation()) + "  Cast " + format(cost.release()) + "  Runtime " + format(cost.maintenancePerSecond()) + "/s" + resourceHint,
                builderLeft + builderWidth / 2, top + 393, 0xBFEFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
    private SourceType[] availableSources() { return knowledge.canSummonDimension() ? SourceType.values() : new SourceType[]{SourceType.CREATE}; }
    private double maxForce() { return forceCap(current().element()); }
    private TargetMode[] availableTargetModes() {
        return ClientSpellbook.knowsSkill(com.strutton.dynamicmagic.skill.MagicSkill.TRACKING)
                ? TargetMode.values() : new TargetMode[]{TargetMode.AIM, TargetMode.SELF};
    }
    private double forceCap(Element element) { return element.ordinal() < forceCaps.size() ? forceCaps.get(element.ordinal()) : 3; }
    private static ImpactType[] allowedImpactsFor(Element element) {
        ImpactType[] base = baseAllowedImpactsFor(element);
        boolean hasEffect = java.util.Arrays.asList(base).contains(ImpactType.APPLY_EFFECT);
        boolean hasExplosion = java.util.Arrays.asList(base).contains(ImpactType.EXPLODE);
        int extra = (hasEffect ? 0 : 1) + (hasExplosion ? 0 : 1);
        if (extra == 0) return base;
        ImpactType[] result = java.util.Arrays.copyOf(base, base.length + extra);
        int at = base.length;
        if (!hasEffect) result[at++] = ImpactType.APPLY_EFFECT;
        if (!hasExplosion) result[at] = ImpactType.EXPLODE;
        return result;
    }

    private static ImpactType[] baseAllowedImpactsFor(Element element) {
        if (element == Element.TIME) return new ImpactType[]{ImpactType.STOP_TIME, ImpactType.SPEED_TIME, ImpactType.SLOW_TIME, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.SPACE) return new ImpactType[]{ImpactType.STORE_ITEM, ImpactType.STORE_ENTITY, ImpactType.RELEASE_STORAGE, ImpactType.TELEPORT, ImpactType.CONTRACT_ENTITY, ImpactType.SUMMON_CONTRACT, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.DIVINE) return new ImpactType[]{ImpactType.HEAL, ImpactType.CLEANSE, ImpactType.RESURRECT, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.WATER) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.PHYSICS, ImpactType.EXTINGUISH, ImpactType.TRANSMUTE_BLOCK, ImpactType.WEATHER_RAIN, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.ICE) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.FREEZE, ImpactType.TRANSMUTE_BLOCK, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.EARTH) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.PHYSICS, ImpactType.ROOT, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.LIGHTNING) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.CHAIN, ImpactType.SUMMON_LIGHTNING, ImpactType.CONJURE_ITEM, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.LIGHT) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.ILLUMINATE, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.SHADOW) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.BLIND, ImpactType.CONJURE_ITEM, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.ARCANE) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.PROTECT, ImpactType.DISPEL, ImpactType.CONJURE_ITEM, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.METAL) return new ImpactType[]{ImpactType.DETECT_ORES, ImpactType.DAMAGE, ImpactType.PROTECT, ImpactType.PHYSICS, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.GLASS) return new ImpactType[]{ImpactType.PROTECT, ImpactType.DAMAGE, ImpactType.PHYSICS, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
        if (element == Element.PLASMA) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.IGNITE, ImpactType.EXPLODE, ImpactType.SUMMON_LIGHTNING, ImpactType.STUDY};
        if (element == Element.QUICK) return new ImpactType[]{ImpactType.PHYSICS, ImpactType.PROTECT, ImpactType.STUDY};
        if (element == Element.SCORCH) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.IGNITE, ImpactType.EXPLODE, ImpactType.STUDY};
        if (element == Element.LAVA) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.IGNITE, ImpactType.EXPLODE, ImpactType.TRANSMUTE_BLOCK, ImpactType.STUDY};
        if (element == Element.STORM) return new ImpactType[]{ImpactType.WEATHER_STORM, ImpactType.SUMMON_LIGHTNING, ImpactType.CHAIN, ImpactType.DAMAGE, ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.SPIRIT) return new ImpactType[]{ImpactType.DETECT_LIFE, ImpactType.FERTILITY, ImpactType.ASTRAL_PROJECTION, ImpactType.PROTECT, ImpactType.STUDY};
        if (element == Element.UNDEAD) return new ImpactType[]{ImpactType.PACIFY_UNDEAD, ImpactType.DETECT_UNDEAD, ImpactType.CORRUPT_LIFE, ImpactType.DAMAGE, ImpactType.STUDY};
        if (element == Element.SAND) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.PHYSICS, ImpactType.BLIND, ImpactType.PROTECT, ImpactType.APPLY_EFFECT, ImpactType.STUDY};
        if (element == Element.BLOOD) return new ImpactType[]{ImpactType.DRAIN_LIFE, ImpactType.HEAL,
                ImpactType.CONVERT_HEALTH_TO_MANA, ImpactType.DAMAGE, ImpactType.APPLY_EFFECT,
                ImpactType.PHYSICS, ImpactType.STUDY};
        if (element == Element.KI) return new ImpactType[]{ImpactType.DAMAGE, ImpactType.PHYSICS,
                ImpactType.PROTECT, ImpactType.APPLY_EFFECT, ImpactType.STUDY};
        return new ImpactType[]{ImpactType.DAMAGE, ImpactType.EXPLODE, ImpactType.IGNITE, ImpactType.PHYSICS, ImpactType.FREEZE, ImpactType.PROTECT, ImpactType.CONJURE_ITEM, ImpactType.STUDY};
    }
    private static boolean isUtility(ImpactType impact) { return impact == ImpactType.STORE_ITEM || impact == ImpactType.STORE_ENTITY || impact == ImpactType.RELEASE_STORAGE || impact == ImpactType.CONTRACT_ENTITY || impact == ImpactType.SUMMON_CONTRACT || impact == ImpactType.CONJURE_ITEM || impact == ImpactType.STOP_TIME || impact == ImpactType.SPEED_TIME || impact == ImpactType.SLOW_TIME || impact == ImpactType.TRANSMUTE_BLOCK || impact == ImpactType.WEATHER_RAIN || impact == ImpactType.WEATHER_STORM || impact == ImpactType.SUMMON_LIGHTNING || impact == ImpactType.DETECT_ORES || impact == ImpactType.STUDY || impact == ImpactType.RESURRECT || impact == ImpactType.ASTRAL_PROJECTION || impact == ImpactType.DETECT_LIFE || impact == ImpactType.FERTILITY || impact == ImpactType.PACIFY_UNDEAD || impact == ImpactType.CORRUPT_LIFE || impact == ImpactType.DETECT_UNDEAD; }
    private static double nextDuration(double value) { double[] values = {.5, 1, 2, 3, 5, 10, 20, 30, 60}; for (double candidate : values) if (candidate > value + .001) return candidate; return values[0]; }
    private static <T> T[] filter(T[] values, Predicate<T> predicate) { return Arrays.stream(values).filter(predicate).toArray(length -> Arrays.copyOf(values, length)); }
    private static <T> T first(T[] values, Predicate<T> predicate) { return Arrays.stream(values).filter(predicate).findFirst().orElseThrow(() -> new IllegalStateException("No learned spell option")); }
    private static <T> T next(T[] values, T current) { if (values.length == 0) return current; int index = Arrays.asList(values).indexOf(current); return values[(Math.max(0, index) + 1) % values.length]; }
    private static List<CraftedSpell> concat(List<CraftedSpell> first, List<CraftedSpell> second) { List<CraftedSpell> result = new ArrayList<>(first); result.addAll(second); return result; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    private static final class BranchDraft {
        private ConditionType condition; private int intervalTicks; private double detectionRange;
        private ProgramTargetMode targetMode; private final List<SpellInstruction> instructions;
        private BranchDraft(SpellBranch branch) { this(branch.condition(), branch.intervalTicks(), branch.detectionRange(), branch.targetMode(), new ArrayList<>(branch.instructions())); }
        private BranchDraft(ConditionType condition, int intervalTicks, double detectionRange, ProgramTargetMode targetMode, List<SpellInstruction> instructions) {
            this.condition = condition; this.intervalTicks = intervalTicks; this.detectionRange = detectionRange; this.targetMode = targetMode; this.instructions = instructions;
        }
        private SpellBranch finish() { return new SpellBranch(condition, intervalTicks, detectionRange, targetMode, instructions); }
    }
}
