package dev.mapselect.client.role.twins;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.entity.TwinBodyEntity;
import dev.mapselect.network.role.twins.TwinsSwapPayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientTwinsState {
	private static Object syncedWorld;
	private static boolean wasAbilityDown;
	private static long nextSwapRequestTick;

	private ClientTwinsState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientTwinsState::tick);
	}

	private static void tick(MinecraftClient client) {
		checkWorld(client);
		restoreMainCamera(client);
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalTwins(client)) {
			wasAbilityDown = false;
			return;
		}
		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean down = ability != null && ClientAbilityKeys.isDown(client, ability);
		long now = client.world.getTime();
		if (down && !wasAbilityDown && now >= nextSwapRequestTick && ClientPlayNetworking.canSend(TwinsSwapPayload.ID)) {
			ClientPlayNetworking.send(new TwinsSwapPayload());
			nextSwapRequestTick = now + 20L;
		}
		wasAbilityDown = down;
	}

	private static boolean isLocalTwins(MinecraftClient client) {
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
		wasAbilityDown = false;
		nextSwapRequestTick = 0L;
	}

	private static void restoreMainCamera(MinecraftClient client) {
		if (client == null || client.player == null) return;
		if (client.getCameraEntity() instanceof TwinBodyEntity) {
			client.setCameraEntity(client.player);
		}
	}
}
