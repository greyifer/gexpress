package dev.mapselect.client;

import dev.mapselect.network.MafiaActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class GodfatherRecruitScreen extends Screen {
	private static final int PANEL_COLOR = 0xEA111820;
	private static final int PANEL_EDGE = 0xFF455365;
	private static final RoleChoice MAFIOSO = new RoleChoice(
		"Mafioso",
		"Close-range enforcer",
		"Turns the target into a family killer with access to weapons.",
		0xFFE35252,
		MafiaActionPayload.RECRUIT_MAFIOSO
	);
	private static final RoleChoice JANITOR = new RoleChoice(
		"Janitor",
		"Cleaner and cover-up",
		"Turns the target into a cleaner who can erase bodies and evidence.",
		0xFF65D69A,
		MafiaActionPayload.RECRUIT_JANITOR
	);

	public GodfatherRecruitScreen() {
		super(Text.literal("Family Recruitment"));
	}

	@Override
	protected void init() {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		Layout layout = layout();
		context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(),
			layout.panelY() + layout.panelHeight(), PANEL_COLOR);
		context.drawBorder(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), PANEL_EDGE);
		context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(), layout.panelY() + 2,
			0xFF9F4E64);
		context.fill(layout.panelX(), layout.panelY() + 2, layout.panelX() + layout.panelWidth(),
			layout.panelY() + 3, 0xFF6ED6A1);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, layout.panelY() + 18, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Choose the role your target joins as").formatted(Formatting.GRAY),
			width / 2, layout.panelY() + 33, 0xFF9BA3AE);
		drawChoice(context, MAFIOSO, layout.leftCardX(), layout.cardY(), layout.cardWidth(), layout.cardHeight(),
			mouseX, mouseY);
		drawChoice(context, JANITOR, layout.rightCardX(), layout.cardY(), layout.cardWidth(), layout.cardHeight(),
			mouseX, mouseY);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Look at a player before choosing.").formatted(Formatting.DARK_GRAY),
			width / 2, layout.panelY() + layout.panelHeight() - 18, 0xFF707B86);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Layout layout = layout();
		if (contains(mouseX, mouseY, layout.leftCardX(), layout.cardY(), layout.cardWidth(), layout.cardHeight())) {
			recruit(MAFIOSO.action());
			return true;
		}
		if (contains(mouseX, mouseY, layout.rightCardX(), layout.cardY(), layout.cardWidth(), layout.cardHeight())) {
			recruit(JANITOR.action());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void drawChoice(DrawContext context, RoleChoice choice, int x, int y, int cardWidth, int cardHeight,
			int mouseX, int mouseY) {
		boolean hovered = contains(mouseX, mouseY, x, y, cardWidth, cardHeight);
		context.fill(x, y, x + cardWidth, y + cardHeight, hovered ? 0xCC253141 : 0xAA1A2430);
		context.drawBorder(x, y, cardWidth, cardHeight, hovered ? 0xFFE7EFF8 : 0x88758AA0);
		context.fill(x, y, x + 4, y + cardHeight, choice.color());
		context.drawTextWithShadow(textRenderer, Text.literal(choice.name()).formatted(Formatting.BOLD),
			x + 13, y + 10, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.literal(choice.subtitle()).formatted(Formatting.GRAY),
			x + 13, y + 24, 0xFF9BA3AE);
		List<OrderedText> lines = textRenderer.wrapLines(Text.literal(choice.description()), cardWidth - 24);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			context.drawTextWithShadow(textRenderer, lines.get(i), x + 13, y + 43 + i * 10, 0xFFD9E0E7);
		}
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

	private Layout layout() {
		int panelWidth = Math.min(width - 48, 430);
		int cardGap = 14;
		int cardWidth = Math.max(132, (panelWidth - 46 - cardGap) / 2);
		int cardHeight = 88;
		int panelHeight = 172;
		int panelX = width / 2 - panelWidth / 2;
		int panelY = height / 2 - panelHeight / 2;
		int leftCardX = panelX + 23;
		int cardY = panelY + 58;
		return new Layout(panelX, panelY, panelWidth, panelHeight, leftCardX,
			leftCardX + cardWidth + cardGap, cardY, cardWidth, cardHeight);
	}

	private boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private record RoleChoice(String name, String subtitle, String description, int color, int action) {}

	private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int leftCardX,
			int rightCardX, int cardY, int cardWidth, int cardHeight) {}
}
