package dev.mapselect.client.screen;

import dev.mapselect.block.FloatingTextBlockEntity;
import dev.mapselect.network.floatingtext.FloatingTextEditSavePayload;
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

public final class FloatingTextEditScreen extends Screen {
	private final Screen parent;
	private final BlockPos pos;
	private final String initialText;
	private final TextFieldWidget[] lines = new TextFieldWidget[3];

	public FloatingTextEditScreen(Screen parent, BlockPos pos, String initialText) {
		super(Text.literal("Floating Text"));
		this.parent = parent;
		this.pos = pos;
		this.initialText = initialText == null ? "" : initialText;
	}

	@Override
	protected void init() {
		int x = width / 2 - 130;
		int y = height / 2 - 55;
		String[] existing = initialText.split("\\n", -1);
		for (int i = 0; i < lines.length; i++) {
			lines[i] = addDrawableChild(new TextFieldWidget(textRenderer, x, y + i * 30, 260, 20,
				Text.literal("Line " + (i + 1))));
			lines[i].setMaxLength(FloatingTextBlockEntity.MAX_TEXT_LENGTH);
			if (i < existing.length) lines[i].setText(existing[i]);
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> save())
			.dimensions(x, y + 100, 90, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
			.dimensions(x + 100, y + 100, 90, 20).build());
		setInitialFocus(lines[0]);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xAA11151B);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 92, 0xFFFFFFFF);
		for (int i = 0; i < lines.length; i++) {
			context.drawTextWithShadow(textRenderer, Text.literal("Line " + (i + 1)).formatted(Formatting.GRAY),
				lines[i].getX(), lines[i].getY() - 11, 0xFFB9C2CE);
		}
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
		String value = String.join("\n", lines[0].getText(), lines[1].getText(), lines[2].getText())
			.replaceAll("(?:\\n)+$", "");
		if (ClientPlayNetworking.canSend(FloatingTextEditSavePayload.ID)) {
			ClientPlayNetworking.send(new FloatingTextEditSavePayload(pos, value));
		}
		close();
	}
}
