package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.MedicShieldUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class ClientMedicState {
	private static boolean wasAbilityDown;

	private ClientMedicState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientMedicState::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || !isLocalMedic(client)) {
			wasAbilityDown = false;
			return;
		}

		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(MedicShieldUsePayload.ID)) {
			ClientPlayNetworking.send(new MedicShieldUsePayload());
		}
		wasAbilityDown = abilityDown;
	}

	private static boolean isLocalMedic(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.MEDIC_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
