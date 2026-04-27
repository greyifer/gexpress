package dev.mapselect.role.trickster;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.TricksterSkinSwapPayload;
import dev.mapselect.network.TricksterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TricksterManager {
	private static final Map<net.minecraft.registry.RegistryKey<World>, ActiveSwap> activeSwaps = new ConcurrentHashMap<>();
	private static final Map<net.minecraft.registry.RegistryKey<World>, Map<UUID, UUID>> previousSwaps = new ConcurrentHashMap<>();
	private static final Map<net.minecraft.registry.RegistryKey<World>, Long> nextNoellesCycleCleanupTicks = new ConcurrentHashMap<>();
	private static NoellesMorphBridge noellesMorphBridge;
	private static boolean lookedUpNoellesMorphBridge;

	private TricksterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TricksterUsePayload.ID, TricksterUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TricksterSkinSwapPayload.ID, TricksterSkinSwapPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TricksterUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryActivate(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(TricksterManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				clearNoellesMorphCycles(handler.player.getServerWorld());
				syncActiveSwap(handler.player);
			}));
	}

	private static void tryActivate(ServerPlayerEntity trickster) {
		if (trickster == null || trickster.getWorld().isClient) return;
		if (VultureManager.isStashed(trickster)) return;
		if (!canUseTricksterHere(trickster.getWorld(), trickster) || !isTrickster(trickster)) return;
		if (!isPlayableForTrickster(trickster, trickster)) return;

		ServerWorld world = trickster.getServerWorld();
		ActiveSwap active = activeSwaps.get(world.getRegistryKey());
		if (active != null && active.isActive(world)) {
			trickster.sendMessage(Text.literal("Masquerade ready in " + secondsCeil(active.remainingTicks(world)) + "s."), true);
			return;
		}

		List<ServerPlayerEntity> players = eligiblePlayers(world, trickster);
		if (players.size() < 2) {
			trickster.sendMessage(Text.literal("Masquerade needs at least two players."), true);
			return;
		}

		Map<UUID, UUID> swaps = buildDerangement(players, previousSwaps.get(world.getRegistryKey()));
		previousSwaps.put(world.getRegistryKey(), swaps);
		int durationTicks = GexpressConfig.getTricksterSwapDurationSeconds() * 20;
		activeSwaps.put(world.getRegistryKey(), new ActiveSwap(world.getTime() + durationTicks, swaps));

		TricksterSkinSwapPayload payload = new TricksterSkinSwapPayload(swaps, durationTicks);
		for (ServerPlayerEntity player : world.getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}

		world.playSound(null, trickster.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
			SoundCategory.PLAYERS, 0.85F, 1.65F);
		trickster.sendMessage(Text.literal("Masquerade."), true);
	}

	private static List<ServerPlayerEntity> eligiblePlayers(ServerWorld world, ServerPlayerEntity trickster) {
		boolean testing = GexpressTestState.isRoleTester(trickster);
		return world.getPlayers(player ->
			testing ? !player.isSpectator() : GameFunctions.isPlayerAliveAndSurvival(player));
	}

	private static Map<UUID, UUID> buildDerangement(List<ServerPlayerEntity> players, Map<UUID, UUID> previous) {
		List<UUID> targets = new ArrayList<>(players.size());
		for (ServerPlayerEntity player : players) targets.add(player.getUuid());

		for (int attempt = 0; attempt < 32; attempt++) {
			List<UUID> sources = new ArrayList<>(targets);
			Collections.shuffle(sources);
			if (isValidDerangement(targets, sources, previous)) {
				return toSwapMap(targets, sources);
			}
		}

		List<UUID> sources = new ArrayList<>(targets);
		Collections.shuffle(sources);
		for (int i = 0; i < sources.size(); i++) {
			if (!sources.get(i).equals(targets.get(i))) continue;
			int swapWith = (i + 1) % sources.size();
			Collections.swap(sources, i, swapWith);
		}

		return toSwapMap(targets, sources);
	}

	private static boolean isValidDerangement(List<UUID> targets, List<UUID> sources, Map<UUID, UUID> previous) {
		boolean avoidPrevious = previous != null && targets.size() > 2;
		for (int i = 0; i < targets.size(); i++) {
			UUID target = targets.get(i);
			UUID replacement = sources.get(i);
			if (replacement.equals(target)) return false;
			if (avoidPrevious && replacement.equals(previous.get(target))) return false;
		}
		return true;
	}

	private static Map<UUID, UUID> toSwapMap(List<UUID> targets, List<UUID> sources) {
		Map<UUID, UUID> swaps = new LinkedHashMap<>();
		for (int i = 0; i < targets.size(); i++) {
			UUID target = targets.get(i);
			UUID replacement = sources.get(i);
			swaps.put(target, replacement.equals(target) ? targets.get((i + 1) % targets.size()) : replacement);
		}
		return swaps;
	}

	private static void tick(ServerWorld world) {
		clearNoellesMorphCyclesIfDue(world);

		ActiveSwap active = activeSwaps.get(world.getRegistryKey());
		if (active == null) return;

		boolean activeGame = isActiveGame(world);
		if (world.getTime() >= active.untilTick() || (!activeGame && !GexpressTestState.hasRoleTesters())) {
			activeSwaps.remove(world.getRegistryKey());
			broadcastClear(world);
		}
	}

	private static void broadcastClear(ServerWorld world) {
		TricksterSkinSwapPayload clear = new TricksterSkinSwapPayload(Map.of(), 0);
		for (ServerPlayerEntity player : world.getPlayers()) {
			ServerPlayNetworking.send(player, clear);
		}
	}

	private static void syncActiveSwap(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		ActiveSwap active = activeSwaps.get(world.getRegistryKey());
		if (active == null || !active.isActive(world)) return;
		ServerPlayNetworking.send(player, new TricksterSkinSwapPayload(active.swaps(),
			(int) Math.min(Integer.MAX_VALUE, active.remainingTicks(world))));
	}

	public static boolean isGlobalMuteActive(ServerWorld world) {
		if (world == null) return false;
		ActiveSwap active = activeSwaps.get(world.getRegistryKey());
		return active != null && active.isActive(world);
	}

	public static void clearForTimeRewind(ServerWorld world) {
		if (world == null) return;
		activeSwaps.remove(world.getRegistryKey());
		nextNoellesCycleCleanupTicks.remove(world.getRegistryKey());
		clearNoellesMorphCycles(world);
		broadcastClear(world);
	}

	private static boolean isTrickster(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.TRICKSTER_ID.equals(role.identifier());
	}

	private static boolean canUseTricksterHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean isPlayableForTrickster(PlayerEntity player, PlayerEntity trickster) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(trickster);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private static void clearNoellesMorphCyclesIfDue(ServerWorld world) {
		long now = world.getTime();
		Long next = nextNoellesCycleCleanupTicks.get(world.getRegistryKey());
		if (next != null && now < next) return;
		nextNoellesCycleCleanupTicks.put(world.getRegistryKey(), now + 20L);
		clearNoellesMorphCycles(world);
	}

	private static void clearNoellesMorphCycles(ServerWorld world) {
		NoellesMorphBridge bridge = noellesMorphBridge();
		if (bridge == null) return;

		Map<UUID, UUID> morphs = new HashMap<>();
		Map<UUID, ServerPlayerEntity> playersById = new HashMap<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID playerId = player.getUuid();
			UUID disguise = bridge.disguise(player);
			if (disguise != null && !disguise.equals(playerId) && bridge.morphTicks(player) > 0) {
				morphs.put(playerId, disguise);
				playersById.put(playerId, player);
			}
		}
		if (morphs.size() < 2) return;

		Set<UUID> cycleIds = new HashSet<>();
		for (UUID playerId : morphs.keySet()) {
			collectMorphCycle(playerId, morphs, cycleIds);
		}

		for (UUID playerId : cycleIds) {
			ServerPlayerEntity player = playersById.get(playerId);
			if (player != null && player.getWorld() == world) {
				bridge.stop(player);
			}
		}
	}

	private static void collectMorphCycle(UUID start, Map<UUID, UUID> morphs, Set<UUID> cycleIds) {
		Set<UUID> path = new LinkedHashSet<>();
		UUID current = start;
		while (current != null && morphs.containsKey(current)) {
			if (!path.add(current)) {
				boolean inCycle = false;
				for (UUID playerId : path) {
					if (playerId.equals(current)) inCycle = true;
					if (inCycle) cycleIds.add(playerId);
				}
				return;
			}
			current = morphs.get(current);
		}
	}

	private static NoellesMorphBridge noellesMorphBridge() {
		if (!lookedUpNoellesMorphBridge) {
			lookedUpNoellesMorphBridge = true;
			try {
				Class<?> componentClass = Class.forName("org.agmas.noellesroles.morphling.MorphlingPlayerComponent");
				Object key = componentClass.getField("KEY").get(null);
				if (key instanceof ComponentKey<?> componentKey) {
					noellesMorphBridge = new NoellesMorphBridge(
						componentKey,
						componentClass.getMethod("getMorphTicks"),
						componentClass.getField("disguise"),
						componentClass.getMethod("stopMorph"),
						componentClass.getMethod("sync")
					);
				}
			} catch (Throwable ignored) {
				noellesMorphBridge = null;
			}
		}
		return noellesMorphBridge;
	}

	private record NoellesMorphBridge(ComponentKey<?> key, Method getMorphTicks, Field disguise,
			Method stopMorph, Method sync) {
		private int morphTicks(ServerPlayerEntity player) {
			try {
				Object comp = key.get(player);
				Object value = getMorphTicks.invoke(comp);
				return value instanceof Number number ? number.intValue() : 0;
			} catch (Throwable ignored) {
				return 0;
			}
		}

		private UUID disguise(ServerPlayerEntity player) {
			try {
				Object comp = key.get(player);
				Object value = disguise.get(comp);
				return value instanceof UUID uuid ? uuid : null;
			} catch (Throwable ignored) {
				return null;
			}
		}

		private void stop(ServerPlayerEntity player) {
			try {
				Object comp = key.get(player);
				stopMorph.invoke(comp);
				sync.invoke(comp);
			} catch (Throwable ignored) {
			}
		}
	}

	private record ActiveSwap(long untilTick, Map<UUID, UUID> swaps) {
		private boolean isActive(ServerWorld world) {
			return world != null && world.getTime() < untilTick;
		}

		private long remainingTicks(ServerWorld world) {
			return world == null ? 0L : Math.max(0L, untilTick - world.getTime());
		}
	}
}
