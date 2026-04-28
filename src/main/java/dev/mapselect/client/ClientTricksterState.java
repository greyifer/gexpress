package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.TricksterDancingCartsPayload;
import dev.mapselect.network.TricksterSkinSwapPayload;
import dev.mapselect.network.TricksterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.Map;
import java.util.UUID;

public final class ClientTricksterState {
	private static Map<UUID, UUID> swaps = Map.of();
	private static long expiresAtTick = 0L;
	private static boolean wasAbilityDown;
	private static boolean wasSecondaryDown;

	private ClientTricksterState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TricksterSkinSwapPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientTricksterState::tick);
	}

	public static UUID replacementFor(UUID playerId) {
		if (playerId == null || !isActive()) return null;
		return swaps.get(playerId);
	}

	public static long remainingTicks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || !isActive()) return 0L;
		return Math.max(0L, expiresAtTick - client.world.getTime());
	}

	private static void apply(TricksterSkinSwapPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || payload.durationTicks() <= 0 || payload.swaps().isEmpty()) {
			clear();
			return;
		}
		swaps = Map.copyOf(payload.swaps());
		expiresAtTick = client.world.getTime() + payload.durationTicks();
	}

	private static boolean isActive() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || swaps.isEmpty()) {
			clear();
			return false;
		}
		if (client.world.getTime() >= expiresAtTick) {
			clear();
			return false;
		}
		return true;
	}

	private static void clear() {
		swaps = Map.of();
		expiresAtTick = 0L;
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			wasAbilityDown = false;
			wasSecondaryDown = false;
			clear();
			return;
		}
		if (client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !isLocalTrickster(client)) {
			wasAbilityDown = false;
			wasSecondaryDown = false;
			return;
		}

		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(TricksterUsePayload.ID)) {
			ClientPlayNetworking.send(new TricksterUsePayload());
		}
		wasAbilityDown = abilityDown;

		KeyBinding secondary = ClientAbilityKeys.secondaryBinding();
		boolean secondaryDown = secondary != null && ClientAbilityKeys.isDown(client, secondary);
		if (secondaryDown && !wasSecondaryDown && ClientPlayNetworking.canSend(TricksterDancingCartsPayload.ID)) {
			ClientPlayNetworking.send(new TricksterDancingCartsPayload());
		}
		wasSecondaryDown = secondaryDown;
	}

	private static boolean isLocalTrickster(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.TRICKSTER_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

}
