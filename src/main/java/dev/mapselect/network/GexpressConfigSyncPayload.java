package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bidirectional snapshot of live G'Express settings. */
public record GexpressConfigSyncPayload(int c4Price, int c4FuseSeconds, int c4FirstBeepSeconds, int wrongWirePercent,
		int grenadePrice, int medicShieldCooldownSeconds, boolean medicShieldKnifeBreaks,
		int silentShadowDurationSeconds, int silentShadowCooldownSeconds,
		int warlockMarkCooldownSeconds, int warlockKillCooldownSeconds,
		int juggernautInitialCooldownSeconds, int juggernautCooldownReductionSeconds,
		int juggernautMinimumCooldownSeconds, int juggernautShieldRechargeSeconds,
		int tricksterSwapDurationSeconds, int tricksterMasqueradeCooldownSeconds,
		int tricksterDancingCartsCooldownSeconds,
		int tricksterDancingCartsMaxUses,
		int puppetmasterControlDurationSeconds, int puppetmasterControlCooldownSeconds,
		boolean puppetmasterRandomTarget, int puppetmasterControlRange, int puppetmasterMaxUses,
		int pelicanEatCooldownSeconds, int pelicanEatPercentage,
		int hungryFoodLimit, int thirstyDrinkLimit, int snitchTasksRequired,
		int snitchWarningTasksRemaining,
		int timeMasterRewindSeconds, int timeMasterCooldownSeconds, int timeMasterMaxUses,
		int timeMasterFreezeDurationSeconds, int timeMasterFreezeCooldownSeconds,
		int timeMasterFreezeMaxUses, int timeMasterFreezeRange,
		int scatterBrainCooldownSeconds, int trackerMaxTargets,
		int trackerRange, int trackerCooldownSeconds, int altruistRange,
		int skincrawlerBodyMaxAgeSeconds, int skincrawlerCooldownSeconds, int skincrawlerStunSeconds,
		int skincrawlerRange, int spyBugCost, int spyBugDurationSeconds, int spyBugRange,
		int squeakerPitchPercent, int masqueradePitchMinPercent, int masqueradePitchMaxPercent,
		boolean lastDeathShieldEnabled,
		int bountyHunterBountyIntervalSeconds, int bountyHunterRewardGold, int bountyHunterFailCooldownSeconds,
		int godfatherBulletPrice, int godfatherStartingBullets, int godfatherMaxLoadedBullets, int mafiaStartingGold,
		int mafiaMinimumPlayers, int godfatherStartingGold, int mafiosoStartingGold, int janitorStartingGold,
		int mafiaRecruitRange, int mafiaReplacementCooldownSeconds, int mafiaRevolverKillCooldownSeconds,
		int janitorCleanRange, int janitorCleanCooldownSeconds, int janitorRevolverCooldownAfterCleanSeconds,
		int janitorCleanCooldownAfterKillSeconds,
		boolean useCustomRoleCounts, int maxKillerAmount, int maxVigilanteAmount,
		int playersPerKiller, int playersPerVigilante,
		float c4BackOffsetX, float c4BackOffsetY, float c4BackOffsetZ,
		float c4BackRotationX, float c4BackRotationY, float c4BackRotationZ,
		float c4BackSlant, float c4BackScale,
		float spyBugOffsetX, float spyBugOffsetY, float spyBugOffsetZ,
		float spyBugRotationX, float spyBugRotationY, float spyBugRotationZ,
		float spyBugSlant, float spyBugScale, String c4PlacementPresets,
		String roleDescriptionOverrides,
		float shortSightedFogRange,
		int medicShieldBlockFlashTicks, int medicShieldBreakFlashTicks,
		int medicShieldBlockFlashAlpha, int medicShieldBreakFlashAlpha,
		float silentShadowAlpha) implements CustomPayload {

	public static final CustomPayload.Id<GexpressConfigSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "config_sync"));

	public static final PacketCodec<PacketByteBuf, GexpressConfigSyncPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.c4Price());
			buf.writeInt(payload.c4FuseSeconds());
			buf.writeInt(payload.c4FirstBeepSeconds());
			buf.writeInt(payload.wrongWirePercent());
			buf.writeInt(payload.grenadePrice());
			buf.writeInt(payload.medicShieldCooldownSeconds());
			buf.writeBoolean(payload.medicShieldKnifeBreaks());
			buf.writeInt(payload.silentShadowDurationSeconds());
			buf.writeInt(payload.silentShadowCooldownSeconds());
			buf.writeInt(payload.warlockMarkCooldownSeconds());
			buf.writeInt(payload.warlockKillCooldownSeconds());
			buf.writeInt(payload.juggernautInitialCooldownSeconds());
			buf.writeInt(payload.juggernautCooldownReductionSeconds());
			buf.writeInt(payload.juggernautMinimumCooldownSeconds());
			buf.writeInt(payload.juggernautShieldRechargeSeconds());
			buf.writeInt(payload.tricksterSwapDurationSeconds());
			buf.writeInt(payload.tricksterMasqueradeCooldownSeconds());
			buf.writeInt(payload.tricksterDancingCartsCooldownSeconds());
			buf.writeInt(payload.tricksterDancingCartsMaxUses());
			buf.writeInt(payload.puppetmasterControlDurationSeconds());
			buf.writeInt(payload.puppetmasterControlCooldownSeconds());
			buf.writeBoolean(payload.puppetmasterRandomTarget());
			buf.writeInt(payload.puppetmasterControlRange());
			buf.writeInt(payload.puppetmasterMaxUses());
			buf.writeInt(payload.pelicanEatCooldownSeconds());
			buf.writeInt(payload.pelicanEatPercentage());
			buf.writeInt(payload.hungryFoodLimit());
			buf.writeInt(payload.thirstyDrinkLimit());
			buf.writeInt(payload.snitchTasksRequired());
			buf.writeInt(payload.snitchWarningTasksRemaining());
			buf.writeInt(payload.timeMasterRewindSeconds());
			buf.writeInt(payload.timeMasterCooldownSeconds());
			buf.writeInt(payload.timeMasterMaxUses());
			buf.writeInt(payload.timeMasterFreezeDurationSeconds());
			buf.writeInt(payload.timeMasterFreezeCooldownSeconds());
			buf.writeInt(payload.timeMasterFreezeMaxUses());
			buf.writeInt(payload.timeMasterFreezeRange());
			buf.writeInt(payload.scatterBrainCooldownSeconds());
			buf.writeInt(payload.trackerMaxTargets());
			buf.writeInt(payload.trackerRange());
			buf.writeInt(payload.trackerCooldownSeconds());
			buf.writeInt(payload.altruistRange());
			buf.writeInt(payload.skincrawlerBodyMaxAgeSeconds());
			buf.writeInt(payload.skincrawlerCooldownSeconds());
			buf.writeInt(payload.skincrawlerStunSeconds());
			buf.writeInt(payload.skincrawlerRange());
			buf.writeInt(payload.spyBugCost());
			buf.writeInt(payload.spyBugDurationSeconds());
			buf.writeInt(payload.spyBugRange());
			buf.writeInt(payload.squeakerPitchPercent());
			buf.writeInt(payload.masqueradePitchMinPercent());
			buf.writeInt(payload.masqueradePitchMaxPercent());
			buf.writeBoolean(payload.lastDeathShieldEnabled());
			buf.writeInt(payload.bountyHunterBountyIntervalSeconds());
			buf.writeInt(payload.bountyHunterRewardGold());
			buf.writeInt(payload.bountyHunterFailCooldownSeconds());
			buf.writeInt(payload.godfatherBulletPrice());
			buf.writeInt(payload.godfatherStartingBullets());
			buf.writeInt(payload.godfatherMaxLoadedBullets());
			buf.writeInt(payload.mafiaStartingGold());
			buf.writeInt(payload.mafiaMinimumPlayers());
			buf.writeInt(payload.godfatherStartingGold());
			buf.writeInt(payload.mafiosoStartingGold());
			buf.writeInt(payload.janitorStartingGold());
			buf.writeInt(payload.mafiaRecruitRange());
			buf.writeInt(payload.mafiaReplacementCooldownSeconds());
			buf.writeInt(payload.mafiaRevolverKillCooldownSeconds());
			buf.writeInt(payload.janitorCleanRange());
			buf.writeInt(payload.janitorCleanCooldownSeconds());
			buf.writeInt(payload.janitorRevolverCooldownAfterCleanSeconds());
			buf.writeInt(payload.janitorCleanCooldownAfterKillSeconds());
			buf.writeBoolean(payload.useCustomRoleCounts());
			buf.writeInt(payload.maxKillerAmount());
			buf.writeInt(payload.maxVigilanteAmount());
			buf.writeInt(payload.playersPerKiller());
			buf.writeInt(payload.playersPerVigilante());
			buf.writeFloat(payload.c4BackOffsetX());
			buf.writeFloat(payload.c4BackOffsetY());
			buf.writeFloat(payload.c4BackOffsetZ());
			buf.writeFloat(payload.c4BackRotationX());
			buf.writeFloat(payload.c4BackRotationY());
			buf.writeFloat(payload.c4BackRotationZ());
			buf.writeFloat(payload.c4BackSlant());
			buf.writeFloat(payload.c4BackScale());
			buf.writeFloat(payload.spyBugOffsetX());
			buf.writeFloat(payload.spyBugOffsetY());
			buf.writeFloat(payload.spyBugOffsetZ());
			buf.writeFloat(payload.spyBugRotationX());
			buf.writeFloat(payload.spyBugRotationY());
			buf.writeFloat(payload.spyBugRotationZ());
			buf.writeFloat(payload.spyBugSlant());
			buf.writeFloat(payload.spyBugScale());
			buf.writeString(payload.c4PlacementPresets());
			buf.writeString(payload.roleDescriptionOverrides());
			buf.writeFloat(payload.shortSightedFogRange());
			buf.writeInt(payload.medicShieldBlockFlashTicks());
			buf.writeInt(payload.medicShieldBreakFlashTicks());
			buf.writeInt(payload.medicShieldBlockFlashAlpha());
			buf.writeInt(payload.medicShieldBreakFlashAlpha());
			buf.writeFloat(payload.silentShadowAlpha());
		},
		buf -> new GexpressConfigSyncPayload(
			buf.readInt(), // c4Price
			buf.readInt(), // c4FuseSeconds
			buf.readInt(), // c4FirstBeepSeconds
			buf.readInt(), // wrongWirePercent
			buf.readInt(), // grenadePrice
			buf.readInt(), // medicShieldCooldownSeconds
			buf.readBoolean(), // medicShieldKnifeBreaks
			buf.readInt(), // silentShadowDurationSeconds
			buf.readInt(), // silentShadowCooldownSeconds
			buf.readInt(), // warlockMarkCooldownSeconds
			buf.readInt(), // warlockKillCooldownSeconds
			buf.readInt(), // juggernautInitialCooldownSeconds
			buf.readInt(), // juggernautCooldownReductionSeconds
			buf.readInt(), // juggernautMinimumCooldownSeconds
			buf.readInt(), // juggernautShieldRechargeSeconds
			buf.readInt(), // tricksterSwapDurationSeconds
			buf.readInt(), // tricksterMasqueradeCooldownSeconds
			buf.readInt(), // tricksterDancingCartsCooldownSeconds
			buf.readInt(), // tricksterDancingCartsMaxUses
			buf.readInt(), // puppetmasterControlDurationSeconds
			buf.readInt(), // puppetmasterControlCooldownSeconds
			buf.readBoolean(), // puppetmasterRandomTarget
			buf.readInt(), // puppetmasterControlRange
			buf.readInt(), // puppetmasterMaxUses
			buf.readInt(), // pelicanEatCooldownSeconds
			buf.readInt(), // pelicanEatPercentage
			buf.readInt(), // hungryFoodLimit
			buf.readInt(), // thirstyDrinkLimit
			buf.readInt(), // snitchTasksRequired
			buf.readInt(), // snitchWarningTasksRemaining
			buf.readInt(), // timeMasterRewindSeconds
			buf.readInt(), // timeMasterCooldownSeconds
			buf.readInt(), // timeMasterMaxUses
			buf.readInt(), // timeMasterFreezeDurationSeconds
			buf.readInt(), // timeMasterFreezeCooldownSeconds
			buf.readInt(), // timeMasterFreezeMaxUses
			buf.readInt(), // timeMasterFreezeRange
			buf.readInt(), // scatterBrainCooldownSeconds
			buf.readInt(), // trackerMaxTargets
			buf.readInt(), // trackerRange
			buf.readInt(), // trackerCooldownSeconds
			buf.readInt(), // altruistRange
			buf.readInt(), // skincrawlerBodyMaxAgeSeconds
			buf.readInt(), // skincrawlerCooldownSeconds
			buf.readInt(), // skincrawlerStunSeconds
			buf.readInt(), // skincrawlerRange
			buf.readInt(), // spyBugCost
			buf.readInt(), // spyBugDurationSeconds
			buf.readInt(), // spyBugRange
			buf.readInt(), // squeakerPitchPercent
			buf.readInt(), // masqueradePitchMinPercent
			buf.readInt(), // masqueradePitchMaxPercent
			buf.readBoolean(), // lastDeathShieldEnabled
			buf.readInt(), // bountyHunterBountyIntervalSeconds
			buf.readInt(), // bountyHunterRewardGold
			buf.readInt(), // bountyHunterFailCooldownSeconds
			buf.readInt(), // godfatherBulletPrice
			buf.readInt(), // godfatherStartingBullets
			buf.readInt(), // godfatherMaxLoadedBullets
			buf.readInt(), // mafiaStartingGold
			buf.readInt(), // mafiaMinimumPlayers
			buf.readInt(), // godfatherStartingGold
			buf.readInt(), // mafiosoStartingGold
			buf.readInt(), // janitorStartingGold
			buf.readInt(), // mafiaRecruitRange
			buf.readInt(), // mafiaReplacementCooldownSeconds
			buf.readInt(), // mafiaRevolverKillCooldownSeconds
			buf.readInt(), // janitorCleanRange
			buf.readInt(), // janitorCleanCooldownSeconds
			buf.readInt(), // janitorRevolverCooldownAfterCleanSeconds
			buf.readInt(), // janitorCleanCooldownAfterKillSeconds
			buf.readBoolean(), // useCustomRoleCounts
			buf.readInt(), // maxKillerAmount
			buf.readInt(), // maxVigilanteAmount
			buf.readInt(), // playersPerKiller
			buf.readInt(), // playersPerVigilante
			buf.readFloat(), // c4BackOffsetX
			buf.readFloat(), // c4BackOffsetY
			buf.readFloat(), // c4BackOffsetZ
			buf.readFloat(), // c4BackRotationX
			buf.readFloat(), // c4BackRotationY
			buf.readFloat(), // c4BackRotationZ
			buf.readFloat(), // c4BackSlant
			buf.readFloat(), // c4BackScale
			buf.readFloat(), // spyBugOffsetX
			buf.readFloat(), // spyBugOffsetY
			buf.readFloat(), // spyBugOffsetZ
			buf.readFloat(), // spyBugRotationX
			buf.readFloat(), // spyBugRotationY
			buf.readFloat(), // spyBugRotationZ
			buf.readFloat(), // spyBugSlant
			buf.readFloat(), // spyBugScale
			buf.readString(), // c4PlacementPresets
			buf.readString(), // roleDescriptionOverrides
			buf.readFloat(), // shortSightedFogRange
			buf.readInt(), // medicShieldBlockFlashTicks
			buf.readInt(), // medicShieldBreakFlashTicks
			buf.readInt(), // medicShieldBlockFlashAlpha
			buf.readInt(), // medicShieldBreakFlashAlpha
			buf.readFloat() // silentShadowAlpha
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
