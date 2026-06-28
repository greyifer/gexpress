package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.network.role.altruist.AltruistUsePayload;
import dev.mapselect.network.role.copycat.CopycatActionPayload;
import dev.mapselect.network.role.covenant.CovenantBatMovementPayload;
import dev.mapselect.network.role.covenant.CovenantBatPayload;
import dev.mapselect.network.role.covenant.CovenantBitePayload;
import dev.mapselect.network.role.cupid.CupidUsePayload;
import dev.mapselect.network.role.guardian.GuardianAngelShieldUsePayload;
import dev.mapselect.network.role.mafia.MafiaActionPayload;
import dev.mapselect.network.role.medic.MedicShieldUsePayload;
import dev.mapselect.network.role.painter.PainterBodyUsePayload;
import dev.mapselect.network.role.painter.PainterChoiceSubmitPayload;
import dev.mapselect.network.role.painter.PainterDoorwayUsePayload;
import dev.mapselect.network.role.painter.PainterPlayerUsePayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterInputPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterSelectPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterUsePayload;
import dev.mapselect.network.role.scatterbrain.ScatterBrainUsePayload;
import dev.mapselect.network.role.silent.ShadowMarchUsePayload;
import dev.mapselect.network.role.seer.SeerCompareUsePayload;
import dev.mapselect.network.role.skincrawler.SkincrawlerUsePayload;
import dev.mapselect.network.role.spy.SpyUsePayload;
import dev.mapselect.network.role.timemaster.TimeMasterFreezeUsePayload;
import dev.mapselect.network.role.timemaster.TimeMasterUsePayload;
import dev.mapselect.network.role.tracker.TrackerUsePayload;
import dev.mapselect.network.role.twins.TwinsSwapPayload;
import dev.mapselect.network.role.harlequin.TricksterDancingCartsPayload;
import dev.mapselect.network.role.harlequin.TricksterUsePayload;
import dev.mapselect.network.role.pelican.VultureEatPayload;
import dev.mapselect.network.role.pelican.VultureReleasePayload;
import dev.mapselect.network.role.warlock.WarlockKillPayload;
import dev.mapselect.network.role.warlock.WarlockMarkPayload;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.testing.GexpressTestState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GexpressAbilityGuards {
	private static final long LOG_THROTTLE_MILLIS = 5000L;
	private static final Map<String, Long> LAST_BLOCK_LOG = new ConcurrentHashMap<>();
	private static final Set<CustomPayload.Id<?>> ABILITY_PAYLOADS = Set.of(
		AltruistUsePayload.ID,
		CopycatActionPayload.ID,
		CovenantBatMovementPayload.ID,
		CovenantBatPayload.ID,
		CovenantBitePayload.ID,
		CupidUsePayload.ID,
		GuardianAngelShieldUsePayload.ID,
		MafiaActionPayload.ID,
		MedicShieldUsePayload.ID,
		PainterBodyUsePayload.ID,
		PainterChoiceSubmitPayload.ID,
		PainterDoorwayUsePayload.ID,
		PainterPlayerUsePayload.ID,
		PuppetmasterInputPayload.ID,
		PuppetmasterSelectPayload.ID,
		PuppetmasterUsePayload.ID,
		ScatterBrainUsePayload.ID,
		SeerCompareUsePayload.ID,
		ShadowMarchUsePayload.ID,
		SkincrawlerUsePayload.ID,
		SpyUsePayload.ID,
		TimeMasterFreezeUsePayload.ID,
		TimeMasterUsePayload.ID,
		TrackerUsePayload.ID,
		TwinsSwapPayload.ID,
		TricksterDancingCartsPayload.ID,
		TricksterUsePayload.ID,
		VultureEatPayload.ID,
		VultureReleasePayload.ID,
		WarlockKillPayload.ID,
		WarlockMarkPayload.ID
	);

	private GexpressAbilityGuards() {}

	public static boolean isSafePreparation(World world) {
		return KinsWatheSafePreparation.isActive(world);
	}

	public static boolean shouldBlockItemAbility(PlayerEntity player) {
		GuardResult result = checkPlayer(player, "item");
		if (result.allowed()) return false;
		notifyAndLog(player, "item", result);
		return true;
	}

	public static boolean shouldBlockAbilityPayload(ServerPlayerEntity player, CustomPayload payload) {
		GuardResult result = checkAbilityPayload(player, payload);
		if (result.allowed()) return false;
		notifyAndLog(player, payload == null ? "payload" : payload.getId().id().toString(), result);
		return true;
	}

	public static GuardResult checkAbilityPayload(ServerPlayerEntity player, CustomPayload payload) {
		if (player == null || payload == null || !ABILITY_PAYLOADS.contains(payload.getId())) {
			return GuardResult.allow();
		}
		return checkPlayer(player, payload.getId().id().toString());
	}

	public static GuardResult checkItemAbility(PlayerEntity player) {
		return checkPlayer(player, "item");
	}

	private static GuardResult checkPlayer(PlayerEntity player, String abilityName) {
		if (player == null || GexpressTestState.hasCreativeAbilityBypass(player)) return GuardResult.allow();
		if (DeadPlayerStatus.isDeadRoundParticipant(player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null)) {
			return GuardResult.block(BlockReason.DEAD, "You cannot use abilities while dead.");
		}
		if (player instanceof ServerPlayerEntity serverPlayer && TimeMasterManager.isRewinding(serverPlayer)) {
			return GuardResult.block(BlockReason.TIME_REWIND, "You cannot use abilities during a rewind.");
		}
		if (isSafePreparation(player.getWorld())) {
			return GuardResult.block(BlockReason.SAFE_PREPARATION, "Abilities are disabled during safe preparation.");
		}
		if (!isActiveRound(player.getWorld())) {
			return GuardResult.block(BlockReason.ROUND_NOT_ACTIVE, "Abilities can only be used during an active round.");
		}
		return GuardResult.allow();
	}

	private static boolean isActiveRound(World world) {
		GameWorldComponent game = world == null ? null : GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static void notifyAndLog(PlayerEntity player, String abilityName, GuardResult result) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(Text.literal(result.message()).formatted(Formatting.RED), true);
		}
		String playerName = player == null ? "unknown" : player.getName().getString();
		String key = playerName + "|" + abilityName + "|" + result.reason();
		long now = System.currentTimeMillis();
		long last = LAST_BLOCK_LOG.getOrDefault(key, 0L);
		if (now - last < LOG_THROTTLE_MILLIS) return;
		LAST_BLOCK_LOG.put(key, now);
		MapSelect.LOGGER.info("Blocked G'Express ability '{}' for {}: {}", abilityName, playerName, result.reason().id());
	}

	public enum BlockReason {
		DEAD("dead"),
		TIME_REWIND("time_rewind"),
		SAFE_PREPARATION("safe_preparation"),
		ROUND_NOT_ACTIVE("round_not_active");

		private final String id;

		BlockReason(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	public record GuardResult(boolean allowed, BlockReason reason, String message) {
		public static GuardResult allow() {
			return new GuardResult(true, null, "");
		}

		public static GuardResult block(BlockReason reason, String message) {
			return new GuardResult(false, reason, message == null || message.isBlank()
				? "That ability cannot be used right now." : message);
		}
	}
}
