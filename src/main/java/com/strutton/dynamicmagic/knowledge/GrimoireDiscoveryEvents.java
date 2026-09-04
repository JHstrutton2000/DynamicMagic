package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Arrays;

/** Wilderness discovery: basic grimoires are uncommon; synthesized and esoteric books are much rarer. */
public final class GrimoireDiscoveryEvents {
    private static final Element[] BASIC = {Element.WATER, Element.EARTH, Element.LIGHTNING, Element.AIR, Element.FIRE};
    private static final Element[] COMBINED = {Element.ICE, Element.METAL, Element.GLASS, Element.PLASMA,
            Element.QUICK, Element.SCORCH, Element.LAVA, Element.STORM};
    private static final Element[] ESOTERIC = {Element.LIGHT, Element.SHADOW, Element.ARCANE, Element.SPACE,
            Element.TIME, Element.DIVINE, Element.SPIRIT, Element.UNDEAD};
    private GrimoireDiscoveryEvents() {}

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        double roll = player.getRandom().nextDouble();
        Element[] tier = roll < .0008 ? ESOTERIC : roll < .004 ? COMBINED : roll < .025 ? BASIC : null;
        if (tier == null) return;
        Element[] unknown = Arrays.stream(tier).filter(element -> !ElementKnowledge.knows(player, element)).toArray(Element[]::new);
        if (unknown.length == 0) return;
        Element element = unknown[player.getRandom().nextInt(unknown.length)];
        ItemStack stack = new ItemStack(DynamicMagic.ELEMENT_GRIMOIRES.get(element).get());
        event.getDrops().add(new ItemEntity(player.level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack));
    }
}
