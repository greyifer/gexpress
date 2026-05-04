package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.TimeMasterFreezeUsePayload;
import dev.mapselect.network.TimeMasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientTimeMasterState {
	private static boolean wasAbilityDown;
	private static boolean wasFreezeDown;

	private ClientTimeMasterState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientTimeMasterState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client)
				|| !isLocalTimeMaster(client)) {
			wasAbilityDown = false;
			wasFreezeDown = false;
			return;
		}

		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(TimeMasterUsePayload.ID)) {
			ClientPlayNetworking.send(new TimeMasterUsePayload());
		}
		wasAbilityDown = abilityDown;

		KeyBinding freeze = ClientAbilityKeys.secondaryBinding();
		boolean freezeDown = freeze != null && ClientAbilityKeys.isDown(client, freeze);
		if (freezeDown && !wasFreezeDown && ClientPlayNetworking.canSend(TimeMasterFreezeUsePayload.ID)) {
			ClientPlayNetworking.send(new TimeMasterFreezeUsePayload());
		}
		wasFreezeDown = freezeDown;
	}

	private static boolean isLocalTimeMaster(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.TIME_MASTER_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
