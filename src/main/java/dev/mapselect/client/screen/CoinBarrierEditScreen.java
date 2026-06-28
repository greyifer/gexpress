package dev.mapselect.client.screen;

import dev.mapselect.block.CoinBarrierBlockEntity;
import dev.mapselect.network.coinbarrier.CoinBarrierEditSavePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class CoinBarrierEditScreen extends Screen {
	private final Screen parent;
	private final BlockPos pos;
	private final int price;
	private final String titleText;
	private TextFieldWidget titleField;
	private TextFieldWidget priceField;

	public CoinBarrierEditScreen(Screen parent, BlockPos pos, int price, String title) {
		super(Text.literal("Red Ribbon"));
		this.parent = parent;
		this.pos = pos;
		this.price = price;
		this.titleText = title == null ? "" : title;
	}

	@Override
	protected void init() {
		int formX = width / 2 - 110;
		int formY = height / 2 - 46;
		titleField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY, 220, 18, Text.literal("Title")));
		titleField.setMaxLength(CoinBarrierBlockEntity.MAX_TITLE_LENGTH);
		titleField.setText(titleText);
		priceField = addDrawableChild(new TextFieldWidget(textRenderer, formX, formY + 38, 92, 18, Text.literal("Price")));
		priceField.setMaxLength(6);
		priceField.setText(Integer.toString(Math.max(0, price)));
		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save())
			.dimensions(formX, formY + 70, 72, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
			.dimensions(formX + 80, formY + 70, 72, 20)
			.build());
		setInitialFocus(titleField);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xAA11151B);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 86, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal("Title").formatted(Formatting.GRAY),
			titleField.getX(), titleField.getY() - 12, 0xFFB9C2CE);
		context.drawTextWithShadow(textRenderer, Text.literal("Price").formatted(Formatting.GRAY),
			priceField.getX(), priceField.getY() - 12, 0xFFB9C2CE);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void save() {
		int parsedPrice;
		try {
			parsedPrice = Integer.parseInt(priceField.getText().strip());
		} catch (NumberFormatException ignored) {
			parsedPrice = 0;
		}
		if (ClientPlayNetworking.canSend(CoinBarrierEditSavePayload.ID)) {
			ClientPlayNetworking.send(new CoinBarrierEditSavePayload(pos,
				CoinBarrierBlockEntity.clampPrice(parsedPrice), titleField.getText()));
		}
		close();
	}
}
