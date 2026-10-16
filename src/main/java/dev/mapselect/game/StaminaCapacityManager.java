package dev.mapselect.game;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaminaCapacityManager {
	private static final int KILLER_STAMINA_TICKS = 20 * 60;
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final Map<RegistryKey<World>, Map<UUID, Capacity>> CAPACITIES = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, Integer> TICKS_UNTIL_NEXT_CHECK = new ConcurrentHashMap<>();

	private StaminaCapacityManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(StaminaCapacityManager::tick);
		RoundLifecycleHooks.clearWorld(StaminaCapacityManager::clearWorld);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			clearWorld(world);
			return;
		}
		RegistryKey<World> worldKey = world.getRegistryKey();
		int ticksUntilNextCheck = TICKS_UNTIL_NEXT_CHECK.getOrDefault(worldKey, 0);
		if (ticksUntilNextCheck > 0) {
			TICKS_UNTIL_NEXT_CHECK.put(worldKey, ticksUntilNextCheck - 1);
			return;
		}
		TICKS_UNTIL_NEXT_CHECK.put(worldKey, CHECK_INTERVAL_TICKS - 1);
		Map<UUID, Capacity> capacities = CAPACITIES.computeIfAbsent(worldKey, ignored -> new HashMap<>());
		Set<UUID> present = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			present.add(player.getUuid());
			Role role = game.getRole(player);
			if (role == null || role.identifier() == null) {
				capacities.remove(player.getUuid());
				continue;
			}
			boolean killerStamina = role.canUseKiller() || game.canUseKillerFeatures(player);
			if (!killerStamina && role.getMaxSprintTime() <= 0) {
				capacities.remove(player.getUuid());
				continue;
			}
			Capacity next = new Capacity(role.identifier(),
				killerStamina ? KILLER_STAMINA_TICKS : role.getMaxSprintTime(), killerStamina);
			Capacity previous = capacities.put(player.getUuid(), next);
			if (!next.equals(previous)) {
				WatheStaminaAccess.set(player, next.maximum());
			}
		}
		capacities.keySet().removeIf(id -> !present.contains(id));
	}

	private static void clear() {
		CAPACITIES.clear();
		TICKS_UNTIL_NEXT_CHECK.clear();
	}

	private static void clearWorld(World world) {
		if (world == null) {
			clear();
			return;
		}
		CAPACITIES.remove(world.getRegistryKey());
		TICKS_UNTIL_NEXT_CHECK.remove(world.getRegistryKey());
	}

	private record Capacity(Identifier role, int maximum, boolean killer) {}
}
