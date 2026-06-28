package dev.mapselect.client;
import dev.mapselect.client.ability.ClientAbilityTargetState;
import dev.mapselect.client.ambience.ClientFreshAirAmbienceState;
import dev.mapselect.client.game.ClientNeutralWinState;
import dev.mapselect.client.game.ClientRenderDistanceGuard;
import dev.mapselect.client.game.ClientRoundEndRoleRoster;
import dev.mapselect.client.game.ClientSpectatorRoleRevealDelay;
import dev.mapselect.client.hud.ClientAbilityCooldownHud;
import dev.mapselect.client.hud.ClientKnifeChargeHud;
import dev.mapselect.client.hud.ClientLevelHud;
import dev.mapselect.client.hud.ClientStaminaBarFix;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.input.ClientDeadGuidebookKey;
import dev.mapselect.client.input.ClientSpectatorVoiceKeys;
import dev.mapselect.client.modifier.lovers.ClientLoversState;
import dev.mapselect.client.modifier.muted.ClientMutedPreferenceState;
import dev.mapselect.client.modifier.shortsighted.ClientShortSightedState;
import dev.mapselect.client.render.C4BackFeatureRenderer;
import dev.mapselect.client.render.CoinBarrierBlockEntityRenderer;
import dev.mapselect.client.render.ClientPainterDoorwayRenderer;
import dev.mapselect.client.render.AxiomClientRefresh;
import dev.mapselect.client.render.DevWeaponModels;
import dev.mapselect.client.render.DoorKeyEngravingRenderer;
import dev.mapselect.client.render.FusedOrnamentBlockEntityRenderer;
import dev.mapselect.client.render.GoldBeveragePlateBlockEntityRenderer;
import dev.mapselect.client.render.GreyiferPlushBlockEntityRenderer;
import dev.mapselect.client.render.TutorialPassengerEntityRenderer;
import dev.mapselect.client.render.TwinBodyEntityRenderer;
import dev.mapselect.client.role.altruist.ClientAltruistState;
import dev.mapselect.client.role.bodyguard.ClientBodyguardState;
import dev.mapselect.client.role.bountyhunter.ClientBountyHunterState;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.covenant.ClientCovenantState;
import dev.mapselect.client.role.cupid.ClientCupidState;
import dev.mapselect.client.role.guardian.ClientGuardianAngelState;
import dev.mapselect.client.role.harlequin.ClientTricksterState;
import dev.mapselect.client.role.juggernaut.ClientJuggernautState;
import dev.mapselect.client.role.mafia.ClientJanitorState;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.client.role.medic.ClientMedicShieldState;
import dev.mapselect.client.role.medic.ClientMedicState;
import dev.mapselect.client.role.painter.ClientPainterState;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import dev.mapselect.client.role.scatterbrain.ClientScatterBrainState;
import dev.mapselect.client.role.seer.ClientSeerState;
import dev.mapselect.client.role.silent.ClientSilentShadowState;
import dev.mapselect.client.role.skincrawler.ClientSkincrawlerState;
import dev.mapselect.client.role.snitch.ClientSnitchState;
import dev.mapselect.client.role.spy.ClientSpyState;
import dev.mapselect.client.role.spy.SpyBugFeatureRenderer;
import dev.mapselect.client.role.timemaster.ClientTimeMasterFreezeState;
import dev.mapselect.client.role.timemaster.ClientTimeMasterRewindState;
import dev.mapselect.client.role.timemaster.ClientTimeMasterState;
import dev.mapselect.client.role.tracker.ClientTrackerState;
import dev.mapselect.client.role.twins.ClientTwinsState;
import dev.mapselect.client.role.vengeful.ClientVengefulSpiritState;
import dev.mapselect.client.role.warlock.ClientWarlockState;
import dev.mapselect.client.skin.BlockbenchSkinImporter;
import dev.mapselect.client.tutorial.ClientTutorialExperience;
import dev.mapselect.client.tutorial.ClientTutorialState;


