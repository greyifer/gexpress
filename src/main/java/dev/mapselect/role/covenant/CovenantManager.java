package dev.mapselect.role.covenant;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.covenant.CovenantBatMovementPayload;
import dev.mapselect.network.role.covenant.CovenantBatPayload;
import dev.mapselect.network.role.covenant.CovenantBatRenderPayload;
import dev.mapselect.network.role.covenant.CovenantBitePayload;
import dev.mapselect.network.role.covenant.CovenantStatePayload;
import dev.mapselect.network.role.covenant.CovenantTurnPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.RoleAvailability;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovenantManager {
	private static final int BLOOD_MAX = 20 * 180;
	private static final int BLOOD_FILL_PER_BITE = BLOOD_MAX / 2;
	private static final double DRACULA_DRAIN_PER_TICK = 1.0D;
	private static final double VAMPIRE_DRAIN_PER_TICK = 2.0D;
	private static final double ORPHAN_VAMPIRE_DRAIN_MULTIPLIER = 2.0D;
	private static final double DRACULA_VAMPIRE_DRAIN_MULTIPLIER = 1.05D;
	private static final int TURN_MIN_BLOOD = (int) Math.ceil(BLOOD_MAX * 0.75D);
	private static final int TURN_RESULT_BLOOD = (int) Math.round(BLOOD_MAX * 0.20D);
	private static final int SUCCESSION_FEEDS_REQUIRED = 3;
	private static final int BAT_MAX = 20 * 10;
	private static final float BAT_FLY_SPEED = 0.10F;
	private static final double BAT_HORIZONTAL_SPEED = 0.45D;
	private static final double BAT_VERTICAL_SPEED = 0.34D;
	private static final double BITE_RANGE = 3.0D;
	private static final Map<UUID, Integer> bloodByPlayer = new HashMap<>();
	private static final Map<UUID, Double> bloodDrainRemainder = new HashMap<>();
	private static final Map<UUID, Integer> orphanFeedCounts = new HashMap<>();
	private static final Map<UUID, Integer> batTicksByPlayer = new HashMap<>();
	private static final Map<UUID, BatState> batStates = new HashMap<>();
	private static final Map<UUID, BatInput> batInputs = new HashMap<>();
	private static final Map<UUID, Long> biteCooldownUntil = new HashMap<>();
	private static int syncTicker;

	private CovenantManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CovenantBitePayload.ID, CovenantBitePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CovenantBatPayload.ID, CovenantBatPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CovenantBatMovementPayload.ID, CovenantBatMovementPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CovenantTurnPayload.ID, CovenantTurnPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CovenantStatePayload.ID, CovenantStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CovenantBatRenderPayload.ID, CovenantBatRenderPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CovenantBitePayload.ID,
			(payload, context) -> context.server().execute(() -> tryBite(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(CovenantBatPayload.ID,
			(payload, context) -> context.server().execute(() -> toggleBat(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(CovenantBatMovementPayload.ID,
			(payload, context) -> context.server().execute(() -> updateBatInput(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(CovenantTurnPayload.ID,
			(payload, context) -> context.server().execute(() -> tryTurn(context.player())));
		AllowPlayerDeath.EVENT.register(CovenantManager::allowCovenantDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
			if (!(entity instanceof ServerPlayerEntity target)
					|| !(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
				return true;
			}
			return !areCovenantTeammates(target, attacker);
		});
		ServerTickEvents.END_WORLD_TICK.register(CovenantManager::tick);
		RoundLifecycleHooks.clearWorld(CovenantManager::clearRound);
	}

	public static void reduceCooldown(ServerPlayerEntity player, long ticks) {
		long next = AbilityCooldowns.reduce(biteCooldownUntil, player, ticks);
		if (player != null && ticks > 0L) syncBiteCooldown(player, next);
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearRound(world);
			return;
		}

		boolean livingDracula = hasLivingDracula(world);
		long livingVampires = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival).stream()
			.filter(player -> roleKind(player) == RoleKind.VAMPIRE)
			.count();
		for (ServerPlayerEntity player : world.getPlayers()) {
			RoleKind kind = roleKind(player);
			if (kind == RoleKind.NONE || PelicanManager.isStashed(player) || !isPlayable(player, player)) {
				boolean hadState = hasState(player.getUuid());
				clearPlayer(player, true);
				if (hadState) sendState(player, CovenantStatePayload.clear());
				continue;
			}

			UUID id = player.getUuid();
			int blood = bloodByPlayer.getOrDefault(id, BLOOD_MAX);
			if (GexpressTestState.hasCreativeAbilityBypass(player)) {
				blood = BLOOD_MAX;
			} else {
				double drainRate = kind == RoleKind.DRACULA
					? DRACULA_DRAIN_PER_TICK * Math.pow(DRACULA_VAMPIRE_DRAIN_MULTIPLIER, livingVampires)
					: VAMPIRE_DRAIN_PER_TICK * (livingDracula ? 1.0D : ORPHAN_VAMPIRE_DRAIN_MULTIPLIER);
				double accumulated = bloodDrainRemainder.getOrDefault(id, 0.0D) + drainRate;
				int wholeDrain = (int) Math.floor(accumulated);
				bloodDrainRemainder.put(id, accumulated - wholeDrain);
				blood = Math.max(0, blood - wholeDrain);
			}
			bloodByPlayer.put(id, blood);

			if (blood <= 0) {
				clearPlayer(player, true);
				GameFunctions.killPlayer(player, true, null, GameConstants.DeathReasons.GENERIC);
				continue;
			}

			if (kind == RoleKind.DRACULA) {
				tickBat(player);
			} else {
				endBat(player);
			}
		}

		if (++syncTicker >= 5) {
			syncTicker = 0;
			syncAll(world);
		}
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		List<ServerPlayerEntity> covenant = alive.stream().filter(CovenantManager::isCovenant).toList();
		if (covenant.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == covenant.size()) {
			ServerPlayerEntity winner = covenant.stream().filter(CovenantManager::isDracula).findFirst()
				.orElse(covenant.getFirst());
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.covenant",
				MapSelectRoles.DRACULA == null ? 0xB81832 : MapSelectRoles.DRACULA.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static void tryBite(ServerPlayerEntity user) {
		if (user == null || user.getWorld().isClient) return;
		RoleKind kind = roleKind(user);
		if (kind == RoleKind.NONE || PelicanManager.isStashed(user) || !canUseHere(user.getWorld(), user)
				|| !isPlayable(user, user)) {
			return;
		}
		long remaining = AbilityCooldowns.remaining(biteCooldownUntil, user);
		if (remaining > 0L && !GexpressTestState.hasCreativeAbilityBypass(user)) {
			syncBiteCooldown(user, remaining);
			return;
		}
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(user, user.getServerWorld().getPlayers(),
			BITE_RANGE, 0.0D, true, candidate -> candidate != user
				&& !PelicanManager.isStashed(candidate)
				&& !isCovenant(candidate)
				&& isPlayable(candidate, user));
		if (target == null) {
			user.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(user, user.getServerWorld().getPlayers(),
				BITE_RANGE, 0.0D, true, candidate -> candidate != user
					&& !PelicanManager.isStashed(candidate)
					&& !isCovenant(candidate)
					&& isPlayable(candidate, user), "bite")).formatted(Formatting.RED), true);
			return;
		}

		if (VeteranManager.interceptAbilityInteraction(user, target)) return;
		GameFunctions.killPlayer(target, true, user, GameConstants.DeathReasons.KNIFE);
		SpyManager.recordInteraction(user, target);
		setBiteCooldown(user);
		if (!GameFunctions.isPlayerAliveAndSurvival(target)) {
			fillBlood(user);
			if (kind == RoleKind.VAMPIRE) recordOrphanFeed(user);
		}
		sync(user);
	}

	private static void tryTurn(ServerPlayerEntity dracula) {
		if (dracula == null || !isDracula(dracula) || PelicanManager.isStashed(dracula)
				|| !canUseHere(dracula.getWorld(), dracula) || !isPlayable(dracula, dracula)
				|| batStates.containsKey(dracula.getUuid())) {
			return;
		}
		if (!RoleAvailability.isRuntimeEnabled(MapSelectRoles.VAMPIRE)) {
			dracula.sendMessage(Text.literal("Vampire is disabled for this game.")
				.formatted(Formatting.RED), true);
			return;
		}
		int blood = bloodByPlayer.getOrDefault(dracula.getUuid(), BLOOD_MAX);
		if (blood < TURN_MIN_BLOOD && !GexpressTestState.hasCreativeAbilityBypass(dracula)) {
			dracula.sendMessage(Text.literal("You need at least 75% Blood to create a Vampire.")
				.formatted(Formatting.RED), true);
			return;
		}
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(dracula,
			dracula.getServerWorld().getPlayers(), BITE_RANGE, 0.0D, true,
			candidate -> candidate != dracula && !PelicanManager.isStashed(candidate)
				&& !isCovenant(candidate) && isPlayable(candidate, dracula));
		if (target == null) {
			dracula.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(dracula,
				dracula.getServerWorld().getPlayers(), BITE_RANGE, 0.0D, true,
				candidate -> candidate != dracula && !PelicanManager.isStashed(candidate)
					&& !isCovenant(candidate) && isPlayable(candidate, dracula), "turn"))
				.formatted(Formatting.RED), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(dracula, target)) return;
		assignRole(target, MapSelectRoles.VAMPIRE);
		bloodByPlayer.put(target.getUuid(), BLOOD_MAX);
		bloodDrainRemainder.remove(target.getUuid());
		orphanFeedCounts.remove(target.getUuid());
		bloodByPlayer.put(dracula.getUuid(), TURN_RESULT_BLOOD);
		bloodDrainRemainder.remove(dracula.getUuid());
		dracula.sendMessage(Text.literal("You turned " + target.getName().getString() + " into a Vampire.")
			.formatted(Formatting.DARK_RED), true);
		target.sendMessage(Text.literal("Dracula turned you into a Vampire.")
			.formatted(Formatting.DARK_RED), true);
		SpyManager.recordInteraction(dracula, target);
		setBiteCooldown(dracula);
		sync(dracula);
		sync(target);
	}

	private static void toggleBat(ServerPlayerEntity player) {
		if (player == null || !isDracula(player) || PelicanManager.isStashed(player)
				|| !canUseHere(player.getWorld(), player) || !isPlayable(player, player)) {
			return;
		}
		if (batStates.containsKey(player.getUuid())) {
			endBat(player);
			sync(player);
			syncBatRender(player.getServerWorld());
			return;
		}
		int ticks = batTicksByPlayer.getOrDefault(player.getUuid(), BAT_MAX);
		if (ticks <= 0) {
			player.sendMessage(Text.literal("Bat form is drained.").formatted(Formatting.RED), true);
			return;
		}
		startBat(player);
		sync(player);
		syncBatRender(player.getServerWorld());
	}

	private static void assignRole(ServerPlayerEntity target, Role nextRole) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(target.getWorld());
		if (game == null || nextRole == null) return;
		Role previous = game.getRole(target);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(target, previous);
		}
		game.addRole(target, nextRole);
		if (!Harpymodloader.VANNILA_ROLES.contains(nextRole)) {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(target, nextRole);
		}
		game.sync();
	}

	private static void recordOrphanFeed(ServerPlayerEntity vampire) {
		if (vampire == null || hasLivingDracula(vampire.getServerWorld())) return;
		int feeds = orphanFeedCounts.merge(vampire.getUuid(), 1, Integer::sum);
		if (feeds < SUCCESSION_FEEDS_REQUIRED) {
			vampire.sendMessage(Text.literal("Dracula succession: " + feeds + "/"
				+ SUCCESSION_FEEDS_REQUIRED + " feeds.").formatted(Formatting.DARK_RED), true);
			return;
		}
		assignRole(vampire, MapSelectRoles.DRACULA);
		orphanFeedCounts.clear();
		bloodDrainRemainder.remove(vampire.getUuid());
		vampire.sendMessage(Text.literal("The Covenant recognizes you as the new Dracula.")
			.formatted(Formatting.DARK_RED), false);
		sync(vampire);
	}

	private static void tickBat(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		BatState state = batStates.get(id);
		int ticks = batTicksByPlayer.getOrDefault(id, BAT_MAX);
		if (state != null) {
			ticks = GexpressTestState.hasCreativeAbilityBypass(player) ? BAT_MAX : Math.max(0, ticks - 1);
			batTicksByPlayer.put(id, ticks);
			if (!player.getAbilities().allowFlying || !player.getAbilities().flying) {
				player.getAbilities().allowFlying = true;
				player.getAbilities().flying = true;
				player.sendAbilitiesUpdate();
			}
			if (Math.abs(player.getAbilities().getFlySpeed() - BAT_FLY_SPEED) > 0.0001F) {
				player.getAbilities().setFlySpeed(BAT_FLY_SPEED);
				player.sendAbilitiesUpdate();
			}
			player.fallDistance = 0.0F;
			applyBatMovement(player);
			if (ticks <= 0) endBat(player);
		} else if (ticks < BAT_MAX) {
			batTicksByPlayer.put(id, Math.min(BAT_MAX, ticks + 1));
		}
	}

	private static void startBat(ServerPlayerEntity player) {
		BatState state = new BatState(player.getAbilities().allowFlying, player.getAbilities().flying,
			player.isInvisible(), player.getAbilities().getFlySpeed());
		batStates.put(player.getUuid(), state);
		player.getAbilities().allowFlying = true;
		player.getAbilities().flying = true;
		player.getAbilities().setFlySpeed(BAT_FLY_SPEED);
		player.fallDistance = 0.0F;
		player.sendAbilitiesUpdate();
	}

	private static void endBat(ServerPlayerEntity player) {
		if (player == null) return;
		BatState state = batStates.remove(player.getUuid());
		batInputs.remove(player.getUuid());
		if (state == null) return;
		player.setInvisible(state.invisible());
		player.getAbilities().allowFlying = state.allowFlying();
		player.getAbilities().flying = state.flying();
		player.getAbilities().setFlySpeed(state.flySpeed());
		player.fallDistance = 0.0F;
		player.sendAbilitiesUpdate();
	}

	private static void fillBlood(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		int blood = bloodByPlayer.getOrDefault(id, BLOOD_MAX);
		bloodByPlayer.put(id, Math.min(BLOOD_MAX, blood + BLOOD_FILL_PER_BITE));
	}

	private static void setBiteCooldown(ServerPlayerEntity player) {
		if (player == null || GexpressTestState.hasCreativeAbilityBypass(player)) return;
		long total = GexpressConfig.getCovenantBiteCooldownSeconds() * 20L;
		if (total <= 0L) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.COVENANT_BITE);
			return;
		}
		AbilityCooldowns.start(biteCooldownUntil, player, total);
		syncBiteCooldown(player, total);
	}

	private static void updateBatInput(ServerPlayerEntity player, CovenantBatMovementPayload payload) {
		if (player == null || payload == null || !batStates.containsKey(player.getUuid()) || !isDracula(player)) return;
		batInputs.put(player.getUuid(), new BatInput(payload.sideways(), payload.forward(), payload.ascending(),
			payload.descending(), payload.sprinting(), player.getServerWorld().getTime()));
	}

	private static void applyBatMovement(ServerPlayerEntity player) {
		BatInput input = batInputs.get(player.getUuid());
		if (input == null || player.getServerWorld().getTime() - input.updatedAt() > 5L) return;
		Vec3d velocity = player.getVelocity();
		double targetY = input.ascending() == input.descending() ? 0.0D
			: input.ascending() ? BAT_VERTICAL_SPEED : -BAT_VERTICAL_SPEED;
		double inputLength = Math.sqrt(input.sideways() * input.sideways() + input.forward() * input.forward());
		if (inputLength < 0.001D) {
			player.setVelocity(velocity.x * 0.72D,
				MathHelper.lerp(0.58D, velocity.y, targetY), velocity.z * 0.72D);
			return;
		}
		double normalizedSideways = input.sideways() / inputLength;
		double normalizedForward = input.forward() / inputLength;
		double yaw = Math.toRadians(player.getYaw());
		double directionX = normalizedSideways * Math.cos(yaw) - normalizedForward * Math.sin(yaw);
		double directionZ = normalizedForward * Math.cos(yaw) + normalizedSideways * Math.sin(yaw);
		double speed = BAT_HORIZONTAL_SPEED * (input.sprinting() ? 1.18D : 1.0D);
		player.setVelocity(
			MathHelper.lerp(0.48D, velocity.x, directionX * speed),
			MathHelper.lerp(0.58D, velocity.y, targetY),
			MathHelper.lerp(0.48D, velocity.z, directionZ * speed)
		);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		if (GexpressTestState.isRoleTester(user)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	public static boolean isCovenant(PlayerEntity player) {
		RoleKind kind = roleKind(player);
		return kind == RoleKind.DRACULA || kind == RoleKind.VAMPIRE;
	}

	public static boolean isDracula(PlayerEntity player) {
		return roleKind(player) == RoleKind.DRACULA;
	}

	private static boolean hasLivingDracula(ServerWorld world) {
		return world != null && world.getPlayers(GameFunctions::isPlayerAliveAndSurvival).stream()
			.anyMatch(CovenantManager::isDracula);
	}

	private static boolean areCovenantTeammates(PlayerEntity first, PlayerEntity second) {
		return first != null && second != null && first != second
			&& first.getWorld() == second.getWorld() && isCovenant(first) && isCovenant(second);
	}

	private static boolean allowCovenantDeath(PlayerEntity victim, PlayerEntity killer,
			net.minecraft.util.Identifier reason) {
		if (!areCovenantTeammates(victim, killer)) return true;
		if (killer instanceof ServerPlayerEntity attacker) {
			attacker.sendMessage(Text.literal("You cannot kill another Covenant member.")
				.formatted(Formatting.RED), true);
		}
		return false;
	}

	private static RoleKind roleKind(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return RoleKind.NONE;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return RoleKind.NONE;
		Role role = game.getRole(player);
		if (role == null || role.identifier() == null) return RoleKind.NONE;
		if (MapSelectRoles.DRACULA_ID.equals(role.identifier())
				|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.DRACULA_ID)) {
			return RoleKind.DRACULA;
		}
		if (MapSelectRoles.VAMPIRE_ID.equals(role.identifier())
				|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.VAMPIRE_ID)) {
			return RoleKind.VAMPIRE;
		}
		return RoleKind.NONE;
	}

	private static void syncAll(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) sync(player);
		syncBatRender(world);
	}

	private static void sync(ServerPlayerEntity player) {
		RoleKind kind = roleKind(player);
		if (kind == RoleKind.NONE || PelicanManager.isStashed(player)) {
			sendState(player, CovenantStatePayload.clear());
			return;
		}
		UUID id = player.getUuid();
		syncBiteCooldown(player, AbilityCooldowns.remaining(biteCooldownUntil, player));
		sendState(player, new CovenantStatePayload(true, kind == RoleKind.DRACULA,
			bloodByPlayer.getOrDefault(id, BLOOD_MAX), BLOOD_MAX,
			batTicksByPlayer.getOrDefault(id, BAT_MAX), BAT_MAX, batStates.containsKey(id)));
	}

	private static void syncBiteCooldown(ServerPlayerEntity player, long remainingTicks) {
		if (player == null) return;
		if (remainingTicks > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.COVENANT_BITE, remainingTicks,
				(long) GexpressConfig.getCovenantBiteCooldownSeconds() * 20L, false);
		} else {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.COVENANT_BITE);
		}
	}

	private static void sendState(ServerPlayerEntity player, CovenantStatePayload payload) {
		PayloadSync.sendIfChanged(player, CovenantStatePayload.ID, payload);
	}

	private static void syncBatRender(ServerWorld world) {
		if (world == null) return;
		CovenantBatRenderPayload payload = new CovenantBatRenderPayload(activeBatPlayers());
		for (ServerPlayerEntity player : world.getPlayers()) {
			PayloadSync.sendIfChanged(player, CovenantBatRenderPayload.ID, payload);
		}
	}

	private static List<UUID> activeBatPlayers() {
		List<UUID> ids = new ArrayList<>(batStates.keySet());
		ids.sort(Comparator.comparing(UUID::toString));
		return ids;
	}

	private static void clearPlayer(ServerPlayerEntity player, boolean restoreBat) {
		if (player == null) return;
		if (restoreBat) endBat(player);
		bloodByPlayer.remove(player.getUuid());
		bloodDrainRemainder.remove(player.getUuid());
		orphanFeedCounts.remove(player.getUuid());
		batTicksByPlayer.remove(player.getUuid());
		batInputs.remove(player.getUuid());
		biteCooldownUntil.remove(player.getUuid());
	}

	private static boolean hasState(UUID playerId) {
		return playerId != null && (bloodByPlayer.containsKey(playerId)
			|| batTicksByPlayer.containsKey(playerId)
			|| batStates.containsKey(playerId));
	}

	private static void clearRound(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				clearPlayer(player, true);
				sendState(player, CovenantStatePayload.clear());
			}
		}
		bloodByPlayer.clear();
		bloodDrainRemainder.clear();
		orphanFeedCounts.clear();
		batTicksByPlayer.clear();
		batStates.clear();
		batInputs.clear();
		biteCooldownUntil.clear();
		syncTicker = 0;
		if (world instanceof ServerWorld serverWorld) syncBatRender(serverWorld);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(Map.copyOf(bloodByPlayer), Map.copyOf(bloodDrainRemainder),
			Map.copyOf(orphanFeedCounts), Map.copyOf(batTicksByPlayer), Set.copyOf(batStates.keySet()));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		if (world != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				endBat(player);
			}
		}
		bloodByPlayer.clear();
		bloodDrainRemainder.clear();
		orphanFeedCounts.clear();
		batTicksByPlayer.clear();
		batStates.clear();
		biteCooldownUntil.clear();
		if (state != null) {
			bloodByPlayer.putAll(state.bloodByPlayer());
			bloodDrainRemainder.putAll(state.bloodDrainRemainder());
			orphanFeedCounts.putAll(state.orphanFeedCounts());
			batTicksByPlayer.putAll(state.batTicksByPlayer());
			for (UUID playerId : state.activeBatPlayers()) {
				if (world == null) continue;
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
				if (player == null || player.getWorld() != world) continue;
				startBat(player);
			}
		}
		if (world != null) syncAll(world);
	}

	public record TimeState(Map<UUID, Integer> bloodByPlayer, Map<UUID, Double> bloodDrainRemainder,
	                        Map<UUID, Integer> orphanFeedCounts, Map<UUID, Integer> batTicksByPlayer,
	                        Set<UUID> activeBatPlayers) {}

	private enum RoleKind {
		NONE,
		DRACULA,
		VAMPIRE
	}

	private record BatState(boolean allowFlying, boolean flying, boolean invisible, float flySpeed) {}
	private record BatInput(float sideways, float forward, boolean ascending, boolean descending,
	                        boolean sprinting, long updatedAt) {}
}
