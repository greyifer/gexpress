package dev.mapselect.game.draft;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.network.game.DraftPickPayload;
import dev.mapselect.network.game.DraftStatePayload;
import dev.mapselect.network.game.DraftWelcomePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.RoleModifierTuningBridge;
import dev.mapselect.role.RoleAvailability;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DraftManager {
	public static final int ORDER_RAFFLE_TICKS = 20 * 8;
	public static final int ORDER_RESULT_TICKS = 20 * 2;
	public static final int ORDER_REVEAL_TICKS = ORDER_RAFFLE_TICKS + ORDER_RESULT_TICKS;
	public static final int PICK_TICKS = 20 * 10;
	public static final int FINISH_FADE_TICKS = 20;
	private static final int ROLE_OPTION_COUNT = 3;
	private static final int VIGILANTE_OPTION_WEIGHT = 5;
	private static final int VIGILANTE_RANDOM_WEIGHT = 7;
	private static final Map<ServerWorld, Session> SESSIONS = new HashMap<>();

	private DraftManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(DraftStatePayload.ID, DraftStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DraftWelcomePayload.ID, DraftWelcomePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DraftPickPayload.ID, DraftPickPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DraftPickPayload.ID, (payload, context) ->
			context.server().execute(() -> submit(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(DraftManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncJoiningPlayer(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> handleDisconnect(handler.player)));
		GameEvents.ON_FINISH_INITIALIZE.register(DraftManager::recordRoundHistory);
		ServerLifecycleEvents.SERVER_STOPPED.register(DraftManager::clearAll);
	}

	/**
	 * Called in place of Wathe's first initializeGame invocation. Returning true means the
	 * normal initialization was intentionally postponed until the draft has completed.
	 */
	public static boolean beginIfDraft(ServerWorld world) {
		if (world == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (!GexpressGameModes.isDraft(game)) return false;
		if (SESSIONS.containsKey(world)) return true;

		List<ServerPlayerEntity> players = readyPlayers(world);
		if (players.isEmpty()) return false;
		prepareRoleConfiguration(world, players.size());
		List<Role> enabledRoles = enabledRoles();
		boolean hasKiller = enabledRoles.stream().anyMatch(role -> slot(role) == SlotType.KILLER);
		boolean hasInnocent = enabledRoles.stream().anyMatch(role -> slot(role) == SlotType.INNOCENT);
		if (enabledRoles.isEmpty() || (players.size() > 1 && (!hasKiller || !hasInnocent))) {
			String reason = enabledRoles.isEmpty()
				? "no enabled murder roles are available"
				: !hasKiller ? "no enabled killer role is available" : "no enabled innocent role is available";
			MapSelect.LOGGER.error("Draft mode could not start because {}.", reason);
			for (ServerPlayerEntity player : players) {
				player.sendMessage(Text.literal("Draft mode could not start: " + reason + ".")
					.formatted(Formatting.RED), false);
			}
			GameFunctions.stopGame(world);
			return true;
		}

		Collections.shuffle(players);
		Session session;
		try {
			session = new Session(world, players, enabledRoles);
		} catch (IllegalStateException error) {
			MapSelect.LOGGER.warn("Draft mode could not satisfy the previous-round alignment lockouts: {}",
				error.getMessage());
			for (ServerPlayerEntity player : players) {
				player.sendMessage(Text.literal("Draft mode could not start: not enough players are eligible "
					+ "for the required Killer and Neutral slots after last round's lockouts.")
					.formatted(Formatting.RED), false);
			}
			GameFunctions.stopGame(world);
			return true;
		}
		SESSIONS.put(world, session);
		session.broadcast();
		return true;
	}

	public static boolean isDrafting(ServerWorld world) {
		Session session = world == null ? null : SESSIONS.get(world);
		return session != null && session.phase != InternalPhase.APPLYING;
	}

	public static Map<UUID, Role> assignmentsForInitialization(ServerWorld world) {
		Session session = world == null ? null : SESSIONS.get(world);
		return session == null ? Map.of() : session.resolvedAssignments();
	}

	public static boolean hasInitializationSession(ServerWorld world) {
		Session session = world == null ? null : SESSIONS.get(world);
		return session != null && session.phase == InternalPhase.APPLYING;
	}

	public static Role fallbackRole(ServerWorld world, ServerPlayerEntity player) {
		Session session = world == null ? null : SESSIONS.get(world);
		if (session != null) {
			Role selected = session.assignments.get(player == null ? null : player.getUuid());
			if (selected != null) return selected;
			Role fallback = session.firstEnabled(player == null ? null : player.getUuid(), SlotType.INNOCENT);
			if (fallback != null) return fallback;
		}
		return WatheRoles.CIVILIAN;
	}

	static int vigilanteSlotCount(int playerCount) {
		if (playerCount <= 0 || !RoleAvailability.isEnabled(WatheRoles.VIGILANTE)) return 0;
		int automatic = playerCount / Math.max(1, GexpressConfig.getPlayersPerVigilante());
		int maximum = GexpressConfig.useCustomRoleCounts()
			? Math.min(automatic, GexpressConfig.getMaxVigilanteAmount()) : automatic;
		return Math.max(0, Math.min(playerCount, maximum));
	}

	public static void finishInitialization(ServerWorld world) {
		Session session = world == null ? null : SESSIONS.remove(world);
		if (session != null) session.restorePlayers();
	}

	public static void cancel(ServerWorld world) {
		Session session = world == null ? null : SESSIONS.remove(world);
		if (session == null) return;
		session.restorePlayers();
		session.sendClosed();
	}

	private static void prepareRoleConfiguration(ServerWorld world, int playerCount) {
		try {
			Harpymodloader.refreshRoles();
			HarpyModLoaderConfig.HANDLER.load();
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Could not refresh HML role configuration before a draft.", error);
		}
		RoleModifierTuningBridge.prepareForGame(world, playerCount);
	}

	private static List<ServerPlayerEntity> readyPlayers(ServerWorld world) {
		MapVariablesWorldComponent variables = MapVariablesWorldComponent.KEY.getNullable(world);
		Box readyArea = variables == null ? null : variables.getReadyArea();
		if (readyArea == null) return List.of();
		List<ServerPlayerEntity> players = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player != null && readyArea.contains(player.getPos())) players.add(player);
		}
		return players;
	}

	private static List<Role> enabledRoles() {
		List<Role> roles = new ArrayList<>();
		for (Role role : WatheRoles.ROLES) {
			if (role == null || role.identifier() == null) continue;
			Identifier id = role.identifier();
			if (!RoleAvailability.isDraftEnabled(role)) continue;
			if (Harpymodloader.NON_MURDER_ROLES.contains(role)
					|| (Harpymodloader.SPECIAL_ROLES.contains(role) && role != WatheRoles.CIVILIAN)
					|| "vulture".equals(id.getPath())) continue;
			roles.add(role);
		}
		return List.copyOf(roles);
	}

	private static void tick(ServerWorld world) {
		Session session = SESSIONS.get(world);
		if (session == null) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (!GexpressGameModes.isDraft(game)
				|| game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING
				|| game.getGameStatus() == GameWorldComponent.GameStatus.INACTIVE) {
			cancel(world);
			return;
		}
		session.tick();
	}

	private static void submit(ServerPlayerEntity player, DraftPickPayload payload) {
		if (player == null || payload == null) return;
		Session session = SESSIONS.get(player.getServerWorld());
		if (session == null) return;
		session.submit(player, payload);
	}

	private static void syncJoiningPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		Session session = SESSIONS.get(player.getServerWorld());
		if (session != null) session.sendState(player);
	}

	private static void handleDisconnect(ServerPlayerEntity player) {
		if (player == null) return;
		Session session = SESSIONS.get(player.getServerWorld());
		if (session != null) session.handleDisconnect(player.getUuid());
	}

	private static void recordRoundHistory(World rawWorld, GameWorldComponent game) {
		if (!(rawWorld instanceof ServerWorld world) || game == null || game.getRoles().isEmpty()) return;
		DraftRoleHistoryComponent.KEY.get(world).replaceWith(new LinkedHashMap<>(game.getRoles()));
	}

	private static void clearAll(MinecraftServer server) {
		for (Session session : List.copyOf(SESSIONS.values())) {
			session.restorePlayers();
			session.sendClosed();
		}
		SESSIONS.clear();
	}

	private enum InternalPhase {
		ORDER,
		PICK,
		FINISHING,
		APPLYING
	}

	private enum SlotType {
		INNOCENT,
		NEUTRAL,
		KILLER
	}

	private static final class Session {
		private final ServerWorld world;
		private final List<UUID> order;
		private final List<Role> enabledRoles;
		private final DraftRoleHistoryComponent roleHistory;
		private final Map<UUID, SlotType> slots = new HashMap<>();
		private final Map<UUID, FrozenPlayer> frozenPlayers = new HashMap<>();
		private final Map<UUID, Role> assignments = new LinkedHashMap<>();
		private final Map<Identifier, Integer> selectedCounts = new HashMap<>();
		private final Set<UUID> randomPicks = new HashSet<>();
		private final Set<UUID> disconnectedPlayers = new HashSet<>();
		private List<Role> currentChoices = List.of();
		private InternalPhase phase = InternalPhase.ORDER;
		private int activePosition = -1;
		private int remainingTicks = ORDER_REVEAL_TICKS;

		private Session(ServerWorld world, List<ServerPlayerEntity> players, List<Role> enabledRoles) {
			this.world = world;
			this.order = new ArrayList<>(players.stream().map(ServerPlayerEntity::getUuid).toList());
			this.enabledRoles = enabledRoles;
			this.roleHistory = DraftRoleHistoryComponent.KEY.get(world);
			if (!assignSlots()) throw new IllegalStateException("alignment allocation is impossible");
			for (ServerPlayerEntity player : players) {
				frozenPlayers.put(player.getUuid(), FrozenPlayer.capture(player));
				player.setInvulnerable(true);
			}
		}

		private boolean assignSlots() {
			int playerCount = order.size();
			boolean hasKillers = enabledRoles.stream().anyMatch(role -> slot(role) == SlotType.KILLER);
			boolean hasNeutrals = enabledRoles.stream().anyMatch(role -> slot(role) == SlotType.NEUTRAL);
			DraftSlotBudget budget = DraftSlotBudget.calculate(playerCount, hasKillers, hasNeutrals,
				GexpressConfig.useCustomRoleCounts(), GexpressConfig.getPlayersPerKiller(),
				GexpressConfig.getMaxKillerAmount(), GexpressConfig.getPlayersPerNeutral(),
				GexpressConfig.getMaxNeutralAmount());

			DraftAlignmentAllocator.Allocation<UUID> allocation = DraftAlignmentAllocator.allocate(
				order, budget.killers(), budget.neutrals(), roleHistory::previousAlignment);
			if (!allocation.possible()) return false;
			for (UUID playerId : order) {
				DraftAlignmentAllocator.Alignment alignment = allocation.assignments().getOrDefault(playerId,
					DraftAlignmentAllocator.Alignment.INNOCENT);
				slots.put(playerId, switch (alignment) {
					case KILLER -> SlotType.KILLER;
					case NEUTRAL -> SlotType.NEUTRAL;
					case INNOCENT -> SlotType.INNOCENT;
				});
			}
			return true;
		}

		private void tick() {
			freezePlayers();
			if (phase == InternalPhase.APPLYING) return;
			if (remainingTicks > 0) remainingTicks--;
			if (remainingTicks > 0) return;

			switch (phase) {
				case ORDER -> startNextTurn();
				case PICK -> {
					choose(-1);
					startNextTurn();
				}
				case FINISHING -> applyDraft();
				case APPLYING -> {
				}
			}
		}

		private void submit(ServerPlayerEntity player, DraftPickPayload payload) {
			if (phase != InternalPhase.PICK || player == null || payload.turn() != activePosition) return;
			if (activePosition < 0 || activePosition >= order.size()
					|| !order.get(activePosition).equals(player.getUuid())
					|| disconnectedPlayers.contains(player.getUuid())) return;
			if (payload.choice() < 0 || payload.choice() > ROLE_OPTION_COUNT) return;
			int exactChoice = exactChoice(payload);
			if (exactChoice < 0) {
				MapSelect.LOGGER.warn("Rejected stale draft choice {} ({}) from {} on turn {}.",
					payload.choice(), payload.roleId(), player.getName().getString(), payload.turn());
				sendState(player);
				return;
			}
			choose(exactChoice);
			startNextTurn();
		}

		private int exactChoice(DraftPickPayload payload) {
			if (payload.choice() == ROLE_OPTION_COUNT) {
				return payload.roleId() == null ? ROLE_OPTION_COUNT : -1;
			}
			if (payload.roleId() == null) return -1;
			for (int index = 0; index < currentChoices.size(); index++) {
				Role offered = currentChoices.get(index);
				if (offered != null && payload.roleId().equals(offered.identifier())) return index;
			}
			return -1;
		}

		private void handleDisconnect(UUID playerId) {
			if (playerId == null || !disconnectedPlayers.add(playerId)) return;
			int disconnectedPosition = order.indexOf(playerId);
			if (disconnectedPosition < 0) return;
			if (phase == InternalPhase.PICK && disconnectedPosition == activePosition) {
				choose(ROLE_OPTION_COUNT);
				startNextTurn();
			} else {
				broadcast();
			}
		}

		private void startNextTurn() {
			activePosition++;
			while (activePosition < order.size()
					&& disconnectedPlayers.contains(order.get(activePosition))) {
				choose(ROLE_OPTION_COUNT);
				activePosition++;
			}
			if (activePosition >= order.size()) {
				ensureReservedSlotsFilled();
				phase = InternalPhase.FINISHING;
				remainingTicks = FINISH_FADE_TICKS;
				currentChoices = List.of();
				broadcast();
				return;
			}

			phase = InternalPhase.PICK;
			remainingTicks = PICK_TICKS;
			UUID playerId = order.get(activePosition);
			currentChoices = createChoices(slots.getOrDefault(playerId, SlotType.INNOCENT));
			broadcast();
		}

		private List<Role> createChoices(SlotType slot) {
			UUID playerId = activePosition >= 0 && activePosition < order.size() ? order.get(activePosition) : null;
			List<Role> choices = new ArrayList<>(DraftOfferPlanner.select(availableForOffer(playerId, slot), ROLE_OPTION_COUNT,
				DraftManager::optionKey, this::optionWeight,
				bound -> ThreadLocalRandom.current().nextInt(bound)));
			Role lastResort = firstEnabled(playerId, slot);
			while (choices.size() < ROLE_OPTION_COUNT && lastResort != null) choices.add(lastResort);
			while (choices.size() < ROLE_OPTION_COUNT) choices.add(WatheRoles.CIVILIAN);
			return List.copyOf(choices);
		}

		private void choose(int choice) {
			if (activePosition < 0 || activePosition >= order.size()) return;
			UUID playerId = order.get(activePosition);
			SlotType slot = slots.getOrDefault(playerId, SlotType.INNOCENT);
			boolean randomChoice = choice == ROLE_OPTION_COUNT;
			Role selected;
			if (randomChoice) {
				selected = randomFromFullPool(playerId, slot, true);
				if (selected == null) selected = randomFromFullPool(playerId, slot, false);
			} else {
				selected = choice >= 0 && choice < currentChoices.size()
					? currentChoices.get(choice) : randomAvailable(playerId, slot);
			}
			if (selected != null) {
				SlotType selectedSlot = slot(selected);
				if (!DraftOfferPlanner.canApplyAlignment(slot, selectedSlot,
						() -> moveReservedSlot(playerId, slot, selectedSlot))) {
					selected = randomAvailable(playerId, slot);
				}
			}
			if (selected == null) selected = firstEnabled(playerId, slot);
			if (selected == null) selected = WatheRoles.CIVILIAN;
			assignments.put(playerId, selected);
			if (randomChoice) randomPicks.add(playerId);
			else randomPicks.remove(playerId);
			selectedCounts.merge(selected.identifier(), 1, Integer::sum);
		}

		private void ensureReservedSlotsFilled() {
			selectedCounts.clear();
			for (UUID playerId : order) {
				Role selected = assignments.get(playerId);
				SlotType expected = slots.getOrDefault(playerId, SlotType.INNOCENT);
				if (selected == null || slot(selected) != expected || isRestricted(playerId, expected)
						|| (!enabledRoles.contains(selected) && selected != WatheRoles.CIVILIAN)) {
					assignments.remove(playerId);
					continue;
				}
				selectedCounts.merge(selected.identifier(), 1, Integer::sum);
			}

			for (UUID playerId : order) {
				if (assignments.containsKey(playerId)) continue;
				SlotType expected = slots.getOrDefault(playerId, SlotType.INNOCENT);
				Role replacement = randomAvailable(playerId, expected);
				if (replacement == null) replacement = firstEnabled(playerId, expected);
				if (replacement == null) {
					MapSelect.LOGGER.error("Draft could not fill reserved {} slot for {}.", expected, playerId);
					replacement = WatheRoles.CIVILIAN;
				}
				assignments.put(playerId, replacement);
				selectedCounts.merge(replacement.identifier(), 1, Integer::sum);
			}
		}

		private Map<UUID, Role> resolvedAssignments() {
			Map<UUID, Role> resolved = new LinkedHashMap<>();
			for (Map.Entry<UUID, Role> entry : assignments.entrySet()) {
				Role canonical = roleById(entry.getValue() == null ? null : entry.getValue().identifier());
				if (canonical != null) resolved.put(entry.getKey(), canonical);
			}
			return Map.copyOf(resolved);
		}

		private Role roleById(Identifier roleId) {
			if (roleId == null) return null;
			for (Role role : WatheRoles.ROLES) {
				if (role != null && roleId.equals(role.identifier())) return role;
			}
			return null;
		}

		private Role randomAvailable(UUID playerId, SlotType slot) {
			List<Role> available = available(playerId, slot);
			return weightedRandom(available);
		}

		private Role randomFromFullPool(UUID playerId, SlotType currentSlot, boolean requireUnclaimed) {
			List<Role> candidates = new ArrayList<>();
			Set<Identifier> seen = new HashSet<>();
			for (Role role : enabledRoles) {
				Identifier id = role.identifier();
				int selected = selectedCounts.getOrDefault(id, 0);
				if (!seen.add(id) || selected >= maximum(role)) continue;
				if (requireUnclaimed && selected > 0) continue;
				if (!canMoveReservedSlot(playerId, currentSlot, slot(role))) continue;
				candidates.add(role);
			}
			return weightedRandom(candidates);
		}

		private boolean canMoveReservedSlot(UUID currentPlayer, SlotType currentSlot, SlotType selectedSlot) {
			if (isRestricted(currentPlayer, selectedSlot)) return false;
			if (currentSlot == selectedSlot) return true;
			for (int position = activePosition + 1; position < order.size(); position++) {
				UUID futurePlayer = order.get(position);
				if (!assignments.containsKey(futurePlayer)
						&& slots.getOrDefault(futurePlayer, SlotType.INNOCENT) == selectedSlot
						&& !isRestricted(futurePlayer, currentSlot)) return true;
			}
			return false;
		}

		private boolean moveReservedSlot(UUID currentPlayer, SlotType currentSlot, SlotType selectedSlot) {
			if (isRestricted(currentPlayer, selectedSlot)) return false;
			if (currentSlot == selectedSlot) return true;
			for (int position = activePosition + 1; position < order.size(); position++) {
				UUID futurePlayer = order.get(position);
				if (assignments.containsKey(futurePlayer)
						|| slots.getOrDefault(futurePlayer, SlotType.INNOCENT) != selectedSlot
						|| isRestricted(futurePlayer, currentSlot)) {
					continue;
				}
				slots.put(currentPlayer, selectedSlot);
				slots.put(futurePlayer, currentSlot);
				return true;
			}
			return false;
		}

		private List<Role> available(UUID playerId, SlotType slot) {
			List<Role> roles = new ArrayList<>();
			Set<Identifier> seen = new HashSet<>();
			for (Role role : enabledRoles) {
				if (slot(role) != slot) continue;
				if (isRestricted(playerId, slot)) continue;
				if (!seen.add(role.identifier())) continue;
				int maximum = maximum(role);
				if (selectedCounts.getOrDefault(role.identifier(), 0) >= maximum) continue;
				roles.add(role);
			}
			return roles;
		}

		private List<Role> availableForOffer(UUID playerId, SlotType currentSlot) {
			List<Role> roles = new ArrayList<>();
			Set<Identifier> seen = new HashSet<>();
			for (Role role : enabledRoles) {
				if (!seen.add(role.identifier())) continue;
				if (selectedCounts.getOrDefault(role.identifier(), 0) >= maximum(role)) continue;
				if (!canMoveReservedSlot(playerId, currentSlot, slot(role))) continue;
				roles.add(role);
			}
			return roles;
		}

		private Role weightedRandom(List<Role> roles) {
			if (roles == null || roles.isEmpty()) return null;
			int totalWeight = 0;
			for (Role role : roles) totalWeight += randomWeight(role);
			int roll = ThreadLocalRandom.current().nextInt(totalWeight);
			for (Role role : roles) {
				roll -= randomWeight(role);
				if (roll < 0) return role;
			}
			return roles.getLast();
		}

		private int optionWeight(Role role) {
			return role == WatheRoles.VIGILANTE ? VIGILANTE_OPTION_WEIGHT : 1;
		}

		private int randomWeight(Role role) {
			return role == WatheRoles.VIGILANTE ? VIGILANTE_RANDOM_WEIGHT : 1;
		}

		private int maximum(Role role) {
			if (role == WatheRoles.VIGILANTE) {
				return vigilanteSlotCount(order.size());
			}
			Integer configured = Harpymodloader.ROLE_MAX.get(role.identifier());
			int maximum = configured == null ? Integer.MAX_VALUE : Math.max(0, configured);
			if (MapSelectRoles.GODFATHER_ID.equals(role.identifier())
					|| MapSelectRoles.DRACULA_ID.equals(role.identifier())) {
				return Math.min(1, maximum);
			}
			return maximum;
		}

		private Role firstEnabled(UUID playerId, SlotType slot) {
			if (isRestricted(playerId, slot)) return null;
			for (Role role : enabledRoles) {
				if (slot(role) == slot) return role;
			}
			return null;
		}

		private boolean isRestricted(UUID playerId, SlotType candidate) {
			if (playerId == null || candidate == SlotType.INNOCENT) return false;
			DraftAlignmentAllocator.Alignment previous = roleHistory.previousAlignment(playerId);
			return candidate == SlotType.KILLER && previous == DraftAlignmentAllocator.Alignment.KILLER
				|| candidate == SlotType.NEUTRAL && previous == DraftAlignmentAllocator.Alignment.NEUTRAL;
		}

		private void applyDraft() {
			ensureReservedSlotsFilled();
			phase = InternalPhase.APPLYING;
			remainingTicks = 0;
			sendClosed();
			restorePlayers();
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
			if (game == null) {
				SESSIONS.remove(world);
				return;
			}
			game.setFade(60);
			game.sync();
			try {
				GameFunctions.initializeGame(world);
			} catch (Throwable error) {
				MapSelect.LOGGER.error("Draft mode failed while resuming normal game initialization.", error);
				SESSIONS.remove(world);
				GameFunctions.stopGame(world);
			}
		}

		private void freezePlayers() {
			for (Map.Entry<UUID, FrozenPlayer> entry : frozenPlayers.entrySet()) {
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
				if (player == null || player.getServerWorld() != world) continue;
				FrozenPlayer frozen = entry.getValue();
				player.setVelocity(Vec3d.ZERO);
				if (player.getPos().squaredDistanceTo(frozen.position) > 0.0025D) {
					player.teleport(world, frozen.position.x, frozen.position.y, frozen.position.z,
						frozen.yaw, frozen.pitch);
				}
			}
		}

		private void restorePlayers() {
			for (Map.Entry<UUID, FrozenPlayer> entry : frozenPlayers.entrySet()) {
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
				if (player != null) player.setInvulnerable(entry.getValue().invulnerable);
			}
		}

		private void broadcast() {
			for (ServerPlayerEntity player : world.getPlayers()) sendState(player);
		}

		private void sendState(ServerPlayerEntity player) {
			if (player == null || !ServerPlayNetworking.canSend(player, DraftStatePayload.ID)) return;
			int selfPosition = order.indexOf(player.getUuid());
			List<DraftStatePayload.RoleChoice> choices = List.of();
			if (phase == InternalPhase.PICK && selfPosition == activePosition) {
				choices = currentChoices.stream().map(DraftManager::payloadChoice).toList();
			}
			ServerPlayNetworking.send(player, new DraftStatePayload(payloadPhase(), order.size(), selfPosition,
				activePosition, remainingTicks, choices, pickedResults(player.getUuid())));
		}

		private List<DraftStatePayload.PickResult> pickedResults(UUID viewerId) {
			List<DraftStatePayload.PickResult> results = new ArrayList<>(assignments.size());
			for (int position = 0; position < order.size(); position++) {
				Role role = assignments.get(order.get(position));
				if (role != null) {
					UUID playerId = order.get(position);
					boolean hiddenRandom = randomPicks.contains(playerId) && !playerId.equals(viewerId);
					results.add(hiddenRandom
						? new DraftStatePayload.PickResult(position, "Random", 0xD6B35A, true)
						: new DraftStatePayload.PickResult(position, displayName(role), role.color(), false));
				}
			}
			return List.copyOf(results);
		}

		private DraftStatePayload.Phase payloadPhase() {
			return switch (phase) {
				case ORDER -> DraftStatePayload.Phase.ORDER;
				case PICK -> DraftStatePayload.Phase.PICK;
				case FINISHING -> DraftStatePayload.Phase.FINISHING;
				case APPLYING -> DraftStatePayload.Phase.CLOSED;
			};
		}

		private void sendClosed() {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (ServerPlayNetworking.canSend(player, DraftStatePayload.ID)) {
					DraftStatePayload payload = new DraftStatePayload(DraftStatePayload.Phase.CLOSED,
						order.size(), -1, -1, 0, List.of(), pickedResults(player.getUuid()));
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	private static SlotType slot(Role role) {
		if (role != null && role.canUseKiller()) return SlotType.KILLER;
		if (role != null && !role.isInnocent()) return SlotType.NEUTRAL;
		return SlotType.INNOCENT;
	}

	private static DraftStatePayload.RoleChoice payloadChoice(Role role) {
		DraftStatePayload.RoleAlignment alignment = switch (slot(role)) {
			case INNOCENT -> DraftStatePayload.RoleAlignment.CIVILIAN;
			case NEUTRAL -> DraftStatePayload.RoleAlignment.NEUTRAL;
			case KILLER -> DraftStatePayload.RoleAlignment.KILLER;
		};
		return new DraftStatePayload.RoleChoice(role.identifier(), displayName(role), role.color(), alignment);
	}

	private static String displayName(Role role) {
		String name;
		try {
			name = Harpymodloader.getRoleName(role).getString();
		} catch (Throwable ignored) {
			name = role.identifier().getPath();
		}
		StringBuilder clean = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			if (name.charAt(i) == '\u00A7' && i + 1 < name.length()) {
				i++;
				continue;
			}
			clean.append(name.charAt(i));
		}
		return clean.toString();
	}

	private static String optionKey(Role role) {
		if (role == null) return "";
		String visibleName = displayName(role).strip().toLowerCase(Locale.ROOT);
		return visibleName.isEmpty() && role.identifier() != null
			? role.identifier().toString() : visibleName;
	}

	private record FrozenPlayer(Vec3d position, float yaw, float pitch, boolean invulnerable) {
		private static FrozenPlayer capture(ServerPlayerEntity player) {
			return new FrozenPlayer(player.getPos(), player.getYaw(), player.getPitch(), player.isInvulnerable());
		}
	}
}
