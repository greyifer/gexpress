package dev.mapselect.client;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.GuardianAngelShieldStatePayload;
import dev.mapselect.network.GuardianAngelShieldUsePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ClientGuardianAngelState {
	public static final int SHIELD_COLOR = 0xFFD76A;
	private static final Map<UUID, Long> shieldExpiresAt = new HashMap<>();
	private static boolean wasAbilityDown;

	private ClientGuardianAngelState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(GuardianAngelShieldStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientGuardianAngelState::tick);
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (player == null || client == null || client.world == null || !isLocalDeadParticipant(client)) {
			return false;
		}
		pruneExpired(client.world.getTime());
		return shieldExpiresAt.containsKey(player.getUuid());
	}

	private static void apply(MinecraftClient client, GuardianAngelShieldStatePayload payload) {
		shieldExpiresAt.clear();
		if (client == null || client.world == null || payload.shieldedTargets().isEmpty()) return;
		long now = client.world.getTime();
		for (Map.Entry<UUID, Integer> entry : payload.shieldedTargets().entrySet()) {
			if (entry.getKey() != null && entry.getValue() > 0) {
				shieldExpiresAt.put(entry.getKey(), now + entry.getValue());
			}
		}
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			shieldExpiresAt.clear();
			wasAbilityDown = false;
			return;
		}
		pruneExpired(client.world.getTime());

		if (client.currentScreen != null || !isLocalDeadParticipant(client)) {
			wasAbilityDown = false;
			return;
		}

		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(GuardianAngelShieldUsePayload.ID)) {
			ClientPlayNetworking.send(new GuardianAngelShieldUsePayload());
		}
		wasAbilityDown = abilityDown;
	}

	private static boolean isLocalDeadParticipant(MinecraftClient client) {
		if (ClientVultureState.isLocalStashed(client) || !client.player.isSpectator()) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.getRoles().containsKey(client.player.getUuid());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void pruneExpired(long now) {
		Iterator<Map.Entry<UUID, Long>> it = shieldExpiresAt.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() <= now) it.remove();
		}
	}
}
