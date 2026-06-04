package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * KinsWathe already draws the stamina HUD from Wathe's internal sprintingTicks field.
 * G'Express only needs to seed that real field when a role with more stamina becomes active.
 */
public final class ClientStaminaBarFix {
	private static Field sprintingTicksField;
	private static boolean lookedUpSprintingTicksField;
	private static UUID trackedPlayerId;
	private static Identifier trackedRoleId;

	private ClientStaminaBarFix() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientStaminaBarFix::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!FabricLoader.getInstance().isModLoaded("kinswathe") || client == null
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
		if (playerId.equals(trackedPlayerId) && roleId.equals(trackedRoleId)) return;

		trackedPlayerId = playerId;
		trackedRoleId = roleId;
		seedRealStamina(client.player, role.getMaxSprintTime());
	}

	private static void seedRealStamina(ClientPlayerEntity player, int maxSprintTime) {
		Field field = sprintingTicksField(player);
		if (field == null) return;
		try {
			float current = field.getFloat(player);
			if (current < maxSprintTime) field.setFloat(player, maxSprintTime);
		} catch (Throwable ignored) {
		}
	}

	private static Field sprintingTicksField(ClientPlayerEntity player) {
		if (lookedUpSprintingTicksField) return sprintingTicksField;
		lookedUpSprintingTicksField = true;
		Class<?> type = player.getClass();
		while (type != null) {
			for (Field field : type.getDeclaredFields()) {
				if (field.getType() != float.class) continue;
				String name = field.getName().toLowerCase(java.util.Locale.ROOT);
				if (!name.contains("sprinting") && !name.contains("stamina")) continue;
				field.setAccessible(true);
				sprintingTicksField = field;
				return field;
			}
			type = type.getSuperclass();
		}
		return null;
	}

	private static void reset() {
		trackedPlayerId = null;
		trackedRoleId = null;
	}
}
