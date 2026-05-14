package dev.mapselect.client;

import dev.mapselect.network.MafiaActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GodfatherRecruitScreen extends Screen {
	public GodfatherRecruitScreen() {
		super(Text.literal("Recruit"));
	}

	@Override
	protected void init() {
		int buttonWidth = 120;
		int gap = 10;
		int total = buttonWidth * 2 + gap;
		int x = width / 2 - total / 2;
		int y = height / 2 - 10;
		addDrawableChild(ButtonWidget.builder(Text.literal("Mafioso"), button -> recruit(MafiaActionPayload.RECRUIT_MAFIOSO))
			.dimensions(x, y, buttonWidth, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Janitor"), button -> recruit(MafiaActionPayload.RECRUIT_JANITOR))
			.dimensions(x + buttonWidth + gap, y, buttonWidth, 20)
			.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 36, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Choose a family role").formatted(Formatting.GRAY),
			width / 2, height / 2 - 24, 0xFF9BA3AE);
		super.render(context, mouseX, mouseY, delta);
	}

	private void recruit(int action) {
		if (ClientPlayNetworking.canSend(MafiaActionPayload.ID)) {
			ClientPlayNetworking.send(new MafiaActionPayload(action));
		}
		close();
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(null);
	}
}
