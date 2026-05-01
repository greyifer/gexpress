package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.timemaster.TimeMasterComponent;
import dev.mapselect.role.warlock.WarlockComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientAbilityCooldownHud {
	private static final int BASE_ICON_SIZE = 12;
	private static final int BASE_ICON_BAR_GAP = 5;
	private static final int BASE_BAR_WIDTH = 91;
	private static final int BASE_BAR_HEIGHT = 6;
	private static final int BASE_BAR_SPACING = 14;
	private static final int BASE_HOTBAR_SIDE_GAP = 7;
	private static final int BASE_HOTBAR_TOP_GAP = 3;
	private static final int HOTBAR_HEIGHT = 22;
	private static final int TIMER_WIDTH = 50;
	private static final int FRAME_DARK = 0xFF140801;
	private static final int FRAME_BROWN = 0xFF5B3108;
	private static final int FRAME_GOLD = 0xFFC48921;
	private static final int TRACK_DARK = 0xFF1B0B03;
	private static final int TRACK_BROWN = 0xFF2B1405;
	private static final int FILL_GOLD = 0xFFE3A12E;
	private static final int FILL_LIGHT = 0xFFFFD869;
	private static final int FILL_DARK = 0xFF8B4A11;
	private static final Identifier ICON_MEDIC_SHIELD = hudIcon("ability_medic_shield");
	private static final Identifier ICON_SHADOW_MARCH = hudIcon("ability_shadow_march");
	private static final Identifier ICON_WARLOCK_MARK = hudIcon("ability_warlock_mark");
	private static final Identifier ICON_HEX_KILL = hudIcon("ability_hex_kill");
	private static final Identifier ICON_JUGGERNAUT_WEAPONS = hudIcon("ability_hex_kill");
	private static final Identifier ICON_MASQUERADE = hudIcon("ability_masquerade");
	private static final Identifier ICON_DANCING_CARTS = hudIcon("ability_masquerade");
	private static final Identifier ICON_TIME_REWIND = hudIcon("ability_masquerade");
	private static final Identifier ICON_TIME_FREEZE = hudIcon("ability_shadow_march");
	private static final Identifier ICON_PUPPET_STRINGS = hudIcon("ability_puppet_strings");
	private static final Identifier ICON_PELICAN_SWALLOW = hudIcon("ability_pelican_swallow");
	private static final Identifier ICON_SCATTER = hudIcon("ability_masquerade");
	private static final Identifier ICON_TRACKER = hudIcon("ability_warlock_mark");
	private static final Identifier ICON_ALTRUIST = hudIcon("ability_medic_shield");
	private static final Map<String, SyncedCooldown> SYNCED = new HashMap<>();
	private static Object syncedWorld;

	private ClientAbilityCooldownHud() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AbilityCooldownPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		HudRenderCallback.EVENT.register(ClientAbilityCooldownHud::render);
	}

	private static void apply(MinecraftClient client, AbilityCooldownPayload payload) {
		checkSyncedWorld(client);
		if (client == null || client.world == null || payload.key().isEmpty()
				|| payload.remainingTicks() <= 0 || payload.totalTicks() <= 0) {
			SYNCED.remove(payload.key());
			return;
		}
		SYNCED.put(payload.key(), new SyncedCooldown(
			client.world.getTime() + payload.remainingTicks(),
			payload.totalTicks(),
			payload.draining()
		));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null || client.options.hudHidden) return;
		checkSyncedWorld(client);

		List<AbilityBar> bars = barsFor(client);
		if (bars.isEmpty()) return;

		float scale = GexpressConfig.getAbilityHudScalePercent() / 100.0F;
		int iconSize = scaled(BASE_ICON_SIZE, scale);
		int iconBarGap = scaled(BASE_ICON_BAR_GAP, scale);
		int barWidth = scaled(BASE_BAR_WIDTH, scale);
		int barHeight = Math.max(4, scaled(BASE_BAR_HEIGHT, scale));
		int barSpacing = Math.max(scaled(BASE_BAR_SPACING, scale), iconSize + 2);
		int hotbarSideGap = scaled(BASE_HOTBAR_SIDE_GAP, scale);
		int hotbarTopGap = scaled(BASE_HOTBAR_TOP_GAP, scale);

		int hotbarRight = context.getScaledWindowWidth() / 2 + 91;
		int x = hotbarRight + hotbarSideGap + iconSize + iconBarGap + GexpressConfig.getAbilityHudOffsetX();
		x = clamp(x, 2 + iconSize + iconBarGap, context.getScaledWindowWidth() - barWidth - TIMER_WIDTH - 4);

		int hotbarTop = context.getScaledWindowHeight() - HOTBAR_HEIGHT;
		int totalIconHeight = iconSize + (bars.size() - 1) * barSpacing;
		int iconStartY = hotbarTop - hotbarTopGap - totalIconHeight + GexpressConfig.getAbilityHudOffsetY();
		iconStartY = clamp(iconStartY, 2, context.getScaledWindowHeight() - totalIconHeight - 2);
		int y = iconStartY + (iconSize - barHeight) / 2;
		TextRenderer text = client.textRenderer;
		for (int i = 0; i < bars.size(); i++) {
			drawBar(context, text, bars.get(i), x, y + i * barSpacing, iconSize, iconBarGap, barWidth, barHeight);
		}
	}

	private static List<AbilityBar> barsFor(MinecraftClient client) {
		Role role = localRole(client);
		if (role == null) return List.of();
		Identifier roleId = role.identifier();
		UUID playerId = client.player.getUuid();
		List<AbilityBar> bars = new ArrayList<>();

		if (MapSelectRoles.MEDIC_ID.equals(roleId)) {
			MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(client.world);
			long remaining = comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
			bars.add(cooldown(ICON_MEDIC_SHIELD, remaining, GexpressConfig.getMedicShieldCooldownSeconds() * 20L,
				0xFF4FD889, 0xFF216B42));
		} else if (MapSelectRoles.THE_SILENT_ID.equals(roleId)) {
			SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(client.world);
			long active = comp == null ? 0L : comp.activeRemainingTicks(playerId);
			if (active > 0L) {
				bars.add(draining(ICON_SHADOW_MARCH, active, GexpressConfig.getSilentShadowDurationSeconds() * 20L,
					0xFFB16CFF, 0xFF4D236E));
			} else {
				long remaining = comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
				bars.add(cooldown(ICON_SHADOW_MARCH, remaining, GexpressConfig.getSilentShadowCooldownSeconds() * 20L,
					0xFFB16CFF, 0xFF4D236E));
			}
		} else if (MapSelectRoles.WARLOCK_ID.equals(roleId)) {
			WarlockComponent comp = WarlockComponent.KEY.getNullable(client.world);
			long mark = comp == null ? 0L : comp.markCooldownRemainingTicks(playerId);
			long kill = comp == null ? 0L : comp.killCooldownRemainingTicks(playerId);
			bars.add(cooldown(ICON_WARLOCK_MARK, mark, GexpressConfig.getWarlockMarkCooldownSeconds() * 20L,
				0xFFD276FF, 0xFF602375));
			bars.add(cooldown(ICON_HEX_KILL, kill, GexpressConfig.getWarlockKillCooldownSeconds() * 20L,
				0xFFFF4A55, 0xFF7C151F));
		} else if (MapSelectRoles.JUGGERNAUT_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.JUGGERNAUT_WEAPONS, ICON_JUGGERNAUT_WEAPONS,
				GexpressConfig.getJuggernautInitialCooldownSeconds() * 20L, 0xFFFF7A42, 0xFF7A2418));
		} else if (MapSelectRoles.TIME_MASTER_ID.equals(roleId)) {
			TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(client.world);
			long rewindRemaining = comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
			int rewinds = comp == null ? GexpressConfig.getTimeMasterMaxUses() : comp.usesRemaining(playerId);
			bars.add(cooldown(ICON_TIME_REWIND, rewindRemaining, GexpressConfig.getTimeMasterCooldownSeconds() * 20L,
				0xFF57D4E6, 0xFF176B77, rewinds + "x"));
			long freezeRemaining = comp == null ? 0L : comp.freezeCooldownRemainingTicks(playerId);
			int freezes = comp == null ? GexpressConfig.getTimeMasterFreezeMaxUses() : comp.freezeUsesRemaining(playerId);
			bars.add(cooldown(ICON_TIME_FREEZE, freezeRemaining, GexpressConfig.getTimeMasterFreezeCooldownSeconds() * 20L,
				0xFF9DEBFF, 0xFF225B72, freezes + "x"));
		} else if (MapSelectRoles.TRICKSTER_ID.equals(roleId)) {
			long remaining = ClientTricksterState.remainingTicks();
			bars.add(remaining > 0L
				? draining(ICON_MASQUERADE, remaining, GexpressConfig.getTricksterSwapDurationSeconds() * 20L,
					0xFF4BE4B1, 0xFF1B775A)
				: cooldown(ICON_MASQUERADE, 0L, GexpressConfig.getTricksterSwapDurationSeconds() * 20L,
					0xFF4BE4B1, 0xFF1B775A));
			bars.add(syncedOrReady(AbilityCooldownPayload.HARLEQUIN_DANCING_CARTS, ICON_DANCING_CARTS,
				GexpressConfig.getTricksterSwapDurationSeconds() * 20L, 0xFFFFC857, 0xFF7A4D16));
		} else if (MapSelectRoles.PUPPETMASTER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.PUPPETMASTER_CONTROL, ICON_PUPPET_STRINGS,
				GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L, 0xFFFF5368, 0xFF741323));
		} else if (MapSelectRoles.SCATTER_BRAIN_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.SCATTER_BRAIN_SCATTER, ICON_SCATTER,
				GexpressConfig.getScatterBrainCooldownSeconds() * 20L, 0xFFFF8A4C, 0xFF8B2E16));
		} else if (MapSelectRoles.VULTURE_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.PELICAN_SWALLOW, ICON_PELICAN_SWALLOW,
				GexpressConfig.getPelicanEatCooldownSeconds() * 20L, 0xFFC5DF5C, 0xFF607421));
		} else if (MapSelectRoles.TRACKER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.TRACKER_TRACK, ICON_TRACKER,
				GexpressConfig.getTrackerCooldownSeconds() * 20L, 0xFF58B7FF, 0xFF1F4D7A));
		} else if (MapSelectRoles.ALTRUIST_ID.equals(roleId)) {
			bars.add(cooldown(ICON_ALTRUIST, 0L, 1L, 0xFFFFE5A3, 0xFF80642B));
		}
		return bars;
	}

	private static AbilityBar syncedOrReady(String key, Identifier icon, long readyTotalTicks, int color, int darkColor) {
		SyncedCooldown synced = SYNCED.get(key);
		if (synced == null) return cooldown(icon, 0L, readyTotalTicks, color, darkColor);
		MinecraftClient client = MinecraftClient.getInstance();
		long now = client != null && client.world != null ? client.world.getTime() : 0L;
		long remaining = Math.max(0L, synced.expiresAtTick() - now);
		if (remaining <= 0L) {
			SYNCED.remove(key);
			return cooldown(icon, 0L, readyTotalTicks, color, darkColor);
		}
		return synced.draining()
			? draining(icon, remaining, synced.totalTicks(), color, darkColor)
			: cooldown(icon, remaining, synced.totalTicks(), color, darkColor);
	}

	private static AbilityBar cooldown(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor) {
		return cooldown(icon, remainingTicks, totalTicks, color, darkColor, "");
	}

	private static AbilityBar cooldown(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor,
			String extraText) {
		long total = Math.max(1L, totalTicks);
		float progress = remainingTicks <= 0L ? 1.0F : 1.0F - Math.min(1.0F, remainingTicks / (float) total);
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, extraText == null ? "" : extraText);
	}

	private static AbilityBar draining(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor) {
		long total = Math.max(1L, totalTicks);
		float progress = Math.min(1.0F, remainingTicks / (float) total);
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, "");
	}

	private static void drawBar(DrawContext context, TextRenderer text, AbilityBar bar, int x, int y,
			int iconSize, int iconBarGap, int barWidth, int barHeight) {
		int iconX = x - iconBarGap - iconSize;
		int iconY = y - (iconSize - barHeight) / 2;
		context.drawGuiTexture(bar.icon(), iconX, iconY, iconSize, iconSize);

		context.fill(x + 1, y + 1, x + barWidth + 1, y + barHeight + 1, 0x99000000);
		context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, FRAME_DARK);
		context.fill(x, y, x + barWidth, y + barHeight, FRAME_BROWN);
		context.fill(x, y, x + barWidth, y + 1, FRAME_GOLD);
		context.fill(x + 1, y + 1, x + barWidth - 1, y + barHeight - 1, TRACK_DARK);
		context.fill(x + 1, y + barHeight - 2, x + barWidth - 1, y + barHeight - 1, TRACK_BROWN);

		int innerWidth = barWidth - 2;
		int fill = Math.round(innerWidth * Math.max(0.0F, Math.min(1.0F, bar.progress())));
		if (fill > 0) {
			int fillX = x + 1;
			int fillEnd = fillX + fill;
			int warm = hotbarTint(bar.color(), FILL_GOLD, 170);
			int warmLight = hotbarTint(lighten(bar.color(), 36), FILL_LIGHT, 190);
			int warmDark = hotbarTint(bar.darkColor(), FILL_DARK, 190);
			context.fill(fillX, y + 1, fillEnd, y + barHeight - 1, warmDark);
			context.fill(fillX, y + 1, fillEnd, y + 2, warmLight);
			context.fill(fillX, y + 2, fillEnd, y + barHeight - 2, warm);
			context.fill(fillX, y + barHeight - 2, fillEnd, y + barHeight - 1, darken(warmDark, 18));
		}

		for (int i = 1; i < 10; i++) {
			int tickX = x + Math.round(i * innerWidth / 10.0F);
			context.fill(tickX, y + 1, tickX + 1, y + barHeight - 1, 0xAA000000);
		}

		String sideText = "";
		if (bar.remainingTicks() > 0L) {
			sideText = Math.max(1L, (bar.remainingTicks() + 19L) / 20L) + "s";
		}
		if (!bar.extraText().isBlank()) {
			sideText = sideText.isEmpty() ? bar.extraText() : sideText + " " + bar.extraText();
		}
		if (!sideText.isEmpty()) {
			context.drawTextWithShadow(text, sideText, x + barWidth + 4, y - 2, 0xFFFFFFFF);
		}
	}

	private static int scaled(int value, float scale) {
		return Math.max(1, Math.round(value * scale));
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) return min;
		return Math.max(min, Math.min(max, value));
	}

	private static Identifier hudIcon(String name) {
		return Identifier.of(MapSelect.MOD_ID, "hud/" + name);
	}

	private static int lighten(int color, int amount) {
		int a = color & 0xFF000000;
		int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
		int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
		int b = Math.min(255, (color & 0xFF) + amount);
		return a | (r << 16) | (g << 8) | b;
	}

	private static int darken(int color, int amount) {
		int a = color & 0xFF000000;
		int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
		int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
		int b = Math.max(0, (color & 0xFF) - amount);
		return a | (r << 16) | (g << 8) | b;
	}

	private static int hotbarTint(int color, int hotbarColor, int hotbarWeight) {
		int weight = Math.max(0, Math.min(255, hotbarWeight));
		int inverse = 255 - weight;
		int a = color & 0xFF000000;
		int r = ((((color >> 16) & 0xFF) * inverse) + (((hotbarColor >> 16) & 0xFF) * weight)) / 255;
		int g = ((((color >> 8) & 0xFF) * inverse) + (((hotbarColor >> 8) & 0xFF) * weight)) / 255;
		int b = (((color & 0xFF) * inverse) + ((hotbarColor & 0xFF) * weight)) / 255;
		return a | (r << 16) | (g << 8) | b;
	}

	private static Role localRole(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game == null ? null : game.getRole(client.player);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void checkSyncedWorld(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (syncedWorld == world) return;
		syncedWorld = world;
		SYNCED.clear();
	}

	private record AbilityBar(Identifier icon, long remainingTicks, float progress, int color, int darkColor,
	                          String extraText) {}

	private record SyncedCooldown(long expiresAtTick, int totalTicks, boolean draining) {}
}
