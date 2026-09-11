package com.strutton.dynamicmagic.knowledge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import java.util.HashSet;
import java.util.Set;

/** Unique native observations turn raw Space mastery into knowledge of a specific dimension. */
public final class DimensionKnowledge {
    private static final String KEY = "DynamicMagicDimensionKnowledge";
    private static final String LAST = "DynamicMagicLastLearnedDimension";
    private DimensionKnowledge() {}

    public static void observePortal(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        ResourceLocation target = portalDestination(player, level, pos);
        if (target == null) return;
        CompoundTag entry = entry(player, target).copy(); entry.putBoolean("Portal", true); save(player, target, entry); check(player, target);
    }
    public static void observeBlock(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        ResourceLocation dimension = level.dimension().location();
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (dimension.equals(LevelIds.OVERWORLD) || block == null || !nativeObservation(dimension, block)) return;
        add(player, dimension, "block:" + block);
    }
    public static void observeEntity(ServerPlayer player, LivingEntity entity) {
        ResourceLocation dimension = player.serverLevel().dimension().location();
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (dimension.equals(LevelIds.OVERWORLD) || type == null || !nativeObservation(dimension, type)) return;
        add(player, dimension, "entity:" + type);
    }
    public static boolean knows(ServerPlayer player, ResourceLocation dimension) { return entry(player, dimension).getBoolean("Learned"); }
    public static ResourceLocation selected(ServerPlayer player) { return ResourceLocation.tryParse(player.getPersistentData().getString(LAST)); }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY)) to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy());
        if (from.getPersistentData().contains(LAST)) to.getPersistentData().putString(LAST, from.getPersistentData().getString(LAST));
    }
    private static void add(ServerPlayer player, ResourceLocation dimension, String observation) {
        CompoundTag entry = entry(player, dimension).copy(); ListTag list = entry.getList("Observations", Tag.TAG_STRING).copy();
        for (Tag tag : list) if (tag.getAsString().equals(observation)) return;
        list.add(StringTag.valueOf(observation)); entry.put("Observations", list); save(player, dimension, entry); check(player, dimension);
        player.displayClientMessage(Component.literal("Dimensional study: " + list.size() + " unique native observations in " + dimension).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
    private static void check(ServerPlayer player, ResourceLocation dimension) {
        CompoundTag entry = entry(player, dimension); if (entry.getBoolean("Learned") || !entry.getBoolean("Portal")) return;
        Set<String> observations = new HashSet<>(); for (Tag tag : entry.getList("Observations", Tag.TAG_STRING)) observations.add(tag.getAsString());
        boolean learned;
        if (dimension.equals(LevelIds.END)) learned = observations.contains("block:minecraft:end_stone")
                && observations.contains("block:minecraft:dragon_egg") && observations.contains("entity:minecraft:shulker");
        else learned = observations.size() >= (dimension.equals(LevelIds.NETHER) ? 6 : 8);
        if (!learned) return;
        CompoundTag changed = entry.copy(); changed.putBoolean("Learned", true); save(player, dimension, changed);
        player.getPersistentData().putString(LAST, dimension.toString());
        player.displayClientMessage(Component.literal("Dimensional breakthrough: you can now create a doorway to " + dimension + ".").withStyle(ChatFormatting.GOLD), false);
    }
    private static boolean nativeObservation(ResourceLocation dimension, ResourceLocation subject) {
        if (dimension.equals(LevelIds.END)) return subject.getPath().equals("end_stone") || subject.getPath().equals("dragon_egg") || subject.getPath().equals("shulker");
        if (dimension.equals(LevelIds.NETHER)) return subject.getNamespace().equals("minecraft");
        return subject.getNamespace().equals(dimension.getNamespace());
    }
    private static ResourceLocation portalDestination(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(Blocks.NETHER_PORTAL)) return level.dimension().location().equals(LevelIds.NETHER) ? LevelIds.OVERWORLD : LevelIds.NETHER;
        if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) return LevelIds.END;
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock()); if (block == null) return null;
        for (ServerLevel candidate : player.server.getAllLevels()) if (!candidate.dimension().location().equals(level.dimension().location())
                && candidate.dimension().location().getNamespace().equals(block.getNamespace())) return candidate.dimension().location();
        return null;
    }
    private static CompoundTag entry(ServerPlayer player, ResourceLocation dimension) {
        CompoundTag root = player.getPersistentData().getCompound(KEY); return root.getCompound(id(dimension));
    }
    private static void save(ServerPlayer player, ResourceLocation dimension, CompoundTag entry) {
        CompoundTag root = player.getPersistentData().getCompound(KEY).copy(); entry.putString("Dimension", dimension.toString()); root.put(id(dimension), entry); player.getPersistentData().put(KEY, root);
    }
    private static String id(ResourceLocation id) { return id.toString().replace(':', '_').replace('/', '_'); }
    private static final class LevelIds {
        static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
        static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");
        static final ResourceLocation END = ResourceLocation.withDefaultNamespace("the_end");
    }
}
