package dev.mapselect.client.hud;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.game.WatheStaminaAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * KinsWathe already draws the stamina HUD from Wathe's internal sprintingTicks field.
 * G'Express only needs to seed that real field when a role with more stamina becomes active.
 */
public final class ClientStaminaBarFix {
	private static UUID trackedPlayerId;
	private static Identifier trackedRoleId;
	private static int trackedMaximum;

	private ClientStaminaBarFix() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientStaminaBarFix::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null
				|| client.player == null || client.world == null || !WatheClient.isPlayerAliveAndInSurvival()) {
			reset();
			return;
		}

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			reset();
			return;
		}

		Role role = game.getRole(client.player);
		if (role == null || role.identifier() == null || role.getMaxSprintTime() <= 0) {
			reset();
			return;
		}

		UUID playerId = client.player.getUuid();
		Identifier roleId = role.identifier();
		int maximum = role.getMaxSprintTime();
		if (playerId.equals(trackedPlayerId) && roleId.equals(trackedRoleId) && maximum == trackedMaximum) return;

		trackedPlayerId = playerId;
		trackedRoleId = roleId;
		trackedMaximum = maximum;
		WatheStaminaAccess.set(client.player, maximum);
	}

	private static void reset() {
		trackedPlayerId = null;
		trackedRoleId = null;
		trackedMaximum = 0;
	}
}
