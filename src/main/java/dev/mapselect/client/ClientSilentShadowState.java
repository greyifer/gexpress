package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.ShadowMarchUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.silent.SilentShadowComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;

public final class ClientSilentShadowState {
	private static boolean wasAbilityDown;

	private ClientSilentShadowState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientSilentShadowState::tick);
	}

	public static boolean isShadowed(Entity entity) {
		return SilentShadowComponent.isShadowed(entity);
	}

	public static int shadowColor() {
		int alpha = Math.max(0, Math.min(255, Math.round(GexpressConfig.getSilentShadowAlpha() * 255.0F)));
		return (alpha << 24);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			wasAbilityDown = false;
			return;
		}
		if (!isLocalSilent(client)) {
			wasAbilityDown = false;
			return;
		}

		KeyBinding ability = resolveAbilityBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(ShadowMarchUsePayload.ID)) {
			ClientPlayNetworking.send(new ShadowMarchUsePayload());
		}
		wasAbilityDown = abilityDown;
	}

	private static boolean isLocalSilent(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.THE_SILENT_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}
}
