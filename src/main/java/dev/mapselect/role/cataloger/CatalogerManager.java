package dev.mapselect.role.cataloger;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.GexpressWorlds;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.cataloger.CatalogerDossierPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class CatalogerManager {
	private static final Map<UUID, Dossier> DOSSIERS = new HashMap<>();

	private CatalogerManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(CatalogerDossierPayload.ID, CatalogerDossierPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(CatalogerManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		RoundLifecycleHooks.clearWorld(CatalogerManager::clearWorld);
	}

	private static void tick(ServerWorld world) {
		if (!GexpressWorlds.isGameWorld(world) || world.getTime() % 10L != 0L) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearWorld(world);
			return;
		}
		if (game == null) return;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isCataloger(game, player) && isPlayable(player)) {
				Dossier dossier = DOSSIERS.get(player.getUuid());
				if (dossier == null) {
					dossier = createDossier(player, world, game);
					if (dossier != null) DOSSIERS.put(player.getUuid(), dossier);
				}
				if (dossier != null) sync(player, dossier);
			} else {
				if (!isCataloger(game, player)) DOSSIERS.remove(player.getUuid());
				PayloadSync.sendIfChanged(player, CatalogerDossierPayload.ID,
					CatalogerDossierPayload.clear());
			}
		}
	}

	private static Dossier createDossier(ServerPlayerEntity cataloger, ServerWorld world,
			GameWorldComponent game) {
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == cataloger || !isPlayable(player) || game.getRole(player) == null) continue;
			candidates.add(player);
		}
		if (candidates.size() < 5) return null;

		Random random = new Random(world.getSeed() ^ world.getTime()
			^ cataloger.getUuid().getMostSignificantBits() ^ cataloger.getUuid().getLeastSignificantBits());
		Collections.shuffle(candidates, random);
		List<ServerPlayerEntity> chosen = new ArrayList<>(candidates.subList(0, 5));
		ServerPlayerEntity exact = chosen.get(random.nextInt(chosen.size()));

		Role exactRole = game.getRole(exact);
		Identifier roleId = exactRole == null ? null : exactRole.identifier();
		if (roleId == null) return null;
		List<CatalogerDossierPayload.Entry> entries = chosen.stream()
			.map(player -> new CatalogerDossierPayload.Entry(player.getUuid(), player.getName().getString()))
			.toList();
		int evilCount = (int) chosen.stream()
			.filter(player -> isEvil(game.getRole(player))).count();
		int civilianCount = chosen.size() - evilCount;
		return new Dossier(entries, evilCount, civilianCount,
			roleId.getNamespace(), roleId.getPath());
	}

	private static boolean isEvil(Role role) {
		// Cataloger treats every non-civilian alignment, including neutrals, as evil.
		return role != null && !role.isInnocent();
	}

	private static boolean isCataloger(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game == null || player == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.CATALOGER_ID.equals(role.identifier());
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GexpressTestState.isRoleTester(player) || DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static void sync(ServerPlayerEntity player) {
		Dossier dossier = player == null ? null : DOSSIERS.get(player.getUuid());
		if (dossier == null) {
			if (player != null) PayloadSync.sendIfChanged(player, CatalogerDossierPayload.ID,
				CatalogerDossierPayload.clear());
			return;
		}
		sync(player, dossier);
	}

	private static void sync(ServerPlayerEntity player, Dossier dossier) {
		PayloadSync.sendIfChanged(player, CatalogerDossierPayload.ID,
			new CatalogerDossierPayload(true, dossier.passengers(),
				dossier.evilCount(), dossier.civilianCount(),
				dossier.exactRoleNamespace(), dossier.exactRolePath()));
	}

	private static void clearWorld(World world) {
		DOSSIERS.clear();
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				PayloadSync.sendIfChanged(player, CatalogerDossierPayload.ID,
					CatalogerDossierPayload.clear());
			}
		}
	}

	private record Dossier(List<CatalogerDossierPayload.Entry> passengers,
			int evilCount, int civilianCount, String exactRoleNamespace,
			String exactRolePath) {}
}
