package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.network.GexpressConfigSyncPayload;
import dev.mapselect.network.PuppetmasterConfigPayload;
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
		GexpressDevCategory.clearPendingTrainCartRows();
		selectedMapPreset = null;
		return buildScreen(parent);
	}

	static void navigateMaps(Screen parent, String presetName) {
		selectedMapPreset = presetName;
		Screen next = buildScreen(parent);
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(next);
		selectMapsTab(next);
	}

	private static Screen buildScreen(Screen parent) {
		boolean isOp = canEditOptions();
		boolean canEditGame = isOp;
		BiConsumer<String, Screen> stage = GexpressOptionsScreen::stage;

		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
			.title(Text.translatable("gui.gexpress.config.title"))
			.save(GexpressOptionsScreen::onSave);

		mapsTabIndex = -1;
		int idx = 0;
		builder.category(GexpressClientCategory.build(parent, isOp, stage));
		idx++;

		if (isDevPlayer()) {
			builder.category(GexpressDevCategory.build(parent));
			idx++;
		}

		if (canEditGame) {
			builder.category(GexpressGameCategory.build(parent, stage));
			idx++;
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
		flushWatheExtendedPending();
		flushRoleModifierTuningCommands();
		GexpressDevCategory.flushTrainCartCommands();
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
			stageChatCommand("g tuning role " + id + " chance " + value));
		pendingRoleMax.forEach((id, value) ->
			stageChatCommand("g tuning role " + id + " amount " + value));
		pendingModifierChance.forEach((id, value) ->
			stageChatCommand("g tuning modifier " + id + " chance " + value));
		pendingModifierMax.forEach((id, value) ->
			stageChatCommand("g tuning modifier " + id + " amount " + value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void pushMapPresetsToServer() {
		if (GexpressMapPresetsCategory.pendingEdits.isEmpty()) return;
		ClientPresetCache.savePending(GexpressMapPresetsCategory.pendingEdits);
		GexpressMapPresetsCategory.pendingEdits.clear();
	}

	static void pushGexpressConfigToServer() {
		if (!ClientPlayNetworking.canSend(GexpressConfigSyncPayload.ID)) return;
		ClientPlayNetworking.send(new GexpressConfigSyncPayload(
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
		));
		if (ClientPlayNetworking.canSend(PuppetmasterConfigPayload.ID)) {
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

	private static boolean canEditOptions() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.canEditGameOptions(mc.player);
	}

	private static boolean isDevPlayer() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && GexpressPermissions.isDev(mc.player);
	}
}
