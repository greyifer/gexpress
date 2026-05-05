package dev.mapselect.game;

import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.AltruistUsePayload;
import dev.mapselect.network.MafiaActionPayload;
import dev.mapselect.network.MedicShieldUsePayload;
import dev.mapselect.network.PuppetmasterInputPayload;
import dev.mapselect.network.PuppetmasterSelectPayload;
import dev.mapselect.network.PuppetmasterUsePayload;
import dev.mapselect.network.ScatterBrainUsePayload;
import dev.mapselect.network.ShadowMarchUsePayload;
import dev.mapselect.network.SkincrawlerUsePayload;
import dev.mapselect.network.SpyUsePayload;
import dev.mapselect.network.TimeMasterFreezeUsePayload;
import dev.mapselect.network.TimeMasterUsePayload;
import dev.mapselect.network.TrackerUsePayload;
import dev.mapselect.network.TricksterDancingCartsPayload;
import dev.mapselect.network.TricksterUsePayload;
import dev.mapselect.network.VultureEatPayload;
import dev.mapselect.network.VultureReleasePayload;
import dev.mapselect.network.WarlockKillPayload;
import dev.mapselect.network.WarlockMarkPayload;
import dev.mapselect.testing.GexpressTestState;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.Set;

public final class GexpressAbilityGuards {
	private static final int SAFE_PREPARATION_TICKS = 30 * 20;
	private static final Set<CustomPayload.Id<?>> ABILITY_PAYLOADS = Set.of(
		AltruistUsePayload.ID,
		MafiaActionPayload.ID,
		MedicShieldUsePayload.ID,
		PuppetmasterInputPayload.ID,
		PuppetmasterSelectPayload.ID,
		PuppetmasterUsePayload.ID,
		ScatterBrainUsePayload.ID,
		ShadowMarchUsePayload.ID,
		SkincrawlerUsePayload.ID,
		SpyUsePayload.ID,
		TimeMasterFreezeUsePayload.ID,
		TimeMasterUsePayload.ID,
		TrackerUsePayload.ID,
		TricksterDancingCartsPayload.ID,
		TricksterUsePayload.ID,
		VultureEatPayload.ID,
		VultureReleasePayload.ID,
		WarlockKillPayload.ID,
		WarlockMarkPayload.ID
	);

	private GexpressAbilityGuards() {}

	public static boolean isSafePreparation(World world) {
		if (world == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		GameTimeComponent time = GameTimeComponent.KEY.getNullable(world);
		if (time == null || time.resetTime <= 0 || time.getTime() <= 0) return false;
		int elapsed = time.resetTime - time.getTime();
		return elapsed >= 0 && elapsed < SAFE_PREPARATION_TICKS;
	}

	public static boolean shouldBlockAbilityPayload(ServerPlayerEntity player, CustomPayload payload) {
		return player != null && payload != null
			&& !GexpressTestState.isRoleTester(player)
			&& isSafePreparation(player.getWorld())
			&& ABILITY_PAYLOADS.contains(payload.getId());
	}
}
