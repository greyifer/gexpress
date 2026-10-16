package dev.mapselect.role.doomsayer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.GexpressWorlds;
import dev.mapselect.game.KinsWatheSafePreparation;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.doomsayer.DoomsayerGuessPayload;
import dev.mapselect.network.role.doomsayer.DoomsayerStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.pelican.PelicanManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DoomsayerManager {
	private static final Map<UUID, Integer> CORRECT_GUESSES = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<UUID>> CORRECTLY_GUESSED_TARGETS = new ConcurrentHashMap<>();

	private DoomsayerManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(DoomsayerGuessPayload.ID, DoomsayerGuessPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DoomsayerStatePayload.ID, DoomsayerStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DoomsayerGuessPayload.ID,
			(payload, context) -> context.server().execute(() -> tryGuess(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(DoomsayerManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		ModdedRoleAssigned.EVENT.register((player, role) -> {
			if (!isDoomsayerRole(role)) return;
			CORRECT_GUESSES.put(player.getUuid(), 0);
			CORRECTLY_GUESSED_TARGETS.put(player.getUuid(), ConcurrentHashMap.newKeySet());
			if (player instanceof ServerPlayerEntity serverPlayer) sync(serverPlayer);
		});
		ModdedRoleRemoved.EVENT.register((player, role) -> {
			if (!isDoomsayerRole(role)) return;
			clearPlayer(player.getUuid());
			if (player instanceof ServerPlayerEntity serverPlayer) {
				PayloadSync.sendIfChanged(serverPlayer, DoomsayerStatePayload.ID, DoomsayerStatePayload.clear());
			}
		});
		RoundLifecycleHooks.clearWorld(world -> clearWorld());
	}

	private static void tryGuess(ServerPlayerEntity doomsayer, DoomsayerGuessPayload payload) {
		if (!canGuess(doomsayer) || payload == null || payload.targetId() == null) return;
		ServerWorld world = doomsayer.getServerWorld();
		ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(payload.targetId());
		if (target == null || target.getServerWorld() != world
				|| !isEligibleGuessTarget(doomsayer, target)) return;

		Role guessedRole = findGuessableRole(payload.rolePath());
		if (guessedRole == null) {
			doomsayer.sendMessage(Text.literal("Choose a role from the Doomsayer list.")
				.formatted(Formatting.RED), true);
			return;
		}

		Set<UUID> correctlyGuessed = CORRECTLY_GUESSED_TARGETS.computeIfAbsent(doomsayer.getUuid(),
			id -> ConcurrentHashMap.newKeySet());
		if (correctlyGuessed.contains(target.getUuid())) {
			doomsayer.sendMessage(Text.literal("You already fulfilled "
				+ target.getName().getString() + "'s prophecy.")
				.formatted(Formatting.GRAY), true);
			return;
		}

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		Role actualRole = game == null ? null : game.getRole(target);
		boolean correct = actualRole != null && actualRole.identifier() != null
			&& actualRole.identifier().getPath().equalsIgnoreCase(guessedRole.identifier().getPath());
		if (correct) {
			// A player becomes unavailable only once their role was actually named correctly.
			if (!correctlyGuessed.add(target.getUuid())) return;
			int goal = GexpressConfig.getDoomsayerGuessesToWin();
			int progress = CORRECT_GUESSES.compute(doomsayer.getUuid(),
				(id, value) -> clampProgress((value == null ? 0 : value) + 1, goal));
			doomsayer.sendMessage(Text.literal("Correct: " + target.getName().getString() + " was "
				+ roleName(actualRole) + ".  " + progress + "/" + goal)
				.formatted(Formatting.GREEN), true);
			if (progress >= goal) {
				win(doomsayer, game);
				return;
			}
			sync(doomsayer);
			return;
		}

		if (GexpressConfig.doesDoomsayerDieOnWrongGuess()) {
			doomsayer.sendMessage(Text.literal("Your prophecy was wrong.").formatted(Formatting.DARK_RED), true);
			GameFunctions.killPlayer(doomsayer, true, null, GameConstants.DeathReasons.GENERIC);
			return;
		}

		int goal = GexpressConfig.getDoomsayerGuessesToWin();
		int progress = CORRECT_GUESSES.compute(doomsayer.getUuid(),
			(id, value) -> clampProgress((value == null ? 0 : value) - 1, goal));
		doomsayer.sendMessage(Text.literal("Wrong. Your prophecy falls to " + progress + "/"
			+ goal + ".").formatted(Formatting.RED), true);
		sync(doomsayer);
	}

	private static void tick(ServerWorld world) {
		if (!GexpressWorlds.isGameWorld(world) || world.getTime() % 10L != 0L) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isDoomsayer(player) && (DeadPlayerStatus.isLivingRoundParticipant(player)
					|| dev.mapselect.testing.GexpressTestState.isRoleTester(player))) {
				int goal = GexpressConfig.getDoomsayerGuessesToWin();
				int progress = CORRECT_GUESSES.compute(player.getUuid(),
					(id, value) -> clampProgress(value == null ? 0 : value, goal));
				GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
				if (progress >= goal && game != null
						&& game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
					win(player, game);
					return;
				}
				sync(player);
			} else {
				PayloadSync.sendIfChanged(player, DoomsayerStatePayload.ID, DoomsayerStatePayload.clear());
			}
		}
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			return false;
		}
		List<ServerPlayerEntity> alive = world.getPlayers(player ->
			isDoomsayer(player) && DeadPlayerStatus.isLivingRoundParticipant(player));
		if (alive.isEmpty()) return false;
		PassiveMoney.grant(world, game);
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), GameFunctions.WinStatus.TIME);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	public static boolean isDoomsayer(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return game != null && isDoomsayerRole(game.getRole(player));
	}

	private static boolean canGuess(ServerPlayerEntity player) {
		if (player == null || !isDoomsayer(player) || PelicanManager.isStashed(player)
				|| KinsWatheSafePreparation.isActive(player.getWorld())) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
			&& DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static Role findGuessableRole(String raw) {
		String path = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
		if (path.isEmpty()) return null;
		for (Role role : WatheRoles.ROLES) {
			if (!isGuessableRole(role) || !role.identifier().getPath().equalsIgnoreCase(path)) continue;
			return role;
		}
		return null;
	}

	public static boolean isGuessableRole(Role role) {
		return role != null && role.identifier() != null && !Harpymodloader.SPECIAL_ROLES.contains(role);
	}

	private static void sync(ServerPlayerEntity doomsayer) {
		if (doomsayer == null || !ServerPlayNetworking.canSend(doomsayer, DoomsayerStatePayload.ID)) return;
		if (!isDoomsayer(doomsayer)) {
			PayloadSync.sendIfChanged(doomsayer, DoomsayerStatePayload.ID, DoomsayerStatePayload.clear());
			return;
		}
		Set<UUID> correctlyGuessed = CORRECTLY_GUESSED_TARGETS.computeIfAbsent(doomsayer.getUuid(),
			id -> ConcurrentHashMap.newKeySet());
		List<DoomsayerStatePayload.Target> targets = doomsayer.getServerWorld().getPlayers().stream()
			.filter(player -> isEligibleGuessTarget(doomsayer, player)
				&& !correctlyGuessed.contains(player.getUuid()))
			.sorted(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
			.map(player -> new DoomsayerStatePayload.Target(player.getUuid(), player.getName().getString()))
			.toList();
		int goal = GexpressConfig.getDoomsayerGuessesToWin();
		int progress = clampProgress(CORRECT_GUESSES.getOrDefault(doomsayer.getUuid(), 0), goal);
		CORRECT_GUESSES.put(doomsayer.getUuid(), progress);
		PayloadSync.sendIfChanged(doomsayer, DoomsayerStatePayload.ID,
			new DoomsayerStatePayload(true, progress, goal, targets));
	}

	private static void win(ServerPlayerEntity doomsayer, GameWorldComponent game) {
		if (doomsayer == null || game == null
				|| game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		ServerWorld world = doomsayer.getServerWorld();
		game.setLooseEndWinner(doomsayer.getUuid());
		NeutralWinManager.announce(world, doomsayer, "announcement.win.gexpress.doomsayer",
			MapSelectRoles.DOOMSAYER == null ? 0x18C978 : MapSelectRoles.DOOMSAYER.color());
		GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), GameFunctions.WinStatus.LOOSE_END);
		GameFunctions.stopGame(world);
	}

	private static String roleName(Role role) {
		if (role == null || role.identifier() == null) return "Unknown";
		String path = role.identifier().getPath().replace('-', '_');
		StringBuilder out = new StringBuilder();
		for (String part : path.split("_+")) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	private static int clampProgress(int progress, int goal) {
		return Math.max(0, Math.min(Math.max(1, goal), progress));
	}

	private static boolean isDoomsayerRole(Role role) {
		return role != null && MapSelectRoles.DOOMSAYER_ID.equals(role.identifier());
	}

	private static boolean isEligibleGuessTarget(ServerPlayerEntity doomsayer,
			ServerPlayerEntity target) {
		// Alignment is intentionally irrelevant: civilians, killers, Mafia, Covenant, and every
		// neutral role are all valid prophecies. Only the Doomsayer themself and non-living players
		// are excluded.
		return target != null && target != doomsayer
			&& DeadPlayerStatus.isLivingRoundParticipant(target);
	}

	private static void clearPlayer(UUID playerId) {
		if (playerId == null) return;
		CORRECT_GUESSES.remove(playerId);
		CORRECTLY_GUESSED_TARGETS.remove(playerId);
	}

	private static void clearWorld() {
		CORRECT_GUESSES.clear();
		CORRECTLY_GUESSED_TARGETS.clear();
		PayloadSync.forgetAll(DoomsayerStatePayload.ID);
	}
}
