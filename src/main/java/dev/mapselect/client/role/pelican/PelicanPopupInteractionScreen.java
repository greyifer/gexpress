package dev.mapselect.client.role.pelican;

import dev.mapselect.client.input.ClientAbilityKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Transparent cursor-only screen used to interact with Pelican belly-chat popups. */
public final class PelicanPopupInteractionScreen extends Screen {
	public PelicanPopupInteractionScreen() {
		super(Text.translatable("screen.gexpress.pelican.popup_cursor"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ClientPelicanChatPopups.renderInteractive(context, mouseX, mouseY);
		String key = ClientAbilityKeys.displayName(ClientAbilityKeys.tertiaryBinding());
		Text hint = Text.translatable("screen.gexpress.pelican.popup_cursor_hint", key);
		context.drawCenteredTextWithShadow(textRenderer, hint, width / 2,
			Math.max(2, height - textRenderer.fontHeight - 4), 0xFFB8C3A0);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return ClientPelicanChatPopups.handleInteractiveClick(mouseX, mouseY, button)
			|| super.mouseClicked(mouseX, mouseY, button);
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
		MinecraftClient.getInstance().setScreen(null);
	}
}
