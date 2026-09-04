package com.strutton.dynamicmagic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/** Peaceful villager-bodied skeleton mage; initialized as a Bone Sage by VillageMageEvents. */
public final class SkeletonMageVillager extends Villager {
    public SkeletonMageVillager(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }
}
