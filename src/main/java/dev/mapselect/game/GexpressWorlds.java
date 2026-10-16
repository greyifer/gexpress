package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class GexpressWorlds {
	private static final String WORLDMANAGER_MOD_ID = "worldmanager";

	private GexpressWorlds() {}

	public static boolean isGameWorld(ServerWorld world) {
		if (world == null) return false;
		RegistryKey<World> key = world.getRegistryKey();
		if (World.OVERWORLD.equals(key)) return true;
		if (World.NETHER.equals(key) || World.END.equals(key)) return false;
		if (isActiveGameWorld(world) || hasActiveMap(world)) return true;
		return FabricLoader.getInstance().isModLoaded(WORLDMANAGER_MOD_ID);
	}

	public static boolean isActiveGameWorld(ServerWorld world) {
		return world != null && isActiveGame(GameWorldComponent.KEY.getNullable(world));
	}

	public static boolean hasOtherActiveGameWorld(ServerWorld world) {
		if (world == null || world.getServer() == null) return false;
		for (ServerWorld otherWorld : world.getServer().getWorlds()) {
			if (otherWorld == null || otherWorld == world) continue;
			if (isActiveGameWorld(otherWorld)) return true;
		}
		return false;
	}

	public static boolean isActiveGame(GameWorldComponent game) {
		if (game == null) return false;
		return game.isRunning()
			|| game.getGameStatus() == GameWorldComponent.GameStatus.STARTING
			|| game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
			|| game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING;
	}

	private static boolean hasActiveMap(ServerWorld world) {
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		if (weather == null) return false;
		String currentMap = weather.getCurrentMapName();
		return currentMap != null && !currentMap.isBlank();
	}
}
