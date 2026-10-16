package dev.mapselect.role.painter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.painter.PainterBodyUsePayload;
import dev.mapselect.network.role.painter.PainterChoiceOpenPayload;
import dev.mapselect.network.role.painter.PainterChoiceSubmitPayload;
import dev.mapselect.network.role.painter.PainterPlayerUsePayload;
import dev.mapselect.network.role.painter.PainterReturnParticlesPayload;
import dev.mapselect.network.role.painter.PainterStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.copycat.CopycatManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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
	private static final Identifier KINS_BODY_COMPONENT = Identifier.of("kinswathe", "body");
	private static final Identifier KINS_BODY_DEATH_COMPONENT = Identifier.of("kinswathe", "body_death_reason");
	private static final Identifier NOELLE_BODY_COMPONENT = Identifier.of("noellesroles", "body_death_reason");
	private static final Map<UUID, BodyPaint> bodyPaints = new ConcurrentHashMap<>();
	private static final Map<UUID, LivingPaint> livingPaints = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> bodyCooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> playerCooldownUntil = new ConcurrentHashMap<>();
	private static final Set<String> compatDebugLogs = ConcurrentHashMap.newKeySet();
	private static int syncTick;

	private PainterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PainterBodyUsePayload.ID, PainterBodyUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterPlayerUsePayload.ID, PainterPlayerUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PainterChoiceSubmitPayload.ID, PainterChoiceSubmitPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterStatePayload.ID, PainterStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterChoiceOpenPayload.ID, PainterChoiceOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PainterReturnParticlesPayload.ID, PainterReturnParticlesPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PainterBodyUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryOpenPaintPicker(context.player(), null)));
		ServerPlayNetworking.registerGlobalReceiver(PainterPlayerUsePayload.ID,
			(payload, context) -> context.server().execute(() ->
				tryOpenPaintPicker(context.player(), payload == null ? null : payload.targetId())));
		ServerPlayNetworking.registerGlobalReceiver(PainterChoiceSubmitPayload.ID,
			(payload, context) -> context.server().execute(() -> submitPaintChoice(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(PainterManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		RoundLifecycleHooks.clearWorld(PainterManager::clear);
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
		if (VeteranManager.interceptAbilityInteraction(painter, target)) return;
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
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		long now = world.getTime();
		boolean changed = false;
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
		if (changed || (++syncTick >= 10 && (!bodyPaints.isEmpty() || !livingPaints.isEmpty()))) {
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
			PayloadSync.forgetAll(PainterStatePayload.ID);
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_BODY);
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.PAINTER_PLAYER);
			}
			broadcast(serverWorld);
		} else {
			bodyPaints.clear();
			livingPaints.clear();
			bodyCooldownUntil.clear();
			playerCooldownUntil.clear();
			PayloadSync.forgetAll(PainterStatePayload.ID);
		}
	}

	private static void sync(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		PayloadSync.sendIfChanged(player, PainterStatePayload.ID, isKillerCohort(player)
			? payloadFor(world)
			: PainterStatePayload.clear());
		syncCooldowns(player);
	}

	private static void broadcast(ServerWorld world) {
		if (world == null) return;
		PainterStatePayload payload = payloadFor(world);
		for (ServerPlayerEntity player : world.getPlayers()) {
			PayloadSync.sendIfChanged(player, PainterStatePayload.ID,
				isKillerCohort(player) ? payload : PainterStatePayload.clear());
		}
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
		return new PainterStatePayload(bodyEntries, playerEntries);
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
		return AbilityCooldowns.remaining(cooldowns, player);
	}

	private static void startCooldown(ServerPlayerEntity painter, Map<UUID, Long> cooldowns, String key, long ticks) {
		if (painter == null || cooldowns == null) return;
		if (ticks <= 0L || GexpressTestState.hasCreativeAbilityBypass(painter)) {
			cooldowns.remove(painter.getUuid());
			AbilityCooldownSync.clear(painter, key);
			return;
		}
		AbilityCooldowns.start(cooldowns, painter, ticks);
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
		AbilityCooldowns.start(bodyCooldownUntil, painter, ticks);
		AbilityCooldowns.start(playerCooldownUntil, painter, ticks);
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
		} catch (Throwable error) {
			debugCompatOnce("refreshRoles", "Painter could not refresh Harpymodloader roles.", error);
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
		} catch (Throwable error) {
			debugCompatOnce("component:" + id, "Painter could not read optional body component " + id + ".", error);
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
		} catch (Throwable error) {
			debugCompatOnce("read:" + component.getClass().getName() + ":" + fieldName,
				"Painter could not read optional body role field " + fieldName + ".", error);
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
		} catch (Throwable error) {
			debugCompatOnce("write:" + component.getClass().getName() + ":" + fieldName,
				"Painter could not write optional body role field " + fieldName + ".", error);
		}
	}

	private static void syncComponent(Object component) {
		if (component == null) return;
		try {
			Method sync = component.getClass().getMethod("sync");
			sync.invoke(component);
		} catch (Throwable error) {
			debugCompatOnce("sync:" + component.getClass().getName(),
				"Painter could not sync optional body component " + component.getClass().getName() + ".", error);
		}
	}

	private static void debugCompatOnce(String key, String message, Throwable error) {
		if (key == null || !compatDebugLogs.add(key)) return;
		MapSelect.LOGGER.debug(message, error);
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
