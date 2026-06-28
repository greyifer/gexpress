package dev.mapselect.client.screen;

import dev.mapselect.client.skin.BlockbenchSkinImporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class GexpressSkinImportScreen extends Screen {
	private static final int PANEL = 0xEE121820;
	private static final int BORDER = 0x885E6D7E;
	private static final int GOLD = 0xFFE7C66A;
	private static final int GREEN = 0xFF74D990;
	private static final int RED = 0xFFFF6B70;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;

	private final Screen parent;
	private List<String> files = List.of();
	private ButtonWidget importButton;
	private String status = "Put .bbmodel files in the server config/gexpress/skins folder, then import.";
	private int statusColor = MUTED;
	private int refreshTicks;
	private boolean importing;

	public GexpressSkinImportScreen(Screen parent) {
		super(Text.literal("Import Skins"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		files = BlockbenchSkinImporter.sourceFiles();
		int center = width / 2;
		int buttonY = Math.min(height - 58, 258);
		addDrawableChild(ButtonWidget.builder(Text.literal("Open Local Folder"), button -> openFolder())
			.dimensions(center - 156, buttonY, 148, 20).build());
		importButton = addDrawableChild(ButtonWidget.builder(Text.literal("Import Skins"), button -> importSkins())
			.dimensions(center + 8, buttonY, 148, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(center - 75, buttonY + 28, 150, 20).build());
	}

	@Override
	public void tick() {
		if (++refreshTicks >= 20) {
			refreshTicks = 0;
			files = BlockbenchSkinImporter.sourceFiles();
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xF00E1116);
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, 18, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Import server-owned Blockbench weapon models and sync them to every client."), width / 2, 33, MUTED);

		int panelW = Math.min(620, width - 40);
		int panelX = (width - panelW) / 2;
		int panelY = 54;
		int panelH = Math.min(188, height - 132);
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		context.drawBorder(panelX, panelY, panelW, panelH, BORDER);
		context.fill(panelX, panelY, panelX + panelW, panelY + 3, GOLD);

		int x = panelX + 16;
		context.drawTextWithShadow(textRenderer, Text.literal("Server import folder").formatted(Formatting.GOLD, Formatting.BOLD),
			x, panelY + 16, TEXT);
		context.drawTextWithShadow(textRenderer, Text.literal("config/gexpress/skins"), x, panelY + 31, TEXT);
		context.drawTextWithShadow(textRenderer,
			Text.literal(textRenderer.trimToWidth("Local/integrated path: " + BlockbenchSkinImporter.inputFolder(), panelW - 32)),
			x, panelY + 44, MUTED);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Include knife, gun, or revolver anywhere in each .bbmodel filename."),
			x, panelY + 64, TEXT);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Embedded textures are extracted automatically; matching PNG files also work."),
			x, panelY + 77, MUTED);

		context.drawTextWithShadow(textRenderer,
			Text.literal("Local/integrated detected models (" + files.size() + ")").formatted(Formatting.BOLD), x, panelY + 100, TEXT);
		if (files.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal("No local .bbmodel files found. Dedicated servers may still have files."),
				x, panelY + 115, MUTED);
		} else {
			int maxRows = Math.max(1, (panelH - 135) / 12);
			for (int i = 0; i < Math.min(maxRows, files.size()); i++) {
				String suffix = i == maxRows - 1 && files.size() > maxRows ? "  +" + (files.size() - maxRows) + " more" : "";
				String line = textRenderer.trimToWidth(files.get(i) + suffix, panelW - 40);
				context.drawTextWithShadow(textRenderer, Text.literal(line), x + 4, panelY + 115 + i * 12, GREEN);
			}
		}

		context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2,
			Math.min(height - 86, panelY + panelH + 10), statusColor);
		super.render(context, mouseX, mouseY, delta);
	}

	private void openFolder() {
		boolean opened = BlockbenchSkinImporter.openInputFolder();
		files = BlockbenchSkinImporter.sourceFiles();
		status = opened ? "Local/integrated folder opened. For dedicated servers, put files on the server."
			: "Could not open the folder. The path is shown above.";
		statusColor = opened ? GOLD : RED;
	}

	private void importSkins() {
		if (importing) return;
		MinecraftClient client = MinecraftClient.getInstance();
		importing = true;
		importButton.active = false;
		status = "Requesting server import...";
		statusColor = GOLD;
		BlockbenchSkinImporter.importAndRefresh(client).whenComplete((result, error) -> client.execute(() -> {
			importing = false;
			importButton.active = true;
			files = BlockbenchSkinImporter.sourceFiles();
			if (error != null) {
				status = "Import failed: " + error.getMessage();
				statusColor = RED;
				return;
			}
			if (result.fatalError() != null) {
				status = "Import failed: " + result.fatalError();
				statusColor = RED;
			} else if (!result.errors().isEmpty()) {
				String more = result.errors().size() > 1 ? " (+" + (result.errors().size() - 1) + " more)" : "";
				status = "Imported " + result.importedIds().size() + "; " + result.errors().getFirst() + more;
				statusColor = GOLD;
			} else {
				status = "Imported " + result.importedIds().size()
					+ " skin(s). Commands, cases, and skin menus are updated.";
				statusColor = GREEN;
			}
		}));
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
