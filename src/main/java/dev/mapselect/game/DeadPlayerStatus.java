package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public final class DeadPlayerStatus {
	private DeadPlayerStatus() {}

	public static boolean isDeadRoundParticipant(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null || VultureManager.isStashed(player)) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null || !isRunningOrStopping(game) || !game.getRoles().containsKey(player.getUuid())) {
			return false;
		}
		return player.interactionManager.getGameMode() == GameMode.SPECTATOR
			&& !GameFunctions.isPlayerAliveAndSurvival(player);
	}

	public static boolean isLivingRoundParticipant(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null || VultureManager.isStashed(player)) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return game != null
			&& isRunningOrStopping(game)
			&& game.getRoles().containsKey(player.getUuid())
			&& GameFunctions.isPlayerAliveAndSurvival(player);
	}

	public static boolean isRunningOrStopping(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && isRunningOrStopping(game);
	}

	private static boolean isRunningOrStopping(GameWorldComponent game) {
		return game.isRunning() || game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING;
	}
}
