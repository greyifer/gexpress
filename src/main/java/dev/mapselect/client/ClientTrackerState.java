package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.TrackerStatePayload;
import dev.mapselect.network.TrackerUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientTrackerState {
	private static final Set<UUID> trackedIds = ConcurrentHashMap.newKeySet();
	private static boolean wasAbilityDown;

	private ClientTrackerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TrackerStatePayload.ID, (payload, context) ->
			context.client().execute(() -> {
				trackedIds.clear();
				trackedIds.addAll(payload.trackedIds());
			}));
		ClientTickEvents.END_CLIENT_TICK.register(ClientTrackerState::tick);
	}

	public static boolean isTracked(UUID playerId) {
		return playerId != null && trackedIds.contains(playerId);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !isLocalTracker(client)) {
			wasAbilityDown = false;
			return;
		}
		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean down = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (down && !wasAbilityDown && ClientPlayNetworking.canSend(TrackerUsePayload.ID)) {
			ClientPlayNetworking.send(new TrackerUsePayload());
		}
		wasAbilityDown = down;
	}

	private static boolean isLocalTracker(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.TRACKER_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
