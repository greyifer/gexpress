package dev.mapselect.client.role.altruist;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.network.role.altruist.AltruistFeedPayload;
import dev.mapselect.network.role.altruist.AltruistStatePayload;
import dev.mapselect.network.role.altruist.AltruistTerminatePayload;
import dev.mapselect.network.role.altruist.AltruistUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class ClientAltruistState {
	private static final int ACCENT = 0xE970A7;
	private static final Deque<FeedLine> FEED = new ArrayDeque<>();
	private static List<AltruistStatePayload.Entry> revivedPlayers = List.of();
	private static int revivesUsed;
	private static int maxRevives = 2;
	private static boolean wasPrimaryDown;
	private static boolean wasSecondaryDown;

	private ClientAltruistState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AltruistStatePayload.ID, (payload, context) ->
			context.client().execute(() -> {
				revivedPlayers = payload.revivedPlayers();
				revivesUsed = payload.revivesUsed();
				maxRevives = payload.maxRevives();
			}));
		ClientPlayNetworking.registerGlobalReceiver(AltruistFeedPayload.ID, (payload, context) ->
			context.client().execute(() -> addFeed(payload.line())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientAltruistState::tick);
		HudRenderCallback.EVENT.register(ClientAltruistState::render);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client)
				|| !isLocalAltruist(client)) {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
			if (client == null || client.world == null) {
				revivedPlayers = List.of();
				FEED.clear();
			}
			return;
		}
		KeyBinding primary = ClientAbilityKeys.primaryBinding();
		boolean primaryDown = primary != null && ClientAbilityKeys.isDown(client, primary);
		if (primaryDown && !wasPrimaryDown && ClientPlayNetworking.canSend(AltruistUsePayload.ID)) {
			ClientPlayNetworking.send(new AltruistUsePayload());
		}
		wasPrimaryDown = primaryDown;

		KeyBinding secondary = ClientAbilityKeys.secondaryBinding();
		boolean secondaryDown = secondary != null && ClientAbilityKeys.isDown(client, secondary);
		if (secondaryDown && !wasSecondaryDown
				&& ClientPlayNetworking.canSend(AltruistTerminatePayload.ID)) {
			ClientPlayNetworking.send(new AltruistTerminatePayload());
		}
		wasSecondaryDown = secondaryDown;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client == null || client.player == null
				|| client.textRenderer == null || !isLocalAltruist(client)
				|| !ClientRoleRevealState.canShowRoleHud(client)) return;
		renderControlled(context, client);
		renderFeed(context, client);
	}

	private static void renderControlled(DrawContext context, MinecraftClient client) {
		TextRenderer text = client.textRenderer;
		int width = 142;
		int height = 25 + revivedPlayers.size() * 27;
		int x = context.getScaledWindowWidth() - width - 9;
		int y = 8;
		context.drawTextWithShadow(text, Text.literal("Revived " + revivesUsed + "/" + maxRevives),
			x + 9, y + 8, 0xFFFFA9CF);
		int rowY = y + 23;
		for (AltruistStatePayload.Entry entry : revivedPlayers) {
			drawHead(context, client, entry.playerId(), x + 8, rowY, 20);
			context.drawTextWithShadow(text, Text.literal(trimToWidth(text, entry.name(), width - 45)),
				x + 34, rowY + 2, 0xFFFFFFFF);
			context.drawTextWithShadow(text, Text.literal("Secondary: end life"),
				x + 34, rowY + 12, 0xFFB88DA1);
			rowY += 27;
		}
	}

	private static void renderFeed(DrawContext context, MinecraftClient client) {
		long now = client.world == null ? 0L : client.world.getTime();
		FEED.removeIf(line -> line.expiresAtTick() <= now);
		int y = context.getScaledWindowHeight() - 78;
		for (FeedLine line : FEED) {
			int width = client.textRenderer.getWidth(line.text()) + 8;
			int x = context.getScaledWindowWidth() - width - 8;
			context.drawTextWithShadow(client.textRenderer, Text.literal(line.text()), x + 2, y, 0xFFFFA9CF);
			y += 13;
		}
	}

	private static void addFeed(String line) {
		if (line == null || line.isBlank()) return;
		MinecraftClient client = MinecraftClient.getInstance();
		long now = client != null && client.world != null ? client.world.getTime() : 0L;
		FEED.addLast(new FeedLine(line, now + 20L * 8L));
		while (FEED.size() > 5) FEED.removeFirst();
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId,
			int x, int y, int size) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000 | ACCENT);
		context.drawTexture(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static boolean isLocalAltruist(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && ClientCopycatState.isEffectiveRole(client, MapSelectRoles.ALTRUIST_ID);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static String trimToWidth(TextRenderer text, String value, int width) {
		String out = value == null ? "" : value;
		while (out.length() > 1 && text.getWidth(out) > width) out = out.substring(0, out.length() - 1);
		return out;
	}

	private record FeedLine(String text, long expiresAtTick) {}
}
