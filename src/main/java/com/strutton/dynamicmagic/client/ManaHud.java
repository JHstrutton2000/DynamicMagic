package com.strutton.dynamicmagic.client;

import com.strutton.dynamicmagic.DynamicMagic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ManaHud {
    private ManaHud() {}
    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(DynamicMagic.MOD_ID, "mana"), ManaHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - 52;
        int width = 81;
        graphics.fill(x - 1, y - 1, x + width + 1, y + 6, 0xC0000000);
        int filled = ClientManaState.unlimited ? width : (int) Math.round(width * Math.max(0,
                Math.min(1, ClientManaState.current / ClientManaState.maximum)));
        graphics.fill(x, y, x + filled, y + 5, 0xFF3F65FF);
        String text = ClientManaState.unlimited ? "Mana ∞" : "Mana " + (int) ClientManaState.current + "/" + (int) ClientManaState.maximum;
        graphics.drawCenteredString(minecraft.font, text, x + width / 2, y - 10, 0x9FB7FF);
    }
}
