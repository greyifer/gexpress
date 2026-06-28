package dev.mapselect.role.twins;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.entity.TwinBodyEntity;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.twins.TwinsSwapPayload;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.registry.MapSelectEntities;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TwinsManager {
	private static final double VOICE_RANGE = 48.0D;
	private static final long MIN_SWAP_INTERVAL_TICKS = 20L;
	private static final Identifier GOLD_LEDGE_ID = Identifier.of("wathe", "gold_ledge");
	private static final Random RANDOM = new Random();
	private static final Map<UUID, TwinRecord> bodies = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> lastSwapAt = new ConcurrentHashMap<>();

	private TwinsManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TwinsSwapPayload.ID, TwinsSwapPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TwinsSwapPayload.ID,
			(payload, context) -> context.server().execute(() -> trySwap(context.player())));
		GameEvents.ON_FINISH_INITIALIZE.register(TwinsManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearWorld(world));
		ServerTickEvents.END_WORLD_TICK.register(TwinsManager::tick);
	}

	private static void initializeRound(World world, GameWorldComponent game) {
		clearWorld(world);
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (isTwins(player) && isPlayable(player, player)) ensureBody(serverWorld, player, game);
		}
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.isRunning();
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearWorld(world);
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID playerId = player.getUuid();
			if (!isTwins(player) || !isPlayable(player, player) || PelicanManager.isStashed(player)) {
				removeBody(world, playerId, true);
				continue;
			}
			TwinBodyEntity body = ensureBody(world, player, game);
			if (body == null) continue;
			body.copyEquipmentFrom(player);
		}
		bodies.keySet().removeIf(playerId -> {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && player.getWorld() == world) return false;
			removeBody(world, playerId, true);
			return true;
		});
	}

	private static TwinBodyEntity ensureBody(ServerWorld world, ServerPlayerEntity owner, GameWorldComponent game) {
		TwinRecord record = bodies.get(owner.getUuid());
		TwinBodyEntity existing = body(world, record);
		if (existing != null && existing.isAlive()) {
			discardOwnedBodies(world, owner.getUuid(), existing);
			return existing;
		}
		if (record != null) bodies.remove(owner.getUuid());

		SpawnPoint spawn = record == null ? null : record.spawnPoint;
		if (spawn == null) spawn = new SpawnPoint(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getPitch());
		if (record == null) {
			SpawnPoint found = findSpawnPoint(world, owner);
			if (found != null) spawn = found;
		}
		TwinBodyEntity body = new TwinBodyEntity(MapSelectEntities.TWIN_BODY, world);
		String decoyRoleId = record == null ? chooseDecoyRole(owner, game) : record.decoyRoleId;
		body.initializeTwin(owner.getUuid(), owner.getGameProfile().getName(), decoyRoleId);
		body.setTwinAnchor(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
		body.copyEquipmentFrom(owner);
		discardOwnedBodies(world, owner.getUuid(), null);
		if (!world.spawnEntity(body)) return null;
		bodies.put(owner.getUuid(), new TwinRecord(body.getUuid(), decoyRoleId, spawn));
		return body;
	}

	private static void trySwap(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		if (!isTwins(player) || PelicanManager.isStashed(player) || !isPlayable(player, player)) return;
		TwinBodyEntity body = ensureBody(world, player, GameWorldComponent.KEY.getNullable(world));
		if (body == null || !body.isAlive()) {
			player.sendMessage(Text.literal("Your twin body is not ready."), true);
			return;
		}
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(player);
		long now = world.getTime();
		Long lastSwap = lastSwapAt.get(player.getUuid());
		if (lastSwap != null && now - lastSwap < MIN_SWAP_INTERVAL_TICKS) {
			return;
		}
		long remaining = creativeBypass ? 0L : cooldownRemaining(player);
		long total = (long) GexpressConfig.getTwinsSwapCooldownSeconds() * 20L;
		if (!creativeBypass && remaining > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.TWINS_SWAP, remaining, total, false);
			player.sendMessage(Text.literal("Twin swap ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}

		double playerX = player.getX();
		double playerY = player.getY();
		double playerZ = player.getZ();
		float playerYaw = player.getYaw();
		float playerPitch = player.getPitch();
		double bodyX = body.getX();
		double bodyY = body.getY();
		double bodyZ = body.getZ();
		float bodyYaw = body.getYaw();
		float bodyPitch = body.getPitch();

		player.teleport(world, bodyX, bodyY, bodyZ, bodyYaw, bodyPitch);
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		body.setTwinAnchor(playerX, playerY, playerZ, playerYaw, playerPitch);
		body.copyEquipmentFrom(player);
		lastSwapAt.put(player.getUuid(), now);

		if (creativeBypass) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.TWINS_SWAP);
		} else {
			cooldownUntil.put(player.getUuid(), world.getTime() + total);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.TWINS_SWAP, total, total, false);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 0.9F, 1.25F);
		world.playSound(null, body.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 0.65F, 0.85F);
	}

	public static Set<UUID> voiceReceiversNear(ServerWorld world, UUID speakerId, Vec3d sourcePos) {
		Set<UUID> receivers = new HashSet<>();
		if (world == null || speakerId == null || sourcePos == null) return receivers;
		double maxDistanceSquared = VOICE_RANGE * VOICE_RANGE;
		for (Map.Entry<UUID, TwinRecord> entry : bodies.entrySet()) {
			UUID ownerId = entry.getKey();
			if (ownerId.equals(speakerId)) continue;
			TwinBodyEntity body = body(world, entry.getValue());
			if (body == null || !body.isAlive() || body.squaredDistanceTo(sourcePos) > maxDistanceSquared) continue;
			ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
			if (owner == null || owner.getWorld() != world || owner.squaredDistanceTo(sourcePos) <= maxDistanceSquared) {
				continue;
			}
			receivers.add(ownerId);
		}
		return receivers;
	}

	private static SpawnPoint findSpawnPoint(ServerWorld world, ServerPlayerEntity owner) {
		List<SpawnPoint> spawns = targetPoints(world);
		if (!spawns.isEmpty()) {
			for (int attempt = 0; attempt < Math.max(24, spawns.size() * 2); attempt++) {
				SpawnPoint spawn = spawns.get(RANDOM.nextInt(spawns.size()));
				if (isSafe(world, owner, spawn.x(), spawn.y(), spawn.z())) return spawn;
			}
			return spawns.get(RANDOM.nextInt(spawns.size()));
		}
		for (int attempt = 0; attempt < 120; attempt++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double distance = 24.0D + RANDOM.nextDouble() * 96.0D;
			Vec3d seed = owner.getPos().add(Math.cos(angle) * distance,
				RANDOM.nextInt(33) - 16, Math.sin(angle) * distance);
			Vec3d safe = safeAround(world, owner, BlockPos.ofFloored(seed));
			if (safe != null && safe.squaredDistanceTo(owner.getPos()) > 36.0D) {
				return new SpawnPoint(safe.x, safe.y, safe.z, RANDOM.nextFloat() * 360.0F, 0.0F);
			}
		}
		Vec3d safe = safeAround(world, owner, owner.getBlockPos());
		return safe == null ? null : new SpawnPoint(safe.x, safe.y, safe.z, owner.getYaw(), owner.getPitch());
	}

	private static List<SpawnPoint> targetPoints(ServerWorld world) {
		List<SpawnPoint> points = new ArrayList<>();
		MapVariablesWorldComponent map = MapVariablesWorldComponent.KEY.getNullable(world);
		Box playArea = map == null ? null : map.getPlayArea();
		Vec3i playAreaOffset = map == null ? null : map.getPlayAreaOffset();
		for (MapPreset.PosData spawn : MapPreset.randomSpawnsFrom(world)) {
			SpawnPoint point = SpawnPoint.from(spawn, playArea, playAreaOffset);
			if (point != null) points.add(point);
		}
		if (!points.isEmpty()) return points;

		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap != null && !currentMap.isBlank()) {
			try {
				MapPreset preset = PresetStorage.load(world.getServer(), currentMap);
				if (preset != null) {
					preset.normalize();
					if (preset.randomSpawnPositions != null) {
						for (MapPreset.PosData spawn : preset.randomSpawnPositions) {
							SpawnPoint point = SpawnPoint.from(spawn, preset);
							if (point != null) points.add(point);
						}
					}
				}
			} catch (IOException ignored) {
			}
		}
		return points;
	}

	private static Vec3d safeAround(ServerWorld world, ServerPlayerEntity owner, BlockPos origin) {
		for (int radius = 0; radius <= 10; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					BlockPos column = origin.add(dx, 0, dz);
					Vec3d safe = safeYInColumn(world, owner, column.getX() + 0.5D, origin.getY(), column.getZ() + 0.5D);
					if (safe != null) return safe;
				}
			}
		}
		return null;
	}

	private static Vec3d safeYInColumn(ServerWorld world, ServerPlayerEntity owner, double x, int seedY, double z) {
		int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
		int minY = Math.max(world.getBottomY() + 1, Math.min(seedY, topY) - 18);
		int maxY = Math.min(world.getTopY() - 2, Math.max(seedY, topY) + 18);
		for (int offset = 0; offset <= Math.max(maxY - seedY, seedY - minY); offset++) {
			int up = seedY + offset;
			if (up >= minY && up <= maxY && isSafe(world, owner, x, up, z)) return new Vec3d(x, up, z);
			int down = seedY - offset;
			if (offset != 0 && down >= minY && down <= maxY && isSafe(world, owner, x, down, z)) {
				return new Vec3d(x, down, z);
			}
		}
		return null;
	}

	private static boolean isSafe(ServerWorld world, ServerPlayerEntity owner, double x, double y, double z) {
		Box current = owner.getBoundingBox();
		Box target = current.offset(x - owner.getX(), y - owner.getY(), z - owner.getZ());
		return world.isSpaceEmpty(owner, target.contract(1.0E-7D)) && hasValidSupport(world, target);
	}

	private static boolean hasValidSupport(ServerWorld world, Box target) {
		double supportY = target.minY - 0.08D;
		double minX = target.minX + 0.05D;
		double maxX = target.maxX - 0.05D;
		double minZ = target.minZ + 0.05D;
		double maxZ = target.maxZ - 0.05D;
		double centerX = (target.minX + target.maxX) * 0.5D;
		double centerZ = (target.minZ + target.maxZ) * 0.5D;
		double[][] samples = {
			{ centerX, centerZ },
			{ minX, minZ },
			{ minX, maxZ },
			{ maxX, minZ },
			{ maxX, maxZ }
		};
		for (double[] sample : samples) {
			BlockPos supportPos = BlockPos.ofFloored(sample[0], supportY, sample[1]);
			if (GOLD_LEDGE_ID.equals(Registries.BLOCK.getId(world.getBlockState(supportPos).getBlock()))) {
				return false;
			}
			if (!world.getBlockState(supportPos).getCollisionShape(world, supportPos).isEmpty()) return true;
		}
		return false;
	}

	private static void clearWorld(World world) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		for (TwinBodyEntity body : serverWorld.getEntitiesByType(MapSelectEntities.TWIN_BODY, entity -> true)) {
			body.discard();
		}
		for (UUID playerId : Set.copyOf(bodies.keySet())) removeBody(serverWorld, playerId, false);
		bodies.clear();
		cooldownUntil.clear();
		lastSwapAt.clear();
	}

	private static void removeBody(ServerWorld world, UUID playerId, boolean removeRecord) {
		TwinRecord record = bodies.get(playerId);
		TwinBodyEntity body = body(world, record);
		if (body != null) body.discard();
		discardOwnedBodies(world, playerId, null);
		cooldownUntil.remove(playerId);
		lastSwapAt.remove(playerId);
		if (removeRecord) bodies.remove(playerId);
	}

	private static TwinBodyEntity body(ServerWorld world, TwinRecord record) {
		if (world == null || record == null || record.entityId == null) return null;
		Entity entity = world.getEntity(record.entityId);
		return entity instanceof TwinBodyEntity twin ? twin : null;
	}

	private static void discardOwnedBodies(ServerWorld world, UUID ownerId, TwinBodyEntity keep) {
		if (world == null || ownerId == null) return;
		UUID keepId = keep == null ? null : keep.getUuid();
		for (TwinBodyEntity candidate : world.getEntitiesByType(MapSelectEntities.TWIN_BODY,
				entity -> entity.ownerId().isPresent() && ownerId.equals(entity.ownerId().get()))) {
			if (keepId != null && keepId.equals(candidate.getUuid())) continue;
			candidate.discard();
		}
	}

	private static String chooseDecoyRole(ServerPlayerEntity owner, GameWorldComponent game) {
		Role ownRole = game == null ? null : game.getRole(owner);
		if (!GexpressConfig.canTwinsBodyUseDifferentRole()) {
			return ownRole == null || ownRole.identifier() == null ? "" : ownRole.identifier().toString();
		}
		List<Role> choices = WatheRoles.ROLES.stream()
			.filter(role -> role != null && role.identifier() != null)
			.filter(role -> !MapSelectRoles.TWINS_ID.equals(role.identifier()))
			.filter(role -> ownRole == null || !role.identifier().equals(ownRole.identifier()))
			.toList();
		if (choices.isEmpty()) return "";
		return choices.get(RANDOM.nextInt(choices.size())).identifier().toString();
	}

	private static boolean isTwins(ServerPlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.TWINS_ID.equals(role.identifier());
	}

	private static boolean isPlayable(ServerPlayerEntity player, ServerPlayerEntity user) {
		if (GexpressTestState.isRoleTester(user)) return true;
		return DeadPlayerStatus.isLivingRoundParticipant(player) || GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static long cooldownRemaining(ServerPlayerEntity player) {
		Long until = cooldownUntil.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldownUntil.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private static final class TwinRecord {
		private final UUID entityId;
		private final String decoyRoleId;
		private final SpawnPoint spawnPoint;

		private TwinRecord(UUID entityId, String decoyRoleId, SpawnPoint spawnPoint) {
			this.entityId = entityId;
			this.decoyRoleId = decoyRoleId == null ? "" : decoyRoleId;
			this.spawnPoint = spawnPoint;
		}
	}

	private record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
		private static SpawnPoint from(MapPreset.PosData pos) {
			if (pos == null) return null;
			return new SpawnPoint(pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
		}

		private static SpawnPoint from(MapPreset.PosData pos, MapPreset preset) {
			if (pos == null || preset == null || preset.playArea == null || preset.playAreaOffset == null) {
				return from(pos);
			}
			return from(pos, preset.playArea.toBox(), preset.playAreaOffset.toVec3i());
		}

		private static SpawnPoint from(MapPreset.PosData pos, Box playArea, Vec3i playAreaOffset) {
			if (pos == null || playArea == null || playAreaOffset == null) return from(pos);
			Vec3d original = new Vec3d(pos.x, pos.y, pos.z);
			Vec3d translated = original.add(playAreaOffset.getX(), playAreaOffset.getY(), playAreaOffset.getZ());
			if (!playArea.contains(original) && playArea.contains(translated)) {
				return new SpawnPoint(translated.x, translated.y, translated.z, pos.yaw, pos.pitch);
			}
			return from(pos);
		}
	}
}
