package com.strutton.dynamicmagic.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Nine player-named teleport marks, displayed through a vanilla chest-compatible menu. */
public final class TeleportLocations {
    private static final String KEY = "DynamicMagicTeleportLocations";
    private static final int CAPACITY = 9;
    private TeleportLocations() {}

    public static int save(ServerPlayer player, String requestedName) {
        String name = requestedName.strip();
        if (name.isEmpty()) return -1;
        CompoundTag locations = player.getPersistentData().getCompound(KEY).copy();
        int slot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            CompoundTag existing = locations.getCompound(Integer.toString(i));
            if (existing.getString("Name").equalsIgnoreCase(name)) { slot = i; break; }
            if (slot < 0 && existing.isEmpty()) slot = i;
        }
        if (slot < 0) return -1;
        CompoundTag location = new CompoundTag();
        location.putString("Name", name.substring(0, Math.min(32, name.length())));
        location.putString("Dimension", player.serverLevel().dimension().location().toString());
        location.putDouble("X", player.getX());
        location.putDouble("Y", player.getY());
        location.putDouble("Z", player.getZ());
        location.putFloat("Yaw", player.getYRot());
        location.putFloat("Pitch", player.getXRot());
        locations.put(Integer.toString(slot), location);
        player.getPersistentData().put(KEY, locations);
        return slot;
    }

    public static void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new TeleportMenu(id, inventory, player),
                Component.literal("Teleport Marks")));
    }

    public static boolean teleportWhereLooking(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(Math.max(2, range)));
        HitResult result = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (!(result instanceof BlockHitResult hit) || result.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.literal("No safe teleport surface is in range.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos base = hit.getBlockPos().relative(hit.getDirection());
        for (int dy = 0; dy <= 3; dy++) {
            Vec3 destination = Vec3.atBottomCenterOf(base.above(dy));
            var moved = player.getBoundingBox().move(destination.subtract(player.position()));
            if (player.serverLevel().noCollision(player, moved)) {
                player.teleportTo(destination.x, destination.y, destination.z);
                return true;
            }
        }
        player.displayClientMessage(Component.literal("The targeted location is obstructed.")
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static CompoundTag locations(ServerPlayer player) { return player.getPersistentData().getCompound(KEY); }
    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY)) to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy());
    }

    private static final class TeleportMenu extends ChestMenu {
        private final ServerPlayer player;
        private TeleportMenu(int id, Inventory inventory, ServerPlayer player) {
            this(id, inventory, player, contents(player));
        }
        private TeleportMenu(int id, Inventory inventory, ServerPlayer player, SimpleContainer contents) {
            super(MenuType.GENERIC_9x1, id, inventory, contents, 1);
            this.player = player;
        }
        @Override public void clicked(int slot, int button, ClickType clickType, net.minecraft.world.entity.player.Player ignored) {
            if (slot >= 0 && slot < CAPACITY && teleportSaved(player, slot)) {
                player.closeContainer();
                return;
            }
            if (slot >= CAPACITY) super.clicked(slot, button, clickType, ignored);
        }
        @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
    }

    private static SimpleContainer contents(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(CAPACITY);
        CompoundTag locations = locations(player);
        for (int i = 0; i < CAPACITY; i++) {
            CompoundTag location = locations.getCompound(Integer.toString(i));
            if (location.isEmpty()) continue;
            ItemStack marker = new ItemStack(Items.ENDER_PEARL);
            marker.set(DataComponents.CUSTOM_NAME, Component.literal(location.getString("Name"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            container.setItem(i, marker);
        }
        return container;
    }

    private static boolean teleportSaved(ServerPlayer player, int slot) {
        CompoundTag location = locations(player).getCompound(Integer.toString(slot));
        if (location.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.tryParse(location.getString("Dimension"));
        if (id == null) return false;
        ServerLevel destination = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        if (destination == null) return false;
        player.teleportTo(destination, location.getDouble("X"), location.getDouble("Y"), location.getDouble("Z"),
                location.getFloat("Yaw"), location.getFloat("Pitch"));
        return true;
    }
}
