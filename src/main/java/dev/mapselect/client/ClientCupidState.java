package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.CupidUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

public final class ClientCupidState {
	private static boolean wasUseDown;

	private ClientCupidState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientCupidState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !isLocalCupid(client)) {
			wasUseDown = false;
			return;
		}
		KeyBinding binding = ClientAbilityKeys.primaryBinding();
		boolean down = binding != null && ClientAbilityKeys.isDown(client, binding);
		if (down && !wasUseDown && ClientPlayNetworking.canSend(CupidUsePayload.ID)) {
			ClientPlayNetworking.send(new CupidUsePayload());
		}
		wasUseDown = down;
	}

	private static boolean isLocalCupid(MinecraftClient client) {
		return MapSelectRoles.CUPID_ID.equals(ClientCopycatState.effectiveRoleId(client, localRoleId(client)));
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
