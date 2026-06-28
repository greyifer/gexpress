package dev.mapselect.client.screen;

import dev.mapselect.network.coinbarrier.CoinBarrierPurchasePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class CoinBarrierPurchaseScreen extends Screen {
	private final Screen parent;
	private final BlockPos pos;

	public CoinBarrierPurchaseScreen(Screen parent, BlockPos pos, int price, String title) {
		super(Text.literal("Are you sure?"));
		this.parent = parent;
		this.pos = pos;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.literal("Buy"), button -> buy())
			.dimensions(width / 2 - 40, height / 2 + 16, 80, 20)
			.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 10, 0xFFFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void blur() {
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void buy() {
		if (ClientPlayNetworking.canSend(CoinBarrierPurchasePayload.ID)) {
			ClientPlayNetworking.send(new CoinBarrierPurchasePayload(pos));
		}
		close();
	}
}
