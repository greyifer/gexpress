package dev.mapselect.client.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.RoleTeams;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.List;

public final class ClientLastStandState {
	private ClientLastStandState() {}

	public static boolean isLocalLastStandDuel() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null || !GexpressConfig.isLastStandEnabled()) {
			return false;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		List<AbstractClientPlayerEntity> alive = client.world.getPlayers().stream()
			.filter(player -> player.isAlive() && !player.isSpectator() && !player.isCreative())
			.toList();
		return alive.size() == 2
			&& alive.contains(client.player)
			&& !RoleTeams.sameTeam(game, alive.get(0), alive.get(1));
	}
}
