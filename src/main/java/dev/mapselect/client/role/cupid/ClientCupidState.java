package dev.mapselect.client.role.cupid;
import dev.mapselect.client.ability.ClientAbilityTargetState;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.AnimatedCounterText;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.pelican.ClientVultureState;


import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.network.role.cupid.CupidStatePayload;
import dev.mapselect.network.role.cupid.CupidUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.registry.MapSelectSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientCupidState {
	private static final int PANEL_MARGIN = 8;
	private static final int PANEL_BOTTOM_OFFSET = 126;
	private static final int MAX_VISIBLE_PAIRS = 4;
	private static boolean wasUseDown;
	private static boolean selecting;
	private static String firstTargetName = "";
	private static List<CupidStatePayload.Pair> pairs = List.of();
	private static int linkedAlivePlayers;
	private static int requiredLinkedPlayers;
	private static float cupidOverlayStrength;
	private static long lastCupidIntroMs;
	private static boolean playedCupidIntroThisRound;
	private static boolean wasLocalCupid;
	private static final AnimatedCounterText COUNTER_TEXT = new AnimatedCounterText();

	private ClientCupidState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CupidStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientCupidState::tick);
		HudRenderCallback.EVENT.register(ClientCupidState::renderHud);
	}

	private static void apply(CupidStatePayload payload) {
		selecting = payload.selecting();
		firstTargetName = payload.firstTargetName();
		pairs = payload.pairs();
		linkedAlivePlayers = payload.linkedAlivePlayers();
		requiredLinkedPlayers = payload.requiredLinkedPlayers();
		COUNTER_TEXT.setTarget(linkedAlivePlayers, requiredLinkedPlayers);
		ClientAbilityTargetState.setCupidSelection(selecting ? payload.firstTargetId() : null);
		ClientAbilityTargetState.setCupidLinkedTargets(linkedTargetIds(pairs));
	}

	private static void tick(MinecraftClient client) {
		boolean localCupid = isLocalCupid(client);
		if (!localCupid && wasLocalCupid) {
			clearCupidHudState();
		}
		wasLocalCupid = localCupid;
		updateCupidIntro(client);
		cupidOverlayStrength = MathHelper.lerp(0.045F, cupidOverlayStrength, shouldUseCupidOverlay(client, localCupid) ? 1.0F : 0.0F);
		if (cupidOverlayStrength < 0.01F) cupidOverlayStrength = 0.0F;
		if (cupidOverlayStrength > 0.99F) cupidOverlayStrength = 1.0F;
		COUNTER_TEXT.tick();
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !localCupid) {
			wasUseDown = false;
			return;
		}
		KeyBinding binding = ClientAbilityKeys.primaryBinding();
		boolean down = binding != null && ClientAbilityKeys.isDown(client, binding);
		if (down && !wasUseDown && ClientPlayNetworking.canSend(CupidUsePayload.ID)) {
			ClientPlayNetworking.send(new CupidUsePayload(ClientAbilityTargetState.currentTargetId()));
		}
		wasUseDown = down;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client == null || client.player == null || !isLocalCupid(client)) return;
		COUNTER_TEXT.render(context, client.textRenderer, "Cupid ", 8, 26, 0xFF8FC1, 255,
			tickCounter.getTickDelta(true));
		if (!selecting && pairs.isEmpty()) return;
		int maxTextWidth = Math.max(84, Math.min(170, context.getScaledWindowWidth() / 2));
		List<String> lines = hudLines(client, maxTextWidth);
		if (lines.isEmpty()) return;
		String title = "Cupid Links";
		int panelWidth = client.textRenderer.getWidth(title);
		for (String line : lines) panelWidth = Math.max(panelWidth, client.textRenderer.getWidth(line));
		panelWidth = Math.min(context.getScaledWindowWidth() - PANEL_MARGIN * 2, panelWidth + 20);
		int panelHeight = 22 + lines.size() * 11;
		int x = Math.max(PANEL_MARGIN, context.getScaledWindowWidth() - PANEL_MARGIN - panelWidth);
		int bottom = Math.max(44, context.getScaledWindowHeight() - PANEL_BOTTOM_OFFSET);
		int y = Math.max(PANEL_MARGIN, bottom - panelHeight);
		context.fill(x, y, x + panelWidth, y + panelHeight, 0xA8120B12);
		context.fill(x, y, x + 2, y + panelHeight, 0xFFFF8FC1);
		context.fill(x, y, x + panelWidth, y + 1, 0x70FFD1E4);
		context.drawTextWithShadow(client.textRenderer, Text.literal(title), x + 8, y + 6, 0xFFFFA6D0);
		int lineY = y + 18;
		for (String line : lines) {
			context.drawTextWithShadow(client.textRenderer, Text.literal(line), x + 8, lineY, 0xFFFFD6EA);
			lineY += 11;
		}
	}

	private static List<String> hudLines(MinecraftClient client, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (selecting && firstTargetName != null && !firstTargetName.isBlank()) {
			lines.add(trim(client, "Choosing: " + firstTargetName, maxWidth));
		}
		int shown = 0;
		for (CupidStatePayload.Pair pair : pairs) {
			if (pair == null) continue;
			String first = pair.firstName().isBlank() ? "Unknown" : pair.firstName();
			String second = pair.secondName().isBlank() ? "Unknown" : pair.secondName();
			lines.add(trim(client, first + " <3 " + second, maxWidth));
			if (++shown >= MAX_VISIBLE_PAIRS) break;
		}
		int hidden = Math.max(0, pairs.size() - shown);
		if (hidden > 0) lines.add("+" + hidden + " more");
		return lines;
	}

	private static List<UUID> linkedTargetIds(List<CupidStatePayload.Pair> pairs) {
		if (pairs == null || pairs.isEmpty()) return List.of();
		List<UUID> ids = new ArrayList<>();
		for (CupidStatePayload.Pair pair : pairs) {
			if (pair == null) continue;
			if (pair.firstId() != null) ids.add(pair.firstId());
			if (pair.secondId() != null) ids.add(pair.secondId());
		}
		return ids;
	}

	private static boolean isLocalCupid(MinecraftClient client) {
		return MapSelectRoles.CUPID_ID.equals(ClientCopycatState.effectiveRoleId(client, localRoleId(client)));
	}

	private static boolean shouldUseCupidOverlay(MinecraftClient client) {
		return shouldUseCupidOverlay(client, isLocalCupid(client));
	}

	private static boolean shouldUseCupidOverlay(MinecraftClient client, boolean localCupid) {
		return client != null && client.player != null && client.world != null
			&& isRoundRunning(client)
			&& localCupid
			&& !ClientVultureState.isLocalStashed(client)
			&& GameFunctions.isPlayerAliveAndSurvival(client.player);
	}

	private static void updateCupidIntro(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || !isRoundRunning(client)) {
			playedCupidIntroThisRound = false;
			return;
		}
		if (!isLocalCupid(client)) {
			playedCupidIntroThisRound = false;
			return;
		}
		if (playedCupidIntroThisRound || !ClientRoleRevealState.isRoleRevealSettled()) return;
		playedCupidIntroThisRound = true;
		playCupidIntro(client);
	}

	private static boolean isRoundRunning(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.isRunning();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void playCupidIntro(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || client.getSoundManager() == null) return;
		long now = System.currentTimeMillis();
		if (now - lastCupidIntroMs < 5000L) return;
		lastCupidIntroMs = now;
		client.getSoundManager().play(PositionedSoundInstance.master(MapSelectSounds.CUPID, 1.0F, 1.0F));
	}

	public static float cupidOverlayStrength() {
		return cupidOverlayStrength;
	}

	public static boolean isCupidOverlayVisible() {
		return cupidOverlayStrength > 0.02F;
	}

	public static boolean shouldSuppressWatheRiser() {
		return isLocalCupid(MinecraftClient.getInstance());
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String trim(MinecraftClient client, String value, int maxWidth) {
		if (client == null || client.textRenderer == null) return value == null ? "" : value;
		String out = value == null ? "" : value;
		while (out.length() > 1 && client.textRenderer.getWidth(out) > maxWidth) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private static void clearCupidHudState() {
		selecting = false;
		firstTargetName = "";
		pairs = List.of();
		linkedAlivePlayers = 0;
		requiredLinkedPlayers = 0;
		COUNTER_TEXT.reset();
		ClientAbilityTargetState.setCupidSelection(null);
		ClientAbilityTargetState.setCupidLinkedTargets(List.of());
	}
}
