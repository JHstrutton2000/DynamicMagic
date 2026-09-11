package com.strutton.dynamicmagic.storage;

import com.strutton.dynamicmagic.mana.Mana;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import com.strutton.dynamicmagic.network.OpenEntityStoragePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** A personal pocket dimension stored with the player. Items are stored as ItemEntity NBT. */
public final class MagicStorage {
    private static final String KEY = "DynamicMagicStorage";
    public static final int BASE_CAPACITY = 9;
    private MagicStorage() {}

    public static boolean store(ServerPlayer player, Entity entity, boolean itemsOnly) {
        if (entity == player || entity instanceof ServerPlayer || (itemsOnly && !(entity instanceof ItemEntity))) return false;
        if (com.strutton.dynamicmagic.compat.SoloLevelingIntegration.isProtectedEntity(entity)) {
            player.displayClientMessage(Component.literal("Gate creatures, bosses, and shadows resist Spatial storage.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ListTag entries = entries(player);
        int capacity = capacity(player);
        if (entries.size() >= capacity) {
            player.displayClientMessage(Component.literal("Spatial backpack is full (" + capacity + ").")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        double manaCost = captureCost(entity);
        if (!Mana.consume(player, manaCost)) {
            player.displayClientMessage(Component.literal("Capturing this target requires " + (int) Math.ceil(manaCost) + " mana.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        CompoundTag entityData = new CompoundTag();
        if (!entity.save(entityData)) return false;
        CompoundTag entry = new CompoundTag();
        entry.put("Entity", entityData);
        entry.putString("Name", entity.getDisplayName().getString());
        entries.add(entry);
        saveEntries(player, entries);
        entity.discard();
        player.displayClientMessage(Component.literal("Stored " + entry.getString("Name") + " (" + entries.size() + "/" + capacity + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    public static boolean releaseLast(ServerPlayer player) {
        return release(player, entries(player).size() - 1);
    }

    public static boolean release(ServerPlayer player, int index) {
        ListTag entries = entries(player);
        if (entries.isEmpty()) {
            player.displayClientMessage(Component.literal("Magic storage is empty.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (index < 0 || index >= entries.size()) return false;
        CompoundTag entry = entries.getCompound(index);
        CompoundTag entityData = entry.getCompound("Entity");
        ServerLevel level = player.serverLevel();
        Vec3 location = player.position().add(player.getLookAngle().scale(2.5));
        Entity restored = EntityType.loadEntityRecursive(entityData, level, entity -> {
            entity.moveTo(location.x, location.y + .25, location.z, player.getYRot(), entity.getXRot());
            return entity;
        });
        if (restored == null || !level.addFreshEntity(restored)) {
            player.displayClientMessage(Component.literal("There is no room to release " + entry.getString("Name") + ".")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        entries.remove(index);
        saveEntries(player, entries);
        player.displayClientMessage(Component.literal("Released " + entry.getString("Name") + " (" + entries.size() + "/" + capacity(player) + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    public static int size(ServerPlayer player) { return entries(player).size(); }
    public static int capacity(ServerPlayer player) {
        return Math.min(54, BASE_CAPACITY + (int) Math.floor(Math.sqrt(
                com.strutton.dynamicmagic.magic.ElementMastery.experience(player, com.strutton.dynamicmagic.magic.Element.SPACE)) * 1.5));
    }

    public static void openMenu(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        ListTag entries = entries(player);
        for (int i = 0; i < entries.size(); i++) names.add(entries.getCompound(i).getString("Name"));
        PacketDistributor.sendToPlayer(player, new OpenEntityStoragePayload(names));
    }

    public static void copy(ServerPlayer original, ServerPlayer replacement) {
        saveEntries(replacement, entries(original));
    }

    private static double captureCost(Entity entity) {
        double size = Math.max(1, entity.getBoundingBox().getXsize() * entity.getBoundingBox().getYsize() * entity.getBoundingBox().getZsize());
        double strength = entity instanceof LivingEntity living ? living.getMaxHealth() / 8.0 : 1;
        double aggression = entity instanceof Mob mob && mob.getTarget() != null ? 5 : 1;
        return Math.max(2, size * 2 + strength + aggression);
    }

    private static ListTag entries(ServerPlayer player) {
        return player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy();
    }

    private static void saveEntries(ServerPlayer player, ListTag entries) {
        player.getPersistentData().put(KEY, entries);
    }
}
