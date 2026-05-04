package dev.mapselect.client;

import dev.mapselect.network.BountyHunterStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public final class ClientBountyHunterState {
	private static UUID targetId;
	private static String targetName = "";
	private static int rewardGold;
	private static long remainingTicks;
	private static boolean active;
	private static float alpha;

	private ClientBountyHunterState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BountyHunterStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientBountyHunterState::tick);
		HudRenderCallback.EVENT.register(ClientBountyHunterState::render);
	}

	private static void apply(BountyHunterStatePayload payload) {
		active = payload.active();
		targetId = payload.targetId();
		targetName = payload.targetName();
		rewardGold = Math.max(0, payload.rewardGold());
		remainingTicks = Math.max(0L, payload.remainingTicks());
	}

	private static void tick(MinecraftClient client) {
		if (remainingTicks > 0L) remainingTicks--;
		boolean visible = active && targetId != null && client != null && client.player != null
			&& client.world != null && ClientRoleRevealState.canShowRoleHud(client);
		alpha = MathHelper.lerp(0.22F, alpha, visible ? 1.0F : 0.0F);
		if (client == null || client.world == null) {
			active = false;
			targetId = null;
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null || client.options.hudHidden || alpha <= 0.02F) return;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		TextRenderer text = client.textRenderer;
		int width = 104;
		int height = 136;
		int x = context.getScaledWindowWidth() - width - 9;
		int y = 24;

		int ink = withAlpha(0x321908, a);
		int inkSoft = withAlpha(0x5A2F12, a);
		int paper = withAlpha(0xB98442, a);
		int paperDark = withAlpha(0x4C2710, a);
		int paperLight = withAlpha(0xD7AA62, a);
		context.fill(x + 3, y + 3, x + width + 3, y + height + 3, withAlpha(0x000000, a / 3));
		context.fill(x + 2, y, x + width - 1, y + height, paper);
		context.fill(x, y + 4, x + width + 1, y + height - 3, paper);
		context.fill(x + 5, y + 5, x + width - 4, y + height - 4, withAlpha(0xC8954D, a));

		drawRoughBorder(context, x, y, width, height, paperDark, paperLight);
		drawPaperWear(context, x, y, width, height, a);
		context.fill(x + 8, y + 38, x + width - 8, y + 41, inkSoft);
		context.fill(x + 8, y + 89, x + width - 8, y + 92, inkSoft);

		drawCentered(context, text, "WANTED", x, y + 8, width, ink);
		drawCentered(context, text, "DEAD OR ALIVE", x, y + 23, width, inkSoft);
		drawHead(context, client, targetId, x + width / 2 - 18, y + 49, a);
		drawCentered(context, text, trimToWidth(text, targetName, width - 12), x, y + 82, width, ink);
		drawCentered(context, text, "REWARD", x, y + 99, width, inkSoft);
		drawCentered(context, text, rewardGold + " gold", x, y + 111, width, ink);
		if (remainingTicks > 0L) {
			String seconds = Math.max(1L, (remainingTicks + 19L) / 20L) + "s left";
			drawCentered(context, text, seconds, x, y + 124, width, withAlpha(0x5A2F12, a));
		}
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y, int alpha) {
		if (playerId == null) return;
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.fill(x - 3, y - 3, x + 39, y + 39, withAlpha(0x5A2F12, alpha));
		context.fill(x - 1, y - 1, x + 37, y + 37, withAlpha(0xD1A45A, alpha));
		context.drawTexture(texture, x, y, 36, 36, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, 36, 36, 40.0F, 8.0F, 8, 8, 64, 64);
		context.fill(x, y, x + 36, y + 36, withAlpha(0x5A3418, Math.max(0, 88 - alpha / 4)));
	}

	private static void drawRoughBorder(DrawContext context, int x, int y, int width, int height, int dark, int light) {
		context.fill(x + 3, y, x + width - 3, y + 3, dark);
		context.fill(x + 2, y + height - 3, x + width - 2, y + height, dark);
		context.fill(x, y + 5, x + 3, y + height - 5, dark);
		context.fill(x + width - 3, y + 4, x + width, y + height - 4, dark);
		context.fill(x + 10, y + 3, x + width - 11, y + 4, light);
		context.fill(x + 4, y + 14, x + 5, y + height - 16, light);
		context.fill(x + 7, y + 1, x + 13, y + 3, dark);
		context.fill(x + width - 20, y, x + width - 12, y + 2, dark);
		context.fill(x, y + 22, x + 2, y + 31, dark);
		context.fill(x + width - 2, y + 57, x + width, y + 68, dark);
		context.fill(x + 18, y + height - 2, x + 29, y + height, dark);
	}

	private static void drawPaperWear(DrawContext context, int x, int y, int width, int height, int alpha) {
		int stain = withAlpha(0x714014, Math.max(0, alpha / 4));
		int scratch = withAlpha(0xE3BC78, Math.max(0, alpha / 5));
		for (int i = 0; i < 10; i++) {
			int sx = x + 8 + ((i * 23) % (width - 16));
			int sy = y + 9 + ((i * 31) % (height - 20));
			context.fill(sx, sy, sx + 2 + (i % 4), sy + 1, stain);
		}
		context.fill(x + 14, y + 44, x + 30, y + 45, scratch);
		context.fill(x + width - 33, y + 72, x + width - 18, y + 73, scratch);
		context.fill(x + 20, y + height - 20, x + 43, y + height - 19, stain);
	}

	private static void drawCentered(DrawContext context, TextRenderer text, String value, int x, int y, int width,
			int color) {
		context.drawTextWithShadow(text, value, x + (width - text.getWidth(value)) / 2, y, color);
	}

	private static String trimToWidth(TextRenderer text, String value, int width) {
		String out = value == null ? "" : value;
		while (out.length() > 1 && text.getWidth(out) > width) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}
}
