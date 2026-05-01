package dev.mapselect.role;

import dev.mapselect.network.NeutralWinPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class NeutralWinManager {
	private NeutralWinManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(NeutralWinPayload.ID, NeutralWinPayload.CODEC);
	}

	public static void announce(ServerWorld world, ServerPlayerEntity winner, String translationKey, int color) {
		if (world == null || winner == null) return;
		NeutralWinPayload payload = new NeutralWinPayload(winner.getUuid(), translationKey, color);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, NeutralWinPayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
