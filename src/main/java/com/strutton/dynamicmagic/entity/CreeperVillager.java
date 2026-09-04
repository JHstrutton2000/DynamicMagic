package com.strutton.dynamicmagic.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** A peaceful villager-bodied creeper scholar. */
public final class CreeperVillager extends Villager {
    private static final String[] NAMES = {"Brindle", "Fern", "Moss", "Nettle", "Pip", "Sprig", "Thistle", "Verdan"};

    public CreeperVillager(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        if (!level.isClientSide() && getCustomName() == null) {
            setCustomName(Component.literal(NAMES[getRandom().nextInt(NAMES.length)] + " the Blastkeeper"));
            setCustomNameVisible(true);
        }
    }
}
