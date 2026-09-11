package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.magic.CraftedSpell;
import com.strutton.dynamicmagic.network.BindSpellPayload;
import com.strutton.dynamicmagic.network.DeleteSpellPayload;
import com.strutton.dynamicmagic.network.OpenSpellbookPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Inventory-like grimoire page: select or drag a remembered spell, then place it into a bound slot. */
public final class SpellbookLoadoutScreen extends Screen {
    private final OpenSpellbookPayload data;
    private CraftedSpell selected;
    private int page;
    private int left, top;
    private boolean draggingSpell;

    public SpellbookLoadoutScreen(OpenSpellbookPayload data) { super(Component.literal("Grimoire")); this.data = data; }

    @Override protected void init() {
        left = (width - Math.min(760, width - 20)) / 2;
        top = Math.max(12, (height - 390) / 2);
        addRenderableWidget(Button.builder(Component.literal("Spell Slots"), b -> {}).bounds(left, top, 112, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal("Craft Spell"), b -> ClientSpellbook.openCraft(null)).bounds(left + 116, top, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Elements & Mastery"), b -> ClientSpellbook.openKnowledge()).bounds(left + 232, top, 148, 20).build());

        List<CraftedSpell> spells = data.savedSpells();
        int start = page * 18;
        for (int index = start; index < Math.min(spells.size(), start + 18); index++) {
            CraftedSpell spell = spells.get(index);
            int local = index - start;
            addRenderableWidget(Button.builder(Component.literal(shortName(spell.name())), b -> { selected = spell; refresh(); })
                    .bounds(left + 12 + local % 3 * 126, top + 48 + local / 3 * 24, 120, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> { if (page > 0) { page--; refresh(); }}).bounds(left + 12, top + 199, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> { if ((page + 1) * 18 < spells.size()) { page++; refresh(); }}).bounds(left + 340, top + 199, 38, 20).build());

        for (int slot = 0; slot < 6; slot++) {
            final int target = slot;
            String key = slot == 5 ? "RMB" : Integer.toString(slot + 1);
            String names = data.bindings().get(slot).isEmpty() ? "Empty" : String.join(" + ", data.bindings().get(slot));
            addRenderableWidget(Button.builder(Component.literal(key + "  " + shortName(names)), b -> {
                if (selected != null) PacketDistributor.sendToServer(new BindSpellPayload(target, selected.name(), hasShiftDown()));
            }).bounds(left + 410, top + 48 + slot * 42, 292, 28).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> PacketDistributor.sendToServer(new BindSpellPayload(target, "", false)))
                    .bounds(left + 706, top + 48 + slot * 42, 24, 28).build());
        }
        Button edit = Button.builder(Component.literal("Edit selected"), b -> { if (selected != null) ClientSpellbook.openCraft(selected); })
                .bounds(left + 12, top + 340, 120, 22).build(); edit.active = selected != null; addRenderableWidget(edit);
        Button delete = Button.builder(Component.literal("Forget selected"), b -> { if (selected != null) PacketDistributor.sendToServer(new DeleteSpellPayload(selected.name())); })
                .bounds(left + 138, top + 340, 120, 22).build(); delete.active = selected != null; addRenderableWidget(delete);
    }

    private void refresh() { clearWidgets(); init(); }
    private static String shortName(String name) { return name.length() <= 18 ? name : name.substring(0, 17) + "…"; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE90C111B);
        graphics.fill(left - 8, top - 8, left + 750, top + 378, 0xF21A2332);
        graphics.drawString(font, "Remembered spells — select one, then click a slot (Shift adds multicast)", left + 12, top + 34, 0xA9EFFF, false);
        graphics.drawString(font, "Bound slots — keys 1–5 use Z/X/C/V/B; slot 6 is right click", left + 410, top + 34, 0xFFD27F, false);
        if (selected != null) {
            graphics.drawString(font, selected.name(), left + 12, top + 230, 0xFFE08A, false);
            graphics.drawString(font, selected.form().displayName() + " • " + selected.delivery().displayName() + " • " + selected.allInstructions().size() + " action(s)", left + 12, top + 244, 0xB8C8D8, false);
            int row = 0;
            for (var instruction : selected.allInstructions()) if (row < 6)
                graphics.drawString(font, instruction.element().displayName() + " " + instruction.impact().displayName()
                        + "  mana force " + String.format(java.util.Locale.ROOT, "%.1f", instruction.power()), left + 20, top + 260 + row++ * 12, 0xD8E5F0, false);
        } else graphics.drawString(font, "Click a spell to inspect its complete settings.", left + 12, top + 230, 0x8294A8, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && selected != null) draggingSpell = true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSpell && selected != null && mouseX >= left + 410 && mouseX < left + 702) {
            for (int slot = 0; slot < 6; slot++) {
                int y = top + 48 + slot * 42;
                if (mouseY >= y && mouseY < y + 28) {
                    PacketDistributor.sendToServer(new BindSpellPayload(slot, selected.name(), hasShiftDown()));
                    draggingSpell = false;
                    return true;
                }
            }
        }
        draggingSpell = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean isPauseScreen() { return false; }
}
