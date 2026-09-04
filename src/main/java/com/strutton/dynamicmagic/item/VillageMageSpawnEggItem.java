package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.mage.VillageMageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A dedicated test egg that always creates a village mage instead of a random villager. */
public final class VillageMageSpawnEggItem extends Item {
    public VillageMageSpawnEggItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        LivingEntity mage = level.random.nextInt(4) == 0 ? EntityType.WITCH.create(level) : EntityType.VILLAGER.create(level);
        if (mage == null) return InteractionResult.FAIL;
        BlockPos position = context.getClickedPos().relative(context.getClickedFace());
        mage.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5,
                level.random.nextFloat() * 360, 0);
        if (mage instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
        VillageMageEvents.makeMage(mage);
        if (!level.addFreshEntity(mage)) return InteractionResult.FAIL;
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)
            context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
