package dev.mapselect.client;

import dev.mapselect.network.PuppetmasterSelectPayload;
import dev.mapselect.network.PuppetmasterTargetsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class PuppetmasterTargetScreen extends Screen {
	private final List<PuppetmasterTargetsPayload.Entry> targets;

	public PuppetmasterTargetScreen(List<PuppetmasterTargetsPayload.Entry> targets) {
		super(Text.translatable("gui.gexpress.puppetmaster.title"));
		this.targets = targets == null ? List.of() : List.copyOf(targets);
	}

	@Override
	protected void init() {
		int y = Math.max(42, this.height / 2 - Math.min(targets.size(), 8) * 12);
		if (targets.isEmpty()) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.puppetmaster.none"),
					button -> close())
				.dimensions(this.width / 2 - 100, y, 200, 20)
				.build());
			return;
		}

		for (PuppetmasterTargetsPayload.Entry target : targets) {
			addDrawableChild(ButtonWidget.builder(Text.literal(target.name()),
					button -> {
						if (ClientPlayNetworking.canSend(PuppetmasterSelectPayload.ID)) {
							ClientPlayNetworking.send(new PuppetmasterSelectPayload(target.id()));
						}
						close();
					})
				.dimensions(this.width / 2 - 100, y, 200, 20)
				.build());
			y += 24;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.puppetmaster.subtitle").formatted(Formatting.GRAY),
			width / 2, 30, 0xA0A0A0);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(null);
	}
}
