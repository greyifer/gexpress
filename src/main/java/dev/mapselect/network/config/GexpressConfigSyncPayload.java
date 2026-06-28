package dev.mapselect.network.config;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bidirectional snapshot of live G'Express settings. */
public record GexpressConfigSyncPayload(int c4Price, int c4FuseSeconds, int c4FirstBeepSeconds, int wrongWirePercent,
		int grenadePrice, int bombSpecialistFirecrackerPrice, int bombSpecialistLockpickPrice,
		int bombSpecialistCrowbarPrice, int mafiosoKnifePrice, int mafiosoRevolverPrice,
		int janitorPoisonVialPrice, int janitorScorpionPrice, int burglarCrowbarPrice,
		int burglarLockpickPrice,
		int passiveIncomeKiller, int passiveIncomeCivilian, int passiveIncomeNeutral,
		int passiveIncomeVigilante, int passiveIncomeMafia,
		int medicShieldCooldownSeconds, boolean medicShieldKnifeBreaks,
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
		int skincrawlerRange, int painterPaintDurationSeconds, int painterBodyCooldownSeconds,
		int painterPlayerCooldownSeconds, int painterDoorwayDurationSeconds, int painterDoorwayCooldownSeconds,
		int painterMaxPaintedBodies, int painterMaxPaintedPlayers,
		int spyBugCost, int spyBugDurationSeconds, int spyBugRange,
		int squeakerPitchPercent, int masqueradePitchMinPercent, int masqueradePitchMaxPercent,
		boolean lastDeathShieldEnabled, boolean guardianAngelAllowNonInnocents,
		boolean lastStandEnabled, String itemPickupPolicy,
		int bountyHunterBountyIntervalSeconds, int bountyHunterRewardGold, int bountyHunterFailCooldownSeconds,
		int godfatherBulletPrice, int godfatherStartingBullets, int godfatherMaxLoadedBullets, int mafiaStartingGold,
		int mafiaMinimumPlayers, int godfatherStartingGold, int mafiosoStartingGold, int janitorStartingGold,
		int mafiaRecruitRange, int mafiaReplacementCooldownSeconds, int mafiaRevolverKillCooldownSeconds,
		int janitorCleanRange, int janitorCleanCooldownSeconds, int janitorRevolverCooldownAfterCleanSeconds,
		int janitorCleanCooldownAfterKillSeconds,
		int pickpocketMaxHoldSeconds, int pickpocketCoinsPerSecond, int pickpocketRange,
		int copycatCopyCooldownSeconds, int copycatCopyDurationSeconds, int copycatCopyRange,
		boolean useCustomRoleCounts, int maxKillerAmount, int maxVigilanteAmount,
		int maxNeutralAmount, int maxModifiersPerPlayer,
		int playersPerKiller, int playersPerVigilante, int playersPerNeutral,
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
		float silentShadowAlpha, String specialRoleOccurrence,
		int seerCompareCooldownSeconds, int seerCompareDeathsRequired, int seerCompareRange,
		int twinsSwapCooldownSeconds, boolean twinsBodyDifferentRole,
		int cupidRequiredAlivePairs, int cupidPairCooldownSeconds, int cupidRange,
		boolean loversShowPartnerHud, boolean loversAllowMixedSidePairs,
		int covenantBiteCooldownSeconds,
		int vengefulSpiritReviveDelaySeconds, int vengefulSpiritRevengeSeconds,
		boolean loversIndependentWin) implements CustomPayload {

	private static final int WIRE_VERSION = 13;
	private static final String DEFAULT_ITEM_PICKUP_POLICY = "civilians_neutrals_killers";
	private static final String DEFAULT_SPECIAL_ROLE_OCCURRENCE = "both";
	private static final int DEFAULT_BOMB_SPECIALIST_FIRECRACKER_PRICE = 10;
	private static final int DEFAULT_BOMB_SPECIALIST_LOCKPICK_PRICE = 50;
	private static final int DEFAULT_BOMB_SPECIALIST_CROWBAR_PRICE = 25;
	private static final int DEFAULT_MAFIOSO_KNIFE_PRICE = 200;
	private static final int DEFAULT_MAFIOSO_REVOLVER_PRICE = 350;
	private static final int DEFAULT_JANITOR_POISON_VIAL_PRICE = 100;
	private static final int DEFAULT_JANITOR_SCORPION_PRICE = 125;
	private static final int DEFAULT_BURGLAR_CROWBAR_PRICE = 25;
	private static final int DEFAULT_BURGLAR_LOCKPICK_PRICE = 50;

	public static final CustomPayload.Id<GexpressConfigSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "config_sync_v2"));
	public static final CustomPayload.Id<GexpressConfigSyncPayload> LEGACY_ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "config_sync"));

	public GexpressConfigSyncPayload {
		specialRoleOccurrence = specialRoleOccurrence == null ? DEFAULT_SPECIAL_ROLE_OCCURRENCE : specialRoleOccurrence;
		itemPickupPolicy = itemPickupPolicy == null ? DEFAULT_ITEM_PICKUP_POLICY : itemPickupPolicy;
	}

	public static final PacketCodec<PacketByteBuf, GexpressConfigSyncPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeVarInt(WIRE_VERSION);
			encodeFields(payload, buf);
		},
		GexpressConfigSyncPayload::decodeVersioned
	);

	public static final PacketCodec<PacketByteBuf, GexpressConfigSyncPayload> LEGACY_CODEC = PacketCodec.of(
		GexpressConfigSyncPayload::encodeFields,
		GexpressConfigSyncPayload::decodeLegacy
	);

	private static void encodeFields(GexpressConfigSyncPayload payload, PacketByteBuf buf) {
		buf.writeInt(payload.c4Price());
		buf.writeInt(payload.c4FuseSeconds());
		buf.writeInt(payload.c4FirstBeepSeconds());
		buf.writeInt(payload.wrongWirePercent());
		buf.writeInt(payload.grenadePrice());
		buf.writeInt(payload.bombSpecialistFirecrackerPrice());
		buf.writeInt(payload.bombSpecialistLockpickPrice());
		buf.writeInt(payload.bombSpecialistCrowbarPrice());
		buf.writeInt(payload.mafiosoKnifePrice());
		buf.writeInt(payload.mafiosoRevolverPrice());
		buf.writeInt(payload.janitorPoisonVialPrice());
		buf.writeInt(payload.janitorScorpionPrice());
		buf.writeInt(payload.burglarCrowbarPrice());
		buf.writeInt(payload.burglarLockpickPrice());
		buf.writeInt(payload.passiveIncomeKiller());
		buf.writeInt(payload.passiveIncomeCivilian());
		buf.writeInt(payload.passiveIncomeNeutral());
		buf.writeInt(payload.passiveIncomeVigilante());
		buf.writeInt(payload.passiveIncomeMafia());
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
		buf.writeInt(payload.painterPaintDurationSeconds());
		buf.writeInt(payload.painterBodyCooldownSeconds());
		buf.writeInt(payload.painterPlayerCooldownSeconds());
		buf.writeInt(payload.painterDoorwayDurationSeconds());
		buf.writeInt(payload.painterDoorwayCooldownSeconds());
		buf.writeInt(payload.painterMaxPaintedBodies());
		buf.writeInt(payload.painterMaxPaintedPlayers());
		buf.writeInt(payload.spyBugCost());
		buf.writeInt(payload.spyBugDurationSeconds());
		buf.writeInt(payload.spyBugRange());
		buf.writeInt(payload.squeakerPitchPercent());
		buf.writeInt(payload.masqueradePitchMinPercent());
		buf.writeInt(payload.masqueradePitchMaxPercent());
		buf.writeBoolean(payload.lastDeathShieldEnabled());
		buf.writeBoolean(payload.guardianAngelAllowNonInnocents());
		buf.writeBoolean(payload.lastStandEnabled());
		buf.writeString(payload.itemPickupPolicy(), 64);
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
		buf.writeInt(payload.pickpocketMaxHoldSeconds());
		buf.writeInt(payload.pickpocketCoinsPerSecond());
		buf.writeInt(payload.pickpocketRange());
		buf.writeInt(payload.copycatCopyCooldownSeconds());
		buf.writeInt(payload.copycatCopyDurationSeconds());
		buf.writeInt(payload.copycatCopyRange());
		buf.writeBoolean(payload.useCustomRoleCounts());
		buf.writeInt(payload.maxKillerAmount());
		buf.writeInt(payload.maxVigilanteAmount());
		buf.writeInt(payload.maxNeutralAmount());
		buf.writeInt(payload.maxModifiersPerPlayer());
		buf.writeInt(payload.playersPerKiller());
		buf.writeInt(payload.playersPerVigilante());
		buf.writeInt(payload.playersPerNeutral());
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
		buf.writeString(payload.specialRoleOccurrence(), 32);
		buf.writeInt(payload.seerCompareCooldownSeconds());
		buf.writeInt(payload.seerCompareDeathsRequired());
		buf.writeInt(payload.seerCompareRange());
		buf.writeInt(payload.twinsSwapCooldownSeconds());
		buf.writeBoolean(payload.twinsBodyDifferentRole());
		buf.writeInt(payload.cupidRequiredAlivePairs());
		buf.writeInt(payload.cupidPairCooldownSeconds());
		buf.writeInt(payload.cupidRange());
		buf.writeBoolean(payload.loversShowPartnerHud());
		buf.writeBoolean(payload.loversAllowMixedSidePairs());
		buf.writeInt(payload.covenantBiteCooldownSeconds());
		buf.writeInt(payload.vengefulSpiritReviveDelaySeconds());
		buf.writeInt(payload.vengefulSpiritRevengeSeconds());
		buf.writeBoolean(payload.loversIndependentWin());
	}

	private static GexpressConfigSyncPayload decodeVersioned(PacketByteBuf buf) {
		int version = buf.readVarInt();
		if (version == 3) return decodeFields(buf, true, false, false, false, false, false, false, false, false, false, false);
		if (version == 4) return decodeFields(buf, true, true, false, false, false, false, false, false, false, false, false);
		if (version == 5) return decodeFields(buf, true, true, true, false, false, false, false, false, false, false, false);
		if (version == 6) return decodeFields(buf, true, true, true, true, false, false, false, false, false, false, false);
		if (version == 7) return decodeFields(buf, true, true, true, true, true, false, false, false, false, false, false);
		if (version == 8) return decodeFields(buf, true, true, true, true, true, true, false, false, false, false, false);
		if (version == 9) return decodeFields(buf, true, true, true, true, true, true, true, false, false, false, false);
		if (version == 10) return decodeFields(buf, true, true, true, true, true, true, true, true, false, false, false);
		if (version == 11) return decodeFields(buf, true, true, true, true, true, true, true, true, true, false, false);
		if (version == 12) return decodeFields(buf, true, true, true, true, true, true, true, true, true, true, false);
		if (version != WIRE_VERSION) {
			throw new IllegalArgumentException("Unsupported G'Express config sync version " + version);
		}
		return decodeFields(buf, true, true, true, true, true, true, true, true, true, true, true);
	}

	private static GexpressConfigSyncPayload decodeLegacy(PacketByteBuf buf) {
		int start = buf.readerIndex();
		try {
			GexpressConfigSyncPayload payload = decodeFields(buf, true, true, true, true, true, false, false, false, false, false, false);
			if (buf.readableBytes() == 0) return payload;
		} catch (RuntimeException ignored) {
			// Fall back to the previous unversioned layout.
		}
		buf.readerIndex(start);
		try {
			GexpressConfigSyncPayload payload = decodeFields(buf, true, true, true, false, false, false, false, false, false, false, false);
			if (buf.readableBytes() == 0) return payload;
		} catch (RuntimeException ignored) {
			// Fall back to the pre-special-role wire layout below.
		}
		buf.readerIndex(start);
		try {
			GexpressConfigSyncPayload payload = decodeFields(buf, true, false, false, false, false, false, false, false, false, false, false);
			if (buf.readableBytes() == 0) return payload;
		} catch (RuntimeException ignored) {
			// Fall back to the pre-Guardian-Angel wire layout below.
		}
		buf.readerIndex(start);
		return decodeFields(buf, false, false, false, false, false, false, false, false, false, false, false);
	}

	private static GexpressConfigSyncPayload decodeFields(PacketByteBuf buf, boolean includesGuardianAngelSetting,
			boolean includesSpecialRoleOccurrence, boolean includesPickpocketCopycatSettings,
			boolean includesRoleShopPrices, boolean includesNewRoleSettings,
			boolean includesIndependentLoversSetting, boolean includesSeerDeathRequirement,
			boolean includesLastStandAndPickupSettings, boolean includesTwinsSettings,
			boolean includesPainterSettings, boolean includesPainterDoorwaySettings) {
		return new GexpressConfigSyncPayload(
			buf.readInt(), // c4Price
			buf.readInt(), // c4FuseSeconds
			buf.readInt(), // c4FirstBeepSeconds
			buf.readInt(), // wrongWirePercent
			buf.readInt(), // grenadePrice
			includesRoleShopPrices ? buf.readInt() : DEFAULT_BOMB_SPECIALIST_FIRECRACKER_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_BOMB_SPECIALIST_LOCKPICK_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_BOMB_SPECIALIST_CROWBAR_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_MAFIOSO_KNIFE_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_MAFIOSO_REVOLVER_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_JANITOR_POISON_VIAL_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_JANITOR_SCORPION_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_BURGLAR_CROWBAR_PRICE,
			includesRoleShopPrices ? buf.readInt() : DEFAULT_BURGLAR_LOCKPICK_PRICE,
			buf.readInt(), // passiveIncomeKiller
			buf.readInt(), // passiveIncomeCivilian
			buf.readInt(), // passiveIncomeNeutral
			buf.readInt(), // passiveIncomeVigilante
			buf.readInt(), // passiveIncomeMafia
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
			includesPainterSettings ? buf.readInt() : 60, // painterPaintDurationSeconds
			includesPainterSettings ? buf.readInt() : 30, // painterBodyCooldownSeconds
			includesPainterSettings ? buf.readInt() : 30, // painterPlayerCooldownSeconds
			includesPainterDoorwaySettings ? buf.readInt() : 10, // painterDoorwayDurationSeconds
			includesPainterDoorwaySettings ? buf.readInt() : 30, // painterDoorwayCooldownSeconds
			includesPainterSettings ? buf.readInt() : 1, // painterMaxPaintedBodies
			includesPainterSettings ? buf.readInt() : 1, // painterMaxPaintedPlayers
			buf.readInt(), // spyBugCost
			buf.readInt(), // spyBugDurationSeconds
			buf.readInt(), // spyBugRange
			buf.readInt(), // squeakerPitchPercent
			buf.readInt(), // masqueradePitchMinPercent
			buf.readInt(), // masqueradePitchMaxPercent
			buf.readBoolean(), // lastDeathShieldEnabled
			includesGuardianAngelSetting ? buf.readBoolean() : false, // guardianAngelAllowNonInnocents
			includesLastStandAndPickupSettings ? buf.readBoolean() : false, // lastStandEnabled
			includesLastStandAndPickupSettings ? buf.readString(64) : DEFAULT_ITEM_PICKUP_POLICY, // itemPickupPolicy
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
			includesPickpocketCopycatSettings ? buf.readInt() : 6, // pickpocketMaxHoldSeconds
			includesPickpocketCopycatSettings ? buf.readInt() : 12, // pickpocketCoinsPerSecond
			includesPickpocketCopycatSettings ? buf.readInt() : 4, // pickpocketRange
			includesPickpocketCopycatSettings ? buf.readInt() : 45, // copycatCopyCooldownSeconds
			includesPickpocketCopycatSettings ? buf.readInt() : 120, // copycatCopyDurationSeconds
			includesPickpocketCopycatSettings ? buf.readInt() : 16, // copycatCopyRange
			buf.readBoolean(), // useCustomRoleCounts
			buf.readInt(), // maxKillerAmount
			buf.readInt(), // maxVigilanteAmount
			buf.readInt(), // maxNeutralAmount
			buf.readInt(), // maxModifiersPerPlayer
			buf.readInt(), // playersPerKiller
			buf.readInt(), // playersPerVigilante
			buf.readInt(), // playersPerNeutral
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
			buf.readFloat(), // silentShadowAlpha
			includesSpecialRoleOccurrence ? buf.readString(32) : DEFAULT_SPECIAL_ROLE_OCCURRENCE,
			includesNewRoleSettings ? buf.readInt() : 45, // seerCompareCooldownSeconds
			includesNewRoleSettings && includesSeerDeathRequirement ? buf.readInt() : 3, // seerCompareDeathsRequired
			includesNewRoleSettings ? buf.readInt() : 4, // seerCompareRange
			includesTwinsSettings ? buf.readInt() : 20, // twinsSwapCooldownSeconds
			includesTwinsSettings && buf.readBoolean(), // twinsBodyDifferentRole
			includesNewRoleSettings ? buf.readInt() : 75, // cupidLovePercentage
			includesNewRoleSettings ? buf.readInt() : 20, // cupidPairCooldownSeconds
			includesNewRoleSettings ? buf.readInt() : 4, // cupidRange
			includesNewRoleSettings ? buf.readBoolean() : true, // loversShowPartnerHud
			includesNewRoleSettings && buf.readBoolean(), // loversAllowMixedSidePairs
			includesNewRoleSettings ? buf.readInt() : 15, // covenantBiteCooldownSeconds
			includesNewRoleSettings ? buf.readInt() : 15, // vengefulSpiritReviveDelaySeconds
			includesNewRoleSettings ? buf.readInt() : 30, // vengefulSpiritRevengeSeconds
			includesIndependentLoversSetting && buf.readBoolean() // loversIndependentWin
		);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
