package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.ScatterBrainUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientScatterBrainState {
	private static boolean wasAbilityDown;

	private ClientScatterBrainState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientScatterBrainState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !isLocalScatterBrain(client)) {
			wasAbilityDown = false;
			return;
		}
		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean down = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (down && !wasAbilityDown && ClientPlayNetworking.canSend(ScatterBrainUsePayload.ID)) {
			ClientPlayNetworking.send(new ScatterBrainUsePayload());
		}
		wasAbilityDown = down;
	}

	private static boolean isLocalScatterBrain(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.SCATTER_BRAIN_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
