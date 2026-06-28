package dev.mapselect.client.modifier.lovers;
import dev.mapselect.client.hud.ClientHudVisibility;


import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.modifier.LoversStatePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class ClientLoversState {
	private static final int HEAD_SIZE = 16;
	private static boolean active;
	private static UUID loverId;
	private static String loverName = "";

	private ClientLoversState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoversStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		HudRenderCallback.EVENT.register(ClientLoversState::render);
	}

	private static void apply(LoversStatePayload payload) {
		active = payload.active();
		loverId = payload.loverId();
		loverName = payload.loverName();
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client == null || client.player == null
				|| !active || loverId == null || !GexpressConfig.shouldShowLoverHud()) {
			return;
		}
		String name = loverName == null || loverName.isBlank() ? "Unknown" : loverName;
		int right = context.getScaledWindowWidth() - 8;
		int y = Math.max(8, context.getScaledWindowHeight() - 52);
		int headX = right - HEAD_SIZE;
		drawHead(context, client, loverId, headX, y);
		String label = trim(client, "Lover: " + name, Math.max(48, context.getScaledWindowWidth() / 2));
		int textX = Math.max(4, headX - 4 - client.textRenderer.getWidth(label));
		context.drawTextWithShadow(client.textRenderer, Text.literal(label), textX, y + 4, 0xFFFF8FC1);
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		Object cached = WatheClient.PLAYER_ENTRIES_CACHE.get(playerId);
		if (cached instanceof PlayerListEntry entry) {
			texture = entry.getSkinTextures().texture();
		} else if (client != null && client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		RenderSystem.enableBlend();
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static String trim(MinecraftClient client, String value, int maxWidth) {
		if (client == null || client.textRenderer == null) return value == null ? "" : value;
		String out = value == null ? "" : value;
		while (out.length() > 1 && client.textRenderer.getWidth(out) > maxWidth) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}
}
