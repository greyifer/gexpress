package dev.mapselect.role.cupid;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.CupidUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
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
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CupidManager {
	private static final Map<UUID, UUID> FIRST_TARGETS = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static int winCheckTicker;

	private CupidManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CupidUsePayload.ID, CupidUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CupidUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryUse(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(CupidManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear());
	}

	public static void reduceCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return;
		UUID id = player.getUuid();
		Long until = COOLDOWN_UNTIL.get(id);
		if (until == null) return;
		COOLDOWN_UNTIL.put(id, Math.max(0L, until - ticks));
	}

	private static void tryUse(ServerPlayerEntity cupid) {
		if (cupid == null || !(cupid.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || !isCupid(game, cupid) || PelicanManager.isStashed(cupid)
				|| !canUseHere(world, cupid) || !isPlayable(cupid)) {
			return;
		}

		long now = world.getTime();
		long remaining = COOLDOWN_UNTIL.getOrDefault(cupid.getUuid(), 0L) - now;
		if (remaining > 0L && !GexpressTestState.hasCreativeAbilityBypass(cupid)) {
			AbilityCooldownSync.send(cupid, AbilityCooldownPayload.CUPID_LINK, remaining,
				GexpressConfig.getCupidPairCooldownSeconds() * 20L, false);
			return;
		}

		ServerPlayerEntity target = AbilityTargeting.findLookTarget(cupid, world.getPlayers(),
			GexpressConfig.getCupidRange(), 0.25D, true, candidate ->
				candidate != cupid && !PelicanManager.isStashed(candidate) && isPlayable(candidate));
		if (target == null) {
			cupid.sendMessage(Text.literal("No living player close enough to charm.").formatted(Formatting.RED), true);
			return;
		}
		if (LoversManager.isLinked(target.getUuid())) {
			cupid.sendMessage(Text.literal(target.getName().getString() + " is already in love.")
				.formatted(Formatting.RED), true);
			return;
		}

		UUID firstId = FIRST_TARGETS.get(cupid.getUuid());
		if (firstId == null) {
			FIRST_TARGETS.put(cupid.getUuid(), target.getUuid());
			cupid.sendMessage(Text.literal("First lover selected: " + target.getName().getString() + ".")
				.formatted(Formatting.LIGHT_PURPLE), true);
			return;
		}
		if (firstId.equals(target.getUuid())) {
			cupid.sendMessage(Text.literal("Pick a different second lover.").formatted(Formatting.RED), true);
			return;
		}

		ServerPlayerEntity first = world.getServer().getPlayerManager().getPlayer(firstId);
		FIRST_TARGETS.remove(cupid.getUuid());
		if (first == null || !isPlayable(first) || LoversManager.isLinked(first.getUuid())) {
			cupid.sendMessage(Text.literal("Your first lover is no longer available.").formatted(Formatting.RED), true);
			return;
		}
		if (LoversManager.createPair(cupid, first, target) && !GexpressTestState.hasCreativeAbilityBypass(cupid)) {
			long total = GexpressConfig.getCupidPairCooldownSeconds() * 20L;
			COOLDOWN_UNTIL.put(cupid.getUuid(), now + total);
			AbilityCooldownSync.send(cupid, AbilityCooldownPayload.CUPID_LINK, total, total, false);
		}
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (++winCheckTicker < 20) return;
		winCheckTicker = 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		int alivePairs = LoversManager.alivePairCount(world);
		if (alivePairs < GexpressConfig.getCupidRequiredAlivePairs()) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isCupid(game, player) || !isPlayable(player)) continue;
			game.setLooseEndWinner(player.getUuid());
			NeutralWinManager.announce(world, player, "announcement.win.gexpress.cupid",
				MapSelectRoles.CUPID == null ? 0xF06AA8 : MapSelectRoles.CUPID.color());
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(),
				GameFunctions.WinStatus.LOOSE_END);
			GameFunctions.stopGame(world);
			return;
		}
	}

	private static boolean isCupid(GameWorldComponent game, PlayerEntity player) {
		Role role = game == null || player == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.CUPID_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.CUPID_ID));
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return (GameFunctions.isPlayerAliveAndSurvival(player) && DeadPlayerStatus.isLivingRoundParticipant(player))
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static void clear() {
		FIRST_TARGETS.clear();
		COOLDOWN_UNTIL.clear();
		winCheckTicker = 0;
	}
}
