package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.RoundEndRoleRosterPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoundEndRoleRosterSync {
	private RoundEndRoleRosterSync() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(RoundEndRoleRosterPayload.ID, RoundEndRoleRosterPayload.CODEC);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear(world));
	}

	public static void send(World world, List<ServerPlayerEntity> players) {
		if (!(world instanceof ServerWorld serverWorld) || players == null || players.isEmpty()) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null) return;

		Map<UUID, String> roleIds = new LinkedHashMap<>();
		for (ServerPlayerEntity player : players) {
			if (player == null) continue;
			Role role = game.getRole(player);
			Identifier id = role == null ? null : role.identifier();
			roleIds.put(player.getUuid(), id == null ? "" : id.toString());
		}
		send(serverWorld, new RoundEndRoleRosterPayload(roleIds));
	}

	private static void clear(World world) {
		if (world instanceof ServerWorld serverWorld) send(serverWorld, RoundEndRoleRosterPayload.clear());
	}

	private static void send(ServerWorld world, RoundEndRoleRosterPayload payload) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, RoundEndRoleRosterPayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