import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.item.DevWeaponSkinStamper;
import dev.mapselect.client.effect.ClientBlackWhiteOverlay;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.client.screen.GexpressSkinsCategory;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.config.GexpressConfigSyncPayload;
import dev.mapselect.network.config.GexpressDevTuningPayload;
import dev.mapselect.network.config.GexpressTaskConfigPayload;
import dev.mapselect.network.config.PuppetmasterConfigPayload;
import dev.mapselect.network.coinbarrier.CoinBarrierEditOpenPayload;
import dev.mapselect.network.coinbarrier.CoinBarrierPurchaseOpenPayload;
import dev.mapselect.network.floatingtext.FloatingTextEditOpenPayload;
import dev.mapselect.network.bugreport.BugReportListPayload;
import dev.mapselect.network.progression.SkinCaseResultPayload;
import dev.mapselect.network.progression.ImportedSkinCatalogPayload;
import dev.mapselect.network.progression.ImportedSkinImportResultPayload;
import dev.mapselect.client.screen.CoinBarrierEditScreen;
import dev.mapselect.client.screen.CoinBarrierPurchaseScreen;
import dev.mapselect.client.screen.FloatingTextEditScreen;
import dev.mapselect.client.screen.BugReportStore;
import dev.mapselect.role.GexpressRoleAnnouncementTexts;
import dev.mapselect.registry.MapSelectBlockEntities;
import dev.mapselect.registry.MapSelectBlocks;
import dev.mapselect.registry.MapSelectEntities;
import dev.mapselect.registry.MapSelectParticles;
import dev.mapselect.client.particle.SandDriftParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MapSelectClient implements ClientModInitializer {
	private static final SoundCategory[] SOUND_CATEGORIES = SoundCategory.values();
	private static final float[] LAST_SOUND_VOLUMES = new float[SOUND_CATEGORIES.length];
	private static boolean soundFadeActive;

	@Override
	public void onInitializeClient() {
		java.util.Arrays.fill(LAST_SOUND_VOLUMES, -1.0F);
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			BlockbenchSkinImporter.refreshGeneratedModels(client).exceptionally(error -> {
				dev.mapselect.MapSelect.LOGGER.warn("Failed to prepare imported skin models.", error);
				return null;
			});
		});
		GexpressRoleAnnouncementTexts.register();
		registerConfigReceiver();
		ClientAbilityKeys.register();
		ClientTutorialState.register();
		ClientTutorialExperience.register();
		ClientMutedPreferenceState.register();
		ClientRenderDistanceGuard.register();
		AxiomClientRefresh.register();
		ClientSpectatorRoleRevealDelay.register();
		ClientSpectatorVoiceKeys.register();
		ClientLevelHud.register();
		registerPlushTooltips();
		ClientStaminaBarFix.register();
		ClientDeadGuidebookKey.register();
		ClientFreshAirAmbienceState.register();
		ClientShortSightedState.register();
		ClientMedicShieldState.register();
		ClientMedicState.register();
		ClientNeutralWinState.register();
		ClientLoversState.register();
		ClientSnitchState.register();
		ClientSeerState.register();
		ClientCupidState.register();
		ClientVengefulSpiritState.register();
		ClientTimeMasterState.register();
		ClientTimeMasterFreezeState.register();
		ClientTimeMasterRewindState.register();
		ClientSilentShadowState.register();
		ClientWarlockState.register();
		ClientJuggernautState.register();
		ClientTricksterState.register();
		ClientPuppetmasterState.register();
		ClientScatterBrainState.register();
		ClientSkincrawlerState.register();
		ClientPainterState.register();
		ClientSpyState.register();
		ClientVultureState.register();
		ClientTrackerState.register();
		ClientTwinsState.register();
		ClientAltruistState.register();
		ClientMafiaState.register();
		ClientCopycatState.register();
		ClientJanitorState.register();
		ClientBountyHunterState.register();
		ClientCovenantState.register();
		ClientBodyguardState.register();
		ClientGuardianAngelState.register();
		ClientAbilityTargetState.register();
		ClientRoundEndRoleRoster.register();
		ClientAbilityCooldownHud.register();
		DoorKeyEngravingRenderer.register();
		ClientPainterDoorwayRenderer.register();
		ClientPlayNetworking.registerGlobalReceiver(CoinBarrierEditOpenPayload.ID, (payload, context) ->
			context.client().execute(() -> context.client().setScreen(new CoinBarrierEditScreen(
				context.client().currentScreen, payload.pos(), payload.price(), payload.title()))));
		ClientPlayNetworking.registerGlobalReceiver(CoinBarrierPurchaseOpenPayload.ID, (payload, context) ->
			context.client().execute(() -> context.client().setScreen(new CoinBarrierPurchaseScreen(
				context.client().currentScreen, payload.pos(), payload.price(), payload.title()))));
		ClientPlayNetworking.registerGlobalReceiver(FloatingTextEditOpenPayload.ID, (payload, context) ->
			context.client().execute(() -> context.client().setScreen(new FloatingTextEditScreen(
				context.client().currentScreen, payload.pos(), payload.text()))));
		ClientPlayNetworking.registerGlobalReceiver(BugReportListPayload.ID, (payload, context) ->
			context.client().execute(() -> BugReportStore.applyRemoteReports(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ImportedSkinCatalogPayload.ID, (payload, context) ->
			context.client().execute(() -> BlockbenchSkinImporter.applyRemoteCatalog(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(ImportedSkinImportResultPayload.ID, (payload, context) ->
			context.client().execute(() -> BlockbenchSkinImporter.completeServerImport(payload)));
		ClientKnifeChargeHud.register();
		ClientBlackWhiteOverlay.register();
		ClientPresetCache.registerClient();
		ClientTrainPresetCache.registerClient();
		ModelLoadingPlugin.register(new DevWeaponModels());
		ParticleFactoryRegistry.getInstance().register(MapSelectParticles.SAND_DRIFT, SandDriftParticle.Factory::new);
		BlockEntityRendererFactories.register(MapSelectBlockEntities.GREYIFER_PLUSH, GreyiferPlushBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(MapSelectBlockEntities.GOLD_BEVERAGE_PLATE, GoldBeveragePlateBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(MapSelectBlockEntities.FUSED_ORNAMENT, FusedOrnamentBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(MapSelectBlockEntities.COIN_BARRIER, CoinBarrierBlockEntityRenderer::new);
		EntityRendererRegistry.register(MapSelectEntities.TUTORIAL_PASSENGER, TutorialPassengerEntityRenderer::new);
		EntityRendererRegistry.register(MapSelectEntities.TWIN_BODY, TwinBodyEntityRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.GREYIFER_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.IWY_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.LUX_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.WTFJIMJIM_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.PIZZA_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.JEMSEA_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.PARROTMARROW_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.EVIEEVEEE_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.ASTRONOMIKYU_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.GOLD_FOOD_PLATTER, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.GOLD_DRINK_TRAY, RenderLayer.getCutout());

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer per) {
				@SuppressWarnings("unchecked")
				FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx =
					(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>) (FeatureRendererContext<?, ?>) per;
				registrationHelper.register(new C4BackFeatureRenderer(ctx));
				registrationHelper.register(new SpyBugFeatureRenderer(ctx));
			}
		});

		ClientTickEvents.END_WORLD_TICK.register(world -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.options == null) return;
			if (mc.player != null) DevWeaponSkinStamper.stamp(mc.player);

			GameWorldComponent gc = GameWorldComponent.KEY.getNullable(world);
			if (gc != null && gc.getFade() > 0) {
				soundFadeActive = true;
				return;
			}

			for (int i = 0; i < SOUND_CATEGORIES.length; i++) {
				SoundCategory category = SOUND_CATEGORIES[i];
				float target = mc.options.getSoundVolume(category);
				if (!soundFadeActive && Float.compare(LAST_SOUND_VOLUMES[i], target) == 0) continue;
				mc.getSoundManager().updateSoundVolume(category, target);
				LAST_SOUND_VOLUMES[i] = target;
			}
			soundFadeActive = false;
		});
	}

	private static void registerConfigReceiver() {
		ClientPlayNetworking.registerGlobalReceiver(GexpressConfigSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> applyConfigPayload(payload)));
		ClientPlayNetworking.registerGlobalReceiver(GexpressConfigSyncPayload.LEGACY_ID, (payload, context) ->
			context.client().execute(() -> applyConfigPayload(payload)));
		ClientPlayNetworking.registerGlobalReceiver(GexpressTaskConfigPayload.ID, (payload, context) ->
			context.client().execute(() -> GexpressConfig.applyTaskConfig(payload.conversationEnabled(),
				payload.conversationChancePercent(), payload.conversationDurationSeconds(),
				payload.conversationRadiusBlocks(), payload.conversationVerticalToleranceBlocks())));
		ClientPlayNetworking.registerGlobalReceiver(GexpressDevTuningPayload.ID, (payload, context) ->
			context.client().execute(() -> GexpressConfig.applyDevTuning(payload.levelRoundXp(),
				payload.levelWinXp(), payload.levelNeutralWinBonusXp(), payload.levelKillXp(),
				payload.levelCivilianTaskXp(), payload.levelBaseXp(), payload.levelXpIncrease(),
				payload.levelRoadmapDisplayLevels(), payload.levelXpOverrides(), payload.levelRewardRoadmap(),
				payload.levelTags(),
				payload.grenadeLineOfSightPassThroughBlocks(),
				payload.goldFoodPlatterPrice(), payload.goldDrinkTrayPrice(), payload.skinCaseRows(),
				payload.mutedNotePrice())));
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterConfigPayload.ID, (payload, context) ->
			context.client().execute(() -> GexpressConfig.puppetmasterCanKillOwnBody = payload.canKillOwnBody()));
		ClientPlayNetworking.registerGlobalReceiver(SkinCaseResultPayload.ID, (payload, context) ->
			context.client().execute(() -> GexpressSkinsCategory.handleCaseResult(payload)));
	}

	private static void registerPlushTooltips() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			String role = plushContribution(stack);
			if (role.isBlank()) return;
			lines.add(Math.min(1, lines.size()), Text.literal(role).formatted(Formatting.GOLD));
		});
	}

	private static String plushContribution(net.minecraft.item.ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		if (stack.isOf(MapSelectBlocks.GREYIFER_PLUSH_ITEM)) return "Developer";
		if (stack.isOf(MapSelectBlocks.IWY_PLUSH_ITEM)) return "Community Owner & Editor";
		if (stack.isOf(MapSelectBlocks.PIZZA_PLUSH_ITEM)) return "Community Owner";
		if (stack.isOf(MapSelectBlocks.LUX_PLUSH_ITEM)) return "Community Host";
		if (stack.isOf(MapSelectBlocks.WTFJIMJIM_PLUSH_ITEM)) return "Community Builder";
		if (stack.isOf(MapSelectBlocks.PARROTMARROW_PLUSH_ITEM)) return "Community Builder";
		if (stack.isOf(MapSelectBlocks.EVIEEVEEE_PLUSH_ITEM)) return "Community Builder";
		if (stack.isOf(MapSelectBlocks.JEMSEA_PLUSH_ITEM)) return "3D Modeller";
		if (stack.isOf(MapSelectBlocks.ASTRONOMIKYU_PLUSH_ITEM)) return "Music Composer";
		return "";
	}

	private static void applyConfigPayload(GexpressConfigSyncPayload payload) {
		GexpressConfig.apply(
				payload.c4Price(),
				payload.c4FuseSeconds(),
				payload.c4FirstBeepSeconds(),
				payload.wrongWirePercent(),
				payload.grenadePrice(),
				payload.bombSpecialistFirecrackerPrice(),
				payload.bombSpecialistLockpickPrice(),
				payload.bombSpecialistCrowbarPrice(),
				payload.mafiosoKnifePrice(),
				payload.mafiosoRevolverPrice(),
				payload.janitorPoisonVialPrice(),
				payload.janitorScorpionPrice(),
				payload.burglarCrowbarPrice(),
				payload.burglarLockpickPrice(),
				payload.passiveIncomeKiller(),
				payload.passiveIncomeCivilian(),
				payload.passiveIncomeNeutral(),
				payload.passiveIncomeVigilante(),
				payload.passiveIncomeMafia(),
				payload.medicShieldCooldownSeconds(),
				payload.medicShieldKnifeBreaks(),
				payload.silentShadowDurationSeconds(),
				payload.silentShadowCooldownSeconds(),
				payload.warlockMarkCooldownSeconds(),
				payload.warlockKillCooldownSeconds(),
				payload.juggernautInitialCooldownSeconds(),
				payload.juggernautCooldownReductionSeconds(),
				payload.juggernautMinimumCooldownSeconds(),
				payload.juggernautShieldRechargeSeconds(),
				payload.tricksterSwapDurationSeconds(),
				payload.tricksterMasqueradeCooldownSeconds(),
				payload.tricksterDancingCartsCooldownSeconds(),
				payload.tricksterDancingCartsMaxUses(),
				payload.puppetmasterControlDurationSeconds(),
				payload.puppetmasterControlCooldownSeconds(),
				payload.puppetmasterRandomTarget(),
				payload.puppetmasterControlRange(),
				payload.puppetmasterMaxUses(),
				payload.pelicanEatCooldownSeconds(),
				payload.pelicanEatPercentage(),
				payload.hungryFoodLimit(),
				payload.thirstyDrinkLimit(),
				payload.snitchTasksRequired(),
				payload.snitchWarningTasksRemaining(),
				payload.timeMasterRewindSeconds(),
				payload.timeMasterCooldownSeconds(),
				payload.timeMasterMaxUses(),
				payload.timeMasterFreezeDurationSeconds(),
				payload.timeMasterFreezeCooldownSeconds(),
				payload.timeMasterFreezeMaxUses(),
				payload.timeMasterFreezeRange(),
				payload.scatterBrainCooldownSeconds(),
				payload.trackerMaxTargets(),
				payload.trackerRange(),
				payload.trackerCooldownSeconds(),
				payload.altruistRange(),
				payload.skincrawlerBodyMaxAgeSeconds(),
				payload.skincrawlerCooldownSeconds(),
				payload.skincrawlerStunSeconds(),
				payload.skincrawlerRange(),
				payload.painterPaintDurationSeconds(),
				payload.painterBodyCooldownSeconds(),
				payload.painterPlayerCooldownSeconds(),
				payload.painterDoorwayDurationSeconds(),
				payload.painterDoorwayCooldownSeconds(),
				payload.painterMaxPaintedBodies(),
				payload.painterMaxPaintedPlayers(),
				payload.spyBugCost(),
				payload.spyBugDurationSeconds(),
				payload.spyBugRange(),
				payload.squeakerPitchPercent(),
				payload.masqueradePitchMinPercent(),
				payload.masqueradePitchMaxPercent(),
				payload.lastDeathShieldEnabled(),
				payload.guardianAngelAllowNonInnocents(),
				payload.lastStandEnabled(),
				payload.itemPickupPolicy(),
				payload.bountyHunterBountyIntervalSeconds(),
				payload.bountyHunterRewardGold(),
				payload.bountyHunterFailCooldownSeconds(),
				payload.godfatherBulletPrice(),
				payload.godfatherStartingBullets(),
				payload.godfatherMaxLoadedBullets(),
				payload.mafiaStartingGold(),
				payload.mafiaMinimumPlayers(),
				payload.godfatherStartingGold(),
				payload.mafiosoStartingGold(),
				payload.janitorStartingGold(),
				payload.mafiaRecruitRange(),
				payload.mafiaReplacementCooldownSeconds(),
				payload.mafiaRevolverKillCooldownSeconds(),
				payload.janitorCleanRange(),
				payload.janitorCleanCooldownSeconds(),
				payload.janitorRevolverCooldownAfterCleanSeconds(),
				payload.janitorCleanCooldownAfterKillSeconds(),
				payload.pickpocketMaxHoldSeconds(),
				payload.pickpocketCoinsPerSecond(),
				payload.pickpocketRange(),
				payload.copycatCopyCooldownSeconds(),
				payload.copycatCopyDurationSeconds(),
				payload.copycatCopyRange(),
				payload.useCustomRoleCounts(),
				payload.maxKillerAmount(),
				payload.maxVigilanteAmount(),
				payload.maxNeutralAmount(),
				payload.maxModifiersPerPlayer(),
				payload.playersPerKiller(),
				payload.playersPerVigilante(),
				payload.playersPerNeutral(),
				payload.c4BackOffsetX(),
				payload.c4BackOffsetY(),
				payload.c4BackOffsetZ(),
				payload.c4BackRotationX(),
				payload.c4BackRotationY(),
				payload.c4BackRotationZ(),
				payload.c4BackSlant(),
				payload.c4BackScale(),
				payload.spyBugOffsetX(),
				payload.spyBugOffsetY(),
				payload.spyBugOffsetZ(),
				payload.spyBugRotationX(),
				payload.spyBugRotationY(),
				payload.spyBugRotationZ(),
				payload.spyBugSlant(),
				payload.spyBugScale(),
				payload.c4PlacementPresets(),
				payload.roleDescriptionOverrides(),
				payload.shortSightedFogRange(),
				payload.medicShieldBlockFlashTicks(),
				payload.medicShieldBreakFlashTicks(),
				payload.medicShieldBlockFlashAlpha(),
				payload.medicShieldBreakFlashAlpha(),
				payload.silentShadowAlpha(),
				payload.specialRoleOccurrence(),
				payload.seerCompareCooldownSeconds(),
				payload.seerCompareDeathsRequired(),
				payload.seerCompareRange(),
				payload.twinsSwapCooldownSeconds(),
				payload.twinsBodyDifferentRole(),
				payload.cupidRequiredAlivePairs(),
				payload.cupidPairCooldownSeconds(),
				payload.cupidRange(),
				payload.loversShowPartnerHud(),
				payload.loversAllowMixedSidePairs(),
				payload.covenantBiteCooldownSeconds(),
				payload.vengefulSpiritReviveDelaySeconds(),
				payload.vengefulSpiritRevengeSeconds(),
				payload.loversIndependentWin()
				);
		GuidebookScreen.invalidateIfOpen();
	}
}
