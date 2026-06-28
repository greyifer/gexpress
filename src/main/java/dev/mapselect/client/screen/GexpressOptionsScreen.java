package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.network.config.GexpressConfigSyncPayload;
import dev.mapselect.network.config.GexpressDevTuningPayload;
import dev.mapselect.network.config.GexpressTaskConfigPayload;
import dev.mapselect.network.config.PuppetmasterConfigPayload;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class GexpressOptionsScreen {
	private GexpressOptionsScreen() {}

	static final Map<String, Boolean> pendingRoleState = new LinkedHashMap<>();
	static final Map<String, Boolean> pendingModifierState = new LinkedHashMap<>();
	static final Map<String, Integer> pendingRoleChance = new LinkedHashMap<>();
	static final Map<String, Integer> pendingRoleMax = new LinkedHashMap<>();
	static final Map<String, Integer> pendingModifierChance = new LinkedHashMap<>();
	static final Map<String, Integer> pendingModifierMax = new LinkedHashMap<>();
	static final List<String> pendingChatCommands = new ArrayList<>();

	private static String selectedMapPreset = null;
	private static int mapsTabIndex = -1;
	private static String selectedTrainCartMap = null;
	private static int trainCartsTabIndex = -1;

	public static Screen create(Screen parent) {
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
		selectedMapPreset = null;
		selectedTrainCartMap = null;
		return buildScreen(parent);
	}

	static void navigateMaps(Screen parent, String presetName) {
		selectedMapPreset = presetName;
		Screen next = buildScreen(parent);
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(next);
		selectMapsTab(next);
	}

	static void navigateTrainCarts(Screen parent, String mapPresetName) {
		selectedTrainCartMap = mapPresetName;
		Screen next = buildScreen(parent);
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(next);
		selectTrainCartsTab(next);
	}

	private static Screen buildScreen(Screen parent) {
		boolean canEditGame = canEditGameTab();
		boolean canViewGame = canViewGameTab();
		boolean canViewPlayers = canViewPlayersTab();
		boolean canViewMaps = canViewMapsTab();
		boolean canViewTrainCarts = canViewTrainCartsTab();
		boolean canViewDev = canViewDevTab();
		BiConsumer<String, Screen> stage = GexpressOptionsScreen::stage;

		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
			.title(Text.translatable("gui.gexpress.config.title"))
			.save(GexpressOptionsScreen::onSave);

		mapsTabIndex = -1;
		trainCartsTabIndex = -1;
		int idx = 0;
		builder.category(GexpressClientCategory.build(parent, canEditGame, stage));
		idx++;
		builder.category(GexpressSkinsCategory.build(parent));
		idx++;
		builder.category(GexpressXpRoadmapCategory.build(parent));
		idx++;
		builder.category(GexpressBugReportsCategory.build(parent));
		idx++;

		if (canViewDev) {
			builder.category(GexpressDevCategory.build(parent));
			idx++;
		}

		if (canViewPlayers) {
			builder.category(GexpressPlayersCategory.build(parent, canEditTags(), canManageProgression(), canManageEconomy()));
			idx++;
		}

		if (canViewTrainCarts) {
			ConfigCategory trainCarts = (selectedTrainCartMap == null)
				? GexpressTrainCartsScreen.buildListCategory(parent)
				: GexpressTrainCartsScreen.buildDetailCategory(parent, selectedTrainCartMap);
			builder.category(trainCarts);
			trainCartsTabIndex = idx;
			idx++;
		}

		if (canViewGame) {
			builder.category(GexpressGameCategory.build(parent, stage));
			idx++;
		}

		if (canViewMaps) {
			ConfigCategory maps = (selectedMapPreset == null)
				? GexpressMapScreen.buildListCategory(parent)
				: GexpressMapDetailScreen.buildDetailCategory(parent, selectedMapPreset);
			builder.category(maps);
			mapsTabIndex = idx;
		}

		return builder.build().generateScreen(parent);
	}

	private static void selectMapsTab(Screen screen) {
		if (mapsTabIndex < 0) return;
		try {
			if (screen instanceof YACLScreen ys && ys.tabNavigationBar != null) {
				ys.tabNavigationBar.selectTab(mapsTabIndex, false);
			}
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to select Maps tab: {}", t.toString());
		}
	}

	private static void selectTrainCartsTab(Screen screen) {
		if (trainCartsTabIndex < 0) return;
		try {
			if (screen instanceof YACLScreen ys && ys.tabNavigationBar != null) {
				ys.tabNavigationBar.selectTab(trainCartsTabIndex, false);
			}
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to select Train Carts tab: {}", t.toString());
		}
	}

	private static void stage(String fullCommand, Screen parent) {
		invokeWatheExtendedVoid("stageCommand", new Class<?>[]{String.class, Screen.class}, new Object[]{fullCommand, parent});
	}

	static void stageChatCommand(String command) {
		pendingChatCommands.add(command);
	}

	private static void onSave() {
		GexpressConfig.save();
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
		pendingRoleChance.put(id, clamp(value, RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX));
	}

	static void setRoleMax(String id, int value) {
		pendingRoleMax.put(id, clamp(value, RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX));
	}

	static void setModifierChance(String id, int value) {
		pendingModifierChance.put(id, clamp(value, RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX));
	}

	static void setModifierMax(String id, int value) {
		pendingModifierMax.put(id, clamp(value, RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX));
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
				GexpressConfig.getPainterDoorwayDurationSeconds(),
				GexpressConfig.getPainterDoorwayCooldownSeconds(),
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
				GexpressConfig.doLoversWinIndependently()
			));
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
				GexpressConfig.getMutedNotePrice()
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

	private static boolean canViewPlayersTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewPlayersTab(mc.player);
	}

	private static boolean canViewGameTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewGameTab(mc.player);
	}

	private static boolean canEditGameTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditGameTab(mc.player);
	}

	private static boolean canViewMapsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewMapsTab(mc.player);
	}

	private static boolean canEditMapsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditMapsTab(mc.player);
	}

	private static boolean canViewTrainCartsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewTrainCartsTab(mc.player);
	}

	private static boolean canEditTrainCartsTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditTrainCartsTab(mc.player);
	}

	private static boolean canViewDevTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canViewDevTab(mc.player);
	}

	private static boolean canEditDevTab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditDevTab(mc.player);
	}

	private static boolean canEditTags() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditTags(mc.player);
	}

	private static boolean canManageProgression() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canManageProgression(mc.player);
	}

	private static boolean canManageEconomy() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canManageEconomy(mc.player);
	}
}
