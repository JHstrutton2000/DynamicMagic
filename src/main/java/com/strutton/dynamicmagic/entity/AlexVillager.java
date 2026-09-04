package com.strutton.dynamicmagic.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** An Alex-themed peaceful villager scholar of spirit and fertility magic. */
public final class AlexVillager extends Villager {
    public AlexVillager(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        if (!level.isClientSide() && getCustomName() == null) {
            setCustomName(Component.literal("Alex the Lifebringer"));
            setCustomNameVisible(true);
        }
    }
}
