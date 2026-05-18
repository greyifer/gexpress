package dev.mapselect.client;

import dev.mapselect.level.LevelComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public final class ClientLevelHud {
	private ClientLevelHud() {}

	public static void register() {
		HudRenderCallback.EVENT.register(ClientLevelHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null || client.textRenderer == null
				|| client.options.hudHidden) {
			return;
		}
		LevelComponent levels = LevelComponent.KEY.getNullable(client.world);
		int level = levels == null ? 1 : levels.level(client.player.getUuid());
		int progress = levels == null ? 0 : levels.xpIntoLevel(client.player.getUuid());
		int needed = levels == null ? LevelComponent.xpNeededForLevel(level) : levels.xpNeededForNextLevel(client.player.getUuid());
		Text text = Text.literal("LvL " + level + "  " + progress + "/" + needed + " XP");
		int y = context.getScaledWindowHeight() - 18;
		context.drawTextWithShadow(client.textRenderer, text, 8, y, 0xFFE6E6E6);
	}

}
