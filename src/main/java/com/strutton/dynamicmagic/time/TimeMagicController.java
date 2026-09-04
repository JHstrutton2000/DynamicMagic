package com.strutton.dynamicmagic.time;

import com.strutton.dynamicmagic.item.CraftedSpellItem;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import com.strutton.dynamicmagic.magic.ImpactType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import com.strutton.dynamicmagic.magic.ProgramSpellController;

/** Coordinates global tick-rate spells so every entity and movement follows the altered time rate. */
public final class TimeMagicController {
    private static final Map<MinecraftServer, Map<UUID, TimeState>> ACTIVE = new WeakHashMap<>();
    private static final Map<MinecraftServer, AppliedState> LAST_APPLIED = new WeakHashMap<>();
    private TimeMagicController() {}

    public static void activate(ServerPlayer player, ImpactType mode, double power) {
        ACTIVE.computeIfAbsent(player.server, ignored -> new HashMap<>())
                .put(player.getUUID(), new TimeState(mode, power));
        apply(player.server);
    }

    public static void deactivate(ServerPlayer player) {
        Map<UUID, TimeState> states = ACTIVE.get(player.server);
        if (states != null) {
            states.remove(player.getUUID());
            if (states.isEmpty()) ACTIVE.remove(player.server);
        }
        apply(player.server);
    }

    public static boolean isTimeMode(ImpactType impact) {
        return impact == ImpactType.STOP_TIME || impact == ImpactType.SPEED_TIME || impact == ImpactType.SLOW_TIME;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        Map<UUID, TimeState> states = ACTIVE.get(server);
        if (states == null) return;
        states.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return true;
            if (ProgramSpellController.hasActiveTime(player)) return false;
            if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof CraftedSpellItem)) return true;
            CraftedSpell spell = CraftedSpell.read(player.getUseItem());
            return spell == null || !spell.hasTimeMagic();
        });
        if (states.isEmpty()) ACTIVE.remove(server);
        apply(server);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) deactivate(player);
    }

    private static void apply(MinecraftServer server) {
        Map<UUID, TimeState> states = ACTIVE.get(server);
        boolean stop = states != null && states.values().stream().anyMatch(state -> state.mode == ImpactType.STOP_TIME);
        float rate = 20;
        if (!stop && states != null) {
            for (TimeState state : states.values()) {
                float requested = switch (state.mode) {
                    case SPEED_TIME -> (float) Math.min(100, 20 * (1 + state.power));
                    case SLOW_TIME -> (float) Math.max(1, 20 / (1 + state.power));
                    default -> 20;
                };
                if (state.mode == ImpactType.SPEED_TIME) rate = Math.max(rate, requested);
                else if (state.mode == ImpactType.SLOW_TIME && rate == 20) rate = requested;
            }
        }
        AppliedState next = new AppliedState(rate, stop);
        if (next.equals(LAST_APPLIED.get(server))) return;
        server.tickRateManager().setTickRate(rate);
        server.tickRateManager().setFrozen(stop);
        server.getPlayerList().broadcastAll(new ClientboundTickingStatePacket(rate, stop));
        LAST_APPLIED.put(server, next);
    }

    private record TimeState(ImpactType mode, double power) {}
    private record AppliedState(float rate, boolean frozen) {}
}
