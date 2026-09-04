package com.strutton.dynamicmagic.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Restores temporary world/player state when mana-backed weather or spirit projection ends. */
public final class SustainedMagicController {
    private static final Map<UUID, GameType> PROJECTIONS = new HashMap<>();
    private static final Map<UUID, WeatherState> WEATHER = new HashMap<>();
    private SustainedMagicController() {}

    public static void activate(ServerPlayer player, ImpactType impact) {
        if (impact == ImpactType.ASTRAL_PROJECTION) {
            PROJECTIONS.computeIfAbsent(player.getUUID(), ignored -> player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);
        } else if (impact == ImpactType.WEATHER_RAIN || impact == ImpactType.WEATHER_STORM) {
            ServerLevel level = player.serverLevel();
            WEATHER.computeIfAbsent(player.getUUID(), ignored -> new WeatherState(level.isRaining(), level.isThundering()));
            level.setWeatherParameters(0, 6000, true, impact == ImpactType.WEATHER_STORM);
        }
    }

    public static void deactivate(ServerPlayer player) {
        GameType previous = PROJECTIONS.remove(player.getUUID());
        if (previous != null && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR)
            player.setGameMode(previous);
        WeatherState weather = WEATHER.remove(player.getUUID());
        if (weather != null && WEATHER.isEmpty())
            player.serverLevel().setWeatherParameters(weather.raining ? 0 : 6000,
                    weather.raining ? 6000 : 0, weather.raining, weather.thundering);
    }

    private record WeatherState(boolean raining, boolean thundering) {}
}
