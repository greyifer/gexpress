package dev.mapselect.game;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.RoleTeams;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EndgameRulesManager {
	private static final int LAST_MINUTE_TICKS = 60 * 20;
	private static final int DOOR_SCAN_CHUNK_RADIUS = 10;
	private static final int DOOR_SCAN_INTERVAL_TICKS = 20;
	private static final Map<RegistryKey<World>, Set<UUID>> lastStandPlayersByWorld = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, Integer> doorScanCountdowns = new ConcurrentHashMap<>();

	private EndgameRulesManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(EndgameRulesManager::tick);
		RoundLifecycleHooks.clearWorld(EndgameRulesManager::clearWorld);
	}

	public static boolean disablesKillerInstinct(PlayerEntity player) {
		return player != null && GexpressConfig.isLastStandEnabled()
			&& lastStandPlayersByWorld
				.getOrDefault(player.getWorld().getRegistryKey(), Set.of())
				.contains(player.getUuid());
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			clearLastStandPlayers(world);
			return;
		}

		GameTimeComponent time = GameTimeComponent.KEY.get(world);
		if (time.getTime() > 0 && time.getTime() <= LAST_MINUTE_TICKS) {
			RegistryKey<World> worldKey = world.getRegistryKey();
			int remaining = doorScanCountdowns.getOrDefault(worldKey, 0);
			if (remaining <= 0) {
				openKeyedDoors(world);
				doorScanCountdowns.put(worldKey, DOOR_SCAN_INTERVAL_TICKS - 1);
			} else {
				doorScanCountdowns.put(worldKey, remaining - 1);
			}
		} else {
			doorScanCountdowns.remove(world.getRegistryKey());
		}
		tickLastStand(world, game);
	}

	private static void openKeyedDoors(ServerWorld world) {
		Set<Long> visitedChunks = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			ChunkPos center = player.getChunkPos();
			for (int dz = -DOOR_SCAN_CHUNK_RADIUS; dz <= DOOR_SCAN_CHUNK_RADIUS; dz++) {
				for (int dx = -DOOR_SCAN_CHUNK_RADIUS; dx <= DOOR_SCAN_CHUNK_RADIUS; dx++) {
					int chunkX = center.x + dx;
					int chunkZ = center.z + dz;
					long packed = ChunkPos.toLong(chunkX, chunkZ);
					if (!visitedChunks.add(packed)) continue;
					var chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
					if (!(chunk instanceof WorldChunk worldChunk)) continue;
					for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
						if (blockEntity instanceof DoorBlockEntity door && hasAssignedKey(door)) {
							keepDoorOpen(door);
						}
					}
				}
			}
		}
	}

	private static boolean hasAssignedKey(DoorBlockEntity door) {
		String keyName = door.getKeyName();
		return keyName != null && !keyName.isBlank();
	}

	private static void keepDoorOpen(DoorBlockEntity door) {
		if (!door.isOpen()) {
			door.toggle(false);
		}
		door.setCloseCountdown(DOOR_SCAN_INTERVAL_TICKS + 5);
	}

	private static void tickLastStand(ServerWorld world, GameWorldComponent game) {
		if (!GexpressConfig.isLastStandEnabled()) {
			clearLastStandPlayers(world);
			return;
		}
		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		if (alive.size() != 2 || RoleTeams.sameTeam(game, alive.get(0), alive.get(1))) {
			clearLastStandPlayers(world);
			return;
		}

		Set<UUID> lastStandPlayers = lastStandPlayersByWorld.computeIfAbsent(
			world.getRegistryKey(), ignored -> new HashSet<>());
		lastStandPlayers.clear();
		for (ServerPlayerEntity player : alive) {
			lastStandPlayers.add(player.getUuid());
			ensureRevolver(player);
		}
	}

	private static void clearLastStandPlayers(ServerWorld world) {
		if (world == null) {
			lastStandPlayersByWorld.clear();
			doorScanCountdowns.clear();
			return;
		}
		lastStandPlayersByWorld.remove(world.getRegistryKey());
	}

	private static void clearWorld(World world) {
		if (world == null) return;
		lastStandPlayersByWorld.remove(world.getRegistryKey());
		doorScanCountdowns.remove(world.getRegistryKey());
	}

	private static void ensureRevolver(ServerPlayerEntity player) {
		if (dev.mapselect.role.RoleItemInventory.count(player, WatheItems.REVOLVER) > 0) return;
		ItemStack stack = WatheItems.REVOLVER.getDefaultStack();
		if (!player.giveItemStack(stack)) player.dropItem(stack, false);
		player.playerScreenHandler.syncState();
	}
}
