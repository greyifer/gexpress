package dev.mapselect.client.role.sniffer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.network.role.sniffer.SnifferUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientSnifferState {
	private static boolean wasPrimaryDown;

	private ClientSnifferState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientSnifferState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !isLocalSniffer(client)) {
			wasPrimaryDown = false;
			return;
		}
		KeyBinding primary = ClientAbilityKeys.primaryBinding();
		boolean down = primary != null && ClientAbilityKeys.isDown(client, primary);
		if (down && !wasPrimaryDown && ClientPlayNetworking.canSend(SnifferUsePayload.ID)) {
			ClientPlayNetworking.send(new SnifferUsePayload());
		}
		wasPrimaryDown = down;
	}

	private static boolean isLocalSniffer(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && ClientCopycatState.isEffectiveRole(client, MapSelectRoles.SNIFFER_ID);
		} catch (Throwable ignored) {
			return false;
		}
	}
}
