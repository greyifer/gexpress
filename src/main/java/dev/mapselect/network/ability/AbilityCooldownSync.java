package dev.mapselect.network.ability;

import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityCooldownSync {
	private static final Map<UUID, Map<String, State>> LAST_SENT = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<String, Long>> REPORTED_AT = new ConcurrentHashMap<>();

	private AbilityCooldownSync() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(AbilityCooldownPayload.ID, AbilityCooldownPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> forget(handler.player.getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUuid()));
		dev.mapselect.game.RoundLifecycleHooks.clear(() -> { LAST_SENT.clear(); REPORTED_AT.clear(); });
	}

	private static void forget(UUID id) { LAST_SENT.remove(id); REPORTED_AT.remove(id); }

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
		if (!draining && maxUses <= 0 && GexpressTestState.hasCreativeAbilityBypass(player)) {
			sendIfChanged(player, key, new State(0, 0, false, 0, 0));
			return;
		}
		int remaining = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, remainingTicks));
		int total = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, totalTicks));
		sendIfChanged(player, key, new State(remaining, total, draining,
			Math.max(0, usesRemaining), Math.max(0, maxUses)));
	}

	public static void clear(ServerPlayerEntity player, String key) {
		send(player, key, 0L, 0L, false);
	}

	private static void sendIfChanged(ServerPlayerEntity player, String key, State state) {
		Map<String, State> playerStates = LAST_SENT.computeIfAbsent(player.getUuid(), id -> new ConcurrentHashMap<>());
		State previous = playerStates.put(key, state);
		REPORTED_AT.computeIfAbsent(player.getUuid(), id -> new ConcurrentHashMap<>()).put(key, player.getWorld().getTime());
		if (state.equals(previous)) return;
		if (!ServerPlayNetworking.canSend(player, AbilityCooldownPayload.ID)) return;
		ServerPlayNetworking.send(player, new AbilityCooldownPayload(key, state.remainingTicks(),
			state.totalTicks(), state.draining(), state.usesRemaining(), state.maxUses()));
	}

	private record State(int remainingTicks, int totalTicks, boolean draining, int usesRemaining, int maxUses) {}

	public static String describe(ServerPlayerEntity player) {
		Map<String, State> states = LAST_SENT.getOrDefault(player.getUuid(), Map.of());
		Map<String, Long> times = REPORTED_AT.getOrDefault(player.getUuid(), Map.of());
		return states.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
			long elapsed = Math.max(0, player.getWorld().getTime() - times.getOrDefault(entry.getKey(), player.getWorld().getTime()));
			return entry.getKey() + "=" + Math.max(0, entry.getValue().remainingTicks() - elapsed) + " ticks";
		}).collect(java.util.stream.Collectors.joining(", "));
	}
}
