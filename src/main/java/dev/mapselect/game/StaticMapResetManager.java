package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.MapSelect;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StaticMapResetManager {
	private static final int RESTORE_BLOCKS_PER_TICK = 1024;
	private static final int MAX_TRACKED_BLOCKS = 250_000;
	private static final int RESTORE_FLAGS = Block.NOTIFY_ALL | Block.FORCE_STATE | Block.SKIP_DROPS;
	private static final Map<RegistryKey<World>, ActiveMap> ACTIVE = new ConcurrentHashMap<>();

	private StaticMapResetManager() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> startRound(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> finishRound(world));
		ServerTickEvents.END_WORLD_TICK.register(StaticMapResetManager::tick);
	}

	public static boolean isStaticMap(ServerWorld world) {
		return world != null && ACTIVE.containsKey(world.getRegistryKey());
	}

	public static void captureOriginal(ServerWorld world, BlockPos pos, BlockState nextState) {
		if (world == null || pos == null) return;
		ActiveMap active = ACTIVE.get(world.getRegistryKey());
		if (active == null || active.restoring || active.saturated || !active.contains(pos)) return;

		BlockState current = world.getBlockState(pos);
		if (current.equals(nextState) && world.getBlockEntity(pos) == null) return;
		BlockPos immutable = pos.toImmutable();
		if (active.originals.containsKey(immutable)) return;
		if (active.originals.size() >= MAX_TRACKED_BLOCKS) {
			active.saturated = true;
			MapSelect.LOGGER.warn("Static map '{}' reached the {} block change tracking limit; further changes will not be tracked.",
				active.mapName, MAX_TRACKED_BLOCKS);
			return;
		}
		active.originals.put(immutable, BlockSnapshot.capture(world, immutable, current, world.getRegistryManager()));
	}

	private static void startRound(World eventWorld) {
		if (!(eventWorld instanceof ServerWorld world)) return;
		MapPreset preset = currentPreset(world);
		if (preset == null || !preset.isStaticMapEnabled() || preset.wholeMapArea == null) {
			ACTIVE.remove(world.getRegistryKey());
			return;
		}
		preset.normalize();
		if (preset.wholeMapArea == null) {
			ACTIVE.remove(world.getRegistryKey());
			return;
		}
		String mapName = currentMapName(world);
		ACTIVE.put(world.getRegistryKey(), new ActiveMap(mapName == null ? "unknown" : mapName, preset.wholeMapArea.toBox()));
	}

	private static void finishRound(World eventWorld) {
		if (!(eventWorld instanceof ServerWorld world)) return;
		ActiveMap active = ACTIVE.get(world.getRegistryKey());
		if (active == null || active.restoring) return;
		cleanupTransientEntities(world, active.area);
		active.restoreQueue = new ArrayDeque<>(active.originals.entrySet());
		active.restoring = true;
		if (active.restoreQueue.isEmpty()) {
			ACTIVE.remove(world.getRegistryKey());
		}
	}

	private static void tick(ServerWorld world) {
		ActiveMap active = ACTIVE.get(world.getRegistryKey());
		if (active == null || !active.restoring || active.restoreQueue == null) return;
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();
		int restored = 0;
		while (restored < RESTORE_BLOCKS_PER_TICK && !active.restoreQueue.isEmpty()) {
			Map.Entry<BlockPos, BlockSnapshot> entry = active.restoreQueue.removeFirst();
			entry.getValue().restore(world, entry.getKey(), lookup);
			restored++;
		}
		if (active.restoreQueue.isEmpty()) {
			ACTIVE.remove(world.getRegistryKey());
			MapSelect.LOGGER.info("Static map '{}' restored {} changed block(s).", active.mapName, active.originals.size());
		}
	}

	private static void cleanupTransientEntities(ServerWorld world, Box area) {
		for (Entity entity : world.getOtherEntities(null, area, StaticMapResetManager::isTransientEntity)) {
			entity.discard();
		}
	}

	private static boolean isTransientEntity(Entity entity) {
		if (entity == null || entity.isPlayer()) return false;
		return entity instanceof ItemEntity
			|| entity instanceof ExperienceOrbEntity
			|| entity instanceof ProjectileEntity
			|| entity instanceof TntEntity
			|| entity instanceof FallingBlockEntity
			|| entity instanceof AreaEffectCloudEntity
			|| entity.getType() == WatheEntities.PLAYER_BODY
			|| entity.getType() == EntityType.FIREWORK_ROCKET;
	}

	private static MapPreset currentPreset(ServerWorld world) {
		String mapName = currentMapName(world);
		if (mapName == null || mapName.isBlank()) return null;
		try {
			return PresetStorage.load(world.getServer(), mapName);
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to load static map preset '{}'.", mapName, e);
			return null;
		}
	}

	private static String currentMapName(ServerWorld world) {
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		return weather == null ? null : weather.getCurrentMapName();
	}

	private static final class ActiveMap {
		private final String mapName;
		private final Box area;
		private final LinkedHashMap<BlockPos, BlockSnapshot> originals = new LinkedHashMap<>();
		private ArrayDeque<Map.Entry<BlockPos, BlockSnapshot>> restoreQueue;
		private boolean restoring;
		private boolean saturated;

		private ActiveMap(String mapName, Box area) {
			this.mapName = mapName;
			this.area = area;
		}

		private boolean contains(BlockPos pos) {
			return area.contains(Vec3d.ofCenter(pos));
		}
	}

	private record BlockSnapshot(BlockState state, NbtCompound blockEntityNbt) {
		private static BlockSnapshot capture(ServerWorld world, BlockPos pos, BlockState state,
				RegistryWrapper.WrapperLookup lookup) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			NbtCompound tag = blockEntity == null ? null : blockEntity.createNbtWithId(lookup).copy();
			return new BlockSnapshot(state, tag);
		}

		private void restore(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup lookup) {
			BlockState current = world.getBlockState(pos);
			if (!current.equals(state)) {
				world.setBlockState(pos, state, RESTORE_FLAGS);
			}
			if (blockEntityNbt == null) return;
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) return;
			blockEntity.read(blockEntityNbt.copy(), lookup);
			blockEntity.markDirty();
			world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
		}
	}
}
