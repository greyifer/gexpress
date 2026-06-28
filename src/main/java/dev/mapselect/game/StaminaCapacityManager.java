package dev.mapselect.game;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StaminaCapacityManager {
	private static final Map<UUID, Capacity> CAPACITIES = new HashMap<>();

	private StaminaCapacityManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(StaminaCapacityManager::tick);
	}

	private static void tick(ServerWorld world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			for (ServerPlayerEntity player : world.getPlayers()) CAPACITIES.remove(player.getUuid());
			return;
		}
		Set<UUID> present = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			present.add(player.getUuid());
			Role role = game.getRole(player);
			if (role == null || role.identifier() == null || role.getMaxSprintTime() <= 0) {
				CAPACITIES.remove(player.getUuid());
				continue;
			}
			Capacity next = new Capacity(role.identifier(), role.getMaxSprintTime());
			Capacity previous = CAPACITIES.put(player.getUuid(), next);
			if (!next.equals(previous)) WatheStaminaAccess.set(player, next.maximum());
		}
		CAPACITIES.keySet().removeIf(id -> !present.contains(id));
	}

	private record Capacity(Identifier role, int maximum) {}
}
