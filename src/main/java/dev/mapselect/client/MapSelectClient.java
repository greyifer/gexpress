package dev.mapselect.client;

import cat.rezelyn.watheextended.client.screen.GuidebookScreen;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.item.DevWeaponSkinStamper;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.GexpressConfigSyncPayload;
import dev.mapselect.network.PuppetmasterConfigPayload;
import dev.mapselect.registry.MapSelectBlockEntities;
import dev.mapselect.registry.MapSelectBlocks;
import dev.mapselect.registry.MapSelectParticles;
import dev.mapselect.client.particle.SandDriftParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.sound.SoundCategory;

public class MapSelectClient implements ClientModInitializer {
	private static final SoundCategory[] SOUND_CATEGORIES = SoundCategory.values();

	@Override
	public void onInitializeClient() {
		registerConfigReceiver();
		ClientAbilityKeys.register();
		ClientShortSightedState.register();
		ClientNightVisionState.register();
		ClientMedicShieldState.register();
		ClientMedicState.register();
		ClientNeutralWinState.register();
		ClientSnitchState.register();
		ClientSeerState.register();
		ClientTimeMasterState.register();
		ClientTimeMasterFreezeState.register();
		ClientTimeMasterRewindState.register();
		ClientSilentShadowState.register();
		ClientWarlockState.register();
		ClientTricksterState.register();
		ClientPuppetmasterState.register();
		ClientScatterBrainState.register();
		ClientVultureState.register();
		ClientTrackerState.register();
		ClientAltruistState.register();
		ClientAbilityCooldownHud.register();
		ClientPresetCache.registerClient();
		ClientTrainPresetCache.registerClient();
		ModelLoadingPlugin.register(new DevWeaponModels());
		ParticleFactoryRegistry.getInstance().register(MapSelectParticles.SAND_DRIFT, SandDriftParticle.Factory::new);
		BlockEntityRendererFactories.register(MapSelectBlockEntities.GREYIFER_PLUSH, GreyiferPlushBlockEntityRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.GREYIFER_PLUSH, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(MapSelectBlocks.IWY_PLUSH, RenderLayer.getCutout());

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer per) {
				@SuppressWarnings("unchecked")
				FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx =
					(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>) (FeatureRendererContext<?, ?>) per;
				registrationHelper.register(new C4BackFeatureRenderer(ctx));
			}
		});

		ClientTickEvents.END_WORLD_TICK.register(world -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.options == null) return;
			if (mc.player != null) DevWeaponSkinStamper.stamp(mc.player);

			GameWorldComponent gc = GameWorldComponent.KEY.getNullable(world);
			if (gc != null && gc.getFade() > 0) return;

			for (SoundCategory cat : SOUND_CATEGORIES) {
				float target = mc.options.getSoundVolume(cat);
				mc.getSoundManager().updateSoundVolume(cat, target);
			}
		});
	}

	private static void registerConfigReceiver() {
		ClientPlayNetworking.registerGlobalReceiver(GexpressConfigSyncPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				GexpressConfig.apply(
				payload.c4Price(),
				payload.c4FuseSeconds(),
				payload.c4FirstBeepSeconds(),
				payload.wrongWirePercent(),
				payload.grenadePrice(),
				payload.medicShieldCooldownSeconds(),
				payload.medicShieldKnifeBreaks(),
				payload.silentShadowDurationSeconds(),
				payload.silentShadowCooldownSeconds(),
				payload.warlockMarkCooldownSeconds(),
				payload.warlockKillCooldownSeconds(),
				payload.juggernautInitialCooldownSeconds(),
				payload.juggernautCooldownReductionSeconds(),
				payload.juggernautMinimumCooldownSeconds(),
				payload.tricksterSwapDurationSeconds(),
				payload.tricksterDancingCartsMaxUses(),
				payload.puppetmasterControlDurationSeconds(),
				payload.puppetmasterControlCooldownSeconds(),
				payload.puppetmasterRandomTarget(),
				payload.puppetmasterControlRange(),
				payload.pelicanEatCooldownSeconds(),
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
				payload.lastDeathShieldEnabled(),
				payload.bountyHunterBountyIntervalSeconds(),
				payload.bountyHunterRewardGold(),
				payload.bountyHunterFailCooldownSeconds(),
				payload.useCustomRoleCounts(),
				payload.maxKillerAmount(),
				payload.maxVigilanteAmount(),
				payload.playersPerKiller(),
				payload.playersPerVigilante(),
				payload.c4BackOffsetX(),
				payload.c4BackOffsetY(),
				payload.c4BackOffsetZ(),
				payload.c4BackRotationX(),
				payload.c4BackRotationY(),
				payload.c4BackRotationZ(),
				payload.c4BackSlant(),
				payload.c4BackScale(),
				payload.c4PlacementPresets(),
				payload.roleDescriptionOverrides(),
				payload.shortSightedFogRange(),
				payload.medicShieldBlockFlashTicks(),
				payload.medicShieldBreakFlashTicks(),
				payload.medicShieldBlockFlashAlpha(),
				payload.medicShieldBreakFlashAlpha(),
				payload.silentShadowAlpha()
				);
				GuidebookScreen.invalidateIfOpen();
			}));
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterConfigPayload.ID, (payload, context) ->
			context.client().execute(() -> GexpressConfig.puppetmasterCanKillOwnBody = payload.canKillOwnBody()));
	}
}
