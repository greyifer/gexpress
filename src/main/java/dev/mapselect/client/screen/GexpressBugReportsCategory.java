package dev.mapselect.client.screen;

import com.google.common.collect.ImmutableList;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class GexpressBugReportsCategory {
	private static BugReportStore.Category draftCategory = BugReportStore.Category.MISCELLANEOUS;
	private static String draftMessage = "";

	private GexpressBugReportsCategory() {}

	public static ConfigCategory build(Screen parent) {
		return new BugReportsCategory();
	}

	private static final class BugReportsCategory implements ConfigCategory, CustomTabProvider {
		private final Text name = Text.literal("Bug Reports");
		private final Text tooltip = Text.literal("Submit G'Express bug reports to the Discord forum.");

		@Override
		public @NotNull Text name() {
			return name;
		}

		@Override
		public @NotNull ImmutableList<OptionGroup> groups() {
			return ImmutableList.of();
		}

		@Override
		public @NotNull Text tooltip() {
			return tooltip;
		}

		@Override
		public Tab createTab(YACLScreen screen, ScreenRect tabArea) {
			return new BugReportsTab(screen, tabArea, tooltip);
		}
	}

	private static final class BugReportsTab implements TabExt {
		private final BugReportsWidget panel;
		private final ButtonWidget doneButton;
		private final Tooltip tooltip;

		private BugReportsTab(YACLScreen screen, ScreenRect tabArea, Text tooltipText) {
			this.panel = new BugReportsWidget(tabArea.getLeft(), tabArea.getTop(), tabArea.width(), tabArea.height());
			this.doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> screen.finishOrSave())
				.size(Math.max(90, screen.width / 6), 20)
				.build();
			this.tooltip = Tooltip.of(tooltipText);
			refreshGrid(tabArea);
		}

		@Override
		public Text getTitle() {
			return Text.literal("Bug Reports");
		}

		@Override
		public void forEachChild(Consumer<ClickableWidget> consumer) {
			consumer.accept(panel);
			consumer.accept(doneButton);
		}

		@Override
		public void refreshGrid(ScreenRect tabArea) {
			panel.setDimensionsAndPosition(tabArea.width(), tabArea.height() - 30, tabArea.getLeft(), tabArea.getTop());
			doneButton.setDimensionsAndPosition(Math.max(90, tabArea.width() / 5), 20,
				tabArea.getLeft() + tabArea.width() - Math.max(90, tabArea.width() / 5) - 12,
				tabArea.getBottom() - 24);
		}

		@Override
		public @Nullable Tooltip getTooltip() {
			return tooltip;
		}
	}

	private static final class BugReportsWidget extends ClickableWidget {
		private static final int PANEL = 0xCC151B22;
		private static final int PANEL_SOFT = 0x88232B35;
		private static final int BORDER = 0x66586A7E;
		private static final int FIELD = 0xDD0E1319;
		private static final int GOLD = 0xFFE6C56A;
		private static final int BLUE = 0xFF7FB6FF;
		private static final int GREEN = 0xFF74D990;
		private static final int RED = 0xFFFF7A7A;
		private static final int TEXT = 0xFFE8EDF2;
		private static final int MUTED = 0xFF9EACB9;
		private static final int MAX_MESSAGE_LENGTH = 4000;

		private final List<HitButton> buttons = new ArrayList<>();
		private boolean textFocused;
		private int issueScroll;
		private boolean requestedReports;
		private String reportFilter = "all";
		private String status = "Reports are saved locally first, then sent to the configured Discord forum bot.";
		private int statusColor = MUTED;

		private BugReportsWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Text.literal("Bug Reports"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;
			if (!requestedReports) {
				requestedReports = true;
				BugReportStore.refreshRemoteReports(reportFilter);
			}
			buttons.clear();
			TextRenderer tr = client.textRenderer;
			int margin = 14;
			int x = getX() + margin;
			int y = getY() + margin;
			int w = width - margin * 2;
			int h = height - margin * 2;
			boolean split = w >= 620;
			int leftW = split ? Math.max(300, (int) (w * 0.48F)) : w;
			int rightX = split ? x + leftW + 12 : x;
			int rightY = split ? y : y + 318;
			int rightW = split ? w - leftW - 12 : w;
			int rightH = split ? h : Math.max(140, h - 330);

			drawWriter(context, tr, x, y, leftW, split ? h : 304, mouseX, mouseY);
			drawReview(context, tr, rightX, rightY, rightW, rightH, mouseX, mouseY);
		}

		private void drawWriter(DrawContext context, TextRenderer tr, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			fillPanel(context, x, y, w, h);
			context.fill(x + 1, y + 1, x + w - 1, y + 44, 0x55293642);
			context.drawTextWithShadow(tr, Text.literal("New Bug Report").formatted(Formatting.BOLD), x + 12, y + 10, TEXT);
			List<OrderedText> intro = tr.wrapLines(Text.literal("Sent to the configured Discord forum through the bot."), w - 24);
			for (int i = 0; i < Math.min(2, intro.size()); i++) {
				context.drawTextWithShadow(tr, intro.get(i), x + 12, y + 25 + i * 10, MUTED);
			}

			context.drawTextWithShadow(tr, Text.literal("Category").formatted(Formatting.BOLD), x + 12, y + 54, TEXT);
			int rowY = y + 70;
			int buttonW = Math.max(86, (w - 32 - 8) / 3);
			int bx = x + 12;
			for (BugReportStore.Category category : BugReportStore.Category.values()) {
				boolean selected = draftCategory == category;
				drawButton(context, tr, category.toString(), bx, rowY, buttonW, 22, mouseX, mouseY,
					selected ? GOLD : BLUE, () -> draftCategory = category);
				bx += buttonW + 4;
			}

			int fieldX = x + 12;
			int fieldY = rowY + 46;
			int fieldW = w - 24;
			context.drawTextWithShadow(tr, Text.literal("Details").formatted(Formatting.BOLD), fieldX, fieldY - 16, TEXT);
			int fieldH = Math.max(120, h - 222);
			context.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, FIELD);
			context.drawBorder(fieldX, fieldY, fieldW, fieldH, textFocused ? 0xFFE6C56A : BORDER);
			drawMessage(context, tr, fieldX + 8, fieldY + 8, fieldW - 16, fieldH - 16);
			String count = Math.min(MAX_MESSAGE_LENGTH, draftMessage.length()) + "/" + MAX_MESSAGE_LENGTH;
			context.drawTextWithShadow(tr, Text.literal(count), fieldX + fieldW - tr.getWidth(count) - 6,
				fieldY + fieldH - 12, 0xFF6F7D8A);

			int actionY = fieldY + fieldH + 12;
			int submitW = Math.min(150, Math.max(120, (w - 32) / 2));
			drawButton(context, tr, "Send to Discord", x + 12, actionY, submitW, 24, mouseX, mouseY, GREEN,
				this::submit);
			drawButton(context, tr, "Copy Report", x + 18 + submitW, actionY, submitW, 24, mouseX, mouseY, BLUE,
				this::copyFallback);

			List<OrderedText> statusLines = tr.wrapLines(Text.literal(status), w - 24);
			for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
				context.drawTextWithShadow(tr, statusLines.get(i), x + 12, actionY + 34 + i * 10, statusColor);
			}
		}

		private void drawMessage(DrawContext context, TextRenderer tr, int x, int y, int w, int h) {
			String message = draftMessage == null ? "" : draftMessage;
			if (message.isBlank()) {
				List<OrderedText> hint = tr.wrapLines(Text.literal("Write what broke, where it happened, and what you were doing. Include the role, modifier, map, or command if it matters."), w);
				for (int i = 0; i < Math.min(hint.size(), h / 10); i++) {
					context.drawTextWithShadow(tr, hint.get(i), x, y + i * 10, 0xFF6F7D8A);
				}
				return;
			}
			List<OrderedText> lines = tr.wrapLines(Text.literal(message + (textFocused ? "_" : "")), w);
			int max = Math.max(1, h / 10);
			int start = Math.max(0, lines.size() - max);
			for (int i = start; i < lines.size(); i++) {
				context.drawTextWithShadow(tr, lines.get(i), x, y + (i - start) * 10, TEXT);
			}
		}

		private void drawReview(DrawContext context, TextRenderer tr, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			fillPanel(context, x, y, w, h);
			context.drawTextWithShadow(tr, Text.literal("Recent Reports").formatted(Formatting.BOLD), x + 12, y + 10, TEXT);
			drawButton(context, tr, BugReportStore.isRemoteRefreshing() ? "Refreshing..." : "Refresh",
				x + w - 96, y + 8, 82, 22, mouseX, mouseY, BLUE, this::refresh);

			String[] filters = {"all", "open", "fixed", "duplicate"};
			String[] labels = {"All", "Open", "Fixed", "Dupes"};
			int chipY = y + 34;
			int chipX = x + 12;
			int chipW = Math.max(52, Math.min(70, (w - 36) / 4));
			for (int i = 0; i < filters.length; i++) {
				String filter = filters[i];
				drawButton(context, tr, labels[i], chipX, chipY, chipW, 20, mouseX, mouseY,
					filter.equals(reportFilter) ? GOLD : BLUE, () -> setReportFilter(filter));
				chipX += chipW + 4;
			}

			List<OrderedText> statusLines = tr.wrapLines(Text.literal(BugReportStore.remoteStatus()), w - 24);
			for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
				context.drawTextWithShadow(tr, statusLines.get(i), x + 12, y + 61 + i * 10, MUTED);
			}

			List<BugReportStore.RemoteReport> reports = BugReportStore.remoteReports().stream()
				.sorted(Comparator.comparing(BugReportStore.RemoteReport::updatedAt).reversed())
				.toList();
			int listY = y + 88;
			int listH = h - 98;
			if (reports.isEmpty()) {
				drawEmptyReports(context, tr, x + 12, listY, w - 24);
				return;
			}
			int rowH = 96;
			int contentH = reports.size() * rowH;
			issueScroll = Math.max(0, Math.min(issueScroll, Math.max(0, contentH - listH)));
			int cy = listY - issueScroll;
			for (BugReportStore.RemoteReport report : reports) {
				if (cy + rowH >= listY && cy <= listY + listH) {
					drawReport(context, tr, report, x + 12, cy, w - 24, rowH - 8, mouseX, mouseY);
				}
				cy += rowH;
			}
		}

		private void drawEmptyReports(DrawContext context, TextRenderer tr, int x, int y, int w) {
			context.drawTextWithShadow(tr, Text.literal("No Discord reports loaded for this filter."), x, y + 4, 0xFF6F7D8A);
			List<OrderedText> lines = tr.wrapLines(Text.literal(
				"Use Refresh after the bot is online. This list reads from the configured Discord forum and the bot's report store."), w);
			for (int i = 0; i < Math.min(3, lines.size()); i++) {
				context.drawTextWithShadow(tr, lines.get(i), x, y + 22 + i * 10, MUTED);
			}
		}

		private void drawReport(DrawContext context, TextRenderer tr, BugReportStore.RemoteReport report,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			int accent = reportAccent(report.status());
			context.fill(x, y, x + w, y + h, "open".equalsIgnoreCase(report.status()) ? 0x7732241A : PANEL_SOFT);
			context.drawBorder(x, y, w, h, BORDER);
			context.fill(x, y, x + 3, y + h, accent);
			String title = report.id() + "  " + report.title();
			context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(title, w - 18)), x + 8, y + 7, accent);
			String meta = statusLabel(report.status()) + " | " + report.category() + " | " + reporter(report) + " | " + shortDate(report.updatedAt());
			context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(meta, w - 16)), x + 8, y + 21, MUTED);
			List<OrderedText> body = tr.wrapLines(Text.literal(report.description().replaceAll("\\s+", " ")), w - 16);
			for (int i = 0; i < Math.min(2, body.size()); i++) {
				context.drawTextWithShadow(tr, body.get(i), x + 8, y + 36 + i * 10, i == 0 ? TEXT : MUTED);
			}
			int by = y + h - 24;
			drawButton(context, tr, "Open", x + 8, by, 52, 18, mouseX, mouseY, BLUE, () -> BugReportStore.openRemoteReport(report));
			if (!BugReportStore.canModerateRemoteReports()) return;
			boolean fixed = "fixed".equalsIgnoreCase(report.status());
			drawButton(context, tr, fixed ? "Reopen" : "Fixed", x + 64, by, 62, 18, mouseX, mouseY,
				fixed ? GOLD : GREEN, () -> BugReportStore.requestRemoteAction(report, fixed ? "open" : "fixed", reportFilter));
			drawButton(context, tr, "Dupe", x + 130, by, 54, 18, mouseX, mouseY, GOLD,
				() -> BugReportStore.requestRemoteAction(report, "duplicate", reportFilter));
			drawButton(context, tr, "Delete", x + 188, by, 62, 18, mouseX, mouseY, RED,
				() -> BugReportStore.requestRemoteAction(report, "delete", reportFilter));
		}

		private void submit() {
			BugReportStore.Submission submission = BugReportStore.submitToDiscord(draftCategory, draftMessage);
			if (submission == null) {
				status = "Write the bug description first.";
				statusColor = RED;
				return;
			}
			status = submission.opened()
				? "Sent to Discord. Refresh to load the forum copy."
				: "Discord reporting is unavailable here. Copied the report text.";
			if (!submission.saved()) status = "Copied the report text. Local fallback failed.";
			statusColor = submission.saved() ? (submission.opened() ? GREEN : GOLD) : RED;
			draftMessage = "";
			textFocused = false;
		}

		private void copyFallback() {
			BugReportStore.Submission submission = BugReportStore.copyReportToClipboard(draftCategory, draftMessage);
			if (submission == null) {
				status = "Write the bug description first.";
				statusColor = RED;
				return;
			}
			status = submission.saved()
				? "Copied the report text for Discord fallback."
				: "Copied the report text. Local fallback failed.";
			statusColor = submission.saved() ? BLUE : RED;
		}

		private void refresh() {
			status = "Refreshing Discord reports...";
			statusColor = BLUE;
			BugReportStore.refreshRemoteReports(reportFilter);
		}

		private void setReportFilter(String filter) {
			reportFilter = filter == null ? "all" : filter;
			issueScroll = 0;
			refresh();
		}

		private int reportAccent(String status) {
			return switch ((status == null ? "" : status).toLowerCase()) {
				case "open" -> GOLD;
				case "fixed" -> GREEN;
				case "duplicate" -> BLUE;
				case "deleted" -> RED;
				default -> MUTED;
			};
		}

		private String statusLabel(String status) {
			String value = status == null ? "" : status.trim();
			if (value.isBlank()) return "unknown";
			return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
		}

		private String reporter(BugReportStore.RemoteReport report) {
			String name = report.reporterName();
			return name == null || name.isBlank() ? "Unknown" : name;
		}

		private String shortDate(String raw) {
			if (raw == null || raw.isBlank()) return "unknown";
			int split = raw.indexOf('T');
			if (split < 0 || raw.length() < split + 6) return raw;
			return raw.substring(0, split) + " " + raw.substring(split + 1, split + 6);
		}

		private void fillPanel(DrawContext context, int x, int y, int w, int h) {
			context.fill(x, y, x + w, y + h, PANEL);
			context.drawBorder(x, y, w, h, BORDER);
			context.fill(x, y, x + w, y + 2, 0xAAE6C56A);
		}

		private void drawButton(DrawContext context, TextRenderer tr, String label, int x, int y, int w, int h,
				int mouseX, int mouseY, int accent, Runnable action) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			context.fill(x, y, x + w, y + h, hovered ? 0xCC2E3A46 : 0xAA202832);
			context.drawBorder(x, y, w, h, hovered ? 0xFFE8EDF2 : BORDER);
			context.fill(x, y, x + 3, y + h, accent);
			context.drawCenteredTextWithShadow(tr, Text.literal(tr.trimToWidth(label, w - 10)),
				x + w / 2, y + (h - 8) / 2, TEXT);
			buttons.add(new HitButton(x, y, w, h, action));
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
			for (HitButton hit : List.copyOf(buttons)) {
				if (!hit.contains(mouseX, mouseY)) continue;
				hit.action().run();
				return true;
			}
			textFocused = isInsideTextField(mouseX, mouseY);
			return textFocused || super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (!textFocused) return super.keyPressed(keyCode, scanCode, modifiers);
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				textFocused = false;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				if (!draftMessage.isEmpty()) draftMessage = draftMessage.substring(0, draftMessage.length() - 1);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DELETE) {
				draftMessage = "";
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				append("\n");
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
				MinecraftClient client = MinecraftClient.getInstance();
				append(client == null || client.keyboard == null ? "" : client.keyboard.getClipboard());
				return true;
			}
			return true;
		}

		@Override
		public boolean charTyped(char chr, int modifiers) {
			if (!textFocused) return super.charTyped(chr, modifiers);
			if (!Character.isISOControl(chr)) append(Character.toString(chr));
			return true;
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (mouseX < getX() || mouseX >= getX() + width || mouseY < getY() || mouseY >= getY() + height) {
				return false;
			}
			issueScroll = Math.max(0, issueScroll - (int) Math.round(verticalAmount * 24.0D));
			return true;
		}

		private void append(String value) {
			if (value == null || value.isEmpty()) return;
			String next = draftMessage + value;
			draftMessage = next.length() > MAX_MESSAGE_LENGTH ? next.substring(0, MAX_MESSAGE_LENGTH) : next;
		}

		private boolean isInsideTextField(double mouseX, double mouseY) {
			int margin = 14;
			int x = getX() + margin;
			int y = getY() + margin;
			int w = width - margin * 2;
			boolean split = w >= 620;
			int leftW = split ? Math.max(300, (int) (w * 0.48F)) : w;
			int fieldX = x + 12;
			int fieldY = y + 116;
			int fieldW = leftW - 24;
			int fieldH = Math.max(120, (split ? height - margin * 2 : 304) - 222);
			return contains(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH);
		}

		private boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private record HitButton(int x, int y, int w, int h, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}
}
