package dev.mapselect.modifier;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.modifier.LoversStatePayload;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.role.RoleTeams;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LoversManager {
	private static final double LOVE_AT_FIRST_SIGHT_RANGE_SQUARED = 25.0D;
	private static final Map<UUID, UUID> PARTNERS = new HashMap<>();
	private static final Map<UUID, UUID> PENDING_SOUL_DEATHS = new HashMap<>();
	private static final Set<UUID> SOUL_LINK_DEATHS = new HashSet<>();
	private static final Set<UUID> RUNTIME_MODIFIERS = new HashSet<>();
	private static final Set<UUID> DISCOVERED_LOVERS = new HashSet<>();
	private static int syncTicker;

	private LoversManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(LoversStatePayload.ID, LoversStatePayload.CODEC);
		GameEvents.ON_FINISH_INITIALIZE.register(LoversManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world, true));
		AllowPlayerDeath.EVENT.register(LoversManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(LoversManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
	}

	public static boolean createPair(ServerPlayerEntity actor, ServerPlayerEntity first, ServerPlayerEntity second) {
		if (first == null || second == null || first == second || first.getWorld() != second.getWorld()) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(first.getWorld());
		if (game == null || game.getRole(first) == null || game.getRole(second) == null) return false;
		if (!GexpressConfig.canLoversPairAcrossSides()
				&& !RoleTeams.compatibleLovers(game, first, second, false)) {
			if (actor != null) {
				actor.sendMessage(Text.literal("Those two cannot be linked by the current Lovers rules.")
					.formatted(Formatting.RED), true);
			}
			return false;
		}
		if (isLinked(first.getUuid()) || isLinked(second.getUuid())) {
			if (actor != null) {
				actor.sendMessage(Text.literal("One of those players is already in love.").formatted(Formatting.RED), true);
			}
			return false;
		}
		link(first, second, true);
		if (actor != null) {
			actor.sendMessage(Text.literal(first.getName().getString() + " and "
				+ second.getName().getString() + " are now lovers.").formatted(Formatting.LIGHT_PURPLE), true);
		}
		return true;
	}

	public static int alivePairCount(ServerWorld world) {
		if (world == null) return 0;
		Set<UUID> counted = new HashSet<>();
		int count = 0;
		for (Map.Entry<UUID, UUID> entry : PARTNERS.entrySet()) {
			UUID firstId = entry.getKey();
			UUID secondId = entry.getValue();
			if (firstId == null || secondId == null || counted.contains(firstId) || counted.contains(secondId)) continue;
			if (!isValidPair(firstId, secondId)) continue;
			ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
			ServerPlayerEntity second = world.getServer().getPlayerManager().getPlayer(secondId);
			if (isLiving(first) && isLiving(second)) {
				count++;
				counted.add(firstId);
				counted.add(secondId);
			}
		}
		return count;
	}

	public static List<PairView> pairViews(ServerWorld world) {
		if (world == null) return List.of();
		List<PairView> pairs = new ArrayList<>();
		Set<UUID> counted = new HashSet<>();
		for (Map.Entry<UUID, UUID> entry : PARTNERS.entrySet()) {
			UUID firstId = entry.getKey();
			UUID secondId = entry.getValue();
			if (firstId == null || secondId == null || counted.contains(firstId) || counted.contains(secondId)) continue;
			if (!isValidPair(firstId, secondId)) continue;
			ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
			ServerPlayerEntity second = world.getServer().getPlayerManager().getPlayer(secondId);
			pairs.add(new PairView(firstId, first == null ? "" : first.getName().getString(),
				secondId, second == null ? "" : second.getName().getString()));
			counted.add(firstId);
			counted.add(secondId);
		}
		return pairs;
	}

	public static boolean isLinked(UUID playerId) {
		if (playerId == null) return false;
		UUID partnerId = PARTNERS.get(playerId);
		return partnerId != null && playerId.equals(PARTNERS.get(partnerId));
	}

	public static UUID partnerId(UUID playerId) {
		if (playerId == null) return null;
		UUID partnerId = PARTNERS.get(playerId);
		return isValidPair(playerId, partnerId) ? partnerId : null;
	}

	public static boolean isIndependentLover(UUID playerId) {
		return GexpressConfig.doLoversWinIndependently() && isLinked(playerId);
	}

	public static boolean didIndependentPairWin(UUID playerId, UUID winnerId) {
		if (!GexpressConfig.doLoversWinIndependently() || playerId == null || winnerId == null) return false;
		return playerId.equals(winnerId) || playerId.equals(partnerId(winnerId));
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (!GexpressConfig.doLoversWinIndependently() || world == null || game == null
				|| game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		List<ServerPlayerEntity> living = world.getPlayers(player ->
			DeadPlayerStatus.isLivingRoundParticipant(player) && !PelicanManager.isStashed(player));
		if (living.size() == 2) {
			ServerPlayerEntity first = living.get(0);
			ServerPlayerEntity second = living.get(1);
			if (second.getUuid().equals(partnerId(first.getUuid()))
					&& hasActiveLoversModifier(first) && hasActiveLoversModifier(second)) {
				game.setLooseEndWinner(first.getUuid());
				NeutralWinManager.announce(world, first, "announcement.win.gexpress.lovers", 0xF06AA8);
				GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(),
					GameFunctions.WinStatus.LOOSE_END);
				GameFunctions.stopGame(world);
				return true;
			}
		}
		if (living.size() < 2) return false;
		for (ServerPlayerEntity player : living) {
			UUID partner = partnerId(player.getUuid());
			if (partner != null && living.stream().anyMatch(other -> other.getUuid().equals(partner))) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unused")
	private static void initializeRound(World world, GameWorldComponent game) {
		clear(world, false);
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null || MapSelectModifiers.LOVERS == null) return;
		try {
			List<ServerPlayerEntity> assigned = new ArrayList<>();
			for (UUID playerId : component.getAllWithModifier(MapSelectModifiers.LOVERS)) {
				ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerId);
				if (player != null && game.getRole(player) != null) assigned.add(player);
			}
			Set<UUID> used = new HashSet<>();
			for (ServerPlayerEntity first : assigned) {
				if (used.contains(first.getUuid())) continue;
				ServerPlayerEntity second = findPartner(serverWorld, game, first, assigned, used, component);
				if (second == null) continue;
				used.add(first.getUuid());
				used.add(second.getUuid());
				link(first, second, true, true);
			}
			createStartingCupidPair(serverWorld, game, used);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to initialize Lovers links.", t);
		}
	}

	private static void createStartingCupidPair(ServerWorld world, GameWorldComponent game, Set<UUID> used) {
		if (!hasCupid(world, game)) return;
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == null || used.contains(player.getUuid()) || isLinked(player.getUuid()) || isCupid(game, player)
					|| game.getRole(player) == null || !isLiving(player)) {
				continue;
			}
			candidates.add(player);
		}
		Collections.shuffle(candidates);
		for (int i = 0; i < candidates.size(); i++) {
			ServerPlayerEntity first = candidates.get(i);
			for (int j = i + 1; j < candidates.size(); j++) {
				ServerPlayerEntity second = candidates.get(j);
				if (!RoleTeams.compatibleLovers(game, first, second, GexpressConfig.canLoversPairAcrossSides())) continue;
				used.add(first.getUuid());
				used.add(second.getUuid());
				link(first, second, true, true);
				return;
			}
		}
	}

	private static ServerPlayerEntity findPartner(ServerWorld world, GameWorldComponent game, ServerPlayerEntity first,
			List<ServerPlayerEntity> assigned, Set<UUID> used, WorldModifierComponent component) {
		for (ServerPlayerEntity candidate : assigned) {
			if (candidate == first || used.contains(candidate.getUuid()) || isLinked(candidate.getUuid())) continue;
			if (RoleTeams.compatibleLovers(game, first, candidate, GexpressConfig.canLoversPairAcrossSides())) {
				return candidate;
			}
		}
		for (ServerPlayerEntity candidate : world.getPlayers()) {
			if (candidate == first || used.contains(candidate.getUuid()) || isLinked(candidate.getUuid())) continue;
			if (component.isModifier(candidate.getUuid(), MapSelectModifiers.LOVERS)) continue;
			if (game.getRole(candidate) == null || !RoleTeams.compatibleLovers(game, first, candidate,
					GexpressConfig.canLoversPairAcrossSides())) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static void link(ServerPlayerEntity first, ServerPlayerEntity second, boolean addModifier) {
		link(first, second, addModifier, false);
	}

	private static void link(ServerPlayerEntity first, ServerPlayerEntity second, boolean addModifier, boolean revealImmediately) {
		if (first == null || second == null || first == second) return;
		PARTNERS.put(first.getUuid(), second.getUuid());
		PARTNERS.put(second.getUuid(), first.getUuid());
		if (addModifier) {
			addLoversModifier(first);
			addLoversModifier(second);
		}
		if (revealImmediately) {
			DISCOVERED_LOVERS.add(first.getUuid());
			DISCOVERED_LOVERS.add(second.getUuid());
			first.sendMessage(Text.literal("Your lover: " + second.getName().getString() + ".")
				.formatted(Formatting.LIGHT_PURPLE), true);
			second.sendMessage(Text.literal("Your lover: " + first.getName().getString() + ".")
				.formatted(Formatting.LIGHT_PURPLE), true);
		}
		sync(first);
		sync(second);
	}

	private static void addLoversModifier(ServerPlayerEntity player) {
		ensureLoversModifier(player, true);
	}

	private static void ensureLoversModifier(ServerPlayerEntity player, boolean runtime) {
		if (player == null || MapSelectModifiers.LOVERS == null || ModifierUtils.has(player, MapSelectModifiers.LOVERS_ID)) {
			return;
		}
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return;
		component.addModifier(player.getUuid(), MapSelectModifiers.LOVERS);
		if (runtime) RUNTIME_MODIFIERS.add(player.getUuid());
		component.sync();
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity serverVictim)) return true;
		UUID victimId = serverVictim.getUuid();
		if (SOUL_LINK_DEATHS.contains(victimId)) return true;
		UUID partnerId = PARTNERS.get(victimId);
		if (partnerId == null || !isValidPair(victimId, partnerId)) {
			PARTNERS.remove(victimId);
			return true;
		}
		ServerPlayerEntity partner = serverVictim.getServer().getPlayerManager().getPlayer(partnerId);
		if (isLiving(partner) && hasActiveLoversModifier(serverVictim) && hasActiveLoversModifier(partner)) {
			PENDING_SOUL_DEATHS.put(partnerId, victimId);
		}
		return true;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (!isRoundState(game) && !GexpressTestState.hasModifierTesters()) {
			if (!PARTNERS.isEmpty() || !PENDING_SOUL_DEATHS.isEmpty() || !SOUL_LINK_DEATHS.isEmpty()
					|| !RUNTIME_MODIFIERS.isEmpty() || hasLoversAssignments(world)) {
				clear(world, true);
			}
			return;
		}
		processSoulDeaths(world);
		updateLoveAtFirstSight(world);
		if (++syncTicker >= 20) {
			syncTicker = 0;
			for (ServerPlayerEntity player : world.getPlayers()) sync(player);
		}
	}

	private static void updateLoveAtFirstSight(ServerWorld world) {
		if (world == null || PARTNERS.isEmpty()) return;
		for (Map.Entry<UUID, UUID> entry : new HashMap<>(PARTNERS).entrySet()) {
			UUID firstId = entry.getKey();
			UUID secondId = entry.getValue();
			if (firstId == null || secondId == null || !isValidPair(firstId, secondId)
					|| DISCOVERED_LOVERS.contains(firstId) && DISCOVERED_LOVERS.contains(secondId)) {
				continue;
			}
			ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
			ServerPlayerEntity second = world.getServer().getPlayerManager().getPlayer(secondId);
			if (!isLiving(first) || !isLiving(second) || first.getWorld() != second.getWorld()) continue;
			if (first.squaredDistanceTo(second) > LOVE_AT_FIRST_SIGHT_RANGE_SQUARED) continue;
			boolean firstNew = DISCOVERED_LOVERS.add(firstId);
			boolean secondNew = DISCOVERED_LOVERS.add(secondId);
			if (firstNew) {
				first.sendMessage(Text.literal("Love at first sight: " + second.getName().getString() + ".")
					.formatted(Formatting.LIGHT_PURPLE), true);
			}
			if (secondNew) {
				second.sendMessage(Text.literal("Love at first sight: " + first.getName().getString() + ".")
					.formatted(Formatting.LIGHT_PURPLE), true);
			}
			sync(first);
			sync(second);
		}
	}

	private static void processSoulDeaths(ServerWorld world) {
		if (PENDING_SOUL_DEATHS.isEmpty()) return;
		Map<UUID, UUID> pending = new HashMap<>(PENDING_SOUL_DEATHS);
		PENDING_SOUL_DEATHS.clear();
		for (Map.Entry<UUID, UUID> entry : pending.entrySet()) {
			ServerPlayerEntity partner = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			ServerPlayerEntity source = world.getServer().getPlayerManager().getPlayer(entry.getValue());
			if (!isValidPair(entry.getKey(), entry.getValue()) || source == null || isLiving(source)
					|| !hasActiveLoversModifier(source) || !hasActiveLoversModifier(partner)) {
				continue;
			}
			if (!isLiving(partner)) continue;
			SOUL_LINK_DEATHS.add(partner.getUuid());
			try {
				GameFunctions.killPlayer(partner, true, null, GameConstants.DeathReasons.GENERIC);
				partner.sendMessage(Text.literal("Your lover died.").formatted(Formatting.LIGHT_PURPLE), true);
			} finally {
				SOUL_LINK_DEATHS.remove(partner.getUuid());
			}
		}
	}

	private static boolean isLiving(ServerPlayerEntity player) {
		return player != null && !PelicanManager.isStashed(player) && DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static boolean isRoundState(GameWorldComponent game) {
		if (game == null) return false;
		GameWorldComponent.GameStatus status = game.getGameStatus();
		return game.isRunning()
			|| status == GameWorldComponent.GameStatus.STARTING
			|| status == GameWorldComponent.GameStatus.ACTIVE
			|| status == GameWorldComponent.GameStatus.STOPPING;
	}

	private static void sync(ServerPlayerEntity player) {
		if (player == null || !ServerPlayNetworking.canSend(player, LoversStatePayload.ID)) return;
		UUID partnerId = PARTNERS.get(player.getUuid());
		if (partnerId == null || !isValidPair(player.getUuid(), partnerId) || !hasActiveLoversModifier(player)) {
			PARTNERS.remove(player.getUuid());
			ServerPlayNetworking.send(player, LoversStatePayload.clear());
			return;
		}
		ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
		if (partner != null && !hasActiveLoversModifier(partner)) {
			PARTNERS.remove(player.getUuid());
			PARTNERS.remove(partnerId);
			DISCOVERED_LOVERS.remove(player.getUuid());
			DISCOVERED_LOVERS.remove(partnerId);
			ServerPlayNetworking.send(player, LoversStatePayload.clear());
			if (partner != null) sync(partner);
			return;
		}
		if (!DISCOVERED_LOVERS.contains(player.getUuid())) {
			ServerPlayNetworking.send(player, LoversStatePayload.clear());
			return;
		}
		String name = partner == null ? "" : partner.getName().getString();
		ServerPlayNetworking.send(player, new LoversStatePayload(true, partnerId, name));
	}

	private static void clear(World world, boolean removeAllLoversModifiers) {
		if (world instanceof ServerWorld serverWorld) {
			if (removeAllLoversModifiers) {
				removeAllLoversModifiers(serverWorld);
			} else {
				removeRuntimeModifiers(serverWorld);
			}
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				if (ServerPlayNetworking.canSend(player, LoversStatePayload.ID)) {
					ServerPlayNetworking.send(player, LoversStatePayload.clear());
				}
			}
		}
		PARTNERS.clear();
		PENDING_SOUL_DEATHS.clear();
		SOUL_LINK_DEATHS.clear();
		RUNTIME_MODIFIERS.clear();
		DISCOVERED_LOVERS.clear();
		syncTicker = 0;
	}

	private static boolean isValidPair(UUID first, UUID second) {
		return first != null && second != null && second.equals(PARTNERS.get(first)) && first.equals(PARTNERS.get(second));
	}

	private static boolean hasActiveLoversModifier(ServerPlayerEntity player) {
		return player != null && ModifierUtils.has(player, MapSelectModifiers.LOVERS_ID);
	}

	private static boolean hasCupid(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null) return false;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isCupid(game, player)) return true;
		}
		return false;
	}

	private static boolean isCupid(GameWorldComponent game, ServerPlayerEntity player) {
		return game != null && player != null && game.getRole(player) != null
			&& dev.mapselect.registry.MapSelectRoles.CUPID_ID.equals(game.getRole(player).identifier());
	}

	private static boolean hasLoversAssignments(ServerWorld world) {
		if (world == null || MapSelectModifiers.LOVERS == null) return false;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return false;
		try {
			return !component.getAllWithModifier(MapSelectModifiers.LOVERS).isEmpty();
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Could not check Lovers modifier assignments.", t);
			return false;
		}
	}

	private static void removeRuntimeModifiers(ServerWorld world) {
		if (world == null || MapSelectModifiers.LOVERS == null || RUNTIME_MODIFIERS.isEmpty()) return;
		removeLoversModifiers(world, new ArrayList<>(RUNTIME_MODIFIERS));
	}

	private static void removeAllLoversModifiers(ServerWorld world) {
		if (world == null || MapSelectModifiers.LOVERS == null) return;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return;
		removeLoversModifiers(world, new ArrayList<>(component.getAllWithModifier(MapSelectModifiers.LOVERS)));
	}

	private static void removeLoversModifiers(ServerWorld world, List<UUID> targets) {
		if (world == null || targets == null || targets.isEmpty() || MapSelectModifiers.LOVERS == null) return;
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return;
		boolean changed = false;
		Identifier id = MapSelectModifiers.LOVERS_ID;
		for (UUID playerId : targets) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null) {
				changed |= ModifierUtils.removeIfPresent(player, MapSelectModifiers.LOVERS);
				continue;
			}
			ArrayList<Modifier> modifiers = component.getModifiers(playerId);
			if (modifiers == null || modifiers.isEmpty()) continue;
			changed |= modifiers.removeIf(value -> value == MapSelectModifiers.LOVERS
				|| (value != null && id != null && id.equals(value.identifier())));
		}
		if (changed) component.sync();
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(Map.copyOf(PARTNERS), Map.copyOf(PENDING_SOUL_DEATHS), Set.copyOf(SOUL_LINK_DEATHS),
			Set.copyOf(RUNTIME_MODIFIERS), Set.copyOf(DISCOVERED_LOVERS));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		if (world != null) removeRuntimeModifiers(world);
		PARTNERS.clear();
		PENDING_SOUL_DEATHS.clear();
		SOUL_LINK_DEATHS.clear();
		RUNTIME_MODIFIERS.clear();
		DISCOVERED_LOVERS.clear();
		if (state != null) {
			PARTNERS.putAll(state.partners());
			PENDING_SOUL_DEATHS.putAll(state.pendingSoulDeaths());
			SOUL_LINK_DEATHS.addAll(state.soulLinkDeaths());
			RUNTIME_MODIFIERS.addAll(state.runtimeModifiers());
			DISCOVERED_LOVERS.addAll(state.discoveredLovers());
		}
		if (world == null) return;
		Set<UUID> linkedPlayers = new HashSet<>(PARTNERS.keySet());
		for (UUID playerId : linkedPlayers) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && isValidPair(playerId, PARTNERS.get(playerId))) {
				ensureLoversModifier(player, RUNTIME_MODIFIERS.contains(playerId));
			}
		}
		for (ServerPlayerEntity player : world.getPlayers()) sync(player);
	}

	public record PairView(UUID firstId, String firstName, UUID secondId, String secondName) {}

	public record TimeState(Map<UUID, UUID> partners, Map<UUID, UUID> pendingSoulDeaths,
			Set<UUID> soulLinkDeaths, Set<UUID> runtimeModifiers, Set<UUID> discoveredLovers) {}
}
