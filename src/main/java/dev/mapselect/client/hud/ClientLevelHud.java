package dev.mapselect.client.hud;

import dev.mapselect.MapSelect;
import dev.mapselect.currency.GcoinComponent;
import dev.mapselect.level.LevelComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClientLevelHud {
	private static final Identifier GCOIN_TEXTURE = Identifier.of(MapSelect.MOD_ID, "textures/gui/gcoin.png");
	private static final int GCOIN_ICON_SIZE = 8;
	private static final int GCOIN_TEXTURE_SIZE = 8;

	private ClientLevelHud() {}

	public static void register() {
		HudRenderCallback.EVENT.register(ClientLevelHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null || client.world == null
				|| client.textRenderer == null) {
			return;
		}
		LevelComponent levels = LevelComponent.KEY.getNullable(client.world);
		int level = levels == null ? 1 : levels.level(client.player.getUuid());
		int progress = levels == null ? 0 : levels.xpIntoLevel(client.player.getUuid());
		int needed = levels == null ? LevelComponent.xpNeededForLevel(level) : levels.xpNeededForNextLevel(client.player.getUuid());
		Text xpText = Text.literal("LvL " + level + "  " + progress + "/" + needed + " XP");
		int right = context.getScaledWindowWidth() - 8;
		int y = context.getScaledWindowHeight() - 18;
		drawRightAligned(context, client.textRenderer, xpText, right, y, 0xFFE6E6E6);
		GcoinComponent gcoins = GcoinComponent.KEY.getNullable(client.world);
		int balance = gcoins == null ? 0 : gcoins.balance(client.player.getUuid());
		drawGcoinBalance(context, client.textRenderer, Integer.toString(balance), right, y - 14);
	}

	private static void drawRightAligned(DrawContext context, TextRenderer textRenderer, Text text, int right, int y, int color) {
		context.drawTextWithShadow(textRenderer, text, Math.max(4, right - textRenderer.getWidth(text)), y, color);
	}

	private static void drawGcoinBalance(DrawContext context, TextRenderer textRenderer, String amount, int right, int y) {
		int iconX = right - GCOIN_ICON_SIZE;
		int textX = Math.max(4, iconX - 3 - textRenderer.getWidth(amount));
		context.drawTextWithShadow(textRenderer, Text.literal(amount), textX, y, 0xFFFFD56E);
		context.drawTexture(GCOIN_TEXTURE, iconX, y, GCOIN_ICON_SIZE, GCOIN_ICON_SIZE,
			0.0F, 0.0F, GCOIN_TEXTURE_SIZE, GCOIN_TEXTURE_SIZE, GCOIN_TEXTURE_SIZE, GCOIN_TEXTURE_SIZE);
	}

}
