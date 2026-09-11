package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.network.SummonStoredEntityPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class EntityStorageScreen extends Screen {
    private int heartbeat;
    private static final int PAGE_SIZE = 8;
    private final List<String> names;
    private int page;
    public EntityStorageScreen(List<String> names) { super(Component.literal("Stored Entities")); this.names = List.copyOf(names); }

    @Override protected void init() {
        int left = width / 2 - 130;
        int top = Math.max(30, height / 2 - 115);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < names.size(); slot++) {
            int index = start + slot;
            addRenderableWidget(Button.builder(Component.literal("Summon: " + names.get(index)), button -> {
                PacketDistributor.sendToServer(new SummonStoredEntityPayload(index));
                onClose();
            }).bounds(left, top + 25 + slot * 23, 260, 20).build());
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("Previous"), button -> { page--; rebuildWidgets(); })
                .bounds(left, top + 214, 90, 20).build());
        if ((page + 1) * PAGE_SIZE < names.size()) addRenderableWidget(Button.builder(Component.literal("Next"), button -> { page++; rebuildWidgets(); })
                .bounds(left + 170, top + 214, 90, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101620);
        graphics.drawCenteredString(font, title, width / 2, Math.max(30, height / 2 - 115), 0xD8C4FF);
        if (names.isEmpty()) graphics.drawCenteredString(font, "No entities are stored", width / 2, height / 2, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        super.tick();
        if (++heartbeat >= 20) { heartbeat = 0; net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.strutton.dynamicmagic.network.SpatialStorageHeartbeatPayload()); }
    }
}
