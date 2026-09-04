package com.strutton.dynamicmagic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.network.OpenSpellcraftRequest;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import com.strutton.dynamicmagic.item.CraftedSpellItem;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class MagicKeyMappings {
    public static final KeyMapping OPEN = new KeyMapping("key.dynamicmagic.open_spellcraft",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.dynamicmagic");
    public static final KeyMapping EDIT = new KeyMapping("key.dynamicmagic.edit_spell",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.dynamicmagic");
    private MagicKeyMappings() {}

    @EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(EDIT); }
    }

    @EventBusSubscriber(modid = DynamicMagic.MOD_ID, value = Dist.CLIENT)
    public static final class Ticking {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            while (OPEN.consumeClick()) PacketDistributor.sendToServer(new OpenSpellcraftRequest(false));
            while (EDIT.consumeClick()) PacketDistributor.sendToServer(new OpenSpellcraftRequest(true));
        }

        /** Vanilla scales movement input to 20% while an item is in use; spell formation is not physical bow aiming. */
        @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
            if (event.getEntity().isUsingItem() && event.getEntity().getUseItem().getItem() instanceof CraftedSpellItem) {
                event.getInput().leftImpulse *= 5.0f;
                event.getInput().forwardImpulse *= 5.0f;
            }
        }
    }
}
