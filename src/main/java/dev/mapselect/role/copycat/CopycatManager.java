package dev.mapselect.role.copycat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.CopycatActionPayload;
import dev.mapselect.network.CopycatStatePayload;
import dev.mapselect.network.CopycatStatePayload.StoredAbility;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CopycatManager {
	private static final int MAX_STORED_ABILITIES = 3;
	private static final Map<UUID, ActiveCopy> activeCopies = new HashMap<>();
	private static final Map<UUID, List<StoredAbility>> storedCopies = new HashMap<>();
	private static final Map<UUID, Integer> selectedCopies = new HashMap<>();
	private static final Map<UUID, Long> cooldownUntil = new HashMap<>();
	private static final Map<UUID, Set<Identifier>> copiedRoles = new HashMap<>();

	private CopycatManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CopycatActionPayload.ID, CopycatActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CopycatStatePayload.ID, CopycatStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CopycatActionPayload.ID,
			(payload, context) -> context.server().execute(() -> {
				switch (payload.action()) {
					case ACTIVATE_STORED -> activateStored(context.player(), payload.index());
					case SELECT_STORED -> selectStored(context.player(), payload.index());
					case STORE_TARGET -> tryStore(context.player(), payload.targetId());
					case STORE -> tryStore(context.player());
				}
			}));
		ServerTickEvents.END_WORLD_TICK.register(CopycatManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearAll());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll());
	}

	private static void tryStore(ServerPlayerEntity copycat) {
		tryStore(copycat, null);
	}

	private static void tryStore(ServerPlayerEntity copycat, UUID requestedTargetId) {
		if (copycat == null || !(copycat.getWorld() instanceof ServerWorld world)) return;
		if (!canUseHere(world, copycat) || !isCopycat(copycat) || PelicanManager.isStashed(copycat)
				|| (!GexpressTestState.isRoleTester(copycat) && !GameFunctions.isPlayerAliveAndSurvival(copycat))) {
			return;
		}
		if (activeCopies.containsKey(copycat.getUuid())) {
			copycat.sendMessage(Text.literal("You are already using a copied ability.").formatted(Formatting.GRAY), true);
			return;
		}
		List<StoredAbility> stored = storedCopies.computeIfAbsent(copycat.getUuid(), ignored -> new ArrayList<>());
		if (stored.size() >= MAX_STORED_ABILITIES) {
			copycat.sendMessage(Text.literal("You can only store 3 abilities. Open your inventory to choose one.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		long remaining = cooldownRemaining(copycat);
		if (remaining > 0L && !GexpressTestState.hasCreativeAbilityBypass(copycat)) {
			AbilityCooldownSync.send(copycat, AbilityCooldownPayload.COPYCAT_COPY, remaining,
				(long) GexpressConfig.getCopycatCopyCooldownSeconds() * 20L, false);
			copycat.sendMessage(Text.literal("Copy ready in " + secondsCeil(remaining) + "s.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		boolean testing = GexpressTestState.isRoleTester(copycat);
		ServerPlayerEntity target = requestedTargetId == null
			? AbilityTargeting.findLookTarget(copycat, world.getPlayers(),
				GexpressConfig.getCopycatCopyRange(), 0.0D, true,
				candidate -> isValidStoreTarget(copycat, candidate, testing))
			: world.getServer().getPlayerManager().getPlayer(requestedTargetId);
		if (requestedTargetId != null && !isValidStoreTarget(copycat, target, testing)) target = null;
		if (target == null) {
			copycat.sendMessage(Text.literal(requestedTargetId == null
				? "No ability close enough to copy."
				: "That ability is not available to copy.").formatted(Formatting.GRAY), true);
			return;
		}
		Role targetRole = currentRole(target);
		if (targetRole == null || targetRole.identifier() == null || !canCopyRole(targetRole.identifier())) {
			copycat.sendMessage(Text.literal("That ability cannot be copied.").formatted(Formatting.GRAY), true);
			return;
		}
		Set<Identifier> used = copiedRoles.computeIfAbsent(copycat.getUuid(), id -> new HashSet<>());
		if (used.contains(targetRole.identifier())) {
			copycat.sendMessage(Text.literal("You already copied that ability this round.").formatted(Formatting.GRAY), true);
			return;
		}
		used.add(targetRole.identifier());
		stored.add(new StoredAbility(targetRole.identifier(), target.getUuid(), target.getGameProfile().getName()));
		selectedCopies.put(copycat.getUuid(), stored.size() - 1);
		syncState(copycat, null, 0L);
		AbilityCooldownSync.clear(copycat, AbilityCooldownPayload.COPYCAT_COPY);
		copycat.sendMessage(Text.literal("Stored " + target.getName().getString() + "'s ability. Open your inventory to select it, then use your secondary ability key.")
			.formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static boolean isValidStoreTarget(ServerPlayerEntity copycat, ServerPlayerEntity target, boolean testing) {
		return target != null
			&& target != copycat
			&& target.getWorld() == copycat.getWorld()
			&& !PelicanManager.isStashed(target)
			&& (testing ? !target.isSpectator() : DeadPlayerStatus.isLivingRoundParticipant(target));
	}

	private static void selectStored(ServerPlayerEntity copycat, int index) {
		if (copycat == null) return;
		List<StoredAbility> stored = storedCopies.getOrDefault(copycat.getUuid(), List.of());
		if (stored.isEmpty()) {
			selectedCopies.remove(copycat.getUuid());
			syncState(copycat, null, 0L);
			return;
		}
		selectedCopies.put(copycat.getUuid(), clampIndex(index, stored.size()));
		syncState(copycat, null, 0L);
	}

	private static void activateStored(ServerPlayerEntity copycat, int requestedIndex) {
		if (copycat == null || !(copycat.getWorld() instanceof ServerWorld world)) return;
		if (!canUseHere(world, copycat) || !isCopycat(copycat) || PelicanManager.isStashed(copycat)
				|| (!GexpressTestState.isRoleTester(copycat) && !GameFunctions.isPlayerAliveAndSurvival(copycat))) {
			return;
		}
		if (activeCopies.containsKey(copycat.getUuid())) {
			copycat.sendMessage(Text.literal("You are already using a copied ability.").formatted(Formatting.GRAY), true);
			return;
		}
		List<StoredAbility> stored = storedCopies.get(copycat.getUuid());
		if (stored == null || stored.isEmpty()) {
			copycat.sendMessage(Text.literal("No stored ability.").formatted(Formatting.GRAY), true);
			syncState(copycat, null, 0L);
			return;
		}
		int index = clampIndex(requestedIndex >= 0 ? requestedIndex : selectedCopies.getOrDefault(copycat.getUuid(), 0), stored.size());
		StoredAbility storedAbility = stored.remove(index);
		Identifier storedRole = storedAbility.roleId();
		if (stored.isEmpty()) {
			storedCopies.remove(copycat.getUuid());
			selectedCopies.remove(copycat.getUuid());
		} else {
			selectedCopies.put(copycat.getUuid(), clampIndex(index, stored.size()));
		}
		long duration = (long) GexpressConfig.getCopycatCopyDurationSeconds() * 20L;
		activeCopies.put(copycat.getUuid(), new ActiveCopy(storedRole, world.getTime() + duration));
		syncState(copycat, storedRole, duration);
		AbilityCooldownSync.send(copycat, AbilityCooldownPayload.COPYCAT_COPY, duration, duration, true);
		copycat.sendMessage(Text.literal("Activated stored ability for "
			+ GexpressConfig.getCopycatCopyDurationSeconds() + "s.").formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD || activeCopies.isEmpty()) return;
		long now = world.getTime();
		for (UUID playerId : Set.copyOf(activeCopies.keySet())) {
			ActiveCopy copy = activeCopies.get(playerId);
			if (copy == null || now < copy.untilTick()) continue;
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			activeCopies.remove(playerId);
			if (player == null || player.getWorld() != world) continue;
			syncState(player, null, 0L);
			long cooldown = (long) GexpressConfig.getCopycatCopyCooldownSeconds() * 20L;
			if (cooldown > 0L && !GexpressTestState.hasCreativeAbilityBypass(player)) {
				cooldownUntil.put(playerId, now + cooldown);
				AbilityCooldownSync.send(player, AbilityCooldownPayload.COPYCAT_COPY, cooldown, cooldown, false);
			} else {
				cooldownUntil.remove(playerId);
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.COPYCAT_COPY);
			}
			player.sendMessage(Text.literal("Your copied ability faded.").formatted(Formatting.GRAY), true);
		}
	}

	public static boolean isBorrowingAbility(PlayerEntity player) {
		if (player == null) return false;
		if (!player.getWorld().isClient) return activeCopies.containsKey(player.getUuid());
		try {
			Class<?> state = Class.forName("dev.mapselect.client.ClientCopycatState");
			return Boolean.TRUE.equals(state.getMethod("isBorrowingAbility").invoke(null));
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static Identifier copiedRoleId(PlayerEntity player) {
		if (player == null) return null;
		if (!player.getWorld().isClient) {
			ActiveCopy copy = activeCopies.get(player.getUuid());
			if (copy == null) return null;
			long now = player.getWorld().getTime();
			return now >= copy.untilTick() ? null : copy.copiedRole();
		}
		try {
			Class<?> state = Class.forName("dev.mapselect.client.ClientCopycatState");
			Object value = state.getMethod("copiedRoleId").invoke(null);
			return value instanceof Identifier id ? id : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static boolean isCopyingRole(PlayerEntity player, Identifier roleId) {
		Identifier copied = copiedRoleId(player);
		return copied != null && copied.equals(roleId);
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		java.util.List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		java.util.List<ServerPlayerEntity> copycats = alive.stream()
			.filter(CopycatManager::isCopycat)
			.toList();
		if (copycats.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == 1 && alive.getFirst() == copycats.getFirst()) {
			ServerPlayerEntity winner = alive.getFirst();
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.copycat",
				MapSelectRoles.COPYCAT == null ? 0x9B7BEA : MapSelectRoles.COPYCAT.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static boolean canCopyRole(Identifier roleId) {
		if (roleId == null) return false;
		if (MapSelectRoles.COPYCAT_ID.equals(roleId)) return false;
		if (MapSelectRoles.GODFATHER_ID.equals(roleId) || MapSelectRoles.MAFIOSO_ID.equals(roleId)
				|| MapSelectRoles.JANITOR_ID.equals(roleId) || MapSelectRoles.PICKPOCKET_ID.equals(roleId)
				|| MapSelectRoles.BURGLAR_ID.equals(roleId)) {
			return false;
		}
		if (WatheRoles.KILLER.identifier().equals(roleId) || WatheRoles.CIVILIAN.identifier().equals(roleId)
				|| WatheRoles.VIGILANTE.identifier().equals(roleId) || WatheRoles.LOOSE_END.identifier().equals(roleId)
				|| WatheRoles.DISCOVERY_CIVILIAN.identifier().equals(roleId)) {
			return false;
		}
		return true;
	}

	private static boolean isCopycat(ServerPlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.COPYCAT_ID.equals(role.identifier());
	}

	private static Role currentRole(ServerPlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		return game == null ? null : game.getRole(player);
	}

	private static boolean canUseHere(World world, ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
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

	private static void syncState(ServerPlayerEntity player, Identifier roleId, long remainingTicks) {
		if (player != null && ServerPlayNetworking.canSend(player, CopycatStatePayload.ID)) {
			List<StoredAbility> stored = storedCopies.getOrDefault(player.getUuid(), List.of());
			int selected = selectedCopies.getOrDefault(player.getUuid(), 0);
			ServerPlayNetworking.send(player, new CopycatStatePayload(roleId != null, roleId, remainingTicks, stored, selected));
		}
	}

	private static void syncClear(ServerPlayerEntity player) {
		if (player != null && ServerPlayNetworking.canSend(player, CopycatStatePayload.ID)) {
			ServerPlayNetworking.send(player, CopycatStatePayload.clear());
		}
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private static void clearAll() {
		activeCopies.clear();
		storedCopies.clear();
		selectedCopies.clear();
		cooldownUntil.clear();
		copiedRoles.clear();
	}

	private static int clampIndex(int index, int size) {
		if (size <= 0) return 0;
		return Math.max(0, Math.min(size - 1, index));
	}

	private record ActiveCopy(Identifier copiedRole, long untilTick) {}
}
