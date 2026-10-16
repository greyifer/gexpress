package dev.mapselect.role.rat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.MapSelect;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.GexpressWorlds;
import dev.mapselect.game.KinsWatheSafePreparation;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.game.WatheStaminaAccess;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.rat.RatInfectPayload;
import dev.mapselect.network.role.rat.RatHuntPayload;
import dev.mapselect.network.role.rat.RatInvisibilityPayload;
import dev.mapselect.network.role.rat.RatPlayDeadPayload;
import dev.mapselect.network.role.rat.RatStatePayload;
import dev.mapselect.network.role.rat.RatVisibilityPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RatManager {
	public static final int INFECT_COOLDOWN_TICKS = 25 * 20;
	public static final int INVISIBILITY_MAX_TICKS = 10 * 20;
	public static final int INVISIBILITY_RECHARGE_TICKS = 60 * 20;
	public static final int HUNT_DURATION_TICKS = 30 * 20;
	public static final int PLAY_DEAD_DURATION_TICKS = 10 * 20;
	public static final double INFECT_RANGE = 3.0D;
	private static final int INVISIBILITY_RECHARGE_STEP =
		Math.max(1, INVISIBILITY_RECHARGE_TICKS / INVISIBILITY_MAX_TICKS);
	private static final Identifier SCALE_MODIFIER_ID = Identifier.of(MapSelect.MOD_ID, "rat_scale");
	private static final Identifier BODY_SCALE_MODIFIER_ID = Identifier.of(MapSelect.MOD_ID, "rat_body_scale");
	private static final Identifier NOELLES_TINY_ID = Identifier.of("noellesroles", "tiny");
	private static final Identifier NOELLES_TINY_SCALE_ID = Identifier.of("noellesroles", "tiny_modifier");
	private static final double SCALE_REDUCTION = -0.67D;
	private static final Map<UUID, Set<UUID>> INFECTED_BY_RAT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> INFECT_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> INVISIBILITY_CHARGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> RECHARGE_PROGRESS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HUNT_END_TICK = new ConcurrentHashMap<>();
	private static final Set<UUID> MANUAL_INVISIBILITY = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> MANAGED_VISIBILITY = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> SCALED_PLAYERS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Double> RAT_SCALE_BY_PLAYER = new ConcurrentHashMap<>();
	private static final Map<UUID, PlayDeadState> PLAYING_DEAD = new ConcurrentHashMap<>();

	private RatManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(RatInfectPayload.ID, RatInfectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RatInvisibilityPayload.ID, RatInvisibilityPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RatPlayDeadPayload.ID, RatPlayDeadPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RatStatePayload.ID, RatStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RatVisibilityPayload.ID, RatVisibilityPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RatHuntPayload.ID, RatHuntPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RatInfectPayload.ID,
			(payload, context) -> context.server().execute(() -> tryInfect(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(RatInvisibilityPayload.ID,
			(payload, context) -> context.server().execute(() -> toggleInvisibility(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(RatPlayDeadPayload.ID,
			(payload, context) -> context.server().execute(() -> togglePlayDead(context.player())));

		ServerTickEvents.END_WORLD_TICK.register(RatManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncJoiningPlayer(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.player != null) {
				MANUAL_INVISIBILITY.remove(handler.player.getUuid());
				MANAGED_VISIBILITY.remove(handler.player.getUuid());
				stopPlayingDead(handler.player, false);
			}
		});
		ModdedRoleAssigned.EVENT.register((player, role) -> {
			if (isRatRole(role)) initializeRat(player);
		});
		ModdedRoleRemoved.EVENT.register((player, role) -> {
			if (isRatRole(role)) {
				clearPlayer(player);
				if (player != null) RAT_SCALE_BY_PLAYER.remove(player.getUuid());
			}
		});
		RoundLifecycleHooks.clearWorld(RatManager::clearWorld);
	}

	private static void tryInfect(ServerPlayerEntity rat) {
		if (!canUse(rat)) return;
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(rat);
		long cooldown = creativeBypass ? 0L : AbilityCooldowns.remaining(INFECT_COOLDOWN_UNTIL, rat);
		if (!creativeBypass && cooldown > 0L) {
			AbilityCooldownSync.send(rat, AbilityCooldownPayload.RAT_INFECT, cooldown,
				INFECT_COOLDOWN_TICKS, false);
			rat.sendMessage(Text.literal("Infect ready in "
				+ Math.max(1L, AbilityCooldowns.secondsCeil(cooldown)) + "s.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(rat, rat.getServerWorld().getPlayers(),
			INFECT_RANGE, 0.15D, true, candidate -> !PelicanManager.isStashed(candidate)
				&& DeadPlayerStatus.isLivingRoundParticipant(candidate));
		if (target == null) {
			rat.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(rat,
				rat.getServerWorld().getPlayers(), INFECT_RANGE, 0.15D, true,
				candidate -> !PelicanManager.isStashed(candidate)
					&& DeadPlayerStatus.isLivingRoundParticipant(candidate), "infect")), true);
			return;
		}
		Set<UUID> infected = INFECTED_BY_RAT.computeIfAbsent(rat.getUuid(), ignored -> ConcurrentHashMap.newKeySet());
		if (infected.contains(target.getUuid())) {
			rat.sendMessage(Text.literal(target.getName().getString() + " is already infected.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(rat, target)) return;
		infected.add(target.getUuid());
		rat.sendMessage(Text.literal("Infected " + target.getName().getString() + ".")
			.formatted(Formatting.GOLD), true);
		AbilitySounds.playTo(rat, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
			SoundCategory.PLAYERS, 0.75F, 1.35F);
		AbilitySounds.playTo(target, SoundEvents.ENTITY_SILVERFISH_HURT,
			SoundCategory.PLAYERS, 0.35F, 0.75F);
		if (creativeBypass) {
			AbilityCooldownSync.clear(rat, AbilityCooldownPayload.RAT_INFECT);
		} else {
			AbilityCooldowns.start(INFECT_COOLDOWN_UNTIL, rat, INFECT_COOLDOWN_TICKS);
			AbilityCooldownSync.send(rat, AbilityCooldownPayload.RAT_INFECT,
				INFECT_COOLDOWN_TICKS, INFECT_COOLDOWN_TICKS, false);
		}
		syncRat(rat);
		tryStartHunt(rat);
	}

	private static void toggleInvisibility(ServerPlayerEntity rat) {
		if (!canUse(rat) || KinsWatheSafePreparation.isActive(rat.getWorld())) return;
		UUID ratId = rat.getUuid();
		if (MANUAL_INVISIBILITY.remove(ratId)) {
			rat.sendMessage(Text.literal("Invisibility disabled.").formatted(Formatting.GRAY), true);
		} else {
			int charge = INVISIBILITY_CHARGE.getOrDefault(ratId, INVISIBILITY_MAX_TICKS);
			if (charge <= 0) {
				rat.sendMessage(Text.literal("Invisibility is still recharging.")
					.formatted(Formatting.GRAY), true);
				return;
			}
			MANUAL_INVISIBILITY.add(ratId);
			rat.sendMessage(Text.literal("Invisibility enabled.").formatted(Formatting.GRAY), true);
		}
		updateVisibility(rat, MANUAL_INVISIBILITY.contains(ratId));
		syncRat(rat);
	}

	private static void togglePlayDead(ServerPlayerEntity rat) {
		if (rat == null) return;
		if (PLAYING_DEAD.containsKey(rat.getUuid())) {
			stopPlayingDead(rat, true);
			return;
		}
		if (!canUse(rat)) return;

		PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(rat.getServerWorld());
		if (body == null) {
			rat.sendMessage(Text.literal("Could not create a body here.").formatted(Formatting.RED), true);
			return;
		}

		Vec3d anchor = rat.getPos();
		body.setPlayerUuid(rat.getUuid());
		body.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, rat.getYaw(), rat.getPitch());
		body.setHeadYaw(rat.getHeadYaw());
		body.setVelocity(Vec3d.ZERO);
		applyBodyScale(body, RAT_SCALE_BY_PLAYER.getOrDefault(rat.getUuid(),
			rat.getAttributeValue(EntityAttributes.GENERIC_SCALE)));
		if (!rat.getServerWorld().spawnEntity(body)) {
			rat.sendMessage(Text.literal("Could not play dead here.").formatted(Formatting.RED), true);
			return;
		}

		MANUAL_INVISIBILITY.remove(rat.getUuid());
		PLAYING_DEAD.put(rat.getUuid(), new PlayDeadState(body.getUuid(), anchor, rat.getYaw(), rat.getPitch(),
			rat.getServerWorld().getTime() + PLAY_DEAD_DURATION_TICKS));
		rat.setSprinting(false);
		rat.setVelocity(Vec3d.ZERO);
		updateVisibility(rat, true);
		AbilitySounds.playTo(rat, SoundEvents.ENTITY_PLAYER_HURT,
			SoundCategory.PLAYERS, 0.55F, 0.82F);
		rat.sendMessage(Text.literal("Playing dead for up to 10 seconds. Press Tertiary Ability to stand up early.")
			.formatted(Formatting.GRAY), true);
		syncRat(rat);
	}

	private static void tick(ServerWorld world) {
		if (!GexpressWorlds.isGameWorld(world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		boolean safePreparation = KinsWatheSafePreparation.isActive(world);
		Set<UUID> invisibleRats = new HashSet<>();

		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean testing = GexpressTestState.isRoleTester(player);
			boolean ratRole = isRat(player);
			if (ratRole) {
				removeTinyModifier(player);
				rememberRatScale(player);
			}
			boolean activeRat = ratRole && (activeGame || testing)
				&& (DeadPlayerStatus.isLivingRoundParticipant(player) || testing);
			if (!activeRat) {
				clearInactivePlayer(player);
				continue;
			}

			applyScale(player);
			regenerateExtraStamina(player);
			UUID ratId = player.getUuid();
			PlayDeadState playDead = PLAYING_DEAD.get(ratId);
			if (playDead != null && world.getTime() >= playDead.endTick()) {
				stopPlayingDead(player, true);
				playDead = null;
			} else if (playDead != null && !maintainPlayDead(player, playDead)) {
				PLAYING_DEAD.remove(ratId);
				playDead = null;
			}
			int charge = INVISIBILITY_CHARGE.computeIfAbsent(ratId, ignored -> INVISIBILITY_MAX_TICKS);
			boolean manual = MANUAL_INVISIBILITY.contains(ratId);
			if (!safePreparation) {
				if (manual) {
					charge = Math.max(0, charge - 1);
					RECHARGE_PROGRESS.put(ratId, 0);
					if (charge <= 0) {
						MANUAL_INVISIBILITY.remove(ratId);
						manual = false;
						player.sendMessage(Text.literal("Invisibility depleted.")
							.formatted(Formatting.GRAY), true);
					}
				} else if (charge < INVISIBILITY_MAX_TICKS) {
					int progress = RECHARGE_PROGRESS.getOrDefault(ratId, 0) + 1;
					if (progress >= INVISIBILITY_RECHARGE_STEP) {
						charge++;
						progress = 0;
					}
					RECHARGE_PROGRESS.put(ratId, progress);
				}
			}
			INVISIBILITY_CHARGE.put(ratId, charge);
			boolean invisible = safePreparation || manual || playDead != null;
			updateVisibility(player, invisible);
			if (invisible) invisibleRats.add(ratId);
			syncRat(player);
			if (!testing) tryStartHunt(player);
		}

		syncRatBodyScales(world);
		syncVisibility(world, invisibleRats);
		tickHunts(world, game, activeGame);
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			return false;
		}
		List<ServerPlayerEntity> aliveRats = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival)
			.stream().filter(RatManager::isRat).toList();
		if (aliveRats.isEmpty()) return false;
		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else {
			for (ServerPlayerEntity rat : aliveRats) {
				tryStartHunt(rat);
				Long endTick = HUNT_END_TICK.get(rat.getUuid());
				if (endTick == null || world.getTime() < endTick) continue;
				game.setLooseEndWinner(rat.getUuid());
				NeutralWinManager.announce(world, rat, "announcement.win.gexpress.rat",
					MapSelectRoles.RAT == null ? 0x8A7256 : MapSelectRoles.RAT.color());
				winStatus = GameFunctions.WinStatus.LOOSE_END;
				break;
			}
		}
		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	public static boolean isRat(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return isRatRole(role);
	}

	public static boolean isPlayingDead(PlayerEntity player) {
		return player != null && PLAYING_DEAD.containsKey(player.getUuid());
	}

	public static void killWithTrap(ServerPlayerEntity rat) {
		if (rat == null || !isRat(rat) || !DeadPlayerStatus.isLivingRoundParticipant(rat)) return;
		MANUAL_INVISIBILITY.remove(rat.getUuid());
		updateVisibility(rat, false);
		GameFunctions.killPlayer(rat, true, null, GameConstants.DeathReasons.GENERIC);
	}

	public static long reduceInfectCooldown(ServerPlayerEntity player, long ticks) {
		long next = AbilityCooldowns.reduce(INFECT_COOLDOWN_UNTIL, player, ticks);
		if (next <= 0L) {
			if (player != null) AbilityCooldownSync.clear(player, AbilityCooldownPayload.RAT_INFECT);
		} else {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.RAT_INFECT, next,
				INFECT_COOLDOWN_TICKS, false);
		}
		return next;
	}

	private static boolean canUse(ServerPlayerEntity player) {
		if (player == null || PelicanManager.isStashed(player) || !isRat(player)
				|| isPlayingDead(player)
				|| KinsWatheSafePreparation.isActive(player.getWorld())) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return ((game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
				&& DeadPlayerStatus.isLivingRoundParticipant(player))
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean hasMetWinCondition(ServerPlayerEntity rat) {
		Set<UUID> infected = INFECTED_BY_RAT.getOrDefault(rat.getUuid(), Set.of());
		for (ServerPlayerEntity player : rat.getServerWorld().getPlayers()) {
			if (player.getUuid().equals(rat.getUuid())) continue;
			if (DeadPlayerStatus.isLivingRoundParticipant(player) && !infected.contains(player.getUuid())) {
				return false;
			}
		}
		return true;
	}

	private static void tryStartHunt(ServerPlayerEntity rat) {
		if (rat == null || GexpressTestState.isRoleTester(rat) || !hasMetWinCondition(rat)
				|| HUNT_END_TICK.containsKey(rat.getUuid())) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(rat.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		HUNT_END_TICK.put(rat.getUuid(), rat.getServerWorld().getTime() + HUNT_DURATION_TICKS);
		syncHunts(rat.getServerWorld());
	}

	private static void tickHunts(ServerWorld world, GameWorldComponent game, boolean activeGame) {
		if (world == null) return;
		if (!activeGame || game == null) {
			if (!HUNT_END_TICK.isEmpty()) {
				HUNT_END_TICK.clear();
				syncHunts(world);
			}
			return;
		}

		HUNT_END_TICK.entrySet().removeIf(entry -> {
			ServerPlayerEntity rat = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			return rat == null || rat.getServerWorld() != world || !isRat(rat)
				|| !DeadPlayerStatus.isLivingRoundParticipant(rat);
		});

		for (Map.Entry<UUID, Long> entry : HUNT_END_TICK.entrySet()) {
			if (world.getTime() < entry.getValue()) continue;
			ServerPlayerEntity rat = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (rat == null || rat.getServerWorld() != world || !isRat(rat)
					|| !DeadPlayerStatus.isLivingRoundParticipant(rat)) continue;
			finishHunt(rat, game);
			return;
		}
		syncHunts(world);
	}

	private static void finishHunt(ServerPlayerEntity rat, GameWorldComponent game) {
		if (rat == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		game.setLooseEndWinner(rat.getUuid());
		NeutralWinManager.announce(rat.getServerWorld(), rat, "announcement.win.gexpress.rat",
			MapSelectRoles.RAT == null ? 0x8A7256 : MapSelectRoles.RAT.color());
		GameRoundEndComponent.KEY.get(rat.getWorld()).setRoundEndData(rat.getServerWorld().getPlayers(),
			GameFunctions.WinStatus.LOOSE_END);
		GameFunctions.stopGame(rat.getServerWorld());
	}

	private static void syncRat(ServerPlayerEntity rat) {
		if (rat == null) return;
		Set<UUID> infected = INFECTED_BY_RAT.getOrDefault(rat.getUuid(), Set.of());
		int required = 0;
		int infectedLiving = 0;
		for (ServerPlayerEntity player : rat.getServerWorld().getPlayers()) {
			if (player.getUuid().equals(rat.getUuid())
					|| !DeadPlayerStatus.isLivingRoundParticipant(player)) continue;
			required++;
			if (infected.contains(player.getUuid())) infectedLiving++;
		}
		int charge = INVISIBILITY_CHARGE.getOrDefault(rat.getUuid(), INVISIBILITY_MAX_TICKS);
		PayloadSync.sendIfChanged(rat, RatStatePayload.ID, new RatStatePayload(true, charge,
			INVISIBILITY_MAX_TICKS, MANAGED_VISIBILITY.contains(rat.getUuid()), isPlayingDead(rat),
			infectedLiving, required,
			new ArrayList<>(infected)));
	}

	private static void syncVisibility(ServerWorld world, Set<UUID> invisibleRats) {
		RatVisibilityPayload payload = new RatVisibilityPayload(new ArrayList<>(invisibleRats));
		for (ServerPlayerEntity player : world.getPlayers()) {
			PayloadSync.sendIfChanged(player, RatVisibilityPayload.ID, payload);
		}
	}

	private static void syncHunts(ServerWorld world) {
		if (world == null) return;
		List<RatHuntPayload.Hunt> hunts = HUNT_END_TICK.entrySet().stream()
			.filter(entry -> {
				ServerPlayerEntity rat = world.getServer().getPlayerManager().getPlayer(entry.getKey());
				return rat != null && rat.getServerWorld() == world && isRat(rat)
					&& DeadPlayerStatus.isLivingRoundParticipant(rat);
			})
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new RatHuntPayload.Hunt(entry.getKey(), entry.getValue()))
			.toList();
		RatHuntPayload payload = new RatHuntPayload(hunts);
		for (ServerPlayerEntity player : world.getPlayers()) {
			PayloadSync.sendIfChanged(player, RatHuntPayload.ID, payload);
		}
	}

	private static void syncJoiningPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		if (isRat(player)) {
			syncRat(player);
			long cooldown = AbilityCooldowns.remaining(INFECT_COOLDOWN_UNTIL, player);
			if (cooldown > 0L) {
				AbilityCooldownSync.send(player, AbilityCooldownPayload.RAT_INFECT, cooldown,
					INFECT_COOLDOWN_TICKS, false);
			} else {
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.RAT_INFECT);
			}
		}
		Set<UUID> invisibleRats = new HashSet<>();
		for (ServerPlayerEntity candidate : player.getServerWorld().getPlayers()) {
			if (MANAGED_VISIBILITY.contains(candidate.getUuid()) && isRat(candidate)) {
				invisibleRats.add(candidate.getUuid());
			}
		}
		PayloadSync.sendIfChanged(player, RatVisibilityPayload.ID, new RatVisibilityPayload(
			new ArrayList<>(invisibleRats)));
		syncHunts(player.getServerWorld());
	}

	private static void initializeRat(PlayerEntity player) {
		if (player == null) return;
		if (player instanceof ServerPlayerEntity serverPlayer) removeTinyModifier(serverPlayer);
		INVISIBILITY_CHARGE.put(player.getUuid(), INVISIBILITY_MAX_TICKS);
		RECHARGE_PROGRESS.put(player.getUuid(), 0);
		INFECTED_BY_RAT.computeIfAbsent(player.getUuid(), ignored -> ConcurrentHashMap.newKeySet());
		applyScale(player);
		if (player instanceof ServerPlayerEntity serverPlayer) syncRat(serverPlayer);
	}

	private static void removeTinyModifier(ServerPlayerEntity rat) {
		if (rat == null) return;
		if (ModifierUtils.has(rat, NOELLES_TINY_ID)) {
			for (Modifier modifier : HMLModifiers.MODIFIERS) {
				if (modifier != null && NOELLES_TINY_ID.equals(modifier.identifier())) {
					ModifierUtils.removeIfPresent(rat, modifier);
					break;
				}
			}
		}
		EntityAttributeInstance scale = rat.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale != null && scale.hasModifier(NOELLES_TINY_SCALE_ID)) {
			scale.removeModifier(NOELLES_TINY_SCALE_ID);
			recalculateDimensions(rat);
		}
	}

	private static void applyScale(PlayerEntity player) {
		if (player == null) return;
		EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale == null || scale.hasModifier(SCALE_MODIFIER_ID)) {
			if (scale != null) {
				SCALED_PLAYERS.add(player.getUuid());
				RAT_SCALE_BY_PLAYER.put(player.getUuid(), scale.getValue());
			}
			return;
		}
		scale.addPersistentModifier(new EntityAttributeModifier(SCALE_MODIFIER_ID, SCALE_REDUCTION,
			EntityAttributeModifier.Operation.ADD_VALUE));
		SCALED_PLAYERS.add(player.getUuid());
		RAT_SCALE_BY_PLAYER.put(player.getUuid(), scale.getValue());
		recalculateDimensions(player);
	}

	private static void rememberRatScale(PlayerEntity player) {
		if (player == null) return;
		EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale != null && scale.hasModifier(SCALE_MODIFIER_ID)) {
			RAT_SCALE_BY_PLAYER.put(player.getUuid(), scale.getValue());
		}
	}

	private static void removeScale(PlayerEntity player) {
		if (player == null) return;
		EntityAttributeInstance scale = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale != null && scale.hasModifier(SCALE_MODIFIER_ID)) {
			scale.removeModifier(SCALE_MODIFIER_ID);
			recalculateDimensions(player);
		}
		SCALED_PLAYERS.remove(player.getUuid());
	}

	private static void updateVisibility(PlayerEntity player, boolean invisible) {
		if (player == null) return;
		UUID id = player.getUuid();
		if (invisible) MANAGED_VISIBILITY.add(id);
		else MANAGED_VISIBILITY.remove(id);
		if (player.isInvisible() != invisible) player.setInvisible(invisible);
	}

	private static void clearInactivePlayer(ServerPlayerEntity player) {
		if (player == null) return;
		UUID id = player.getUuid();
		boolean hadState = INFECTED_BY_RAT.containsKey(id) || INVISIBILITY_CHARGE.containsKey(id)
			|| SCALED_PLAYERS.contains(id) || MANAGED_VISIBILITY.contains(id)
			|| HUNT_END_TICK.containsKey(id);
		if (!hadState) return;
		clearPlayer(player);
	}

	private static void clearPlayer(PlayerEntity player) {
		if (player == null) return;
		UUID id = player.getUuid();
		INFECTED_BY_RAT.remove(id);
		INFECT_COOLDOWN_UNTIL.remove(id);
		INVISIBILITY_CHARGE.remove(id);
		RECHARGE_PROGRESS.remove(id);
		HUNT_END_TICK.remove(id);
		MANUAL_INVISIBILITY.remove(id);
		if (player instanceof ServerPlayerEntity serverPlayer) stopPlayingDead(serverPlayer, false);
		else PLAYING_DEAD.remove(id);
		if (MANAGED_VISIBILITY.remove(id) && player.isInvisible()) player.setInvisible(false);
		removeScale(player);
		if (player instanceof ServerPlayerEntity serverPlayer) {
			PayloadSync.sendIfChanged(serverPlayer, RatStatePayload.ID, RatStatePayload.clear());
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.RAT_INFECT);
		}
	}

	private static void clearWorld(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) clearPlayer(player);
			syncVisibility(serverWorld, Set.of());
			HUNT_END_TICK.clear();
			syncHunts(serverWorld);
		}
		INFECTED_BY_RAT.clear();
		INFECT_COOLDOWN_UNTIL.clear();
		INVISIBILITY_CHARGE.clear();
		RECHARGE_PROGRESS.clear();
		HUNT_END_TICK.clear();
		MANUAL_INVISIBILITY.clear();
		MANAGED_VISIBILITY.clear();
		SCALED_PLAYERS.clear();
		RAT_SCALE_BY_PLAYER.clear();
		PLAYING_DEAD.clear();
		PayloadSync.forgetAll(RatStatePayload.ID);
		PayloadSync.forgetAll(RatVisibilityPayload.ID);
		PayloadSync.forgetAll(RatHuntPayload.ID);
	}

	private static void recalculateDimensions(PlayerEntity player) {
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(),
			player.getYaw(), player.getPitch());
	}

	private static void regenerateExtraStamina(PlayerEntity player) {
		if (player == null || player.isSprinting() || isPlayingDead(player)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		if (!isRatRole(role) || role.getMaxSprintTime() <= 0) return;
		float stamina = WatheStaminaAccess.get(player);
		if (stamina < 0.0F || stamina >= role.getMaxSprintTime()) return;
		// Wathe restores 0.25 per tick. This matching extra step makes Rat regeneration exactly 2x.
		WatheStaminaAccess.set(player, Math.min(role.getMaxSprintTime(), stamina + 0.25F));
	}

	private static boolean maintainPlayDead(ServerPlayerEntity rat, PlayDeadState state) {
		if (rat == null || state == null) return false;
		if (!(rat.getServerWorld().getEntity(state.bodyId()) instanceof PlayerBodyEntity body)) {
			updateVisibility(rat, false);
			rat.sendMessage(Text.literal("Your disguise was disturbed.").formatted(Formatting.GRAY), true);
			syncRat(rat);
			return false;
		}
		rat.setSprinting(false);
		rat.setVelocity(Vec3d.ZERO);
		if (rat.getPos().squaredDistanceTo(state.anchor()) > 0.0004D) {
			rat.teleport(rat.getServerWorld(), state.anchor().x, state.anchor().y, state.anchor().z,
				state.yaw(), state.pitch());
		}
		return true;
	}

	private static void stopPlayingDead(ServerPlayerEntity rat, boolean notify) {
		if (rat == null) return;
		PlayDeadState state = PLAYING_DEAD.remove(rat.getUuid());
		if (state == null) return;
		if (rat.getServerWorld().getEntity(state.bodyId()) instanceof PlayerBodyEntity body) {
			body.discard();
		}
		boolean invisible = KinsWatheSafePreparation.isActive(rat.getWorld())
			|| MANUAL_INVISIBILITY.contains(rat.getUuid());
		updateVisibility(rat, invisible);
		if (notify) {
			AbilitySounds.playTo(rat, SoundEvents.ENTITY_PLAYER_BREATH,
				SoundCategory.PLAYERS, 0.45F, 1.15F);
			rat.sendMessage(Text.literal("You stand back up.").formatted(Formatting.GRAY), true);
			syncRat(rat);
		}
	}

	private static void syncRatBodyScales(ServerWorld world) {
		if (world == null) return;
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			UUID playerId = body.getPlayerUuid();
			Double targetScale = RAT_SCALE_BY_PLAYER.get(playerId);
			ServerPlayerEntity owner = playerId == null
				? null : world.getServer().getPlayerManager().getPlayer(playerId);
			if (owner != null && owner.getWorld() == world && isRat(owner)) {
				EntityAttributeInstance ownerScale = owner.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
				if (ownerScale != null && ownerScale.hasModifier(SCALE_MODIFIER_ID)) {
					targetScale = ownerScale.getValue();
					RAT_SCALE_BY_PLAYER.put(playerId, targetScale);
				} else if (targetScale == null) {
					targetScale = 1.0D + SCALE_REDUCTION;
				}
			}
			applyBodyScale(body, targetScale);
		}
	}

	private static void applyBodyScale(PlayerBodyEntity body, Double targetScale) {
		if (body == null) return;
		EntityAttributeInstance scale = body.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale == null) return;
		double target = targetScale == null ? Double.NaN : targetScale;
		if (Double.isFinite(target) && target > 0.0D
				&& scale.hasModifier(BODY_SCALE_MODIFIER_ID)
				&& Math.abs(scale.getValue() - target) < 0.0001D) {
			return;
		}
		boolean changed = scale.hasModifier(BODY_SCALE_MODIFIER_ID);
		if (changed) scale.removeModifier(BODY_SCALE_MODIFIER_ID);
		if (Double.isFinite(target) && target > 0.0D) {
			double unscaled = scale.getValue();
			if (unscaled > 0.0D && Math.abs(unscaled - target) >= 0.0001D) {
				scale.addPersistentModifier(new EntityAttributeModifier(BODY_SCALE_MODIFIER_ID,
					target / unscaled - 1.0D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				changed = true;
			}
		}
		if (changed) body.calculateDimensions();
	}

	private static boolean isRatRole(Role role) {
		return role != null && role.identifier() != null && MapSelectRoles.RAT_ID.equals(role.identifier());
	}

	private record PlayDeadState(UUID bodyId, Vec3d anchor, float yaw, float pitch, long endTick) {}
}
