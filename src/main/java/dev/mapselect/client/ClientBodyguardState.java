package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.BodyguardFeedPayload;
import dev.mapselect.network.BodyguardStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public final class ClientBodyguardState {
	public static final int GLOW_COLOR = 0x51D6FF;
	private static final Deque<FeedLine> FEED = new ArrayDeque<>();
	private static UUID targetId;
	private static String targetName = "";
	private static boolean active;
	private static float alpha;

	private ClientBodyguardState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BodyguardStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(BodyguardFeedPayload.ID, (payload, context) ->
			context.client().execute(() -> addLine(payload.line())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientBodyguardState::tick);
		HudRenderCallback.EVENT.register(ClientBodyguardState::render);
	}

	private static void apply(BodyguardStatePayload payload) {
		active = payload.active();
		targetId = payload.targetId();
		targetName = payload.targetName();
	}

	private static void addLine(String line) {
		if (line == null || line.isBlank()) return;
		long now = now();
		FEED.addLast(new FeedLine(line, now + 20L * 8L));
		while (FEED.size() > 5) FEED.removeFirst();
	}

	private static void tick(MinecraftClient client) {
		boolean visible = active && targetId != null && client != null && client.player != null
			&& client.world != null && !ClientVultureState.isLocalStashed(client)
			&& ClientRoleRevealState.canShowRoleHud(client) && isLocalBodyguard(client);
		alpha = MathHelper.lerp(0.22F, alpha, visible ? 1.0F : 0.0F);
		if (client == null || client.world == null) {
			active = false;
			targetId = null;
			FEED.clear();
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null || client.player == null || client.options.hudHidden) return;
		if (!isLocalBodyguard(client) || !ClientRoleRevealState.canShowRoleHud(client)) return;
		renderTarget(context, client);
		renderFeed(context, client);
	}

	private static void renderTarget(DrawContext context, MinecraftClient client) {
		if (alpha <= 0.02F || targetId == null) return;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		TextRenderer text = client.textRenderer;
		int width = 128;
		int height = 42;
		int x = context.getScaledWindowWidth() - width - 9;
		int y = 8;
		context.fill(x + 2, y + 2, x + width + 2, y + height + 2, withAlpha(0x000000, a / 3));
		context.fill(x, y, x + width, y + height, withAlpha(0x10222A, a));
		context.fill(x, y, x + 3, y + height, withAlpha(GLOW_COLOR, a));
		context.fill(x + 4, y + height - 2, x + width - 4, y + height, withAlpha(GLOW_COLOR, a));
		drawHead(context, client, targetId, x + 9, y + 9, a);
		context.drawTextWithShadow(text, Text.literal("Protect"), x + 41, y + 9, withAlpha(0xA8EFFF, a));
		String name = trimToWidth(text, targetName, width - 48);
		context.drawTextWithShadow(text, Text.literal(name), x + 41, y + 22, withAlpha(0xFFFFFF, a));
	}

	private static void renderFeed(DrawContext context, MinecraftClient client) {
		long now = now();
		FEED.removeIf(line -> line.expiresAtTick() <= now);
		if (FEED.isEmpty()) return;
		int y = context.getScaledWindowHeight() - 78;
		for (FeedLine line : FEED) {
			int width = client.textRenderer.getWidth(line.text()) + 8;
			int x = context.getScaledWindowWidth() - width - 8;
			context.fill(x - 2, y - 2, x + width, y + 11, 0x66000000);
			context.drawTextWithShadow(client.textRenderer, Text.literal(line.text()), x + 2, y, 0xFF9DEBFF);
			y += 13;
		}
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		return active && targetId != null && player != null && targetId.equals(player.getUuid())
			&& isLocalBodyguard(MinecraftClient.getInstance());
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y, int alpha) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.fill(x - 2, y - 2, x + 26, y + 26, withAlpha(GLOW_COLOR, alpha));
		context.drawTexture(texture, x, y, 24, 24, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, 24, 24, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static boolean isLocalBodyguard(MinecraftClient client) {
		try {
			if (client == null || client.world == null || client.player == null) return false;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.BODYGUARD_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static String trimToWidth(TextRenderer text, String value, int width) {
		String out = value == null ? "" : value;
		while (out.length() > 1 && text.getWidth(out) > width) out = out.substring(0, out.length() - 1);
		return out;
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	private static long now() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.world != null ? client.world.getTime() : 0L;
	}

	private record FeedLine(String text, long expiresAtTick) {}
}
