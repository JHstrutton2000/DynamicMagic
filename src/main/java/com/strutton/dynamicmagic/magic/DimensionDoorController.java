package com.strutton.dynamicmagic.magic;

import com.strutton.dynamicmagic.knowledge.DimensionKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Temporary 1x2 physical door markers backed by learned cross-dimension destinations. */
public final class DimensionDoorController {
    private static final Map<DoorKey, Door> DOORS = new HashMap<>();
    private DimensionDoorController() {}
    public static boolean create(ServerPlayer player, double range, double durationSeconds) {
        ResourceLocation destination = DimensionKnowledge.selected(player);
        if (destination == null || !DimensionKnowledge.knows(player, destination)) {
            player.displayClientMessage(Component.literal("Study a portal and enough native subjects in its dimension first.").withStyle(ChatFormatting.RED), true); return false;
        }
        var hit = player.pick(Math.max(2, range), 0, false); BlockPos base = BlockPos.containing(hit.getLocation()).relative(player.getDirection().getOpposite());
        ServerLevel level = player.serverLevel(); if (!level.getBlockState(base).canBeReplaced() || !level.getBlockState(base.above()).canBeReplaced()) return false;
        DoorKey key = new DoorKey(level.dimension().location(), base); Door door = new Door(destination, level.getBlockState(base), level.getBlockState(base.above()), level.getGameTime() + Math.max(100, (long)(durationSeconds * 20)));
        DOORS.put(key, door); level.setBlockAndUpdate(base, Blocks.PURPLE_STAINED_GLASS.defaultBlockState()); level.setBlockAndUpdate(base.above(), Blocks.PURPLE_STAINED_GLASS.defaultBlockState()); return true;
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 4 != 0) return;
        Door door = DOORS.get(new DoorKey(player.serverLevel().dimension().location(), player.blockPosition()));
        if (door == null) door = DOORS.get(new DoorKey(player.serverLevel().dimension().location(), player.blockPosition().below()));
        if (door == null) return;
        ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, door.destination())); if (target == null) return;
        BlockPos spawn = target.getSharedSpawnPos(); player.teleportTo(target, spawn.getX() + .5, spawn.getY() + 1, spawn.getZ() + .5, player.getYRot(), player.getXRot());
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<DoorKey, Door>> iterator = DOORS.entrySet().iterator();
        while (iterator.hasNext()) { var value = iterator.next(); ServerLevel level = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, value.getKey().dimension()));
            if (level != null && level.getGameTime() >= value.getValue().expires()) { level.setBlockAndUpdate(value.getKey().base(), value.getValue().lower()); level.setBlockAndUpdate(value.getKey().base().above(), value.getValue().upper()); iterator.remove(); }
        }
    }
    private record DoorKey(ResourceLocation dimension, BlockPos base) {}
    private record Door(ResourceLocation destination, BlockState lower, BlockState upper, long expires) {}
}
