package dev.mapselect.modifier;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.LoversStatePayload;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.role.RoleTeams;
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
import org.agmas.harpymodloader.events.ModifierAssigned;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LoversManager {
	private static final Map<UUID, UUID> PARTNERS = new HashMap<>();
	private static final Map<UUID, UUID> PENDING_SOUL_DEATHS = new HashMap<>();
	private static final Set<UUID> SOUL_LINK_DEATHS = new HashSet<>();
	private static int syncTicker;

	private LoversManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(LoversStatePayload.ID, LoversStatePayload.CODEC);
		GameEvents.ON_FINISH_INITIALIZE.register(LoversManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
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

	public static boolean isLinked(UUID playerId) {
		return playerId != null && PARTNERS.containsKey(playerId);
	}

	private static void initializeRound(World world, GameWorldComponent game) {
		clear(world);
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
				link(first, second, true);
			}
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to initialize Lovers links.", t);
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
		if (first == null || second == null || first == second) return;
		PARTNERS.put(first.getUuid(), second.getUuid());
		PARTNERS.put(second.getUuid(), first.getUuid());
		if (addModifier) {
			addLoversModifier(first);
			addLoversModifier(second);
		}
		sync(first);
		sync(second);
	}

	private static void addLoversModifier(ServerPlayerEntity player) {
		if (player == null || MapSelectModifiers.LOVERS == null || ModifierUtils.has(player, MapSelectModifiers.LOVERS_ID)) {
			return;
		}
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return;
		component.addModifier(player.getUuid(), MapSelectModifiers.LOVERS);
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, MapSelectModifiers.LOVERS);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed while adding Lovers to {}.",
				player.getName().getString(), t);
		}
		component.sync();
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity serverVictim)) return true;
		UUID victimId = serverVictim.getUuid();
		if (SOUL_LINK_DEATHS.contains(victimId)) return true;
		UUID partnerId = PARTNERS.get(victimId);
		if (partnerId == null) return true;
		ServerPlayerEntity partner = serverVictim.getServer().getPlayerManager().getPlayer(partnerId);
		if (isLiving(partner)) {
			PENDING_SOUL_DEATHS.put(partnerId, victimId);
		}
		return true;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || (!game.isRunning() && !GexpressTestState.hasModifierTesters())) {
			if (!PARTNERS.isEmpty()) clear(world);
			return;
		}
		processSoulDeaths(world);
		if (++syncTicker >= 20) {
			syncTicker = 0;
			for (ServerPlayerEntity player : world.getPlayers()) sync(player);
		}
	}

	private static void processSoulDeaths(ServerWorld world) {
		if (PENDING_SOUL_DEATHS.isEmpty()) return;
		Map<UUID, UUID> pending = new HashMap<>(PENDING_SOUL_DEATHS);
		PENDING_SOUL_DEATHS.clear();
		for (Map.Entry<UUID, UUID> entry : pending.entrySet()) {
			ServerPlayerEntity partner = world.getServer().getPlayerManager().getPlayer(entry.getKey());
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

	private static void sync(ServerPlayerEntity player) {
		if (player == null || !ServerPlayNetworking.canSend(player, LoversStatePayload.ID)) return;
		UUID partnerId = PARTNERS.get(player.getUuid());
		if (partnerId == null) {
			ServerPlayNetworking.send(player, LoversStatePayload.clear());
			return;
		}
		ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
		String name = partner == null ? "" : partner.getName().getString();
		ServerPlayNetworking.send(player, new LoversStatePayload(true, partnerId, name));
	}

	private static void clear(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				if (ServerPlayNetworking.canSend(player, LoversStatePayload.ID)) {
					ServerPlayNetworking.send(player, LoversStatePayload.clear());
				}
			}
		}
		PARTNERS.clear();
		PENDING_SOUL_DEATHS.clear();
		SOUL_LINK_DEATHS.clear();
		syncTicker = 0;
	}
}
