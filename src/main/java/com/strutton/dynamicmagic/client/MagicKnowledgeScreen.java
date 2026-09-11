package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.magic.ElementPreset;
import com.strutton.dynamicmagic.network.OpenSpellbookPayload;
import com.strutton.dynamicmagic.network.SaveElementPresetPayload;
import com.strutton.dynamicmagic.skill.MagicSkill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** Element affinities, skill ledger, and the deliberately smaller ratio-preset editor. */
public final class MagicKnowledgeScreen extends Screen {
    private final OpenSpellbookPayload data;
    private final List<ElementPreset.Entry> draft = new ArrayList<>();
    private EditBox name;
    private Element selectedElement;
    private int ratio = 50;
    private int left, top, elementPage, skillPage;
    public MagicKnowledgeScreen(OpenSpellbookPayload data) { super(Component.literal("Elements & Mastery")); this.data = data; selectedElement = firstKnown(); }

    @Override protected void init() {
        left = (width - Math.min(800, width - 20)) / 2; top = Math.max(8, (height - 438) / 2);
        addRenderableWidget(Button.builder(Component.literal("Spell Slots"), b -> ClientSpellbook.openLoadout()).bounds(left, top, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Craft Spell"), b -> ClientSpellbook.openCraft(null)).bounds(left + 116, top, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Elements & Mastery"), b -> {}).bounds(left + 232, top, 148, 20).build()).active = false;
        name = new EditBox(font, left + 420, top + 55, 210, 20, Component.literal("Preset name")); name.setMaxLength(32); name.setValue("Element Preset"); addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleElement(-1)).bounds(left + 420, top + 82, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal(selectedElement.displayName()), b -> {}).bounds(left + 458, top + 82, 134, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleElement(1)).bounds(left + 596, top + 82, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal("−5"), b -> { ratio = Math.max(5, ratio - 5); rebuild(); }).bounds(left + 420, top + 108, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal(ratio + "% mana"), b -> {}).bounds(left + 478, top + 108, 94, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal("+5"), b -> { ratio = Math.min(100, ratio + 5); rebuild(); }).bounds(left + 576, top + 108, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add element"), b -> addElement()).bounds(left + 420, top + 134, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save ratio preset"), b -> save()).bounds(left + 420, top + 160, 210, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀ Elements"), b -> {
            elementPage = Math.floorMod(elementPage - 1, elementPages()); rebuild();
        }).bounds(left + 10, top + 400, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Elements ▶"), b -> {
            elementPage = (elementPage + 1) % elementPages(); rebuild();
        }).bounds(left + 100, top + 400, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀ Skills"), b -> {
            skillPage = Math.floorMod(skillPage - 1, skillPages()); rebuild();
        }).bounds(left + 220, top + 400, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Skills ▶"), b -> {
            skillPage = (skillPage + 1) % skillPages(); rebuild();
        }).bounds(left + 302, top + 400, 78, 20).build());
        int row = 0;
        for (ElementPreset preset : data.elementPresets()) {
            if (row >= 8) break;
            addRenderableWidget(Button.builder(Component.literal("×"), b -> PacketDistributor.sendToServer(new SaveElementPresetPayload(preset, true)))
                    .bounds(left + 610, top + 214 + row * 24, 20, 20).build()); row++;
        }
    }
    private void addElement() {
        draft.removeIf(entry -> entry.element() == selectedElement);
        draft.add(new ElementPreset.Entry(selectedElement, ratio)); rebuild();
    }
    private void save() {
        if (draft.isEmpty()) addElement();
        PacketDistributor.sendToServer(new SaveElementPresetPayload(new ElementPreset(name.getValue(), draft), false));
    }
    private void cycleElement(int delta) {
        Element[] known = java.util.Arrays.stream(Element.values()).filter(data.snapshot()::knows).toArray(Element[]::new);
        int at = java.util.Arrays.asList(known).indexOf(selectedElement); selectedElement = known[Math.floorMod(at + delta, known.length)]; rebuild();
    }
    private Element firstKnown() { return java.util.Arrays.stream(Element.values()).filter(data.snapshot()::knows).findFirst().orElse(Element.WATER); }
    private void rebuild() { String value = name == null ? "Element Preset" : name.getValue(); clearWidgets(); init(); name.setValue(value); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEB0B1019); graphics.fill(left - 8, top - 8, left + 790, top + 430, 0xF2192331);
        graphics.drawString(font, "Elements", left + 10, top + 36, 0x7FE7FF, false);
        int row = 0, elementSkip = elementPage * 18, knownIndex = 0;
        for (Element element : Element.values()) if (data.snapshot().knows(element)) {
            if (knownIndex++ < elementSkip) continue;
            if (row >= 18) break;
            double mastery = element.ordinal() < data.elementMastery().size() ? data.elementMastery().get(element.ordinal()) : 0;
            double cap = element.ordinal() < data.forceCaps().size() ? data.forceCaps().get(element.ordinal()) : 3;
            graphics.drawString(font, element.displayName() + "  mastery " + fmt(mastery) + "  force cap " + fmt(cap), left + 10, top + 52 + row++ * 12, 0xCBD8E5, false);
        }
        graphics.drawString(font, "Skills", left + 220, top + 36, 0xFFD27F, false); row = 0;
        int skillSkip = skillPage * 21, learnedIndex = 0;
        for (MagicSkill skill : MagicSkill.values()) if ((data.skills() & (1L << skill.ordinal())) != 0) {
            if (learnedIndex++ < skillSkip) continue;
            if (row >= 21) break;
            graphics.drawString(font, skill.displayName(), left + 220, top + 52 + row++ * 12, 0xD7E7BE, false);
        }
        boolean combining = (data.skills() & (1L << MagicSkill.MANA_COMBINING.ordinal())) != 0;
        graphics.drawString(font, "Element ratio presets", left + 420, top + 36, 0xE0A8FF, false);
        graphics.drawString(font, combining ? "Ratios become relative mana weights when used in a spell." : "Mana Combining is required for multi-element presets.", left + 420, top + 187, combining ? 0xA8D8FF : 0xFF8B8B, false);
        row = 0;
        for (ElementPreset preset : data.elementPresets()) if (row < 8) {
            String ratios = preset.entries().stream().map(e -> e.element().displayName() + " " + e.ratio()).collect(java.util.stream.Collectors.joining(" : "));
            graphics.drawString(font, preset.name(), left + 420, top + 214 + row * 24, 0xFFE08A, false);
            graphics.drawString(font, ratios, left + 420, top + 225 + row++ * 24, 0xAFC0D0, false);
        }
        if (!draft.isEmpty()) graphics.drawString(font, "Draft: " + draft.stream().map(e -> e.element().displayName() + " " + e.ratio()).collect(java.util.stream.Collectors.joining(" : ")), left + 420, top + 400, 0xC8B6FF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private int elementPages() { return Math.max(1, (int) Math.ceil(java.util.Arrays.stream(Element.values()).filter(data.snapshot()::knows).count() / 18.0)); }
    private int skillPages() { return Math.max(1, (int) Math.ceil(java.util.Arrays.stream(MagicSkill.values()).filter(skill -> (data.skills() & (1L << skill.ordinal())) != 0).count() / 21.0)); }
    @Override public boolean isPauseScreen() { return false; }
}
