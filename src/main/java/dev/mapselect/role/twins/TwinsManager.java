package dev.mapselect.role.twins;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.twins.TwinsSwapPayload;
import dev.mapselect.network.role.twins.TwinsVoiceRelayPayload;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.registry.MapSelectEntities;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
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
	private static final double VOICE_RANGE = 16.0D;
	private static final long MIN_SWAP_INTERVAL_TICKS = 20L;
	private static final long BODY_MISSING_GRACE_TICKS = 40L;
	private static final Identifier GOLD_LEDGE_ID = Identifier.of("wathe", "gold_ledge");
	private static final Random RANDOM = new Random();
	private static final Map<UUID, TwinRecord> bodies = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> lastSwapAt = new ConcurrentHashMap<>();
	private static final Set<UUID> eliminatedBodies = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> voiceRelayEnabled = ConcurrentHashMap.newKeySet();

	private TwinsManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(dev.mapselect.network.role.twins.TwinsBodyPayload.ID, dev.mapselect.network.role.twins.TwinsBodyPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			for (ServerWorld world : server.getWorlds()) for (TwinFakePlayerEntity body : world.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(TwinFakePlayerEntity.class), entity -> true)) sendBody(handler.player, body, true);
		}));
		PayloadTypeRegistry.playC2S().register(TwinsSwapPayload.ID, TwinsSwapPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TwinsVoiceRelayPayload.ID, TwinsVoiceRelayPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TwinsSwapPayload.ID,
			(payload, context) -> context.server().execute(() -> trySwap(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(TwinsVoiceRelayPayload.ID,
			(payload, context) -> context.server().execute(() -> toggleVoiceRelay(context.player())));
		GameEvents.ON_FINISH_INITIALIZE.register(TwinsManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearWorld(world));
		ServerTickEvents.END_WORLD_TICK.register(TwinsManager::tick);
	}

	public static TwinFakePlayerEntity bodyFor(ServerPlayerEntity owner) {
		if (owner == null || !(owner.getWorld() instanceof ServerWorld world)) return null;
		TwinFakePlayerEntity tracked = body(world, bodies.get(owner.getUuid()));
		return tracked != null && tracked.isAlive() ? tracked : ownedBody(world, owner.getUuid());
	}

	public static ServerPlayerEntity ownerFor(TwinFakePlayerEntity body) {
		if (body == null || !(body.getWorld() instanceof ServerWorld world)) return null;
		UUID ownerId = body.ownerId().orElse(null);
		if (ownerId == null) return null;
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
		return owner != null && owner.getWorld() == world && (isTwins(owner) || dev.mapselect.role.loki.LokiManager.isLokiBody(body)) ? owner : null;
	}

	public static void killBody(TwinFakePlayerEntity body, PlayerEntity killer) {
		if (body == null || !body.isAlive() || body.getWorld().isClient) return;
		DamageSource source = killer == null
			? body.getDamageSources().generic()
			: body.getDamageSources().playerAttack(killer);
		boolean damaged = body.damage(source, Float.MAX_VALUE);
		if (!damaged && body.isAlive() && !body.isRemoved()) {
			body.setHealth(0.0F);
			body.onDeath(source);
		}
	}

	public static void onOwnerDeath(ServerPlayerEntity owner) {
		if (owner == null) return;
		TwinFakePlayerEntity body = body(owner.getServerWorld(), bodies.get(owner.getUuid()));
		if (body != null && body.isAlive()) killBody(body, null);
		removeBody(owner.getServerWorld(), owner.getUuid(), true);
		eliminatedBodies.add(owner.getUuid());
		voiceRelayEnabled.remove(owner.getUuid());
	}

	public static void onBodyDeath(TwinFakePlayerEntity body, DamageSource source) {
        if (dev.mapselect.role.loki.LokiManager.bodyDeath(body, source)) return;
		if (body == null || !(body.getWorld() instanceof ServerWorld world)) return;
		UUID ownerId = body.ownerId().orElse(null);
		if (ownerId == null || !eliminatedBodies.add(ownerId)) return;
		voiceRelayEnabled.remove(ownerId);
		if (spawnPlayerBody(world, body, ownerId)) {
			body.discard();
		}
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
		if (owner != null) {
			owner.sendMessage(Text.literal("Your other body was killed."), true);
		}
	}

	private static boolean spawnPlayerBody(ServerWorld world, TwinFakePlayerEntity twinBody, UUID ownerId) {
		PlayerBodyEntity playerBody = WatheEntities.PLAYER_BODY.create(world);
		if (playerBody == null) return false;

		Vec3d facing = twinBody.getRotationVec(1.0F);
		Vec3d position = facing.lengthSquared() > 1.0E-6D
			? twinBody.getPos().add(facing.normalize())
			: twinBody.getPos();
		playerBody.setPlayerUuid(ownerId);
		playerBody.refreshPositionAndAngles(position.x, twinBody.getY(), position.z,
			twinBody.getYaw(), 0.0F);
		playerBody.setHeadYaw(twinBody.getHeadYaw());
		playerBody.setBodyYaw(twinBody.getBodyYaw());
		playerBody.setVelocity(Vec3d.ZERO);
		return world.spawnEntity(playerBody);
	}

	private static void initializeRound(World world, GameWorldComponent game) {
		clearWorld(world);
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (isTwins(player) && isPlayable(player, player)) ensureBody(serverWorld, player, game);
		}
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
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
				eliminatedBodies.remove(playerId);
				continue;
			}
			TwinFakePlayerEntity body = ensureBody(world, player, game);
			if (body == null) continue;
			TwinRecord record = bodies.get(playerId);
			if (record != null && record.entityId.equals(body.getUuid())) {
				record.spawnPoint = new SpawnPoint(body.getX(), body.getY(), body.getZ(),
					body.getYaw(), body.getPitch(), true);
			}
		}
		for (Map.Entry<UUID, TwinRecord> entry : List.copyOf(bodies.entrySet())) {
			if (!entry.getValue().belongsTo(world)) continue;
			UUID playerId = entry.getKey();
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && player.getWorld() == world) continue;
			removeBody(world, playerId, true);
		}
	}

	private static TwinFakePlayerEntity ensureBody(ServerWorld world, ServerPlayerEntity owner, GameWorldComponent game) {
		if (eliminatedBodies.contains(owner.getUuid())) return null;
		TwinRecord record = bodies.get(owner.getUuid());
		if (record != null && !record.belongsTo(world)) {
			removeBody(world, owner.getUuid(), true);
			record = null;
		}
		TwinFakePlayerEntity existing = body(world, record);
		if (existing == null) {
			existing = ownedBody(world, owner.getUuid());
			if (existing != null && existing.isAlive()
					&& (record == null || !existing.getUuid().equals(record.entityId))) {
				SpawnPoint adoptedSpawn = record == null
					? new SpawnPoint(existing.getX(), existing.getY(), existing.getZ(),
						existing.getYaw(), existing.getPitch(), true)
					: record.spawnPoint;
				record = new TwinRecord(world.getRegistryKey(), existing.getUuid(),
					existing.decoyRoleId(), adoptedSpawn);
				bodies.put(owner.getUuid(), record);
			}
		}
		if (existing != null && existing.isAlive()) {
			if (record != null) record.missingSinceTick = -1L;
			discardOwnedBodies(world, owner.getUuid(), existing);
			return existing;
		}
		if (existing != null) existing.discard();
		if (record != null) {
			long now = world.getTime();
			if (record.missingSinceTick < 0L) {
				record.missingSinceTick = now;
				return null;
			}
			if (now - record.missingSinceTick < BODY_MISSING_GRACE_TICKS) return null;
			bodies.remove(owner.getUuid(), record);
		}

		SpawnPoint spawn = record == null ? null : record.spawnPoint;
		if (spawn == null) {
			spawn = new SpawnPoint(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getPitch(), true);
		}
		if (record == null) {
			SpawnPoint found = findSpawnPoint(world, owner);
			if (found != null) spawn = found;
		}
		TwinFakePlayerEntity body = new TwinFakePlayerEntity(world, owner);
		String decoyRoleId = record == null ? chooseDecoyRole(owner, game) : record.decoyRoleId;
		body.initializeTwin(owner.getUuid(), owner.getGameProfile().getName(), decoyRoleId);
		body.setTwinAnchor(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
		discardOwnedBodies(world, owner.getUuid(), null);
		for (ServerPlayerEntity viewer : world.getServer().getPlayerManager().getPlayerList()) sendBody(viewer, body, true);
		if (!world.spawnEntity(body)) return null;
		bodies.put(owner.getUuid(), new TwinRecord(world.getRegistryKey(), body.getUuid(), decoyRoleId, spawn));
		return body;
	}

	public static void trySwap(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		if (!isTwins(player) || PelicanManager.isStashed(player) || !isPlayable(player, player)) return;
		TwinFakePlayerEntity body = ensureBody(world, player, GameWorldComponent.KEY.getNullable(world));
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
			player.sendMessage(Text.literal("Twin swap ready in " + Math.max(1L, AbilityCooldowns.secondsCeil(remaining)) + "s."), true);
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

		BodyState inactiveState = BodyState.capture(body);
		BodyState activeState = BodyState.capture(player);
		player.stopUsingItem();
		player.closeHandledScreen();
		body.storeInventoryFrom(player);

		player.teleport(world, bodyX, bodyY, bodyZ, bodyYaw, bodyPitch);
		body.setTwinAnchor(playerX, playerY, playerZ, playerYaw, playerPitch);
		activeState.applyLivingState(body);
		inactiveState.restorePlayer(player);
		bodies.put(player.getUuid(), new TwinRecord(world.getRegistryKey(), body.getUuid(), body.decoyRoleId(),
			new SpawnPoint(playerX, playerY, playerZ, playerYaw, playerPitch, true)));
		lastSwapAt.put(player.getUuid(), now);

		if (creativeBypass) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.TWINS_SWAP);
		} else {
			AbilityCooldowns.start(cooldownUntil, player, total);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.TWINS_SWAP, total, total, false);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 0.9F, 1.25F);
		world.playSound(null, body.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 0.65F, 0.85F);
	}

	private static void toggleVoiceRelay(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)
				|| !isTwins(player) || PelicanManager.isStashed(player) || !isPlayable(player, player)) {
			return;
		}
		TwinFakePlayerEntity body = bodyFor(player);
		if (body == null || !body.isAlive()) {
			voiceRelayEnabled.remove(player.getUuid());
			player.sendMessage(Text.literal("Your twin body is not ready."), true);
			return;
		}
		boolean enabled;
		if (voiceRelayEnabled.remove(player.getUuid())) {
			enabled = false;
		} else {
			voiceRelayEnabled.add(player.getUuid());
			enabled = true;
		}
		player.sendMessage(Text.literal(enabled
			? "Remote twin voice enabled."
			: "Remote twin voice disabled."), true);
		world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(),
			SoundCategory.PLAYERS, 0.45F, enabled ? 1.2F : 0.8F);
	}

	public static Set<UUID> voiceReceiversNear(ServerWorld world, UUID speakerId, Vec3d sourcePos) {
		Set<UUID> receivers = new HashSet<>();
		if (world == null || speakerId == null || sourcePos == null) return receivers;
		double maxDistanceSquared = VOICE_RANGE * VOICE_RANGE;
		for (Map.Entry<UUID, TwinRecord> entry : bodies.entrySet()) {
			UUID ownerId = entry.getKey();
			if (ownerId.equals(speakerId) || !voiceRelayEnabled.contains(ownerId)) continue;
			TwinFakePlayerEntity body = body(world, entry.getValue());
			if (body == null || !body.isAlive() || body.squaredDistanceTo(sourcePos) > maxDistanceSquared) continue;
			ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
			if (owner == null || owner.getWorld() != world || !GameFunctions.isPlayerAliveAndSurvival(owner) || PelicanManager.isStashed(owner) || owner.squaredDistanceTo(sourcePos) <= maxDistanceSquared) {
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
				SpawnPoint safe = safeSpawnNear(world, owner, spawn);
				if (safe != null) return safe;
			}
		}
		for (int attempt = 0; attempt < 120; attempt++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double distance = 24.0D + RANDOM.nextDouble() * 96.0D;
			Vec3d seed = owner.getPos().add(Math.cos(angle) * distance,
				RANDOM.nextInt(33) - 16, Math.sin(angle) * distance);
			Vec3d safe = safeAround(world, owner, BlockPos.ofFloored(seed));
			if (safe != null && safe.squaredDistanceTo(owner.getPos()) > 36.0D) {
				return new SpawnPoint(safe.x, safe.y, safe.z, RANDOM.nextFloat() * 360.0F, 0.0F, true);
			}
		}
		Vec3d safe = safeAround(world, owner, owner.getBlockPos());
		return safe == null ? null : new SpawnPoint(safe.x, safe.y, safe.z, owner.getYaw(), owner.getPitch(), true);
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

	private static SpawnPoint safeSpawnNear(ServerWorld world, ServerPlayerEntity owner, SpawnPoint spawn) {
		if (spawn == null) return null;
		if (isSafe(world, owner, spawn.x(), spawn.y(), spawn.z())) return spawn;
		Vec3d safe = safeAround(world, owner, BlockPos.ofFloored(spawn.x(), spawn.y(), spawn.z()), spawn.explicitY());
		return safe == null ? null : new SpawnPoint(safe.x, safe.y, safe.z, spawn.yaw(), spawn.pitch(), true);
	}

	private static Vec3d safeAround(ServerWorld world, ServerPlayerEntity owner, BlockPos origin, boolean explicitY) {
		for (int radius = 0; radius <= 10; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					BlockPos column = origin.add(dx, 0, dz);
					Vec3d safe = safeYInColumn(world, owner, column.getX() + 0.5D, origin.getY(),
						column.getZ() + 0.5D, explicitY);
					if (safe != null) return safe;
				}
			}
		}
		return null;
	}

	private static Vec3d safeYInColumn(ServerWorld world, ServerPlayerEntity owner, double x, int seedY, double z) {
		return safeYInColumn(world, owner, x, seedY, z, false);
	}

	private static Vec3d safeYInColumn(ServerWorld world, ServerPlayerEntity owner, double x, int seedY, double z,
			boolean explicitY) {
		if (explicitY) {
			int minY = Math.max(world.getBottomY() + 1, seedY - 4);
			int maxY = Math.min(world.getTopY() - 2, seedY + 4);
			for (int offset = 0; offset <= 4; offset++) {
				int up = seedY + offset;
				if (up >= minY && up <= maxY && isSafe(world, owner, x, up, z)) return new Vec3d(x, up, z);
				int down = seedY - offset;
				if (offset != 0 && down >= minY && down <= maxY && isSafe(world, owner, x, down, z)) {
					return new Vec3d(x, down, z);
				}
			}
			return null;
		}

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
		for (dev.mapselect.entity.TwinBodyEntity legacy : serverWorld.getEntitiesByType(MapSelectEntities.TWIN_BODY, entity -> true)) legacy.discard();
		for (TwinFakePlayerEntity body : serverWorld.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(TwinFakePlayerEntity.class), entity -> true)) {
			body.discard();
		}
		for (Map.Entry<UUID, TwinRecord> entry : List.copyOf(bodies.entrySet())) {
			if (!entry.getValue().belongsTo(serverWorld)) continue;
			removeBody(serverWorld, entry.getKey(), true);
		}
		eliminatedBodies.clear();
		voiceRelayEnabled.clear();
	}

	private static void removeBody(ServerWorld world, UUID playerId, boolean removeRecord) {
		TwinRecord record = bodies.get(playerId);
		ServerWorld bodyWorld = record == null || record.belongsTo(world)
			? world
			: world.getServer().getWorld(record.worldKey);
		TwinFakePlayerEntity body = body(bodyWorld, record);
		if (body != null) body.discard();
		discardOwnedBodies(bodyWorld, playerId, null);
		if (bodyWorld != world) discardOwnedBodies(world, playerId, null);
		cooldownUntil.remove(playerId);
		lastSwapAt.remove(playerId);
		voiceRelayEnabled.remove(playerId);
		if (removeRecord) {
			if (record == null) bodies.remove(playerId);
			else bodies.remove(playerId, record);
		}
	}

	private static TwinFakePlayerEntity body(ServerWorld world, TwinRecord record) {
		if (world == null || record == null || record.entityId == null || !record.belongsTo(world)) return null;
		Entity entity = world.getEntity(record.entityId);
		return entity instanceof TwinFakePlayerEntity twin ? twin : null;
	}

	public static void sendBody(ServerPlayerEntity viewer, TwinFakePlayerEntity body, boolean present) {
		viewer.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlayerListS2CPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.ADD_PLAYER, body));
		if (ServerPlayNetworking.canSend(viewer, dev.mapselect.network.role.twins.TwinsBodyPayload.ID)) ServerPlayNetworking.send(viewer,
			new dev.mapselect.network.role.twins.TwinsBodyPayload(body.ownerId().orElse(body.getUuid()), body.getUuid(), present));
	}

	private static TwinFakePlayerEntity ownedBody(ServerWorld world, UUID ownerId) {
		if (world == null || ownerId == null) return null;
		for (TwinFakePlayerEntity candidate : world.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(TwinFakePlayerEntity.class),
				entity -> entity.isAlive() && !dev.mapselect.role.loki.LokiManager.isLokiBody(entity)
					&& entity.ownerId().filter(ownerId::equals).isPresent())) {
			return candidate;
		}
		return null;
	}

	private static void discardOwnedBodies(ServerWorld world, UUID ownerId, TwinFakePlayerEntity keep) {
		if (world == null || ownerId == null) return;
		UUID keepId = keep == null ? null : keep.getUuid();
		for (TwinFakePlayerEntity candidate : world.getEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(TwinFakePlayerEntity.class),
				entity -> !dev.mapselect.role.loki.LokiManager.isLokiBody(entity) && entity.ownerId().isPresent() && ownerId.equals(entity.ownerId().get()))) {
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
		return AbilityCooldowns.remaining(cooldownUntil, player);
	}

	private record BodyState(NbtList inventory, int selectedSlot, EquipmentState equipment,
			float health, float absorption, int fireTicks, int frozenTicks, int air,
			float fallDistance, Vec3d velocity, List<StatusEffectInstance> effects) {
		private static BodyState capture(TwinFakePlayerEntity body) {
			return new BodyState(body.copyStoredInventory(), body.storedSelectedSlot(),
				EquipmentState.capture(body), body.getHealth(), body.getAbsorptionAmount(),
				body.getFireTicks(), body.getFrozenTicks(), body.getAir(), body.fallDistance,
				body.getVelocity(), copyEffects(body));
		}

		private static BodyState capture(ServerPlayerEntity player) {
			return new BodyState(
				(NbtList) player.getInventory().writeNbt(new NbtList()).copy(),
				player.getInventory().selectedSlot, EquipmentState.capture(player),
				player.getHealth(), player.getAbsorptionAmount(), player.getFireTicks(),
				player.getFrozenTicks(), player.getAir(), player.fallDistance,
				player.getVelocity(), copyEffects(player));
		}

		private static List<StatusEffectInstance> copyEffects(LivingEntity entity) {
			List<StatusEffectInstance> copy = new ArrayList<>();
			for (StatusEffectInstance effect : entity.getStatusEffects()) {
				copy.add(new StatusEffectInstance(effect));
			}
			return copy;
		}

		private void applyLivingState(LivingEntity target) {
			target.clearStatusEffects();
			for (StatusEffectInstance effect : effects) {
				target.addStatusEffect(new StatusEffectInstance(effect));
			}
			target.setHealth(Math.max(0.01F, Math.min(health, target.getMaxHealth())));
			target.setAbsorptionAmount(Math.max(0.0F, absorption));
			target.setFireTicks(fireTicks);
			target.setFrozenTicks(frozenTicks);
			target.setAir(air);
			target.fallDistance = fallDistance;
			target.setVelocity(velocity);
			target.velocityModified = true;
		}

		private void restorePlayer(ServerPlayerEntity player) {
			player.getInventory().clear();
			player.getInventory().readNbt((NbtList) inventory.copy());
			player.getInventory().selectedSlot = Math.max(0, Math.min(8, selectedSlot));
			equipment.apply(player);
			player.getInventory().markDirty();
			applyLivingState(player);
			player.playerScreenHandler.syncState();
		}
	}

	private record EquipmentState(ItemStack mainHand, ItemStack offHand, ItemStack feet,
			ItemStack legs, ItemStack chest, ItemStack head) {
		private static EquipmentState capture(LivingEntity entity) {
			return new EquipmentState(
				entity.getEquippedStack(EquipmentSlot.MAINHAND).copy(),
				entity.getEquippedStack(EquipmentSlot.OFFHAND).copy(),
				entity.getEquippedStack(EquipmentSlot.FEET).copy(),
				entity.getEquippedStack(EquipmentSlot.LEGS).copy(),
				entity.getEquippedStack(EquipmentSlot.CHEST).copy(),
				entity.getEquippedStack(EquipmentSlot.HEAD).copy());
		}

		private void apply(LivingEntity entity) {
			entity.equipStack(EquipmentSlot.MAINHAND, mainHand.copy());
			entity.equipStack(EquipmentSlot.OFFHAND, offHand.copy());
			entity.equipStack(EquipmentSlot.FEET, feet.copy());
			entity.equipStack(EquipmentSlot.LEGS, legs.copy());
			entity.equipStack(EquipmentSlot.CHEST, chest.copy());
			entity.equipStack(EquipmentSlot.HEAD, head.copy());
		}
	}

	private static final class TwinRecord {
		private final RegistryKey<World> worldKey;
		private final UUID entityId;
		private final String decoyRoleId;
		private SpawnPoint spawnPoint;
		private long missingSinceTick = -1L;

		private TwinRecord(RegistryKey<World> worldKey, UUID entityId, String decoyRoleId, SpawnPoint spawnPoint) {
			this.worldKey = worldKey;
			this.entityId = entityId;
			this.decoyRoleId = decoyRoleId == null ? "" : decoyRoleId;
			this.spawnPoint = spawnPoint;
		}

		private boolean belongsTo(ServerWorld world) {
			return world != null && worldKey != null && worldKey.equals(world.getRegistryKey());
		}
	}

	private record SpawnPoint(double x, double y, double z, float yaw, float pitch, boolean explicitY) {
		private static SpawnPoint from(MapPreset.PosData pos) {
			if (pos == null) return null;
			return new SpawnPoint(pos.x, pos.y, pos.z, pos.yaw, pos.pitch, true);
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
				return new SpawnPoint(translated.x, translated.y, translated.z, pos.yaw, pos.pitch, true);
			}
			return from(pos);
		}
	}
}
