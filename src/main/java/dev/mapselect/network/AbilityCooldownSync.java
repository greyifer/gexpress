package dev.mapselect.network;

import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AbilityCooldownSync {
	private AbilityCooldownSync() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(AbilityCooldownPayload.ID, AbilityCooldownPayload.CODEC);
	}

	public static void send(ServerPlayerEntity player, String key, long remainingTicks, long totalTicks, boolean draining) {
		send(player, key, remainingTicks, totalTicks, draining, 0, 0);
	}

	public static void sendUses(ServerPlayerEntity player, String key, long remainingTicks, long totalTicks,
			int usesRemaining, int maxUses) {
		send(player, key, remainingTicks, totalTicks, false, usesRemaining, maxUses);
	}

	private static void send(ServerPlayerEntity player, String key, long remainingTicks, long totalTicks,
			boolean draining, int usesRemaining, int maxUses) {
		if (player == null || key == null || key.isEmpty()) return;
		if (!ServerPlayNetworking.canSend(player, AbilityCooldownPayload.ID)) return;
		if (!draining && GexpressTestState.hasCreativeAbilityBypass(player)) {
			ServerPlayNetworking.send(player, new AbilityCooldownPayload(key, 0, 0, false));
			return;
		}
		int remaining = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remainingTicks));
		int total = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalTicks));
		ServerPlayNetworking.send(player, new AbilityCooldownPayload(key, remaining, total, draining,
			usesRemaining, maxUses));
	}

	public static void clear(ServerPlayerEntity player, String key) {
		send(player, key, 0L, 0L, false);
	}
}
