package com.strutton.dynamicmagic.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** A peaceful Enderman-themed villager scholar of space and teleportation. */
public final class EndermanVillager extends Villager {
    private static final String[] NAMES = {"Aster", "Erebus", "Nyx", "Orion", "Vanta", "Vesper", "Void", "Zenith"};

    public EndermanVillager(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        if (!level.isClientSide() && getCustomName() == null) {
            setCustomName(Component.literal(NAMES[getRandom().nextInt(NAMES.length)] + " the Wayfarer"));
            setCustomNameVisible(true);
        }
    }
}
