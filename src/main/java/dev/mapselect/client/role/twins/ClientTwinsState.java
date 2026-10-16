package dev.mapselect.client.role.twins;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.entity.TwinBodyEntity;
import dev.mapselect.network.role.twins.TwinsSwapPayload;
import dev.mapselect.network.role.twins.TwinsVoiceRelayPayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientTwinsState {
	private static Object syncedWorld;
	private static boolean wasSwapDown;
	private static boolean wasVoiceRelayDown;
	private static long nextSwapRequestTick;

	private ClientTwinsState() {}

	public static void register() {
		ClientTwinsBodies.register();
		ClientTickEvents.END_CLIENT_TICK.register(ClientTwinsState::tick);
		ClientTwinsFeedRenderer.register();
	}

	private static void tick(MinecraftClient client) {
		checkWorld(client);
		restoreMainCamera(client);
		if (client != null && client.player != null && client.world != null && isLocalTwins(client)) {
			ClientTwinsFeedRenderer.prepare(client);
		}
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalTwins(client)) {
			wasSwapDown = false;
			wasVoiceRelayDown = false;
			return;
		}
		KeyBinding swap = ClientAbilityKeys.primaryBinding();
		boolean swapDown = swap != null && ClientAbilityKeys.isDown(client, swap);
		long now = client.world.getTime();
		if (swapDown && !wasSwapDown && now >= nextSwapRequestTick
				&& ClientPlayNetworking.canSend(TwinsSwapPayload.ID)) {
			ClientPlayNetworking.send(new TwinsSwapPayload());
			nextSwapRequestTick = now + 20L;
		}
		wasSwapDown = swapDown;

		KeyBinding voiceRelay = ClientAbilityKeys.secondaryBinding();
		boolean voiceRelayDown = voiceRelay != null && ClientAbilityKeys.isDown(client, voiceRelay);
		if (voiceRelayDown && !wasVoiceRelayDown
				&& ClientPlayNetworking.canSend(TwinsVoiceRelayPayload.ID)) {
			ClientPlayNetworking.send(new TwinsVoiceRelayPayload());
		}
		wasVoiceRelayDown = voiceRelayDown;
	}

	static boolean isLocalTwins(MinecraftClient client) {
		try {
			if (client == null || client.world == null || client.player == null) return false;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.TWINS_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void checkWorld(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (syncedWorld == world) return;
		syncedWorld = world;
		wasSwapDown = false;
		wasVoiceRelayDown = false;
		nextSwapRequestTick = 0L;
		ClientTwinsFeedRenderer.release();
	}

	private static void restoreMainCamera(MinecraftClient client) {
		if (client == null || client.player == null) return;
		if (client.getCameraEntity() instanceof TwinBodyEntity || ClientTwinsBodies.isBody(client.getCameraEntity())) {
			client.setCameraEntity(client.player);
		}
	}
}
