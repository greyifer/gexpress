package dev.mapselect.level;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.command.TagCommand;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class LevelManager {
	private static final int GAME_XP = 25;
	private static final int WIN_XP = 50;

	private LevelManager() {}

	public static void register() {
		GameEvents.ON_FINISH_FINALIZE.register(LevelManager::grantRoundXp);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				if (handler.player.getWorld() instanceof ServerWorld world) {
					LevelComponent.KEY.sync(world);
					TagCommand.refreshPlayerListName(handler.player);
				}
			}));
	}

	private static void grantRoundXp(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world)) return;
		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.getNullable(world);
		if (roundEnd == null || roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;
		LevelComponent levels = LevelComponent.KEY.get(world);
		Set<UUID> awarded = new LinkedHashSet<>();
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null || !awarded.add(entry.player().getId())) continue;
			int amount = GAME_XP + (roundEnd.didWin(entry.player().getId()) ? WIN_XP : 0);
			levels.addXp(entry.player().getId(), amount);
		}
		if (!awarded.isEmpty()) {
			LevelComponent.KEY.sync(world);
			for (ServerPlayerEntity player : world.getPlayers()) {
				TagCommand.refreshPlayerListName(player);
			}
		}
	}
}
