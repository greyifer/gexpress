package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.GameEvents;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EndgameRulesManager {
	private static final int LAST_MINUTE_TICKS = 60 * 20;
	private static final int DOOR_SCAN_CHUNK_RADIUS = 10;
	private static final Set<UUID> lastStandPlayers = new HashSet<>();

	private EndgameRulesManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(EndgameRulesManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> lastStandPlayers.clear());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> lastStandPlayers.clear());
	}

	public static boolean disablesKillerInstinct(PlayerEntity player) {
		return player != null && GexpressConfig.isLastStandEnabled()
			&& lastStandPlayers.contains(player.getUuid());
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			lastStandPlayers.clear();
			return;
		}

		GameTimeComponent time = GameTimeComponent.KEY.get(world);
		if (time.getTime() > 0 && time.getTime() <= LAST_MINUTE_TICKS) {
			openKeyedDoors(world);
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
		door.setCloseCountdown(2);
	}

	private static void tickLastStand(ServerWorld world, GameWorldComponent game) {
		if (!GexpressConfig.isLastStandEnabled()) {
			lastStandPlayers.clear();
			return;
		}
		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		if (alive.size() != 2 || RoleTeams.sameTeam(game, alive.get(0), alive.get(1))) {
			lastStandPlayers.clear();
			return;
		}

		lastStandPlayers.clear();
		for (ServerPlayerEntity player : alive) {
			lastStandPlayers.add(player.getUuid());
			ensureRevolver(player);
		}
	}

	private static void ensureRevolver(ServerPlayerEntity player) {
		if (player.getInventory().count(WatheItems.REVOLVER) > 0) return;
		ItemStack stack = WatheItems.REVOLVER.getDefaultStack();
		if (!player.giveItemStack(stack)) player.dropItem(stack, false);
		player.playerScreenHandler.syncState();
	}
}
