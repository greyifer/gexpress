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
						payload.medicShieldCooldownSeconds(), payload.medicShieldKnifeBreaks(),
						payload.silentShadowDurationSeconds(), payload.silentShadowCooldownSeconds(),
						payload.warlockMarkCooldownSeconds(), payload.warlockKillCooldownSeconds(),
						payload.juggernautInitialCooldownSeconds(), payload.juggernautCooldownReductionSeconds(),
						payload.juggernautMinimumCooldownSeconds(), payload.tricksterSwapDurationSeconds(),
						payload.puppetmasterControlDurationSeconds(), payload.puppetmasterControlCooldownSeconds(),
						payload.puppetmasterRandomTarget(), payload.pelicanEatCooldownSeconds(),
						payload.snitchTasksRequired(),
						payload.timeMasterRewindSeconds(), payload.timeMasterCooldownSeconds(), payload.timeMasterMaxUses(),
						payload.maxKillerAmount(),
						payload.c4BackOffsetX(), payload.c4BackOffsetY(), payload.c4BackOffsetZ(),
						payload.c4BackRotationX(), payload.c4BackRotationY(), payload.c4BackRotationZ(),
						payload.c4BackSlant(), payload.c4BackScale(), payload.c4PlacementPresets(),
						payload.roleDescriptionOverrides(),
						payload.shortSightedFogRange(),
						payload.medicShieldBlockFlashTicks(), payload.medicShieldBreakFlashTicks(),
						payload.medicShieldBlockFlashAlpha(), payload.medicShieldBreakFlashAlpha(),
						payload.silentShadowAlpha());
					GexpressConfigSyncPayload after = currentPayload();
					if (after.equals(before)) return;

					GexpressConfig.save();
					MapSelect.LOGGER.info("G'Express config updated by {}: price={}, fuse={}s, firstBeep={}s, wrongWire={}%, grenade={}, maxKillers={}, medicShieldCooldown={}s, medicShieldKnifeBreaks={}, silent=[duration {}s, cooldown {}s, alpha {}], warlock=[markCooldown {}s, killCooldown {}s], juggernaut=[initial {}s, reduction {}s, min {}s], trickster=[duration {}s], puppetmaster=[duration {}s, cooldown {}s, random {}], pelican=[eatCooldown {}s], snitch=[tasks {}], timeMaster=[rewind {}s, cooldown {}s, uses {}], c4Back=[{}, {}, {}; rot {}, {}, {}; slant {}; scale {}]",
						sender.getName().getString(),
						GexpressConfig.getC4Price(),
						GexpressConfig.getC4FuseSeconds(),
						GexpressConfig.getC4FirstBeepSeconds(),
						GexpressConfig.getWrongWirePercent(),
						GexpressConfig.getGrenadePrice(),
						GexpressConfig.getMaxKillerAmount(),
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
						GexpressConfig.getTricksterSwapDurationSeconds(),
						GexpressConfig.getPuppetmasterControlDurationSeconds(),
						GexpressConfig.getPuppetmasterControlCooldownSeconds(),
						GexpressConfig.isPuppetmasterRandomTarget(),
						GexpressConfig.getPelicanEatCooldownSeconds(),
						GexpressConfig.getSnitchTasksRequired(),
						GexpressConfig.getTimeMasterRewindSeconds(),
						GexpressConfig.getTimeMasterCooldownSeconds(),
						GexpressConfig.getTimeMasterMaxUses(),
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
			GexpressConfig.getMedicShieldCooldownSeconds(),
			GexpressConfig.doesMedicShieldKnifeBreaks(),
			GexpressConfig.getSilentShadowDurationSeconds(),
			GexpressConfig.getSilentShadowCooldownSeconds(),
			GexpressConfig.getWarlockMarkCooldownSeconds(),
			GexpressConfig.getWarlockKillCooldownSeconds(),
			GexpressConfig.getJuggernautInitialCooldownSeconds(),
			GexpressConfig.getJuggernautCooldownReductionSeconds(),
			GexpressConfig.getJuggernautMinimumCooldownSeconds(),
			GexpressConfig.getTricksterSwapDurationSeconds(),
			GexpressConfig.getPuppetmasterControlDurationSeconds(),
			GexpressConfig.getPuppetmasterControlCooldownSeconds(),
			GexpressConfig.isPuppetmasterRandomTarget(),
			GexpressConfig.getPelicanEatCooldownSeconds(),
			GexpressConfig.getSnitchTasksRequired(),
			GexpressConfig.getTimeMasterRewindSeconds(),
			GexpressConfig.getTimeMasterCooldownSeconds(),
			GexpressConfig.getTimeMasterMaxUses(),
			GexpressConfig.getMaxKillerAmount(),
			GexpressConfig.getC4BackOffsetX(),
			GexpressConfig.getC4BackOffsetY(),
			GexpressConfig.getC4BackOffsetZ(),
			GexpressConfig.getC4BackRotationX(),
			GexpressConfig.getC4BackRotationY(),
			GexpressConfig.getC4BackRotationZ(),
			GexpressConfig.getC4BackSlant(),
			GexpressConfig.getC4BackScale(),
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
