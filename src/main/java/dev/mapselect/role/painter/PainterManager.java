package dev.mapselect.role.painter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.painter.PainterBodyUsePayload;
import dev.mapselect.network.role.painter.PainterChoiceOpenPayload;
import dev.mapselect.network.role.painter.PainterChoiceSubmitPayload;
import dev.mapselect.network.role.painter.PainterDoorwayCrossPayload;
import dev.mapselect.network.role.painter.PainterDoorwayTransitionPayload;
import dev.mapselect.network.role.painter.PainterDoorwayUsePayload;
import dev.mapselect.network.role.painter.PainterPlayerUsePayload;
import dev.mapselect.network.role.painter.PainterReturnParticlesPayload;
import dev.mapselect.network.role.painter.PainterStatePayload;
import dev.mapselect.network.preset.GexpressPresetsSyncHandler;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.copycat.CopycatManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PainterManager {
	private static final double BODY_LOOK_RADIUS_SQUARED = 1.4D;
	private static final double PAINT_RANGE = 4.0D;
	private static final double DOORWAY_RANGE = 4.5D;
	private static final int DOORWAY_COLOR = 0xB45BFF;
	private static final double DOORWAY_TARGET_HALF_WIDTH = 0.72D;
	private static final double DOORWAY_TARGET_HALF_HEIGHT = 1.10D;
	private static final double DOORWAY_TARGET_HALF_DEPTH = 0.75D;
	private static final double OPEN_SLIDE_OFFSET = 12.5D / 16.0D;
	private static final float OPEN_KEYFRAME_START_SECONDS = 0.10F;
	private static final float OPEN_KEYFRAME_END_SECONDS = 0.70F;
	private static final double DESTINATION_VIEW_OFFSET = 8.0D / 16.0D;
	private static final double DOORWAY_EXIT_CLEARANCE = DESTINATION_VIEW_OFFSET;
	private static final double DOORWAY_REMOTE_PLAYER_RANGE = 32.0D;
	private static final int DOORWAY_PREFERRED_EXIT_DISTANCE = 2;
	private static final int DOORWAY_BLOCK_FORWARD = 34;
	private static final int DOORWAY_BLOCK_BACK = 2;
	private static final int DOORWAY_BLOCK_SIDE = 16;
	private static final int DOORWAY_BLOCK_Y_BELOW = 3;
	private static final int DOORWAY_BLOCK_Y_ABOVE = 8;
	private static final Identifier KINS_BODY_COMPONENT = Identifier.of("kinswathe", "body");
	private static final Identifier KINS_BODY_DEATH_COMPONENT = Identifier.of("kinswathe", "body_death_reason");
	private static final Identifier NOELLE_BODY_COMPONENT = Identifier.of("noellesroles", "body_death_reason");
	private static final Map<UUID, BodyPaint> bodyPaints = new ConcurrentHashMap<>();
	private static final Map<UUID, LivingPaint> livingPaints = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> bodyCooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> playerCooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> doorwayCooldownUntil = new ConcurrentHashMap<>();
	private static final Map<BlockPos, ActiveDoorway> activeDoorways = new ConcurrentHashMap<>();
	private static final Set<BlockPos> disabledDoorways = ConcurrentHashMap.newKeySet();
	private static int syncTick;

	private PainterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PainterBodyUsePayload.ID, PainterBodyUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterPlayerUsePayload.ID, PainterPlayerUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterDoorwayUsePayload.ID, PainterDoorwayUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterDoorwayCrossPayload.ID, PainterDoorwayCrossPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterChoiceSubmitPayload.ID, PainterChoiceSubmitPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterStatePayload.ID, PainterStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterChoiceOpenPayload.ID, PainterChoiceOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterReturnParticlesPayload.ID, PainterReturnParticlesPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterDoorwayTransitionPayload.ID, PainterDoorwayTransitionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PainterBodyUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryOpenPaintPicker(context.player(), null)));
		ServerPlayNetworking.registerGlobalReceiver(PainterPlayerUsePayload.ID,
			(payload, context) -> context.server().execute(() ->
				tryOpenPaintPicker(context.player(), payload == null ? null : payload.targetId())));
		ServerPlayNetworking.registerGlobalReceiver(PainterDoorwayUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryPaintDoorway(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(PainterDoorwayCrossPayload.ID,
			(payload, context) -> context.server().execute(() -> tryCrossDoorway(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(PainterChoiceSubmitPayload.ID,
			(payload, context) -> context.server().execute(() -> submitPaintChoice(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(PainterManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
	}

	public static DoorwayToggleResult setLookedDoorwayDisabled(ServerPlayerEntity player, Boolean disabled) {
		if (player == null) return new DoorwayToggleResult(null, false, false);
		DoorwayTarget target = doorTarget(player, true);
		if (target == null) return new DoorwayToggleResult(null, false, false);
		return setDoorwayDestinationDisabled(player.getServerWorld(), target.lowerPos(), disabled);
	}

	public static DoorwayToggleResult setDoorwayDestinationDisabled(ServerWorld world, BlockPos pos, Boolean disabled) {
		if (world == null || pos == null) return new DoorwayToggleResult(null, false, false);
		DoorwayTarget target = doorwayTargetAt(world, pos, true);
		if (target == null) return new DoorwayToggleResult(null, false, false);
		BlockPos key = target.lowerPos().toImmutable();
		boolean next = disabled == null ? !disabledDoorways.contains(key) : disabled;
		if (next) {
			disabledDoorways.add(key);
		} else {
			disabledDoorways.remove(key);
		}
		removeDoorwaysUsingDestination(world, key);
		broadcast(world);
		saveDisabledDoorwaysToCurrentMap(world);
		return new DoorwayToggleResult(key, next, true);
	}

	public static int clearDisabledDoorways() {
		return clearDisabledDoorways(null);
	}

	public static int clearDisabledDoorways(ServerWorld world) {
		int count = disabledDoorways.size();
		disabledDoorways.clear();
		if (world != null) {
			broadcast(world);
			saveDisabledDoorwaysToCurrentMap(world);
		}
		return count;
	}

	public static List<BlockPos> disabledDoorwaysSnapshot() {
		return disabledDoorways.stream()
			.sorted(Comparator.comparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getY)
				.thenComparingInt(BlockPos::getZ))
			.map(BlockPos::toImmutable)
			.toList();
	}

	public static void replaceDisabledDoorways(ServerWorld world, Collection<BlockPos> positions) {
		disabledDoorways.clear();
		if (positions != null) {
			Set<BlockPos> normalized = new LinkedHashSet<>();
			for (BlockPos position : positions) {
				BlockPos key = canonicalDoorwayLower(world, position);
				if (key != null) normalized.add(key);
			}
			disabledDoorways.addAll(normalized);
		}
		if (world != null) {
			for (BlockPos key : disabledDoorways) removeDoorwaysUsingDestination(world, key);
			broadcast(world);
		}
	}

	private static BlockPos canonicalDoorwayLower(ServerWorld world, BlockPos pos) {
		if (pos == null) return null;
		DoorwayTarget target = world == null ? null : doorwayTargetAt(world, pos, true);
		return (target == null ? pos : target.lowerPos()).toImmutable();
	}

	private static void saveDisabledDoorwaysToCurrentMap(ServerWorld world) {
		if (world == null || world.getServer() == null) return;
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(world);
		String mapName = weather == null ? null : weather.getCurrentMapName();
		if (mapName == null || mapName.isBlank() || !PresetStorage.isValidName(mapName)) return;
		try {
			MapPreset preset = PresetStorage.load(world.getServer(), mapName);
			if (preset == null) return;
			preset.disabledPainterDoorways = preset.disabledPainterDoorwaysFromRuntime(disabledDoorwaysSnapshot());
			PresetStorage.save(world.getServer(), mapName, preset);
			GexpressPresetsSyncHandler.broadcastPresets(world.getServer());
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save disabled Painter doorways for map preset '{}'.", mapName, e);
		}
	}

	public static Role investigationRole(GameWorldComponent game, PlayerEntity player) {
		Role fallback = game == null || player == null ? null : game.getRole(player);
		if (game == null || player == null) return fallback;
		LivingPaint paint = livingPaints.get(player.getUuid());
		if (paint == null || !paint.active(player.getWorld().getTime())) {
			if (paint != null) livingPaints.remove(player.getUuid());
			return fallback;
		}
		Role fake = roleById(paint.fakeRoleId());
		return fake == null ? fallback : fake;
	}

	public static Role investigationRole(GameWorldComponent game, UUID playerId) {
		Role fallback = game == null || playerId == null ? null : game.getRole(playerId);
		if (game == null || playerId == null) return fallback;
		LivingPaint paint = livingPaints.get(playerId);
		if (paint == null) return fallback;
		Role fake = roleById(paint.fakeRoleId());
		return fake == null ? fallback : fake;
	}

	private static void tryOpenPaintPicker(ServerPlayerEntity painter, UUID requestedPlayerTargetId) {
		if (!canUsePainter(painter)) return;
		ServerWorld world = painter.getServerWorld();
		PlayerBodyEntity body = findBody(painter);
		if (body != null) {
			if (!checkPaintCooldown(painter, bodyCooldownTicks(), "Paint is cooling down.")) return;
			if (activeBodyPaintCount(painter.getUuid(), world.getTime()) >= GexpressConfig.getPainterMaxPaintedBodies()) {
				painter.sendMessage(Text.literal("Your corpse paint is already active.").formatted(Formatting.RED), true);
				return;
			}
			openPicker(painter, new PainterChoiceOpenPayload(PainterChoiceOpenPayload.Mode.BODY, body.getUuid(), "body",
				deathChoices(), roleChoices()));
			return;
		}
		ServerPlayerEntity target = requestedTarget(painter, requestedPlayerTargetId);
		if (target == null) {
			target = AbilityTargeting.findLookTarget(painter, world.getPlayers(), PAINT_RANGE, 0.25D, true,
				candidate -> candidate != painter && canPaintLivingTarget(candidate));
		}
		if (target == null) {
			painter.sendMessage(Text.literal("No body or living player close enough to repaint.").formatted(Formatting.RED), true);
			return;
		}
		if (!checkPaintCooldown(painter, playerCooldownTicks(), "Paint is cooling down.")) return;
		if (activeLivingPaintCount(painter.getUuid(), world.getTime()) >= GexpressConfig.getPainterMaxPaintedPlayers()) {
			painter.sendMessage(Text.literal("Your living paint is already active.").formatted(Formatting.RED), true);
			return;
		}
		openPicker(painter, new PainterChoiceOpenPayload(PainterChoiceOpenPayload.Mode.PLAYER, target.getUuid(),
			target.getName().getString(), List.of(), roleChoices()));
	}

	private static void tryPaintDoorway(ServerPlayerEntity painter) {
		if (!canUsePainter(painter)) return;
		if (!checkCooldown(painter, doorwayCooldownUntil, AbilityCooldownPayload.PAINTER_DOORWAY,
				doorwayCooldownTicks(), "Da Vinci's Doorway is cooling down.")) {
			return;
		}
		ServerWorld world = painter.getServerWorld();
		DoorwayTarget target = doorTarget(painter);
		if (target == null) {
			painter.sendMessage(Text.literal("Look at a door to paint Da Vinci's Doorway.").formatted(Formatting.RED),
				true);
			return;
		}

		Direction through = doorwayDirection(painter, target.lowerPos(), target.facing());
		RemoteDoorway destination = alternateDoorDestination(world, target.lowerPos(), through);
		if (destination == null) {
			painter.sendMessage(Text.literal("No different safe doorway destination was found.").formatted(Formatting.RED), true);
			return;
		}

		BlockPos key = target.lowerPos().toImmutable();
		long now = world.getTime();
		removeDoorwayPortal(world, activeDoorways.get(key));
		PainterImmersivePortalBridge.DoorwayPortalIds immersivePortalIds =
			PainterImmersivePortalBridge.spawnDoorwayPortals(world, painter.getUuid(),
			immersivePortalSourceCenter(world, key, target.facing(), through), through,
			immersivePortalDestinationCenter(destination),
			destination.through());
		activeDoorways.put(key, new ActiveDoorway(painter.getUuid(), key, target.facing(), through,
			destination.lowerPos(), destination.facing(), destination.through(), destination.destination(),
			now + doorwayDurationTicks(), immersivePortalIds.sourceId(), immersivePortalIds.reverseId(),
			seededDoorSides(world, key, target.facing(), through)));
		startCooldown(painter, doorwayCooldownUntil, AbilityCooldownPayload.PAINTER_DOORWAY, doorwayCooldownTicks());
		world.playSound(null, key, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.75F, 0.75F);
		painter.sendMessage(Text.literal("Da Vinci's Doorway will hold for "
				+ GexpressConfig.getPainterDoorwayDurationSeconds() + " seconds.")
			.formatted(Formatting.LIGHT_PURPLE), true);
		broadcast(world);
	}

	private static void tryCrossDoorway(ServerPlayerEntity player, PainterDoorwayCrossPayload payload) {
		if (player == null || payload == null || !(player.getWorld() instanceof ServerWorld world)) return;
		ActiveDoorway doorway = activeDoorways.get(payload.lowerPos());
		if (doorway == null || doorway.untilTick() <= world.getTime()) return;
		if (doorway.hasImmersivePortal()) return;
		if (player.isSpectator()) return;
		Vec3d eyeOffset = player.getEyePos().subtract(player.getPos());
		Vec3d sourcePos = payload.playerPos().add(eyeOffset);
		Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
		double sourceDistance = sourcePos.squaredDistanceTo(center);
		double serverDistance = player.getEyePos().squaredDistanceTo(center);
		if (sourceDistance > 16.0D || serverDistance > 36.0D) return;
		if (!player.getBoundingBox().expand(2.5D).intersects(doorwayTriggerBox(doorway).expand(1.0D))) {
			return;
		}
		crossDoorway(world, doorway, player, sourcePos, payload.yaw(), payload.pitch(), true);
	}

	private static void submitPaintChoice(ServerPlayerEntity painter, PainterChoiceSubmitPayload payload) {
		if (!canUsePainter(painter) || payload == null || payload.targetId() == null || payload.roleId() == null) return;
		if (!isDisguisableRole(payload.roleId()) || roleById(payload.roleId()) == null) {
			painter.sendMessage(Text.literal("That role cannot be painted.").formatted(Formatting.RED), true);
			return;
		}
		if (payload.mode() == PainterChoiceOpenPayload.Mode.BODY) {
			confirmBodyPaint(painter, payload.targetId(), payload.deathReasonId(), payload.roleId());
		} else {
			confirmPlayerPaint(painter, payload.targetId(), payload.roleId());
		}
	}

	private static void confirmBodyPaint(ServerPlayerEntity painter, UUID bodyId, Identifier deathReasonId,
			Identifier fakeRoleId) {
		if (!isKnownDeathReason(deathReasonId)) {
			painter.sendMessage(Text.literal("That death message cannot be painted.").formatted(Formatting.RED), true);
			return;
		}
		ServerWorld world = painter.getServerWorld();
		if (!checkPaintCooldown(painter, bodyCooldownTicks(), "Paint is cooling down.")) return;
		if (activeBodyPaintCount(painter.getUuid(), world.getTime()) >= GexpressConfig.getPainterMaxPaintedBodies()) {
			painter.sendMessage(Text.literal("Your corpse paint is already active.").formatted(Formatting.RED), true);
			return;
		}
		PlayerBodyEntity body = findBodyById(world, bodyId);
		if (body == null || !canTargetBody(painter, body)) {
			painter.sendMessage(Text.literal("That body is no longer in paint range.").formatted(Formatting.RED), true);
			return;
		}

		long duration = durationTicks();
		BodyPaint old = bodyPaints.get(body.getUuid());
		if (old != null && old.active(world.getTime())) {
			painter.sendMessage(Text.literal("That body is already painted.").formatted(Formatting.RED), true);
			return;
		}
		old = bodyPaints.remove(body.getUuid());
		BodySnapshot original = old == null ? BodySnapshot.capture(body) : old.original();
		if (old != null) old.restore(world);

		original.applyFake(body, deathReasonId, fakeRoleId);
		int color = randomPaintColor();
		bodyPaints.put(body.getUuid(), new BodyPaint(body.getUuid(), body.getPlayerUuid(), painter.getUuid(),
			deathReasonId, fakeRoleId, color, world.getTime() + duration, duration, original));
		startPaintCooldown(painter, bodyCooldownTicks());
		painter.sendMessage(Text.literal("Painted body as " + displayRole(fakeRoleId) + ".")
			.formatted(Formatting.LIGHT_PURPLE), true);
		broadcast(world);
	}

	private static void confirmPlayerPaint(ServerPlayerEntity painter, UUID targetId, Identifier fakeRoleId) {
		ServerWorld world = painter.getServerWorld();
		if (!checkPaintCooldown(painter, playerCooldownTicks(), "Paint is cooling down.")) return;
		if (activeLivingPaintCount(painter.getUuid(), world.getTime()) >= GexpressConfig.getPainterMaxPaintedPlayers()) {
			painter.sendMessage(Text.literal("Your living paint is already active.").formatted(Formatting.RED), true);
			return;
		}
		ServerPlayerEntity target = requestedTarget(painter, targetId);
		if (target == null) {
			painter.sendMessage(Text.literal("That player is no longer in paint range.").formatted(Formatting.RED), true);
			return;
		}

		long duration = durationTicks();
		LivingPaint old = livingPaints.get(target.getUuid());
		if (old != null && old.active(world.getTime())) {
			painter.sendMessage(Text.literal("That player is already painted.").formatted(Formatting.RED), true);
			return;
		}
		int color = randomPaintColor();
		livingPaints.put(target.getUuid(), new LivingPaint(target.getUuid(), painter.getUuid(), fakeRoleId, color,
			world.getTime() + duration, duration));
		startPaintCooldown(painter, playerCooldownTicks());
		painter.sendMessage(Text.literal("Painted " + target.getName().getString() + " as "
			+ displayRole(fakeRoleId) + ".").formatted(Formatting.LIGHT_PURPLE), true);
		broadcast(world);
	}

	private static void openPicker(ServerPlayerEntity player, PainterChoiceOpenPayload payload) {
		if (player == null || payload == null) return;
		if (!ServerPlayNetworking.canSend(player, PainterChoiceOpenPayload.ID)) {
			player.sendMessage(Text.literal("Your client cannot open the Painter menu.").formatted(Formatting.RED), true);
			return;
		}
		ServerPlayNetworking.send(player, payload);
	}

	private static ServerPlayerEntity requestedTarget(ServerPlayerEntity painter, UUID targetId) {
		if (painter == null || targetId == null) return null;
		ServerPlayerEntity target = painter.getServer().getPlayerManager().getPlayer(targetId);
		if (!canPaintLivingTarget(target)) return null;
		return AbilityTargeting.canTarget(painter, target, PAINT_RANGE, 0.25D, true,
			candidate -> candidate != painter && canPaintLivingTarget(candidate)) ? target : null;
	}

	private static boolean canPaintLivingTarget(ServerPlayerEntity target) {
		return target != null && !PelicanManager.isStashed(target)
			&& !isLivingPaintActive(target, target.getWorld().getTime())
			&& (DeadPlayerStatus.isLivingRoundParticipant(target) || GexpressTestState.isRoleTester(target));
	}

	private static boolean canUsePainter(ServerPlayerEntity painter) {
		if (painter == null || !(painter.getWorld() instanceof ServerWorld world)) return false;
		if (PelicanManager.isStashed(painter) || !isPainter(painter) || !canUseHere(world, painter)) return false;
		return GexpressTestState.hasCreativeAbilityBypass(painter) || GameFunctions.isPlayerAliveAndSurvival(painter);
	}

	private static boolean isPainter(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.PAINTER_ID.equals(role.identifier())
			|| CopycatManager.isCopyingRole(player, MapSelectRoles.PAINTER_ID));
	}

	private static PlayerBodyEntity findBody(ServerPlayerEntity painter) {
		Vec3d eye = painter.getEyePos();
		Vec3d look = painter.getRotationVec(1.0F).normalize();
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (PlayerBodyEntity body : painter.getServerWorld().getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			if (isBodyPaintActive(body, painter.getWorld().getTime())) continue;
			Vec3d to = body.getPos().add(0.0D, 0.8D, 0.0D).subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > PAINT_RANGE || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > BODY_LOOK_RADIUS_SQUARED || !painter.canSee(body)) continue;
			best = body;
			bestAlong = along;
		}
		return best;
	}

	private static PlayerBodyEntity findBodyById(ServerWorld world, UUID bodyId) {
		if (world == null || bodyId == null) return null;
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY,
				entity -> bodyId.equals(entity.getUuid()))) {
			return body;
		}
		return null;
	}

	private static boolean canTargetBody(ServerPlayerEntity painter, PlayerBodyEntity body) {
		if (painter == null || body == null || body.isRemoved()) return false;
		if (isBodyPaintActive(body, painter.getWorld().getTime())) return false;
		Vec3d eye = painter.getEyePos();
		Vec3d look = painter.getRotationVec(1.0F).normalize();
		Vec3d to = body.getPos().add(0.0D, 0.8D, 0.0D).subtract(eye);
		double along = to.dotProduct(look);
		if (along < 0.0D || along > PAINT_RANGE) return false;
		double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
		return perpendicularSq <= BODY_LOOK_RADIUS_SQUARED && painter.canSee(body);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		long now = world.getTime();
		boolean changed = tickDoorways(world, now);
		for (BodyPaint paint : List.copyOf(bodyPaints.values())) {
			if (paint.untilTick() > now) continue;
			bodyPaints.remove(paint.bodyId());
			Vec3d origin = paint.restoreAndPosition(world);
			sendReturnParticles(world, paint.painterId(), origin, paint.color());
			changed = true;
		}
		for (LivingPaint paint : List.copyOf(livingPaints.values())) {
			if (paint.untilTick() > now) continue;
			livingPaints.remove(paint.playerId());
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(paint.playerId());
			Vec3d origin = target == null ? null : target.getPos().add(0.0D, 1.0D, 0.0D);
			sendReturnParticles(world, paint.painterId(), origin, paint.color());
			changed = true;
		}
		if (changed || (++syncTick >= 10 && (!bodyPaints.isEmpty() || !livingPaints.isEmpty()
				|| !activeDoorways.isEmpty()))) {
			syncTick = 0;
			broadcast(world);
		}
	}

	private static void clear(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (BodyPaint paint : bodyPaints.values()) paint.restore(serverWorld);
			bodyPaints.clear();
			livingPaints.clear();
			bodyCooldownUntil.clear();
			playerCooldownUntil.clear();
			doorwayCooldownUntil.clear();
			activeDoorways.clear();
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_BODY);
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_PLAYER);
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_DOORWAY);
			}
			broadcast(serverWorld);
		} else {
			bodyPaints.clear();
			livingPaints.clear();
			bodyCooldownUntil.clear();
			playerCooldownUntil.clear();
			doorwayCooldownUntil.clear();
			activeDoorways.clear();
		}
	}

	private static void sync(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)
				|| !ServerPlayNetworking.canSend(player, PainterStatePayload.ID)) {
			return;
		}
		ServerPlayNetworking.send(player, isKillerCohort(player)
			? payloadFor(world)
			: doorwayPayloadFor(world));
		syncCooldowns(player);
	}

	private static void broadcast(ServerWorld world) {
		if (world == null) return;
		PainterStatePayload payload = payloadFor(world);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, PainterStatePayload.ID)) continue;
			ServerPlayNetworking.send(player, isKillerCohort(player) ? payload : doorwayOnly(payload));
		}
	}

	private static PainterStatePayload doorwayPayloadFor(ServerWorld world) {
		return doorwayOnly(payloadFor(world));
	}

	private static PainterStatePayload doorwayOnly(PainterStatePayload payload) {
		return payload == null
			? PainterStatePayload.clear()
			: new PainterStatePayload(List.of(), List.of(), payload.doorways());
	}

	private static PainterStatePayload payloadFor(ServerWorld world) {
		long now = world.getTime();
		List<PainterStatePayload.Entry> bodyEntries = new ArrayList<>();
		for (BodyPaint paint : bodyPaints.values()) {
			int remaining = remainingTicks(now, paint.untilTick());
			if (remaining > 0) bodyEntries.add(new PainterStatePayload.Entry(
				paint.bodyId(), paint.color(), remaining, (int) Math.min(Integer.MAX_VALUE, paint.totalTicks())));
		}
		List<PainterStatePayload.Entry> playerEntries = new ArrayList<>();
		for (LivingPaint paint : livingPaints.values()) {
			int remaining = remainingTicks(now, paint.untilTick());
			if (remaining > 0) playerEntries.add(new PainterStatePayload.Entry(
				paint.playerId(), paint.color(), remaining, (int) Math.min(Integer.MAX_VALUE, paint.totalTicks())));
		}
		List<PainterStatePayload.DoorwayEntry> doorwayEntries = new ArrayList<>();
		for (ActiveDoorway doorway : activeDoorways.values()) {
			int remaining = remainingTicks(now, doorway.untilTick());
			if (remaining > 0) doorwayEntries.add(new PainterStatePayload.DoorwayEntry(
				doorway.lowerPos(), DOORWAY_COLOR, remaining, (int) Math.min(Integer.MAX_VALUE, doorwayDurationTicks()),
				doorway.facing(), doorway.through(), doorway.destinationLowerPos(), doorway.destinationFacing(),
				doorway.destinationThrough(), doorway.destination(), doorwayPlayers(world, doorway),
				doorwayBlocks(world, doorway)));
		}
		return new PainterStatePayload(bodyEntries, playerEntries, doorwayEntries);
	}

	private static List<PainterStatePayload.DoorwayPlayerEntry> doorwayPlayers(ServerWorld world, ActiveDoorway doorway) {
		if (world == null || doorway == null) return List.of();
		Vec3d center = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		double maxDistanceSquared = DOORWAY_REMOTE_PLAYER_RANGE * DOORWAY_REMOTE_PLAYER_RANGE;
		List<PainterStatePayload.DoorwayPlayerEntry> entries = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || player.squaredDistanceTo(center) > maxDistanceSquared) continue;
			entries.add(new PainterStatePayload.DoorwayPlayerEntry(player.getUuid(),
				player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(),
				player.getYaw(), player.getPitch(), player.bodyYaw, player.headYaw,
				player.isSneaking(), player.isSprinting(), player.isInvisible()));
			if (entries.size() >= 16) break;
		}
		return entries;
	}

	private static List<PainterStatePayload.DoorwayBlockEntry> doorwayBlocks(ServerWorld world,
			ActiveDoorway doorway) {
		if (world == null || doorway == null || doorway.destinationLowerPos() == null
				|| doorway.destinationThrough() == null) {
			return List.of();
		}
		BlockPos origin = doorway.destinationLowerPos();
		Direction through = doorway.destinationThrough();
		Direction right = through.rotateYClockwise();
		List<PainterStatePayload.DoorwayBlockEntry> entries = new ArrayList<>();
		for (int forward = -DOORWAY_BLOCK_BACK; forward <= DOORWAY_BLOCK_FORWARD; forward++) {
			for (int side = -DOORWAY_BLOCK_SIDE; side <= DOORWAY_BLOCK_SIDE; side++) {
				for (int dy = -DOORWAY_BLOCK_Y_BELOW; dy <= DOORWAY_BLOCK_Y_ABOVE; dy++) {
					BlockPos pos = origin.offset(through, forward).offset(right, side).add(0, dy, 0);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) continue;
					entries.add(PainterStatePayload.DoorwayBlockEntry.of(origin, pos, state));
				}
			}
		}
		return entries;
	}

	private static int remainingTicks(long now, long untilTick) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, untilTick - now));
	}

	private static boolean isKillerCohort(ServerPlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (role.canUseKiller() || game.canUseKillerFeatures(player)
			|| CopycatManager.isCopyingRole(player, MapSelectRoles.PAINTER_ID));
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean checkCooldown(ServerPlayerEntity painter, Map<UUID, Long> cooldowns, String key,
			long totalTicks, String message) {
		long remaining = cooldownRemaining(painter, cooldowns);
		if (remaining <= 0L) return true;
		AbilityCooldownSync.send(painter, key, remaining, totalTicks, false);
		painter.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
		return false;
	}

	private static boolean checkPaintCooldown(ServerPlayerEntity painter, long totalTicks, String message) {
		long remaining = paintCooldownRemaining(painter);
		if (remaining <= 0L) return true;
		AbilityCooldownSync.send(painter, AbilityCooldownPayload.PAINTER_BODY, remaining,
			Math.max(1L, totalTicks), false);
		AbilityCooldownSync.clear(painter, AbilityCooldownPayload.PAINTER_PLAYER);
		painter.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
		return false;
	}

	private static long paintCooldownRemaining(ServerPlayerEntity player) {
		return Math.max(cooldownRemaining(player, bodyCooldownUntil),
			cooldownRemaining(player, playerCooldownUntil));
	}

	private static long cooldownRemaining(ServerPlayerEntity player, Map<UUID, Long> cooldowns) {
		if (player == null || cooldowns == null) return 0L;
		Long until = cooldowns.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldowns.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	private static void startCooldown(ServerPlayerEntity painter, Map<UUID, Long> cooldowns, String key, long ticks) {
		if (painter == null || cooldowns == null) return;
		if (ticks <= 0L || GexpressTestState.hasCreativeAbilityBypass(painter)) {
			cooldowns.remove(painter.getUuid());
			AbilityCooldownSync.clear(painter, key);
			return;
		}
		cooldowns.put(painter.getUuid(), painter.getWorld().getTime() + ticks);
		AbilityCooldownSync.send(painter, key, ticks, ticks, false);
	}

	private static void startPaintCooldown(ServerPlayerEntity painter, long ticks) {
		if (painter == null) return;
		if (ticks <= 0L || GexpressTestState.hasCreativeAbilityBypass(painter)) {
			bodyCooldownUntil.remove(painter.getUuid());
			playerCooldownUntil.remove(painter.getUuid());
			AbilityCooldownSync.clear(painter, AbilityCooldownPayload.PAINTER_BODY);
			AbilityCooldownSync.clear(painter, AbilityCooldownPayload.PAINTER_PLAYER);
			return;
		}
		long until = painter.getWorld().getTime() + ticks;
		bodyCooldownUntil.put(painter.getUuid(), until);
		playerCooldownUntil.put(painter.getUuid(), until);
		AbilityCooldownSync.send(painter, AbilityCooldownPayload.PAINTER_BODY, ticks, ticks, false);
		AbilityCooldownSync.clear(painter, AbilityCooldownPayload.PAINTER_PLAYER);
	}

	private static void syncCooldowns(ServerPlayerEntity player) {
		if (player == null || !ServerPlayNetworking.canSend(player, AbilityCooldownPayload.ID)) return;
		long paint = paintCooldownRemaining(player);
		if (paint > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.PAINTER_BODY, paint,
				Math.max(bodyCooldownTicks(), playerCooldownTicks()), false);
		} else {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_BODY);
		}
		AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_PLAYER);
		long doorway = cooldownRemaining(player, doorwayCooldownUntil);
		if (doorway > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.PAINTER_DOORWAY, doorway,
				doorwayCooldownTicks(), false);
		} else {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_DOORWAY);
		}
	}

	private static int activeBodyPaintCount(UUID painterId, long now) {
		int count = 0;
		for (BodyPaint paint : bodyPaints.values()) {
			if (painterId.equals(paint.painterId()) && paint.untilTick() > now) count++;
		}
		return count;
	}

	private static int activeLivingPaintCount(UUID painterId, long now) {
		int count = 0;
		for (LivingPaint paint : livingPaints.values()) {
			if (painterId.equals(paint.painterId()) && paint.untilTick() > now) count++;
		}
		return count;
	}

	private static boolean isBodyPaintActive(PlayerBodyEntity body, long now) {
		if (body == null) return false;
		BodyPaint paint = bodyPaints.get(body.getUuid());
		if (paint == null) return false;
		if (paint.active(now)) return true;
		bodyPaints.remove(body.getUuid(), paint);
		return false;
	}

	private static boolean isLivingPaintActive(ServerPlayerEntity player, long now) {
		if (player == null) return false;
		LivingPaint paint = livingPaints.get(player.getUuid());
		if (paint == null) return false;
		if (paint.active(now)) return true;
		livingPaints.remove(player.getUuid(), paint);
		return false;
	}

	private static long durationTicks() {
		return (long) GexpressConfig.getPainterPaintDurationSeconds() * 20L;
	}

	private static long bodyCooldownTicks() {
		return (long) GexpressConfig.getPainterBodyCooldownSeconds() * 20L;
	}

	private static long playerCooldownTicks() {
		return (long) GexpressConfig.getPainterPlayerCooldownSeconds() * 20L;
	}

	private static long doorwayDurationTicks() {
		return (long) GexpressConfig.getPainterDoorwayDurationSeconds() * 20L;
	}

	private static long doorwayCooldownTicks() {
		return (long) GexpressConfig.getPainterDoorwayCooldownSeconds() * 20L;
	}

	private static List<PainterChoiceOpenPayload.DeathChoice> deathChoices() {
		List<PainterChoiceOpenPayload.DeathChoice> out = new ArrayList<>();
		out.add(deathChoice(GameConstants.DeathReasons.KNIFE, "Knife Stab", WatheItems.KNIFE));
		out.add(deathChoice(GameConstants.DeathReasons.GUN, "Gun Shot", WatheItems.REVOLVER));
		out.add(deathChoice(GameConstants.DeathReasons.BAT, "Bat Hit", WatheItems.BAT));
		out.add(deathChoice(GameConstants.DeathReasons.GRENADE, "Grenade", WatheItems.GRENADE));
		out.add(deathChoice(GameConstants.DeathReasons.POISON, "Poison", WatheItems.POISON_VIAL));
		if (FabricLoader.getInstance().isModLoaded("noellesroles")) {
			out.add(new PainterChoiceOpenPayload.DeathChoice(Identifier.of("noellesroles", "voodoo"),
				"Voodoo", Registries.ITEM.getId(Items.TOTEM_OF_UNDYING)));
		}
		if (FabricLoader.getInstance().isModLoaded("starexpress")) {
			out.add(new PainterChoiceOpenPayload.DeathChoice(Identifier.of("starexpress", "silenced_and_outside"),
				"Silenced Outside", Identifier.of("starexpress", "tape")));
		}
		if (FabricLoader.getInstance().isModLoaded("stupid_express")) {
			out.add(new PainterChoiceOpenPayload.DeathChoice(Identifier.of("stupid_express", "ignited"),
				"Ignited", Identifier.of("stupid_express", "lighter")));
		}
		out.add(deathChoice(GameConstants.DeathReasons.GENERIC, "Generic", Items.PAPER));
		return List.copyOf(out);
	}

	private static PainterChoiceOpenPayload.DeathChoice deathChoice(Identifier deathReason, String name, Item item) {
		return new PainterChoiceOpenPayload.DeathChoice(deathReason, name, Registries.ITEM.getId(item));
	}

	private static boolean isKnownDeathReason(Identifier deathReason) {
		if (deathReason == null) return false;
		for (PainterChoiceOpenPayload.DeathChoice choice : deathChoices()) {
			if (deathReason.equals(choice.deathReasonId())) return true;
		}
		return false;
	}

	private static List<PainterChoiceOpenPayload.RoleChoice> roleChoices() {
		refreshRoles();
		List<PainterChoiceOpenPayload.RoleChoice> out = new ArrayList<>();
		for (Role role : WatheRoles.ROLES) {
			if (role == null || role.identifier() == null || !isDisguisableRole(role.identifier())) continue;
			out.add(new PainterChoiceOpenPayload.RoleChoice(role.identifier(), displayRole(role.identifier()), role.color()));
		}
		out.sort(Comparator.comparing(PainterChoiceOpenPayload.RoleChoice::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(choice -> choice.roleId().toString()));
		return List.copyOf(out);
	}

	private static void refreshRoles() {
		try {
			Harpymodloader.refreshRoles();
		} catch (Throwable ignored) {
		}
	}

	private static boolean isDisguisableRole(Identifier id) {
		return id != null
			&& !WatheRoles.LOOSE_END.identifier().equals(id)
			&& !WatheRoles.DISCOVERY_CIVILIAN.identifier().equals(id);
	}

	private static Role roleById(Identifier roleId) {
		if (roleId == null) return null;
		refreshRoles();
		for (Role role : WatheRoles.ROLES) {
			if (role != null && roleId.equals(role.identifier())) return role;
		}
		return null;
	}

	private static int randomPaintColor() {
		return java.awt.Color.HSBtoRGB(ThreadLocalRandom.current().nextFloat(), 0.96F, 0.78F) & 0xFFFFFF;
	}

	private static DoorwayTarget doorTarget(ServerPlayerEntity painter) {
		return doorTarget(painter, false);
	}

	private static DoorwayTarget doorTarget(ServerPlayerEntity painter, boolean includeDisabled) {
		Vec3d start = painter.getEyePos();
		Vec3d end = start.add(painter.getRotationVec(1.0F).multiply(DOORWAY_RANGE));
		BlockHitResult hit = painter.getServerWorld().raycast(new RaycastContext(start, end,
			RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, painter));
		double hitDistanceSquared = hit == null || hit.getType() == HitResult.Type.MISS
			? start.squaredDistanceTo(end)
			: start.squaredDistanceTo(hit.getPos());
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			DoorwayTarget target = doorwayTargetAt(painter.getServerWorld(), hit.getBlockPos(), includeDisabled);
			if (target != null) return target;
		}
		double searchDistance = Math.min(DOORWAY_RANGE,
			Math.sqrt(hitDistanceSquared) + DOORWAY_TARGET_HALF_DEPTH + 0.25D);
		DoorwayTarget rayTarget = doorwayTargetAlongRay(painter.getServerWorld(), start, end, searchDistance,
			includeDisabled);
		if (rayTarget != null) return rayTarget;
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			return null;
		}

		Vec3d step = end.subtract(start).normalize().multiply(0.16D);
		Vec3d cursor = start;
		for (int i = 0; i <= Math.ceil(DOORWAY_RANGE / 0.16D); i++) {
			BlockPos pos = BlockPos.ofFloored(cursor);
			DoorwayTarget target = closestDoorwayTargetAt(painter.getServerWorld(), pos, includeDisabled);
			if (target != null) return target;
			cursor = cursor.add(step);
		}
		return null;
	}

	private static DoorwayTarget doorwayTargetAlongRay(ServerWorld world, Vec3d start, Vec3d end,
			double maxDistance, boolean includeDisabled) {
		if (world == null || start == null || end == null || maxDistance <= 0.0D) return null;
		Vec3d ray = end.subtract(start);
		double length = Math.min(maxDistance, ray.length());
		if (length <= 0.0D) return null;
		Vec3d direction = ray.normalize();
		BlockPos origin = BlockPos.ofFloored(start);
		Set<BlockPos> seen = ConcurrentHashMap.newKeySet();
		DoorwayTarget best = null;
		double bestAlong = Double.MAX_VALUE;
		for (BlockPos candidate : BlockPos.iterateOutwards(origin, 6, 3, 6)) {
			DoorwayTarget target = doorwayTargetAt(world, candidate, includeDisabled);
			if (target == null || !seen.add(target.lowerPos())) continue;
			double along = doorwayRayAlong(start, direction, length, target);
			if (along < 0.0D || along >= bestAlong) continue;
			best = target;
			bestAlong = along;
		}
		return best;
	}

	private static double doorwayRayAlong(Vec3d start, Vec3d direction, double maxDistance, DoorwayTarget target) {
		if (start == null || direction == null || target == null) return -1.0D;
		Vec3d center = doorwayCenter(target.lowerPos(), target.facing());
		double along = MathHelper.clamp(center.subtract(start).dotProduct(direction), 0.0D, maxDistance);
		Vec3d closest = start.add(direction.multiply(along));
		Vec3d delta = closest.subtract(center);
		Direction facing = target.facing() == null || target.facing().getAxis().isVertical()
			? Direction.NORTH
			: target.facing();
		Vec3d normal = Vec3d.of(facing.getVector()).normalize();
		Vec3d right = Vec3d.of(facing.rotateYClockwise().getVector()).normalize();
		return Math.abs(delta.dotProduct(right)) <= DOORWAY_TARGET_HALF_WIDTH
			&& Math.abs(delta.y) <= DOORWAY_TARGET_HALF_HEIGHT
			&& Math.abs(delta.dotProduct(normal)) <= DOORWAY_TARGET_HALF_DEPTH
				? along
				: -1.0D;
	}

	private static DoorwayTarget closestDoorwayTargetAt(ServerWorld world, BlockPos pos) {
		return closestDoorwayTargetAt(world, pos, false);
	}

	private static DoorwayTarget closestDoorwayTargetAt(ServerWorld world, BlockPos pos, boolean includeDisabled) {
		if (world == null || pos == null) return null;
		for (BlockPos candidate : List.of(pos, pos.down(), pos.up(), pos.north(), pos.south(), pos.east(), pos.west())) {
			DoorwayTarget target = doorwayTargetAt(world, candidate, includeDisabled);
			if (target != null) return target;
		}
		return null;
	}

	private static DoorwayTarget doorwayTargetAt(ServerWorld world, BlockPos pos) {
		return doorwayTargetAt(world, pos, false);
	}

	private static DoorwayTarget doorwayTargetAt(ServerWorld world, BlockPos pos, boolean includeDisabled) {
		if (world == null || pos == null) return null;
		BlockPos lower = lowerDoorPos(world, pos);
		DoorBlockEntity door = doorBlockEntityNear(world, lower);
		if (door != null) {
			Direction facing = door.getFacing();
			BlockPos doorLower = lowerDoorPos(world, door.getPos());
			if (facing != null && !facing.getAxis().isVertical()) {
				return new DoorwayTarget(doorLower.toImmutable(), facing);
			}
		}
		BlockState state = world.getBlockState(lower);
		if (isDoorLikeState(state)) {
			Direction facing = state.contains(Properties.HORIZONTAL_FACING)
				? state.get(Properties.HORIZONTAL_FACING)
				: Direction.NORTH;
			return new DoorwayTarget(lower.toImmutable(), facing);
		}
		return null;
	}

	private static boolean isDoorwayDisabled(BlockPos lower) {
		return lower != null && disabledDoorways.contains(lower.toImmutable());
	}

	private static DoorBlockEntity doorBlockEntityNear(ServerWorld world, BlockPos lower) {
		for (BlockPos candidate : List.of(lower, lower.up(), lower.down())) {
			BlockEntity blockEntity = world.getBlockEntity(candidate);
			if (blockEntity instanceof DoorBlockEntity door) return door;
		}
		return null;
	}

	private static boolean isDoorLikeState(BlockState state) {
		if (state == null || state.isAir()) return false;
		if (state.getBlock() instanceof DoorBlock) return true;
		if (!state.contains(Properties.HORIZONTAL_FACING)
				|| !state.contains(Properties.DOUBLE_BLOCK_HALF)
				|| !state.contains(Properties.OPEN)) {
			return false;
		}
		return Registries.BLOCK.getId(state.getBlock()).getPath().contains("door");
	}

	private static BlockPos lowerDoorPos(ServerWorld world, BlockPos pos) {
		if (world == null || pos == null) return pos;
		BlockState state = world.getBlockState(pos);
		if (state.contains(Properties.DOUBLE_BLOCK_HALF)
				&& state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return pos.down();
		}
		return pos;
	}

	private static Direction doorwayDirection(ServerPlayerEntity painter, BlockPos lower, Direction facing) {
		if (facing == null || facing.getAxis().isVertical()) facing = painter.getHorizontalFacing();
		Vec3d facingVector = new Vec3d(facing.getOffsetX(), 0.0D, facing.getOffsetZ());
		Vec3d toPlayer = painter.getPos().subtract(Vec3d.ofCenter(lower));
		return toPlayer.dotProduct(facingVector) > 0.0D ? facing.getOpposite() : facing;
	}

	private static Vec3d safeDoorDestination(ServerWorld world, BlockPos lower, Direction through) {
		int[] yOffsets = {0, 1, -1, 2, -2};
		Vec3d best = null;
		int bestScore = Integer.MIN_VALUE;
		double bestTieBreak = Double.MAX_VALUE;
		for (int distance = 1; distance <= 5; distance++) {
			for (int yOffset : yOffsets) {
				BlockPos feet = lower.offset(through, distance).add(0, yOffset, 0);
				if (!isSafeStandPos(world, feet)) continue;
				int score = doorwayStandScore(world, feet, through, distance);
				double tieBreak = Math.abs(distance - DOORWAY_PREFERRED_EXIT_DISTANCE) + Math.abs(yOffset) * 0.25D;
				if (score > bestScore || (score == bestScore && tieBreak < bestTieBreak)) {
					best = Vec3d.ofBottomCenter(feet);
					bestScore = score;
					bestTieBreak = tieBreak;
				}
			}
		}
		return best;
	}

	private static RemoteDoorway alternateDoorDestination(ServerWorld world, BlockPos sourceLower, Direction sourceThrough) {
		if (world == null || sourceLower == null) return null;
		Vec3d sourceCenter = Vec3d.ofCenter(sourceLower);
		List<RemoteDoorway> candidates = new ArrayList<>();
		Set<BlockPos> seenDoors = ConcurrentHashMap.newKeySet();
		for (BlockPos pos : BlockPos.iterateOutwards(sourceLower, 96, 16, 96)) {
			DoorwayTarget target = doorwayTargetAt(world, pos);
			if (target == null || !seenDoors.add(target.lowerPos())) continue;
			if (isDoorwayDisabled(target.lowerPos())) continue;
			if (target.lowerPos().equals(sourceLower)) continue;
			if (Vec3d.ofCenter(target.lowerPos()).squaredDistanceTo(sourceCenter) < 64.0D) continue;

			Vec3d forward = safeDoorDestination(world, target.lowerPos(), target.facing());
			Vec3d backward = safeDoorDestination(world, target.lowerPos(), target.facing().getOpposite());
			addRemoteDoorCandidate(candidates, sourceCenter, sourceThrough, target, target.facing(), forward);
			addRemoteDoorCandidate(candidates, sourceCenter, sourceThrough, target,
				target.facing().getOpposite(), backward);
		}
		if (candidates.isEmpty()) return null;
		candidates.sort(Comparator.comparingInt((RemoteDoorway remote) ->
			doorwayBlockScore(world, remote.lowerPos(), remote.through())).reversed());
		int bestScore = doorwayBlockScore(world, candidates.getFirst().lowerPos(), candidates.getFirst().through());
		List<RemoteDoorway> bestCandidates = candidates.stream()
			.filter(remote -> doorwayBlockScore(world, remote.lowerPos(), remote.through()) >= bestScore - 8)
			.limit(8)
			.toList();
		return bestCandidates.get(ThreadLocalRandom.current().nextInt(bestCandidates.size()));
	}

	private static void addRemoteDoorCandidate(List<RemoteDoorway> candidates, Vec3d sourceCenter,
			Direction sourceThrough, DoorwayTarget target, Direction targetSide, Vec3d destination) {
		if (candidates == null || sourceCenter == null || target == null || targetSide == null || destination == null) return;
		if (destination.squaredDistanceTo(sourceCenter) < 64.0D) return;
		RemoteDoorway remote = new RemoteDoorway(target.lowerPos(), target.facing(), targetSide, destination);
		if (sourceThrough != null && targetSide == sourceThrough.getOpposite()) {
			candidates.add(0, remote);
		} else {
			candidates.add(remote);
		}
	}

	private static boolean isSafeStandPos(ServerWorld world, BlockPos feet) {
		if (world == null || feet == null) return false;
		return !emptyCollision(world, feet.down()) && emptyCollision(world, feet) && emptyCollision(world, feet.up());
	}

	private static boolean isSafePlayerStandPos(ServerWorld world, ServerPlayerEntity player,
			double x, double y, double z) {
		if (world == null || player == null) return false;
		Box current = player.getBoundingBox();
		Box target = current.offset(x - player.getX(), y - player.getY(), z - player.getZ());
		return world.isSpaceEmpty(player, target.contract(1.0E-7D)) && hasValidSupport(world, target);
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
			if (!world.getBlockState(supportPos).getCollisionShape(world, supportPos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static int doorwayBlockScore(ServerWorld world, BlockPos lower, Direction through) {
		if (world == null || lower == null || through == null || through.getAxis().isVertical()) return 0;
		Vec3d destination = safeDoorDestination(world, lower, through);
		if (destination == null) return Integer.MIN_VALUE;
		BlockPos feet = BlockPos.ofFloored(destination);
		int distance = Math.max(1, doorwayForwardDistance(lower, feet, through));
		Direction right = through.rotateYClockwise();
		int score = 60 + doorwayStandScore(world, feet, through, distance);
		for (int forward = 1; forward <= 10; forward++) {
			for (int side = -4; side <= 4; side++) {
				for (int dy = 0; dy <= 3; dy++) {
					BlockPos pos = feet.offset(through, forward).offset(right, side).add(0, dy, 0);
					BlockState state = world.getBlockState(pos);
					boolean empty = state.getCollisionShape(world, pos).isEmpty();
					if (empty) {
						score += forward <= 3 ? 3 : 1;
					} else if (forward <= 2 && Math.abs(side) <= 1 && dy <= 2) {
						score -= 18;
					} else if (forward >= 4 && !state.isAir()) {
						score += 1;
					}
				}
			}
		}
		return score;
	}

	private static int doorwayStandScore(ServerWorld world, BlockPos feet, Direction through, int distance) {
		if (world == null || feet == null || through == null || through.getAxis().isVertical()) return 0;
		Direction right = through.rotateYClockwise();
		int score = Math.max(0, 14 - Math.abs(distance - DOORWAY_PREFERRED_EXIT_DISTANCE) * 5);
		score += hasForwardViewClearance(world, feet, through) ? 90 : -45;
		for (int forward = 1; forward <= 5; forward++) {
			for (int side = -2; side <= 2; side++) {
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos pos = feet.offset(through, forward).offset(right, side).add(0, dy, 0);
					boolean empty = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
					if (empty) {
						score += forward <= 2 ? 4 : 1;
					} else {
						score -= forward <= 2 && Math.abs(side) <= 1 ? 10 : 1;
					}
				}
			}
		}
		return score;
	}

	private static boolean hasForwardViewClearance(ServerWorld world, BlockPos feet, Direction through) {
		if (world == null || feet == null || through == null || through.getAxis().isVertical()) return false;
		for (int forward = 1; forward <= 2; forward++) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos pos = feet.offset(through, forward).add(0, dy, 0);
				if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;
			}
		}
		return true;
	}

	private static int doorwayForwardDistance(BlockPos lower, BlockPos feet, Direction through) {
		return (feet.getX() - lower.getX()) * through.getOffsetX()
			+ (feet.getZ() - lower.getZ()) * through.getOffsetZ();
	}

	private static boolean emptyCollision(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	private static boolean tickDoorways(ServerWorld world, long now) {
		if (activeDoorways.isEmpty()) return false;
		boolean changed = false;
		for (ActiveDoorway doorway : List.copyOf(activeDoorways.values())) {
			if (doorway.untilTick() <= now) {
				activeDoorways.remove(doorway.lowerPos(), doorway);
				removeDoorwayPortal(world, doorway);
				changed = true;
				continue;
			}
			updateImmersiveDoorwayPortal(world, doorway);
			if (doorway.hasImmersivePortal()) continue;
			Box trigger = doorwayTriggerBox(doorway);
			Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.isSpectator()) continue;
				Vec3d eyePos = player.getEyePos();
				double side = doorwaySide(eyePos, center, doorway.through());
				Double previous = doorway.playerSides().put(player.getUuid(), side);
				if (previous == null || previous > -0.02D || side < 0.02D || !player.getBoundingBox().intersects(trigger)) continue;
				crossDoorway(world, doorway, player, eyePos, player.getYaw(), player.getPitch(), false);
			}
		}
		return changed;
	}

	private static void crossDoorway(ServerWorld world, ActiveDoorway doorway, ServerPlayerEntity player,
			Vec3d sourceEyePos, float sourceYaw, float pitch, boolean clientPredicted) {
		if (world == null || doorway == null || player == null || sourceEyePos == null) return;
		float yawDelta = MathHelper.wrapDegrees(
			doorway.destinationThrough().asRotation() - doorway.through().asRotation());
		float yaw = sourceYaw + yawDelta;
		Vec3d velocity = rotateHorizontal(player.getVelocity(), Math.toRadians(yawDelta));
		Vec3d eyeOffset = player.getEyePos().subtract(player.getPos());
		Vec3d visualDestinationEye = pushToDestinationSide(doorway,
			transformedDoorwayPosition(doorway, sourceEyePos, 0.0D), DOORWAY_EXIT_CLEARANCE);
		Vec3d landingTarget = visualDestinationEye.subtract(eyeOffset);
		Vec3d destination = nearestSafeDoorwayDestination(world, player, landingTarget, doorway.destination(),
			doorway.destinationThrough());
		player.teleport(world, destination.x, destination.y, destination.z, yaw, pitch);
		sendDoorwayTransition(player, visualDestinationEye, destination.add(eyeOffset));
		player.setVelocity(velocity);
		player.velocityModified = true;
	}

	private static Map<UUID, Double> seededDoorSides(ServerWorld world, BlockPos lower, Direction facing, Direction through) {
		Map<UUID, Double> sides = new ConcurrentHashMap<>();
		Vec3d center = sourcePortalPlaneCenter(lower, facing, through);
		for (ServerPlayerEntity player : world.getPlayers()) {
			sides.put(player.getUuid(), doorwaySide(player.getEyePos(), center, through));
		}
		return sides;
	}

	private static Box doorwayTriggerBox(ActiveDoorway doorway) {
		Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
		boolean xAxis = doorway.through().getAxis() == Direction.Axis.X;
		double halfWidthX = xAxis ? 0.28D : 0.58D;
		double halfWidthZ = xAxis ? 0.58D : 0.28D;
		return new Box(center.x - halfWidthX, doorway.lowerPos().getY() + 0.02D, center.z - halfWidthZ,
			center.x + halfWidthX, doorway.lowerPos().getY() + 1.98D, center.z + halfWidthZ);
	}

	private static Vec3d doorwayCenter(BlockPos lower, Direction facing) {
		return Vec3d.ofCenter(lower).add(0.0D, 0.50D, 0.0D);
	}

	private static Vec3d animatedDoorwayCenter(ServerWorld world, BlockPos lower, Direction facing) {
		Vec3d center = doorwayCenter(lower, facing);
		if (world == null || lower == null) return center;
		BlockEntity blockEntity = world.getBlockEntity(lower);
		if (blockEntity instanceof DoorBlockEntity door) {
			float openProgress = slideProgress(door);
			Vec3d slide = Vec3d.of(door.getFacing().rotateYClockwise().getVector());
			return center.add(slide.multiply(OPEN_SLIDE_OFFSET * openProgress));
		}
		BlockState state = world.getBlockState(lower);
		if (state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.OPEN) && state.get(DoorBlock.OPEN)
				&& facing != null && !facing.getAxis().isVertical()) {
			return center.add(Vec3d.of(facing.rotateYClockwise().getVector()).multiply(OPEN_SLIDE_OFFSET));
		}
		return center;
	}

	private static Vec3d portalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		Vec3d center = doorwayCenter(lower, facing);
		if (through == null || through.getAxis().isVertical()) return center;
		return center.add(Vec3d.of(through.getVector()).multiply(DESTINATION_VIEW_OFFSET));
	}

	private static Vec3d immersivePortalSourceCenter(BlockPos lower, Direction facing, Direction through) {
		return doorwayCenter(lower, facing);
	}

	private static Vec3d immersivePortalSourceCenter(ServerWorld world, BlockPos lower, Direction facing,
			Direction through) {
		return doorwayCenter(lower, facing);
	}

	private static Vec3d immersivePortalDestinationCenter(RemoteDoorway destination) {
		if (destination == null || destination.lowerPos() == null) return Vec3d.ZERO;
		return portalPlaneCenter(destination.lowerPos(), destination.facing(), destination.through());
	}

	private static Vec3d sourcePortalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		return immersivePortalSourceCenter(lower, facing, through);
	}

	private static void removeDoorwayPortal(ServerWorld world, ActiveDoorway doorway) {
		if (world == null || doorway == null) return;
		PainterImmersivePortalBridge.removeDoorwayPortal(world, doorway.sourceImmersivePortalId());
		PainterImmersivePortalBridge.removeDoorwayPortal(world, doorway.reverseImmersivePortalId());
	}

	private static void removeDoorwaysUsingDestination(ServerWorld world, BlockPos destinationLower) {
		if (world == null || destinationLower == null) return;
		for (ActiveDoorway doorway : List.copyOf(activeDoorways.values())) {
			if (!destinationLower.equals(doorway.destinationLowerPos())) continue;
			if (activeDoorways.remove(doorway.lowerPos(), doorway)) {
				removeDoorwayPortal(world, doorway);
			}
		}
	}

	private static void updateImmersiveDoorwayPortal(ServerWorld world, ActiveDoorway doorway) {
		if (world == null || doorway == null || !doorway.hasImmersivePortal()) return;
		Vec3d sourceCenter = immersivePortalSourceCenter(world, doorway.lowerPos(), doorway.facing(), doorway.through());
		Vec3d destinationCenter = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		PainterImmersivePortalBridge.updateDoorwayPortal(world, doorway.sourceImmersivePortalId(),
			sourceCenter, destinationCenter);
		PainterImmersivePortalBridge.updateDoorwayPortal(world, doorway.reverseImmersivePortalId(),
			destinationCenter, sourceCenter);
	}

	private static float slideProgress(DoorBlockEntity door) {
		boolean open = door.isOpen();
		if (door.state == null || !door.state.isRunning()) return open ? 1.0F : 0.0F;
		float seconds = door.state.getTimeRunning() / 1000.0F;
		float t = (seconds - OPEN_KEYFRAME_START_SECONDS)
			/ (OPEN_KEYFRAME_END_SECONDS - OPEN_KEYFRAME_START_SECONDS);
		float eased = watheDoorEase(t);
		return open ? eased : 1.0F - eased;
	}

	private static float watheDoorEase(float keyframeProgress) {
		float t = Math.max(0.0F, Math.min(1.0F, keyframeProgress));
		return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * t);
	}

	private static double doorwaySide(Vec3d pos, Vec3d center, Direction through) {
		Vec3d normal = new Vec3d(through.getOffsetX(), 0.0D, through.getOffsetZ());
		return pos.subtract(center).dotProduct(normal);
	}

	private static Vec3d transformedDoorwayPosition(ActiveDoorway doorway, Vec3d playerPos, double extraThroughOffset) {
		Vec3d sourceCenter = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
		Vec3d destinationCenter = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		Vec3d relative = playerPos.subtract(sourceCenter);
		Vec3d sourceForward = horizontalVector(doorway.through());
		Vec3d sourceRight = horizontalVector(doorway.through().rotateYClockwise());
		Vec3d destinationForward = horizontalVector(doorway.destinationThrough());
		Vec3d destinationRight = horizontalVector(doorway.destinationThrough().rotateYClockwise());
		double forward = relative.dotProduct(sourceForward) + extraThroughOffset;
		double right = relative.dotProduct(sourceRight);
		return destinationCenter
			.add(destinationForward.multiply(forward))
			.add(destinationRight.multiply(right))
			.add(0.0D, relative.y, 0.0D);
	}

	private static Vec3d pushToDestinationSide(ActiveDoorway doorway, Vec3d pos, double minimumDistance) {
		if (doorway == null || pos == null || doorway.destinationThrough() == null) return pos;
		Vec3d center = portalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		Vec3d normal = Vec3d.of(doorway.destinationThrough().getVector());
		double distance = pos.subtract(center).dotProduct(normal);
		return distance >= minimumDistance
			? pos
			: pos.add(normal.multiply(minimumDistance - distance));
	}

	private static Vec3d rotateHorizontal(Vec3d value, double yawRadians) {
		if (value == null) return Vec3d.ZERO;
		double cos = Math.cos(yawRadians);
		double sin = Math.sin(yawRadians);
		return new Vec3d(value.x * cos - value.z * sin, value.y, value.x * sin + value.z * cos);
	}

	private static Vec3d horizontalVector(Direction direction) {
		if (direction == null || direction.getAxis().isVertical()) return Vec3d.ZERO;
		return new Vec3d(direction.getOffsetX(), 0.0D, direction.getOffsetZ()).normalize();
	}

	private static Vec3d nearestSafeDoorwayDestination(ServerWorld world, ServerPlayerEntity player,
			Vec3d transformed, Vec3d fallback, Direction through) {
		if (world == null || transformed == null) return fallback == null ? Vec3d.ZERO : fallback;
		if (isSafePlayerStandPos(world, player, transformed.x, transformed.y, transformed.z)) return transformed;
		if (fallback != null && isSafePlayerStandPos(world, player, fallback.x, fallback.y, fallback.z)) return fallback;
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Vec3d seed : new Vec3d[] {transformed, fallback}) {
			if (seed == null) continue;
			Vec3d candidate = scanSafeDoorwayDestination(world, player, seed, transformed, through);
			if (candidate == null) continue;
			double distance = candidate.squaredDistanceTo(transformed);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best == null ? (fallback == null ? transformed : fallback) : best;
	}

	private static Vec3d scanSafeDoorwayDestination(ServerWorld world, ServerPlayerEntity player,
			Vec3d seed, Vec3d transformed, Direction through) {
		Direction forward = through == null || through.getAxis().isVertical() ? Direction.NORTH : through;
		Direction right = forward.rotateYClockwise();
		BlockPos base = BlockPos.ofFloored(seed);
		int[] yOffsets = {0, 1, -1, 2, -2};
		int[] sideOffsets = {0, -1, 1, -2, 2};
		Vec3d best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int yOffset : yOffsets) {
			for (int distance = 0; distance <= 4; distance++) {
				for (int side : sideOffsets) {
					BlockPos feet = base.offset(forward, distance).offset(right, side).add(0, yOffset, 0);
					Vec3d candidate = Vec3d.ofBottomCenter(feet);
					if (!isSafePlayerStandPos(world, player, candidate.x, candidate.y, candidate.z)) continue;
					double score = candidate.squaredDistanceTo(transformed);
					if (score < bestDistance) {
						best = candidate;
						bestDistance = score;
					}
				}
			}
		}
		return best;
	}

	private static void sendDoorwayTransition(ServerPlayerEntity player, Vec3d exactDestination, Vec3d destination) {
		if (player == null || exactDestination == null || destination == null) return;
		Vec3d offset = exactDestination.subtract(destination);
		if (offset.lengthSquared() < 0.0009D) return;
		if (!ServerPlayNetworking.canSend(player, PainterDoorwayTransitionPayload.ID)) return;
		ServerPlayNetworking.send(player, new PainterDoorwayTransitionPayload(offset.x, offset.y, offset.z, 8));
	}

	private static void sendReturnParticles(ServerWorld world, UUID painterId, Vec3d origin, int color) {
		if (world == null || painterId == null || origin == null) return;
		ServerPlayerEntity painter = world.getServer().getPlayerManager().getPlayer(painterId);
		if (painter == null || !ServerPlayNetworking.canSend(painter, PainterReturnParticlesPayload.ID)) return;
		ServerPlayNetworking.send(painter, new PainterReturnParticlesPayload(origin.x, origin.y, origin.z, color));
	}

	private static String displayRole(Identifier roleId) {
		if (roleId == null) return "Unknown";
		String[] parts = roleId.getPath().replace('-', '_').split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.isEmpty() ? roleId.toString() : out.toString();
	}

	private static Object component(PlayerBodyEntity body, Identifier id) {
		if (body == null || id == null) return null;
		try {
			ComponentKey<?> key = ComponentRegistry.get(id);
			if (key == null || !key.isProvidedBy(body)) return null;
			return key.getNullable(body);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object kinsComponent(PlayerBodyEntity body) {
		Object component = component(body, KINS_BODY_COMPONENT);
		return component == null ? component(body, KINS_BODY_DEATH_COMPONENT) : component;
	}

	private static Object noelleComponent(PlayerBodyEntity body) {
		return component(body, NOELLE_BODY_COMPONENT);
	}

	private static Identifier readIdentifier(Object component, String fieldName) {
		if (component == null || fieldName == null) return null;
		try {
			Field field = component.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(component);
			if (value instanceof Identifier id) return id;
			if (value instanceof Role role) return role.identifier();
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static void writeIdentifier(Object component, String fieldName, Identifier value) {
		if (component == null || fieldName == null) return;
		try {
			Field field = component.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Class<?> type = field.getType();
			if (Identifier.class.isAssignableFrom(type)) {
				field.set(component, value);
			} else if (Role.class.isAssignableFrom(type)) {
				field.set(component, roleById(value));
			}
			syncComponent(component);
		} catch (Throwable ignored) {
		}
	}

	private static void syncComponent(Object component) {
		if (component == null) return;
		try {
			Method sync = component.getClass().getMethod("sync");
			sync.invoke(component);
		} catch (Throwable ignored) {
		}
	}

	private record BodyPaint(UUID bodyId, UUID bodyOwnerId, UUID painterId, Identifier fakeDeathReason,
			Identifier fakeRoleId, int color, long untilTick, long totalTicks, BodySnapshot original) {
		private boolean active(long now) {
			return untilTick > now;
		}

		private void restore(ServerWorld world) {
			restoreAndPosition(world);
		}

		private Vec3d restoreAndPosition(ServerWorld world) {
			if (world == null) return null;
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY,
					entity -> bodyId.equals(entity.getUuid()))) {
				Vec3d origin = body.getPos().add(0.0D, 0.8D, 0.0D);
				original.restore(body);
				return origin;
			}
			return null;
		}
	}

	private record LivingPaint(UUID playerId, UUID painterId, Identifier fakeRoleId, int color,
			long untilTick, long totalTicks) {
		private boolean active(long now) {
			return untilTick > now;
		}
	}

	private record DoorwayTarget(BlockPos lowerPos, Direction facing) {}

	public record DoorwayToggleResult(BlockPos lowerPos, boolean disabled, boolean found) {}

	private record RemoteDoorway(BlockPos lowerPos, Direction facing, Direction through, Vec3d destination) {}

	private record ActiveDoorway(UUID painterId, BlockPos lowerPos, Direction facing, Direction through,
			BlockPos destinationLowerPos, Direction destinationFacing, Direction destinationThrough,
			Vec3d destination, long untilTick, UUID sourceImmersivePortalId, UUID reverseImmersivePortalId,
			Map<UUID, Double> playerSides) {
		private boolean hasImmersivePortal() {
			return sourceImmersivePortalId != null || reverseImmersivePortalId != null;
		}
	}

	private record BodySnapshot(boolean kinsPresent, Identifier kinsDeathReason,
			boolean noellePresent, Identifier noelleDeathReason, Identifier noelleRoleId) {
		private static BodySnapshot capture(PlayerBodyEntity body) {
			Object kins = kinsComponent(body);
			Object noelle = noelleComponent(body);
			return new BodySnapshot(kins != null, readIdentifier(kins, "deathReason"),
				noelle != null, readIdentifier(noelle, "deathReason"), readIdentifier(noelle, "playerRole"));
		}

		private void applyFake(PlayerBodyEntity body, Identifier fakeDeathReason, Identifier fakeRoleId) {
			Object kins = kinsComponent(body);
			if (kins != null) writeIdentifier(kins, "deathReason", fakeDeathReason);
			Object noelle = noelleComponent(body);
			if (noelle != null) {
				writeIdentifier(noelle, "deathReason", fakeDeathReason);
				writeIdentifier(noelle, "playerRole", fakeRoleId);
			}
		}

		private void restore(PlayerBodyEntity body) {
			if (kinsPresent) writeIdentifier(kinsComponent(body), "deathReason", kinsDeathReason);
			if (noellePresent) {
				Object noelle = noelleComponent(body);
				writeIdentifier(noelle, "deathReason", noelleDeathReason);
				writeIdentifier(noelle, "playerRole", noelleRoleId);
			}
		}
	}
}
