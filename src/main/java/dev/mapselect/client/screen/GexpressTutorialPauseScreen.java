package dev.mapselect.client.screen;

import dev.mapselect.client.tutorial.ClientTutorialExperience;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class GexpressTutorialPauseScreen extends Screen {
	public GexpressTutorialPauseScreen(Screen vanillaPause) {
		super(Text.literal("Tutorial Paused"));
	}

	@Override
	protected void init() {
		int x = width / 2 - 100;
		int y = height / 2 - 12;
		addDrawableChild(ButtonWidget.builder(Text.literal("Resume Tutorial"), button -> close())
			.dimensions(x, y, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Leave Tutorial"), button -> {
			ClientTutorialExperience.requestExit();
			MinecraftClient.getInstance().setScreen(null);
		}).dimensions(x, y + 28, 200, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xCC0E1116);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 42, 0xFFE7C66A);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("You can leave without relogging."),
			width / 2, height / 2 - 28, 0xFF9DA9B6);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(null);
	}
}
