package dev.mapselect.network;

import dev.doctor4t.wathe.api.event.GameEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PayloadSync {
	private static final Map<UUID, Map<CustomPayload.Id<?>, CustomPayload>> LAST_SENT = new ConcurrentHashMap<>();

	private PayloadSync() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clear(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearAll());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll());
	}

	public static <T extends CustomPayload> boolean sendIfChanged(ServerPlayerEntity player,
			CustomPayload.Id<T> id, T payload) {
		if (player == null || id == null || payload == null) return false;
		if (!ServerPlayNetworking.canSend(player, id)) return false;
		Map<CustomPayload.Id<?>, CustomPayload> playerPayloads = LAST_SENT.computeIfAbsent(player.getUuid(),
			ignored -> new ConcurrentHashMap<>());
		CustomPayload previous = playerPayloads.get(id);
		if (payload.equals(previous)) return false;
		playerPayloads.put(id, payload);
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	public static void clear(ServerPlayerEntity player) {
		if (player != null) LAST_SENT.remove(player.getUuid());
	}

	public static void clearAll() {
		LAST_SENT.clear();
	}

	public static void forget(ServerPlayerEntity player, CustomPayload.Id<?> id) {
		if (player == null || id == null) return;
		Map<CustomPayload.Id<?>, CustomPayload> playerPayloads = LAST_SENT.get(player.getUuid());
		if (playerPayloads != null) playerPayloads.remove(id);
	}

	public static void forgetAll(CustomPayload.Id<?> id) {
		if (id == null) return;
		for (Map<CustomPayload.Id<?>, CustomPayload> playerPayloads : LAST_SENT.values()) {
			playerPayloads.remove(id);
		}
	}
}
