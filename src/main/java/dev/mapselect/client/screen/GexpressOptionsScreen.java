package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.mapselect.MapSelect;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.network.config.GexpressConfigSyncPayload;
import dev.mapselect.network.config.GexpressDevTuningPayload;
import dev.mapselect.network.config.GexpressTaskConfigPayload;
import dev.mapselect.network.config.PuppetmasterConfigPayload;
import dev.mapselect.network.config.DoomsayerConfigPayload;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GexpressOptionsScreen {
	private static final int AUTOSAVE_DELAY_TICKS = 8;
	private static final Set<Option<?>> pendingAutosaveOptions =
		Collections.newSetFromMap(new IdentityHashMap<>());
	private static int autosaveTicks = -1;

	private GexpressOptionsScreen() {}

	public static void registerClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tickAutosave());
	}

	static final Map<String, Boolean> pendingRoleState = new LinkedHashMap<>();
	static final Map<String, Boolean> pendingModifierState = new LinkedHashMap<>();
	static final Map<String, Integer> pendingRoleChance = new LinkedHashMap<>();
	static final Map<String, Integer> pendingRoleMax = new LinkedHashMap<>();
	static final Map<String, Integer> pendingModifierChance = new LinkedHashMap<>();
	static final Map<String, Integer> pendingModifierMax = new LinkedHashMap<>();
	static final List<String> pendingChatCommands = new ArrayList<>();

	public static void prepareForMenu() {
		cancelScheduledAutosave();
		pendingRoleState.clear();
		pendingModifierState.clear();
		pendingRoleChance.clear();
		pendingRoleMax.clear();
		pendingModifierChance.clear();
		pendingModifierMax.clear();
		pendingChatCommands.clear();
		RoleModifierTuningConfig.load();
		OptionVisibility.clearAll();
		resetWatheExtendedState();
		GexpressMapPresetsCategory.pendingEdits.clear();
		GexpressTrainCartsScreen.pendingEdits.clear();
	}

	public static void addWatheTabs(dev.isxander.yacl3.api.YetAnotherConfigLib.Builder builder, Screen parent) {
		var express = ConfigCategory.createBuilder().name(net.minecraft.text.Text.literal("G'Express"));
		if (dev.mapselect.client.replay.ClientReplay.canAccess()) express.option(menuButton("Past Matches", screen -> {
			dev.mapselect.client.replay.ClientReplay.control("list", "");
			return screen;
		}));
		express.option(menuButton("XP Roadmap", GexpressXpRoadmapCategory::createGlibScreen));
		express.option(menuButton("Bug Reports", GexpressBugReportsCategory::createGlibScreen));
		if (canViewGameTab()) express.option(menuButton("Gameplay", GexpressGameCategory::createGlibScreen));
		if (canViewDevTab()) express.option(menuButton("Developer", GexpressDevCategory::createGlibScreen));
		builder.category(express.build());
		if (canViewMapsTab() || canViewTrainCartsTab()) {
			var maps = ConfigCategory.createBuilder().name(net.minecraft.text.Text.literal("Maps & Trains"));
			if (canViewMapsTab()) maps.option(menuButton("Maps", GexpressPresetLibraryScreen::maps));
			if (canViewTrainCartsTab()) maps.option(menuButton("Train Carts", GexpressPresetLibraryScreen::trainCarts));
			builder.category(maps.build());
		}
		if (canViewPlayersTab()) {
			builder.category(ConfigCategory.createBuilder().name(net.minecraft.text.Text.literal("Players"))
				.option(menuButton("Players", screen -> GexpressPlayersCategory.createGlibScreen(screen,
					canEditTags(), canManageProgression(), canManageEconomy())))
				.build());
		}
	}

	private static dev.isxander.yacl3.api.ButtonOption menuButton(String label,
			java.util.function.Function<Screen, Screen> destination) {
		return dev.isxander.yacl3.api.ButtonOption.createBuilder()
			.name(net.minecraft.text.Text.literal(label))
			.text(net.minecraft.text.Text.literal("Open"))
			.action((screen, option) -> MinecraftClient.getInstance().setScreen(destination.apply(screen)))
			.build();
	}

	static void navigateMaps(Screen parent, String presetName) {
		if (presetName == null) {
			io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen.open(parent,
				"Loading map library...", 0xFFF4B860, () -> GexpressPresetLibraryScreen.maps(parent));
			return;
		}
		Map<String, dev.mapselect.preset.map.MapPreset> presets = ClientPresetCache.snapshot();
		dev.mapselect.preset.map.MapPreset preset = presets.get(presetName);
		if (preset == null) {
			io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen.open(parent,
				"Refreshing map library...", 0xFFF4B860, () -> GexpressPresetLibraryScreen.maps(parent));
			return;
		}
		ConfigCategory category = GexpressMapPresetsCategory.buildPresetCategory(presetName, preset);
		List<dev.isxander.yacl3.api.OptionGroup> groups = category.groups().stream().toList();
		List<GexpressGlibEditor.PageSource> pages = groups.size() >= 8 ? List.of(
			new GexpressGlibEditor.PageSource(net.minecraft.text.Text.literal("Areas"),
				net.minecraft.text.Text.literal("Whole-map, lobby, ready, play, reset, and fresh-air regions."), groups.subList(0, 2)),
			new GexpressGlibEditor.PageSource(net.minecraft.text.Text.literal("Spawning"),
				net.minecraft.text.Text.literal("Player positions and random spawns."), groups.subList(2, 5)),
			new GexpressGlibEditor.PageSource(net.minecraft.text.Text.literal("Rules"),
				net.minecraft.text.Text.literal("Terrain, play-area, reset, and couch behavior."), groups.subList(5, 6)),
			new GexpressGlibEditor.PageSource(net.minecraft.text.Text.literal("Environment"),
				net.minecraft.text.Text.literal("Weather, fog, room count, and linked train preset."), groups.subList(6, 7)),
			new GexpressGlibEditor.PageSource(net.minecraft.text.Text.literal("Tools"),
				net.minecraft.text.Text.literal("Import the current Wathe map state into this preset."), groups.subList(7, groups.size()))
		) : List.of(new GexpressGlibEditor.PageSource(category.name(), category.tooltip(), groups));
		io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen.open(parent,
			"Opening " + presetName + "...", 0xFFF4B860,
			() -> GexpressGlibEditor.screen(parent, presetName,
				"Map layout, environment, spawning, and role overrides.", 0xFFF4B860, pages));
	}

	static void navigateTrainCarts(Screen parent, String mapPresetName) {
		String message = mapPresetName == null ? "Loading train-cart library..." : "Opening " + mapPresetName + " carts...";
		io.github.caspe.glib.api.client.ui.GlibLoadingTransitionScreen.open(parent, message, 0xFFD98BFF,
			() -> mapPresetName == null ? GexpressPresetLibraryScreen.trainCarts(parent)
				: new GexpressTrainCartEditorScreen(parent, mapPresetName));
	}

	static void stage(String fullCommand, Screen parent) {
		invokeWatheExtendedVoid("stageCommand", new Class<?>[]{String.class, Screen.class}, new Object[]{fullCommand, parent});
	}

	static void stageChatCommand(String command) {
		pendingChatCommands.add(command);
	}

	public static void finishGlibSave() {
		cancelScheduledAutosave();
		GexpressConfig.save();
		RoleModifierTuningConfig.save();
		pushGexpressConfigToServer();
		pushMapPresetsToServer();
		pushTrainPresetsToServer();
		flushWatheExtendedPending();
		flushRoleModifierTuningCommands();
		flushChatCommands();
		pendingRoleState.clear();
		pendingModifierState.clear();
		pendingRoleChance.clear();
		pendingRoleMax.clear();
		pendingModifierChance.clear();
		pendingModifierMax.clear();
	}

	static void discardGlibChanges() {
		cancelScheduledAutosave();
		pendingRoleState.clear();
		pendingModifierState.clear();
		pendingRoleChance.clear();
		pendingRoleMax.clear();
		pendingModifierChance.clear();
		pendingModifierMax.clear();
		pendingChatCommands.clear();
		GexpressMapPresetsCategory.pendingEdits.clear();
		GexpressTrainCartsScreen.pendingEdits.clear();
	}

	static void requestGlibAutosave(Option<?> option) {
		if (option == null) return;
		pendingAutosaveOptions.add(option);
		autosaveTicks = AUTOSAVE_DELAY_TICKS;
	}

	private static void tickAutosave() {
		if (autosaveTicks < 0) return;
		if (autosaveTicks-- > 0) return;
		List<Option<?>> changed = List.copyOf(pendingAutosaveOptions);
		for (Option<?> option : changed) option.applyValue();
		finishGlibSave();
	}

	private static void cancelScheduledAutosave() {
		pendingAutosaveOptions.clear();
		autosaveTicks = -1;
	}

	static int getRoleChance(String id) {
		return pendingRoleChance.getOrDefault(id, RoleModifierTuningConfig.getRoleChance(id));
	}

	static int getRoleMax(String id) {
		return pendingRoleMax.getOrDefault(id, RoleModifierTuningConfig.getRoleMax(id));
	}

	static int getModifierChance(String id) {
		return pendingModifierChance.getOrDefault(id, RoleModifierTuningConfig.getModifierChance(id));
	}

	static int getModifierMax(String id) {
		return pendingModifierMax.getOrDefault(id, RoleModifierTuningConfig.getModifierMax(id));
	}

	static void setRoleChance(String id, int value) {
		int clamped = clamp(value, RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX);
		pendingRoleChance.put(id, clamped);
		RoleModifierTuningConfig.setRoleChance(id, clamped);
	}

	static void setRoleMax(String id, int value) {
		int clamped = clamp(value, RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX);
		pendingRoleMax.put(id, clamped);
		RoleModifierTuningConfig.setRoleMax(id, clamped);
	}

	static void setModifierChance(String id, int value) {
		int clamped = clamp(value, RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX);
		pendingModifierChance.put(id, clamped);
		RoleModifierTuningConfig.setModifierChance(id, clamped);
	}

	static void setModifierMax(String id, int value) {
		int clamped = clamp(value, RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX);
		pendingModifierMax.put(id, clamped);
		RoleModifierTuningConfig.setModifierMax(id, clamped);
	}

	private static void flushRoleModifierTuningCommands() {
		pendingRoleChance.forEach((id, value) ->
			stageChatCommand("g roles tuning " + id + " chance " + value));
		pendingRoleMax.forEach((id, value) ->
			stageChatCommand("g roles tuning " + id + " amount " + value));
		pendingModifierChance.forEach((id, value) ->
			stageChatCommand("g modifiers tuning " + id + " chance " + value));
		pendingModifierMax.forEach((id, value) ->
			stageChatCommand("g modifiers tuning " + id + " amount " + value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void pushMapPresetsToServer() {
		if (GexpressMapPresetsCategory.pendingEdits.isEmpty()) return;
		if (!canEditMapsTab()) {
			GexpressMapPresetsCategory.pendingEdits.clear();
			return;
		}
		ClientPresetCache.savePending(GexpressMapPresetsCategory.pendingEdits);
		GexpressMapPresetsCategory.pendingEdits.clear();
	}

	private static void pushTrainPresetsToServer() {
		if (GexpressTrainCartsScreen.pendingEdits.isEmpty()) return;
		if (!canEditTrainCartsTab()) {
			GexpressTrainCartsScreen.pendingEdits.clear();
			return;
		}
		ClientTrainPresetCache.savePending(GexpressTrainCartsScreen.pendingEdits);
		GexpressTrainCartsScreen.pendingEdits.clear();
	}

	static void pushGexpressConfigToServer() {
		boolean canEditGame = canEditGameTab();
		boolean canEditDev = canEditDevTab();
		if (canEditGame && ClientPlayNetworking.canSend(GexpressConfigSyncPayload.ID)) {
			ClientPlayNetworking.send(new GexpressConfigSyncPayload(
				GexpressConfig.getC4Price(),
				GexpressConfig.getC4FuseSeconds(),
				GexpressConfig.getC4FirstBeepSeconds(),
				GexpressConfig.getWrongWirePercent(),
				GexpressConfig.getGrenadePrice(),
				GexpressConfig.getBombSpecialistFirecrackerPrice(),
				GexpressConfig.getBombSpecialistLockpickPrice(),
				GexpressConfig.getBombSpecialistCrowbarPrice(),
				GexpressConfig.getMafiosoKnifePrice(),
				GexpressConfig.getMafiosoRevolverPrice(),
				GexpressConfig.getJanitorPoisonVialPrice(),
				GexpressConfig.getJanitorScorpionPrice(),
				GexpressConfig.getBurglarCrowbarPrice(),
				GexpressConfig.getBurglarLockpickPrice(),
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
				GexpressConfig.getPainterPaintDurationSeconds(),
				GexpressConfig.getPainterBodyCooldownSeconds(),
				GexpressConfig.getPainterPlayerCooldownSeconds(),
				GexpressConfig.getPainterMaxPaintedBodies(),
				GexpressConfig.getPainterMaxPaintedPlayers(),
				GexpressConfig.getSpyBugCost(),
				GexpressConfig.getSpyBugDurationSeconds(),
				GexpressConfig.getSpyBugRange(),
				GexpressConfig.getSqueakerPitchPercent(),
				GexpressConfig.getMasqueradePitchMinPercent(),
				GexpressConfig.getMasqueradePitchMaxPercent(),
				GexpressConfig.isLastDeathShieldEnabled(),
				GexpressConfig.canGuardianAngelPickNonInnocents(),
				GexpressConfig.isLastStandEnabled(),
				GexpressConfig.getItemPickupPolicyId(),
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
				GexpressConfig.getPickpocketMaxHoldSeconds(),
				GexpressConfig.getPickpocketCoinsPerSecond(),
				GexpressConfig.getPickpocketRange(),
				GexpressConfig.getCopycatCopyCooldownSeconds(),
				GexpressConfig.getCopycatCopyDurationSeconds(),
				GexpressConfig.getCopycatCopyRange(),
				GexpressConfig.useCustomRoleCounts(),
				GexpressConfig.getMaxKillerAmount(),
				GexpressConfig.getMaxVigilanteAmount(),
				GexpressConfig.getMaxNeutralAmount(),
				GexpressConfig.getMaxModifiersPerPlayer(),
				GexpressConfig.getPlayersPerKiller(),
				GexpressConfig.getPlayersPerVigilante(),
				GexpressConfig.getPlayersPerNeutral(),
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
				GexpressConfig.getSilentShadowAlpha(),
				GexpressConfig.getSpecialRoleOccurrenceId(),
				GexpressConfig.getSeerCompareCooldownSeconds(),
				GexpressConfig.getSeerCompareDeathsRequired(),
				GexpressConfig.getSeerCompareRange(),
				GexpressConfig.getTwinsSwapCooldownSeconds(),
				GexpressConfig.canTwinsBodyUseDifferentRole(),
				GexpressConfig.getCupidLovePercentage(),
				GexpressConfig.getCupidPairCooldownSeconds(),
				GexpressConfig.getCupidRange(),
				GexpressConfig.shouldShowLoverHud(),
				GexpressConfig.canLoversPairAcrossSides(),
				GexpressConfig.getCovenantBiteCooldownSeconds(),
				GexpressConfig.getVengefulSpiritReviveDelaySeconds(),
				GexpressConfig.getVengefulSpiritRevengeSeconds(),
				GexpressConfig.doLoversWinIndependently(),
				GexpressConfig.getVeteranMaxUses()
			));
		}
		if (canEditGame && ClientPlayNetworking.canSend(DoomsayerConfigPayload.ID)) {
			ClientPlayNetworking.send(new DoomsayerConfigPayload(
				GexpressConfig.getDoomsayerGuessesToWin(),
				GexpressConfig.doesDoomsayerDieOnWrongGuess()));
		}
		if (canEditGame && ClientPlayNetworking.canSend(GexpressTaskConfigPayload.ID)) {
			ClientPlayNetworking.send(new GexpressTaskConfigPayload(
				GexpressConfig.isConversationTaskEnabled(),
				GexpressConfig.getConversationTaskChancePercent(),
				GexpressConfig.getConversationTaskDurationSeconds(),
				GexpressConfig.getConversationTaskRadiusBlocks(),
				GexpressConfig.getConversationTaskVerticalToleranceBlocks()
			));
		}
		if (canEditDev && ClientPlayNetworking.canSend(GexpressDevTuningPayload.ID)) {
			ClientPlayNetworking.send(new GexpressDevTuningPayload(
				GexpressConfig.getLevelRoundXp(),
				GexpressConfig.getLevelWinXp(),
				GexpressConfig.getLevelNeutralWinBonusXp(),
				GexpressConfig.getLevelKillXp(),
				GexpressConfig.getLevelCivilianTaskXp(),
				GexpressConfig.getLevelBaseXp(),
				GexpressConfig.getLevelXpIncrease(),
				GexpressConfig.getLevelRoadmapDisplayLevels(),
				GexpressConfig.getLevelXpOverridesSyncString(),
				GexpressConfig.getLevelRewardRoadmapSyncString(),
				GexpressConfig.getLevelTagsSyncString(),
				GexpressConfig.getGrenadeLineOfSightPassThroughBlocksSyncString(),
				GexpressConfig.getGoldFoodPlatterPrice(),
				GexpressConfig.getGoldDrinkTrayPrice(),
				GexpressConfig.getSkinCaseRowsSyncString(),
				GexpressConfig.getMutedNotePrice(),
				GexpressConfig.getLobbyPollCooldownSeconds(),
				GexpressConfig.getLobbyPollDurationSeconds()
			));
		}
		if (canEditGame && ClientPlayNetworking.canSend(PuppetmasterConfigPayload.ID)) {
			ClientPlayNetworking.send(new PuppetmasterConfigPayload(GexpressConfig.canPuppetmasterKillOwnBody()));
		}
	}

	private static void flushChatCommands() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.player.networkHandler == null) {
			pendingChatCommands.clear();
			return;
		}
		for (String cmd : pendingChatCommands) {
			mc.player.networkHandler.sendChatCommand(cmd);
		}
		pendingChatCommands.clear();
	}

	private static void resetWatheExtendedState() {
		invokeWatheExtendedVoid("clearPendingState", new Class<?>[0], new Object[0]);
	}

	private static void flushWatheExtendedPending() {
		invokeWatheExtendedVoid("flushPendingChanges", new Class<?>[0], new Object[0]);
	}

	private static void invokeWatheExtendedVoid(String method, Class<?>[] paramTypes, Object[] args) {
		try {
			Class<?> cls = Class.forName("cat.rezelyn.watheextended.client.screen.WatheOptionsScreen");
			Method m = cls.getDeclaredMethod(method, paramTypes);
			m.setAccessible(true);
			m.invoke(null, args);
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to call WatheOptionsScreen.{}: {}", method, t.toString());
		}
	}

	static boolean canViewPlayersTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewPlayersTab(mc.player);
	}

	static boolean canViewGameTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewGameTab(mc.player);
	}

	static boolean canEditGameTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditGameTab(mc.player);
	}

	static boolean canViewMapsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewMapsTab(mc.player);
	}

	private static boolean canEditMapsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditMapsTab(mc.player);
	}

	static boolean canViewTrainCartsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewTrainCartsTab(mc.player);
	}

	private static boolean canEditTrainCartsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditTrainCartsTab(mc.player);
	}

	static boolean canViewDevTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewDevTab(mc.player);
	}

	static boolean canEditDevTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditDevTab(mc.player);
	}

	static boolean canEditTags() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditTags(mc.player);
	}

	static boolean canManageProgression() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canManageProgression(mc.player);
	}

	static boolean canManageEconomy() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canManageEconomy(mc.player);
	}
}
