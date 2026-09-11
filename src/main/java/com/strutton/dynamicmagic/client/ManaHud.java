package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ManaHud {
    private static final int BOTTOM_OFFSET = 52;

    private ManaHud() {}
    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana"), ManaHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        boolean cultivation = ModList.get().isLoaded("eternal_cultivation");
        int x = cultivation ? 8 : graphics.guiWidth() / 2 + 10;
        // Eternal Cultivation draws its 116x8 qi bar at height-31. Stack mana above it when present.
        int y = cultivation ? graphics.guiHeight() - 54 : graphics.guiHeight() - BOTTOM_OFFSET;
        int width = cultivation ? 116 : 81;
        int barHeight = cultivation ? 8 : 5;
        if (cultivation) graphics.fill(x - 1, y - 1, x + width + 1, y + barHeight + 1, 0xE00A0E17);
        graphics.fill(x, y, x + width, y + barHeight, cultivation ? 0xFF202738 : 0xC0000000);
        int filled = ClientManaState.unlimited ? width : (int) Math.round(width * Math.max(0,
                Math.min(1, ClientManaState.current / ClientManaState.maximum)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + barHeight, 0xFF2E4FAF);
            if (cultivation) graphics.fill(x, y, x + filled, y + 2, 0xFF729DFF);
        }
        if (cultivation) graphics.fill(x, y, x + width, y + 1, 0xFF6579A8);
        String text = ClientManaState.unlimited ? "Mana ∞" : "Mana " + (int) ClientManaState.current + "/" + (int) ClientManaState.maximum;
        if (cultivation) graphics.drawString(minecraft.font, Component.literal(text), x, y - 10, 0xAFC5FF, false);
        else graphics.drawCenteredString(minecraft.font, text, x + width / 2, y - 10, 0x9FB7FF);
    }

    @EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT)
    public static final class IronManaHudSuppressor {
        private IronManaHudSuppressor() {}

        @SubscribeEvent
        public static void beforeLayer(RenderGuiLayerEvent.Pre event) {
            if (event.getLayer().getClass().getName()
                    .equals("io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay"))
                event.setCanceled(true);
        }
    }
}
