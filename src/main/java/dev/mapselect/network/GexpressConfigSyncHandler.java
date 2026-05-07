package dev.mapselect.network;

import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Registers GexpressConfigSyncPayload and keeps the server as the authoritative copy.
 * Clients may request changes, but the server clamps/persists them and broadcasts the
 * accepted values back to every connected player so client-side shop displays match buys.
 */
public final class GexpressConfigSyncHandler {
	private GexpressConfigSyncHandler() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(GexpressConfigSyncPayload.ID, GexpressConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PuppetmasterConfigPayload.ID, PuppetmasterConfigPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GexpressConfigSyncPayload.ID, GexpressConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PuppetmasterConfigPayload.ID, PuppetmasterConfigPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sendConfigTo(handler.player)));

		ServerPlayNetworking.registerGlobalReceiver(GexpressConfigSyncPayload.ID,
			(payload, context) -> {
				ServerPlayerEntity sender = context.player();
				if (!GexpressPermissions.canEditGameOptions(sender)) {
					MapSelect.LOGGER.warn("Ignoring gexpress config sync from {} (not OP/host/dev)",
						sender.getName().getString());
					return;
				}

				MinecraftServer server = sender.getServer();
				if (server == null) return;
				server.execute(() -> {
					GexpressConfigSyncPayload before = currentPayload();
					GexpressConfig.apply(payload.c4Price(), payload.c4FuseSeconds(),
						payload.c4FirstBeepSeconds(), payload.wrongWirePercent(), payload.grenadePrice(),
						payload.passiveIncomeKiller(), payload.passiveIncomeCivilian(),
						payload.passiveIncomeNeutral(), payload.passiveIncomeVigilante(), payload.passiveIncomeMafia(),
						payload.medicShieldCooldownSeconds(), payload.medicShieldKnifeBreaks(),
						payload.silentShadowDurationSeconds(), payload.silentShadowCooldownSeconds(),
						payload.warlockMarkCooldownSeconds(), payload.warlockKillCooldownSeconds(),
						payload.juggernautInitialCooldownSeconds(), payload.juggernautCooldownReductionSeconds(),
						payload.juggernautMinimumCooldownSeconds(), payload.juggernautShieldRechargeSeconds(),
						payload.tricksterSwapDurationSeconds(), payload.tricksterMasqueradeCooldownSeconds(),
						payload.tricksterDancingCartsCooldownSeconds(),
						payload.tricksterDancingCartsMaxUses(),
						payload.puppetmasterControlDurationSeconds(), payload.puppetmasterControlCooldownSeconds(),
						payload.puppetmasterRandomTarget(), payload.puppetmasterControlRange(),
						payload.puppetmasterMaxUses(),
						payload.pelicanEatCooldownSeconds(), payload.pelicanEatPercentage(),
						payload.hungryFoodLimit(), payload.thirstyDrinkLimit(),
						payload.snitchTasksRequired(),
						payload.snitchWarningTasksRemaining(),
						payload.timeMasterRewindSeconds(), payload.timeMasterCooldownSeconds(), payload.timeMasterMaxUses(),
						payload.timeMasterFreezeDurationSeconds(), payload.timeMasterFreezeCooldownSeconds(),
						payload.timeMasterFreezeMaxUses(), payload.timeMasterFreezeRange(),
						payload.scatterBrainCooldownSeconds(), payload.trackerMaxTargets(), payload.trackerRange(),
						payload.trackerCooldownSeconds(), payload.altruistRange(),
						payload.skincrawlerBodyMaxAgeSeconds(), payload.skincrawlerCooldownSeconds(),
						payload.skincrawlerStunSeconds(), payload.skincrawlerRange(),
						payload.spyBugCost(), payload.spyBugDurationSeconds(), payload.spyBugRange(),
						payload.squeakerPitchPercent(), payload.masqueradePitchMinPercent(),
						payload.masqueradePitchMaxPercent(), payload.lastDeathShieldEnabled(),
						payload.guardianAngelAllowNonInnocents(),
						payload.bountyHunterBountyIntervalSeconds(), payload.bountyHunterRewardGold(),
						payload.bountyHunterFailCooldownSeconds(), payload.godfatherBulletPrice(),
						payload.godfatherStartingBullets(), payload.godfatherMaxLoadedBullets(),
						payload.mafiaStartingGold(), payload.mafiaMinimumPlayers(),
						payload.godfatherStartingGold(), payload.mafiosoStartingGold(),
						payload.janitorStartingGold(), payload.mafiaRecruitRange(),
						payload.mafiaReplacementCooldownSeconds(), payload.mafiaRevolverKillCooldownSeconds(),
						payload.janitorCleanRange(), payload.janitorCleanCooldownSeconds(),
						payload.janitorRevolverCooldownAfterCleanSeconds(),
						payload.janitorCleanCooldownAfterKillSeconds(), payload.useCustomRoleCounts(),
						payload.maxKillerAmount(), payload.maxVigilanteAmount(),
						payload.playersPerKiller(), payload.playersPerVigilante(),
						payload.c4BackOffsetX(), payload.c4BackOffsetY(), payload.c4BackOffsetZ(),
						payload.c4BackRotationX(), payload.c4BackRotationY(), payload.c4BackRotationZ(),
						payload.c4BackSlant(), payload.c4BackScale(),
						payload.spyBugOffsetX(), payload.spyBugOffsetY(), payload.spyBugOffsetZ(),
						payload.spyBugRotationX(), payload.spyBugRotationY(), payload.spyBugRotationZ(),
						payload.spyBugSlant(), payload.spyBugScale(), payload.c4PlacementPresets(),
						payload.roleDescriptionOverrides(),
						payload.shortSightedFogRange(),
						payload.medicShieldBlockFlashTicks(), payload.medicShieldBreakFlashTicks(),
						payload.medicShieldBlockFlashAlpha(), payload.medicShieldBreakFlashAlpha(),
						payload.silentShadowAlpha());
					GexpressConfigSyncPayload after = currentPayload();
					if (after.equals(before)) return;

					GexpressConfig.save();
					MapSelect.LOGGER.info("G'Express config updated by {}: price={}, fuse={}s, firstBeep={}s, wrongWire={}%, grenade={}, maxKillers={}, maxVigilantes={}, medicShieldCooldown={}s, medicShieldKnifeBreaks={}, silent=[duration {}s, cooldown {}s, alpha {}], warlock=[markCooldown {}s, killCooldown {}s], juggernaut=[initial {}s, reduction {}s, min {}s, shieldRecharge {}s], trickster=[duration {}s, cartUses {}], puppetmaster=[duration {}s, cooldown {}s, random {}], pelican=[eatCooldown {}s], snitch=[tasks {}, warnRemaining {}], timeMaster=[rewind {}s, cooldown {}s, uses {}, freezeDuration {}s, freezeCooldown {}s, freezeUses {}, freezeRange {}], c4Back=[{}, {}, {}; rot {}, {}, {}; slant {}; scale {}]",
						sender.getName().getString(),
						GexpressConfig.getC4Price(),
						GexpressConfig.getC4FuseSeconds(),
						GexpressConfig.getC4FirstBeepSeconds(),
						GexpressConfig.getWrongWirePercent(),
						GexpressConfig.getGrenadePrice(),
						GexpressConfig.getMaxKillerAmount(),
						GexpressConfig.getMaxVigilanteAmount(),
						GexpressConfig.getMedicShieldCooldownSeconds(),
						GexpressConfig.doesMedicShieldKnifeBreaks(),
						GexpressConfig.getSilentShadowDurationSeconds(),
						GexpressConfig.getSilentShadowCooldownSeconds(),
						GexpressConfig.getSilentShadowAlpha(),
						GexpressConfig.getWarlockMarkCooldownSeconds(),
						GexpressConfig.getWarlockKillCooldownSeconds(),
						GexpressConfig.getJuggernautInitialCooldownSeconds(),
						GexpressConfig.getJuggernautCooldownReductionSeconds(),
						GexpressConfig.getJuggernautMinimumCooldownSeconds(),
						GexpressConfig.getJuggernautShieldRechargeSeconds(),
						GexpressConfig.getTricksterSwapDurationSeconds(),
						GexpressConfig.getTricksterDancingCartsMaxUses(),
						GexpressConfig.getPuppetmasterControlDurationSeconds(),
						GexpressConfig.getPuppetmasterControlCooldownSeconds(),
						GexpressConfig.isPuppetmasterRandomTarget(),
						GexpressConfig.getPelicanEatCooldownSeconds(),
						GexpressConfig.getSnitchTasksRequired(),
						GexpressConfig.getSnitchWarningTasksRemaining(),
						GexpressConfig.getTimeMasterRewindSeconds(),
						GexpressConfig.getTimeMasterCooldownSeconds(),
						GexpressConfig.getTimeMasterMaxUses(),
						GexpressConfig.getTimeMasterFreezeDurationSeconds(),
						GexpressConfig.getTimeMasterFreezeCooldownSeconds(),
						GexpressConfig.getTimeMasterFreezeMaxUses(),
						GexpressConfig.getTimeMasterFreezeRange(),
						GexpressConfig.getC4BackOffsetX(),
						GexpressConfig.getC4BackOffsetY(),
						GexpressConfig.getC4BackOffsetZ(),
						GexpressConfig.getC4BackRotationX(),
						GexpressConfig.getC4BackRotationY(),
						GexpressConfig.getC4BackRotationZ(),
						GexpressConfig.getC4BackSlant(),
						GexpressConfig.getC4BackScale());
					broadcastConfig(server);
				});
			});
		ServerPlayNetworking.registerGlobalReceiver(PuppetmasterConfigPayload.ID,
			(payload, context) -> {
				ServerPlayerEntity sender = context.player();
				if (!GexpressPermissions.canEditGameOptions(sender)) {
					MapSelect.LOGGER.warn("Ignoring puppetmaster config sync from {} (not OP/host/dev)",
						sender.getName().getString());
					return;
				}

				MinecraftServer server = sender.getServer();
				if (server == null) return;
				server.execute(() -> {
					boolean before = GexpressConfig.canPuppetmasterKillOwnBody();
					GexpressConfig.puppetmasterCanKillOwnBody = payload.canKillOwnBody();
					if (before == GexpressConfig.canPuppetmasterKillOwnBody()) return;

					GexpressConfig.save();
					MapSelect.LOGGER.info("G'Express puppetmaster config updated by {}: selfKill={}",
						sender.getName().getString(), GexpressConfig.canPuppetmasterKillOwnBody());
					broadcastPuppetmasterConfig(server);
				});
			});
	}

	public static void broadcastConfig(MinecraftServer server) {
		GexpressConfigSyncPayload payload = currentPayload();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
		broadcastPuppetmasterConfig(server);
	}

	private static void sendConfigTo(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, currentPayload());
		sendPuppetmasterConfigTo(player);
	}

	private static void broadcastPuppetmasterConfig(MinecraftServer server) {
		PuppetmasterConfigPayload payload =
			new PuppetmasterConfigPayload(GexpressConfig.canPuppetmasterKillOwnBody());
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(player, PuppetmasterConfigPayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void sendPuppetmasterConfigTo(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, PuppetmasterConfigPayload.ID)) return;
		ServerPlayNetworking.send(player,
			new PuppetmasterConfigPayload(GexpressConfig.canPuppetmasterKillOwnBody()));
	}

	private static GexpressConfigSyncPayload currentPayload() {
		return new GexpressConfigSyncPayload(
			GexpressConfig.getC4Price(),
			GexpressConfig.getC4FuseSeconds(),
			GexpressConfig.getC4FirstBeepSeconds(),
			GexpressConfig.getWrongWirePercent(),
			GexpressConfig.getGrenadePrice(),
			GexpressConfig.getPassiveIncomeKiller(),
			GexpressConfig.getPassiveIncomeCivilian(),
			GexpressConfig.getPassiveIncomeNeutral(),
			GexpressConfig.getPassiveIncomeVigilante(),
			GexpressConfig.getPassiveIncomeMafia(),
			GexpressConfig.getMedicShieldCooldownSeconds(),
			GexpressConfig.doesMedicShieldKnifeBreaks(),
			GexpressConfig.getSilentShadowDurationSeconds(),
			GexpressConfig.getSilentShadowCooldownSeconds(),
			GexpressConfig.getWarlockMarkCooldownSeconds(),
			GexpressConfig.getWarlockKillCooldownSeconds(),
			GexpressConfig.getJuggernautInitialCooldownSeconds(),
			GexpressConfig.getJuggernautCooldownReductionSeconds(),
			GexpressConfig.getJuggernautMinimumCooldownSeconds(),
			GexpressConfig.getJuggernautShieldRechargeSeconds(),
			GexpressConfig.getTricksterSwapDurationSeconds(),
			GexpressConfig.getTricksterMasqueradeCooldownSeconds(),
			GexpressConfig.getTricksterDancingCartsCooldownSeconds(),
			GexpressConfig.getTricksterDancingCartsMaxUses(),
			GexpressConfig.getPuppetmasterControlDurationSeconds(),
			GexpressConfig.getPuppetmasterControlCooldownSeconds(),
			GexpressConfig.isPuppetmasterRandomTarget(),
			GexpressConfig.getPuppetmasterControlRange(),
			GexpressConfig.getPuppetmasterMaxUses(),
			GexpressConfig.getPelicanEatCooldownSeconds(),
			GexpressConfig.getPelicanEatPercentage(),
			GexpressConfig.getHungryFoodLimit(),
			GexpressConfig.getThirstyDrinkLimit(),
			GexpressConfig.getSnitchTasksRequired(),
			GexpressConfig.getSnitchWarningTasksRemaining(),
			GexpressConfig.getTimeMasterRewindSeconds(),
			GexpressConfig.getTimeMasterCooldownSeconds(),
			GexpressConfig.getTimeMasterMaxUses(),
			GexpressConfig.getTimeMasterFreezeDurationSeconds(),
			GexpressConfig.getTimeMasterFreezeCooldownSeconds(),
			GexpressConfig.getTimeMasterFreezeMaxUses(),
			GexpressConfig.getTimeMasterFreezeRange(),
			GexpressConfig.getScatterBrainCooldownSeconds(),
			GexpressConfig.getTrackerMaxTargets(),
			GexpressConfig.getTrackerRange(),
			GexpressConfig.getTrackerCooldownSeconds(),
			GexpressConfig.getAltruistRange(),
			GexpressConfig.getSkincrawlerBodyMaxAgeSeconds(),
			GexpressConfig.getSkincrawlerCooldownSeconds(),
			GexpressConfig.getSkincrawlerStunSeconds(),
			GexpressConfig.getSkincrawlerRange(),
			GexpressConfig.getSpyBugCost(),
			GexpressConfig.getSpyBugDurationSeconds(),
			GexpressConfig.getSpyBugRange(),
			GexpressConfig.getSqueakerPitchPercent(),
			GexpressConfig.getMasqueradePitchMinPercent(),
			GexpressConfig.getMasqueradePitchMaxPercent(),
			GexpressConfig.isLastDeathShieldEnabled(),
			GexpressConfig.canGuardianAngelPickNonInnocents(),
			GexpressConfig.getBountyHunterBountyIntervalSeconds(),
			GexpressConfig.getBountyHunterRewardGold(),
			GexpressConfig.getBountyHunterFailCooldownSeconds(),
			GexpressConfig.getGodfatherBulletPrice(),
			GexpressConfig.getGodfatherStartingBullets(),
			GexpressConfig.getGodfatherMaxLoadedBullets(),
			GexpressConfig.getMafiaStartingGold(),
			GexpressConfig.getMafiaMinimumPlayers(),
			GexpressConfig.getGodfatherStartingGold(),
			GexpressConfig.getMafiosoStartingGold(),
			GexpressConfig.getJanitorStartingGold(),
			GexpressConfig.getMafiaRecruitRange(),
			GexpressConfig.getMafiaReplacementCooldownSeconds(),
			GexpressConfig.getMafiaRevolverKillCooldownSeconds(),
			GexpressConfig.getJanitorCleanRange(),
			GexpressConfig.getJanitorCleanCooldownSeconds(),
			GexpressConfig.getJanitorRevolverCooldownAfterCleanSeconds(),
			GexpressConfig.getJanitorCleanCooldownAfterKillSeconds(),
			GexpressConfig.useCustomRoleCounts(),
			GexpressConfig.getMaxKillerAmount(),
			GexpressConfig.getMaxVigilanteAmount(),
			GexpressConfig.getPlayersPerKiller(),
			GexpressConfig.getPlayersPerVigilante(),
			GexpressConfig.getC4BackOffsetX(),
			GexpressConfig.getC4BackOffsetY(),
			GexpressConfig.getC4BackOffsetZ(),
			GexpressConfig.getC4BackRotationX(),
			GexpressConfig.getC4BackRotationY(),
			GexpressConfig.getC4BackRotationZ(),
			GexpressConfig.getC4BackSlant(),
			GexpressConfig.getC4BackScale(),
			GexpressConfig.getSpyBugOffsetX(),
			GexpressConfig.getSpyBugOffsetY(),
			GexpressConfig.getSpyBugOffsetZ(),
			GexpressConfig.getSpyBugRotationX(),
			GexpressConfig.getSpyBugRotationY(),
			GexpressConfig.getSpyBugRotationZ(),
			GexpressConfig.getSpyBugSlant(),
			GexpressConfig.getSpyBugScale(),
			GexpressConfig.getC4PlacementPresetsSyncString(),
			GexpressConfig.getRoleDescriptionOverridesSyncString(),
			GexpressConfig.getShortSightedEntityRange(),
			GexpressConfig.getMedicShieldBlockFlashTicks(),
			GexpressConfig.getMedicShieldBreakFlashTicks(),
			GexpressConfig.getMedicShieldBlockFlashAlpha(),
			GexpressConfig.getMedicShieldBreakFlashAlpha(),
			GexpressConfig.getSilentShadowAlpha()
		);
	}
}
