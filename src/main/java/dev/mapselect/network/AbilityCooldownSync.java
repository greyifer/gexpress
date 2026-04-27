package dev.mapselect.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AbilityCooldownSync {
	private AbilityCooldownSync() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(AbilityCooldownPayload.ID, AbilityCooldownPayload.CODEC);
	}

	public static void send(ServerPlayerEntity player, String key, long remainingTicks, long totalTicks, boolean draining) {
		if (player == null || key == null || key.isEmpty()) return;
		if (!ServerPlayNetworking.canSend(player, AbilityCooldownPayload.ID)) return;
		int remaining = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remainingTicks));
		int total = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalTicks));
		ServerPlayNetworking.send(player, new AbilityCooldownPayload(key, remaining, total, draining));
	}

	public static void clear(ServerPlayerEntity player, String key) {
		send(player, key, 0L, 0L, false);
	}
}
