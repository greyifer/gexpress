package dev.mapselect.client.screen;

import dev.doctor4t.wathe.index.WatheItems;
import io.github.caspe.glib.api.client.editor.GlibContentScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.render.DevWeaponModels;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.currency.GcoinComponent;
import dev.mapselect.item.GexpressCaseItem;
import dev.mapselect.network.progression.SkinCaseResultPayload;
import dev.mapselect.skin.PlayerSkinComponent;
import dev.mapselect.skin.SkinDailyShop;
import dev.mapselect.skin.SkinShopConfig;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.time.Instant;

public final class GexpressSkinsCategory {
	private GexpressSkinsCategory() {}
	private static final Identifier GCOIN_TEXTURE = Identifier.of(MapSelect.MOD_ID, "textures/gui/gcoin.png");
	private static SkinsPanelWidget activePanel;

	public static void handleCaseResult(SkinCaseResultPayload payload) {
		SkinsPanelWidget panel = activePanel;
		if (panel != null) panel.acceptCaseResult(payload);
	}

	public static Screen createGlibScreen(Screen parent) {
		return GlibContentScreen.builder(parent, Text.literal(SkinShopConfig.title()))
			.subtitle(Text.literal(SkinShopConfig.subtitle()))
			.accentColor(SkinShopConfig.accentArgb())
			.content((host, rect) -> new SkinsPanelWidget(host, rect.x() + 4, rect.y() + 4,
				rect.width() - 8, rect.height() - 8))
			.onClose(() -> activePanel = null)
			.build();
	}

	private static final class SkinsPanelWidget extends ClickableWidget {
		private static final int TILE_WIDTH = 116;
		private static final int TILE_HEIGHT = 72;
		private static final int TILE_GAP = 8;
		private static final long CASE_ZOOM_MS = 1600L;
		private static final long CASE_OPEN_MS = 2500L;
		private static final long CASE_REEL_MS = 7600L;
		private static final long CASE_REVEAL_MS = 3600L;
		private final List<TypeBox> typeBoxes = new ArrayList<>();
		private final List<CaseBox> caseBoxes = new ArrayList<>();
		private final List<SkinTab> tabs = new ArrayList<>();
		private final List<SkinTile> skinTiles = new ArrayList<>();
		private final List<CaseTile> caseTiles = new ArrayList<>();
		private final List<CaseBuyTile> caseBuyTiles = new ArrayList<>();
		private final List<DailyOfferTile> dailyOfferTiles = new ArrayList<>();
		private final List<CaseCarouselButton> caseCarouselButtons = new ArrayList<>();
		private final List<CaseCarouselDot> caseCarouselDots = new ArrayList<>();
		private final Random caseAnimationRandom = new Random();
		private List<CaseRewardPreview> openingReel = List.of();
		private CaseRewardPreview openingResult;
		private GexpressConfig.SkinCaseEntry openingCase;
		private ItemStack openingClosedStack = ItemStack.EMPTY;
		private ItemStack openingOpenStack = ItemStack.EMPTY;
		private ItemStack openingOpenedStack = ItemStack.EMPTY;
		private long openingStartedAt;
		private int openingTargetIndex;
		private boolean openingNewUnlock;
		private int openingDuplicateRefund;
		private boolean openingCommandSent;
		private long selectedCaseOpenedAt;
		private boolean openingRevealSoundPlayed;
		private final Screen parentScreen;
		private BackButton backButton;
		private CaseOpenButton caseOpenButton;
		private GexpressConfig.SkinCaseEntry selectedCase;
		private WeaponSkinType selectedType = WeaponSkinType.KNIFE;
		private boolean showingCases = true;
		private int featuredCaseIndex;
		private CaseCarouselArea caseCarouselArea;
		private int scroll;

		private SkinsPanelWidget(Screen parentScreen, int x, int y, int width, int height) {
			super(x, y, width, height, Text.translatable("gui.gexpress.config.category.skins"));
			this.parentScreen = parentScreen;
			activePanel = this;
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			activePanel = this;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;
			typeBoxes.clear();
			caseBoxes.clear();
			tabs.clear();
			skinTiles.clear();
			caseTiles.clear();
			caseBuyTiles.clear();
			dailyOfferTiles.clear();
			caseCarouselButtons.clear();
			caseCarouselDots.clear();
			caseCarouselArea = null;
			backButton = null;
			caseOpenButton = null;

			if (openingCase != null) {
				renderOpeningAnimation(context, client);
				return;
			}
			if (showingCases && selectedCase != null) {
				renderCaseDetail(context, client, mouseX, mouseY);
			} else {
				renderWorkspaceTabs(context, client, mouseX, mouseY);
				if (showingCases) renderCases(context, client, mouseX, mouseY);
				else renderPicker(context, client, mouseX, mouseY);
			}
		}

		private void renderWorkspaceTabs(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			int workspaceX = storefrontX();
			int workspaceW = storefrontWidth();
			drawGcoinBalance(context, client, workspaceX + workspaceW);
			int x = workspaceX;
			int y = getY() + 4;
			int available = workspaceW - (showGcoin(client) ? 124 : 0);
			int gap = 5;
			int tabWidth = Math.max(64, Math.min(132, (available - gap * 2) / 3));
			drawWorkspaceTab(context, client, x, y, tabWidth, 22, "Store",
				showingCases, SkinTabMode.STORE, mouseX, mouseY);
			x += tabWidth + gap;
			drawWorkspaceTab(context, client, x, y, tabWidth, 22, "Knives",
				!showingCases && selectedType == WeaponSkinType.KNIFE, SkinTabMode.KNIVES, mouseX, mouseY);
			x += tabWidth + gap;
			drawWorkspaceTab(context, client, x, y, tabWidth, 22, "Guns",
				!showingCases && selectedType == WeaponSkinType.GUN, SkinTabMode.GUNS, mouseX, mouseY);
		}

		private void drawWorkspaceTab(DrawContext context, MinecraftClient client, int x, int y, int w, int h,
				String label, boolean selected, SkinTabMode mode, int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			int accent = SkinShopConfig.accentArgb();
			context.fill(x, y, x + w, y + h, selected ? 0xE02C3038 : hovered ? 0xCC252C35 : 0x991A2029);
			context.drawBorder(x, y, w, h, selected ? accent : hovered ? withAlpha(accent, 0xAA) : 0x6655606E);
			if (selected) context.fill(x, y + h - 2, x + w, y + h, accent);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + w / 2, y + 7,
				selected ? 0xFFFFFFFF : 0xFFB8C3CC);
			tabs.add(new SkinTab(x, y, w, h, mode));
		}

		private void renderOverview(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			drawGcoinBalance(context, client);
			int top = getY() + (showGcoin(client) ? 42 : 24);
			int available = width - 36;
			boolean stacked = available < 540;
			int boxWidth = stacked ? available : (available - 28) / 3;
			int boxHeight = stacked ? 112 : Math.min(164, Math.max(120, height - 72));
			int x = getX() + 18;
			int y = top;
			drawTypeBox(context, client, WeaponSkinType.KNIFE, x, y, boxWidth, boxHeight, mouseX, mouseY);
			if (stacked) {
				y += boxHeight + 12;
			} else {
				x += boxWidth + 14;
			}
			drawTypeBox(context, client, WeaponSkinType.GUN, x, y, boxWidth, boxHeight, mouseX, mouseY);
			if (stacked) {
				y += boxHeight + 12;
				x = getX() + 18;
			} else {
				x += boxWidth + 14;
			}
			drawCaseBox(context, client, x, y, boxWidth, boxHeight, mouseX, mouseY);
		}

		private void drawTypeBox(DrawContext context, MinecraftClient client, WeaponSkinType type,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			WeaponSkin equipped = equipped(type);
			context.fill(x, y, x + w, y + h, hovered ? 0x55353D45 : 0x44272E36);
			context.drawBorder(x, y, w, h, hovered ? 0xDDEAF2FF : 0x8890A0AA);
			drawItemPreview(context, previewStack(type, equipped), x + w / 2, y + h / 2 - 14, Math.max(3.0F, Math.min(5.0F, w / 58.0F)));
			context.drawTextWithShadow(client.textRenderer,
				Text.literal(type.displayName()).formatted(Formatting.GRAY), x + 12, y + h - 28, 0xFFB8C3CC);
			context.drawTextWithShadow(client.textRenderer,
				Text.literal(equipped.displayName()), x + 12, y + h - 15, 0xFFFFFFFF);
			context.fill(x + 12, y + h - 5, x + w - 12, y + h - 3, 0xFF000000 | equipped.color());
			typeBoxes.add(new TypeBox(x, y, w, h, type));
		}

		private void drawCaseBox(DrawContext context, MinecraftClient client, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			int owned = ownedCaseTotal(client);
			context.fill(x, y, x + w, y + h, hovered ? 0x55353D45 : 0x44272E36);
			context.drawBorder(x, y, w, h, hovered ? 0xDDEAF2FF : 0x8890A0AA);
			drawCasePreview(context, x + w / 2, y + h / 2 + 2, Math.max(0.9F, Math.min(1.18F, w / 230.0F)));
			context.drawTextWithShadow(client.textRenderer,
				Text.literal("G'Express Cases").formatted(Formatting.GRAY), x + 12, y + h - 28, 0xFFB8C3CC);
			context.drawTextWithShadow(client.textRenderer,
				Text.literal(owned + " owned"), x + 12, y + h - 15, 0xFFFFD56E);
			context.fill(x + 12, y + h - 5, x + w - 12, y + h - 3, 0xFFE0B65A);
			caseBoxes.add(new CaseBox(x, y, w, h));
		}

		private void renderPicker(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			WeaponSkin equipped = equipped(selectedType);

			int listX = getX() + 14;
			int listY = getY() + 52;
			int listWidth = Math.min(260, Math.max(TILE_WIDTH, width / 3));
			int previewX = listX + listWidth + 22;
			int previewW = getX() + width - previewX - 14;
			boolean compact = previewW < 140;
			if (compact) {
				listWidth = width - 28;
				previewX = listX;
				previewW = listWidth;
			}

			List<WeaponSkin> skins = unlocked(selectedType);
			int columns = Math.max(1, listWidth / (TILE_WIDTH + TILE_GAP));
			int contentHeight = ((skins.size() + columns - 1) / columns) * (TILE_HEIGHT + TILE_GAP);
			int viewport = Math.max(0, height - 56);
			scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport)));
			int x = listX;
			int y = listY - scroll;
			context.enableScissor(listX, listY, listX + listWidth, getY() + height - 10);
			for (int i = 0; i < skins.size(); i++) {
				WeaponSkin skin = skins.get(i);
				drawSkinTile(context, client, skin, skin == equipped, x, y, mouseX, mouseY);
				if ((i + 1) % columns == 0) {
					x = listX;
					y += TILE_HEIGHT + TILE_GAP;
				} else {
					x += TILE_WIDTH + TILE_GAP;
				}
			}
			context.disableScissor();

			if (!compact) {
				int previewY = listY;
				context.fill(previewX, previewY, previewX + previewW, getY() + height - 8, 0x33272E36);
				context.drawBorder(previewX, previewY, previewW, height - 54, 0x6690A0AA);
				drawItemPreview(context, previewStack(selectedType, equipped),
					previewX + previewW / 2, previewY + Math.max(68, (height - 72) / 2), Math.max(5.0F, Math.min(9.0F, previewW / 46.0F)));
				context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(equipped.displayName()),
					previewX + previewW / 2, getY() + height - 30, 0xFFFFFFFF);
			}
		}

		private void renderCases(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			boolean showCases = SkinShopConfig.casesEnabled();
			boolean showOffers = SkinShopConfig.dailyOffersEnabled();
			List<GexpressConfig.SkinCaseEntry> cases = showCases
				? GexpressConfig.getSkinCaseEntries() : List.of();
			List<SkinDailyShop.Offer> offers = showOffers && client.player != null
				? SkinDailyShop.offers(client.player.getUuid()) : List.of();
			int panelX = storefrontX();
			int panelY = getY() + 34;
			int panelW = storefrontWidth();
			int panelBottom = getY() + height - 6;
			int viewportHeight = Math.max(80, panelBottom - panelY);
			scroll = 0;
			context.enableScissor(panelX, panelY, panelX + panelW, panelBottom);
			if (!showCases && !showOffers) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("The store is currently unavailable.").formatted(Formatting.GRAY),
					panelX + panelW / 2, panelY + 36, 0xFFB8C3CC);
			}
			int cursorY = panelY;
			if (showCases) {
				boolean compactStyle = "compact".equals(SkinShopConfig.style());
				int featuredHeight = showOffers
					? Math.min(viewportHeight, Math.max(compactStyle ? 64 : 72,
						Math.min(compactStyle ? 112 : 146, viewportHeight * (compactStyle ? 36 : 44) / 100)))
					: viewportHeight;
				drawFeaturedCaseCarousel(context, client, cases, panelX, cursorY, panelW,
					featuredHeight, mouseX, mouseY);
				cursorY += featuredHeight + (showOffers ? 7 : 0);
			}
			if (showOffers) {
				int headerHeight = 19;
				drawOffersHeader(context, client, panelX, cursorY, panelW);
				cursorY += headerHeight;
				drawDailyOffers(context, client, offers, panelX, cursorY, panelW,
					Math.max(24, panelBottom - cursorY), mouseX, mouseY);
			}
			context.disableScissor();
		}

		private void drawFeaturedCaseCarousel(DrawContext context, MinecraftClient client,
				List<GexpressConfig.SkinCaseEntry> cases, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			int accent = SkinShopConfig.accentArgb();
			context.fill(x, y, x + w, y + h, SkinShopConfig.panelArgb(0x55));
			context.drawBorder(x, y, w, h, withAlpha(accent, 0x88));
			context.drawTextWithShadow(client.textRenderer, Text.literal("FEATURED"), x + 11, y + 7, 0xFFFFFFFF);
			caseCarouselArea = new CaseCarouselArea(x, y, w, h);
			if (cases.isEmpty()) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("No cases configured").formatted(Formatting.GRAY), x + w / 2, y + h / 2, 0xFFB8C3CC);
				return;
			}
			featuredCaseIndex = Math.floorMod(featuredCaseIndex, cases.size());
			GexpressConfig.SkinCaseEntry skinCase = cases.get(featuredCaseIndex);
			boolean hovered = contains(mouseX, mouseY, x + 30, y + 20, w - 60, h - 20);
			int previewY = y + 23 + Math.max(24, (h - 48) / 2);
			float scale = Math.max(0.62F, Math.min(1.08F, (h - 42) / 78.0F));
			drawCasePreview(context, x + w / 2, previewY, hovered ? scale * 1.05F : scale);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(textRendererTrim(client, skinCase.displayName(), Math.max(80, w - 100))),
				x + w / 2, y + h - 16, 0xFFFFFFFF);
			caseTiles.add(new CaseTile(x + 30, y + 20, w - 60, h - 20, skinCase.id()));

			if (cases.size() > 1) {
				int buttonY = y + Math.max(25, h / 2 - 14);
				drawCaseCarouselArrow(context, client, x + 8, buttonY, 22, 28, -1, mouseX, mouseY);
				drawCaseCarouselArrow(context, client, x + w - 30, buttonY, 22, 28, 1, mouseX, mouseY);
				int dotW = 12;
				int dotGap = 4;
				int dotsX = x + (w - (cases.size() * dotW + (cases.size() - 1) * dotGap)) / 2;
				for (int index = 0; index < cases.size(); index++) {
					int dotX = dotsX + index * (dotW + dotGap);
					boolean dotHovered = contains(mouseX, mouseY, dotX - 2, y + 5, dotW + 4, 8);
					context.fill(dotX, y + 8, dotX + dotW, y + 10,
						index == featuredCaseIndex ? accent : dotHovered ? withAlpha(accent, 0xAA) : 0x6655606E);
					caseCarouselDots.add(new CaseCarouselDot(dotX - 2, y + 5, dotW + 4, 8, index));
				}
			}
		}

		private void drawCaseCarouselArrow(DrawContext context, MinecraftClient client,
				int x, int y, int w, int h, int direction, int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			int accent = SkinShopConfig.accentArgb();
			context.fill(x, y, x + w, y + h, hovered ? SkinShopConfig.panelArgb(0x99) : SkinShopConfig.panelArgb(0x66));
			context.drawBorder(x, y, w, h, hovered ? accent : withAlpha(accent, 0x77));
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(direction < 0 ? "<" : ">"),
				x + w / 2, y + 9, hovered ? 0xFFFFFFFF : 0xFFB8C3CC);
			caseCarouselButtons.add(new CaseCarouselButton(x, y, w, h, direction));
		}

		private void drawDailyOffers(DrawContext context, MinecraftClient client,
				List<SkinDailyShop.Offer> offers, int x, int y, int w, int h,
				int mouseX, int mouseY) {
			if (offers.isEmpty()) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("No skins are eligible for the daily store.").formatted(Formatting.GRAY),
					x + w / 2, y + Math.min(34, h / 2), 0xFFB8C3CC);
				return;
			}
			int gap = 6;
			int columns = Math.max(1, Math.min(offers.size(), Math.max(1, (w + gap) / 82)));
			int rows = Math.max(1, (offers.size() + columns - 1) / columns);
			int cardW = Math.max(42, (w - Math.max(0, columns - 1) * gap) / columns);
			int rowGap = rows > 1 ? 5 : 0;
			int cardH = Math.max(42, (h - Math.max(0, rows - 1) * rowGap) / rows);
			for (int index = 0; index < offers.size(); index++) {
				SkinDailyShop.Offer offer = offers.get(index);
				int cardX = x + (index % columns) * (cardW + gap);
				int cardY = y + (index / columns) * (cardH + rowGap);
				drawDailyOffer(context, client, offer, cardX, cardY, cardW, cardH, mouseX, mouseY);
			}
		}

		private void drawDailyOffer(DrawContext context, MinecraftClient client, SkinDailyShop.Offer offer,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			boolean owned = isUnlocked(client, offer.type(), offer.skin());
			boolean canBuy = !owned && gcoinBalance(client) >= offer.price();
			boolean hovered = !owned && contains(mouseX, mouseY, x, y, w, h);
			int accent = 0xFF000000 | offer.skin().color();
			context.fill(x, y, x + w, y + h, SkinShopConfig.panelArgb(hovered ? 0x77 : 0x55));
			context.drawBorder(x, y, w, h, hovered ? accent : 0x7790A0AA);
			context.fill(x + 1, y + h - 5, x + w - 1, y + h - 1, accent);
			boolean tight = h < 96 || "compact".equals(SkinShopConfig.style());
			drawItemPreview(context, previewStack(offer.type(), offer.skin()), x + w / 2,
				y + (tight ? Math.max(18, h / 2 - 4) : Math.max(34, (h - 48) / 2)),
				Math.max(tight ? 1.8F : 2.7F, Math.min(tight ? 3.0F : 5.2F, w / 36.0F)));
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(textRendererTrim(client, offer.skin().displayName(), w - 12)),
				x + w / 2, y + h - (tight ? 28 : 44), 0xFFFFFFFF);
			if (!tight) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal(offer.type().displayName()).formatted(Formatting.GRAY),
					x + w / 2, y + h - 31, 0xFFB8C3CC);
			}
			if (owned) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("OWNED").formatted(Formatting.GREEN), x + w / 2, y + h - 17, 0xFF7EE2A8);
			} else {
				int priceW = client.textRenderer.getWidth(Integer.toString(offer.price())) + 13;
				drawCoinPrice(context, client, offer.price(), x + (w - priceW) / 2, y + h - 18,
					canBuy ? 0xFFFFD56E : 0xFF8A8A8A);
			}
			dailyOfferTiles.add(new DailyOfferTile(x, y, w, h, offer.type(), offer.skin(), !owned));
		}

		private void drawOffersHeader(DrawContext context, MinecraftClient client, int x, int y, int w) {
			String label = "OFFERS  |  " + dailyRotationCountdown();
			int labelW = client.textRenderer.getWidth(label);
			int lineY = y + 7;
			int centerX = x + w / 2;
			context.fill(x, lineY, Math.max(x, centerX - labelW / 2 - 10), lineY + 1, 0x6655606E);
			context.fill(Math.min(x + w, centerX + labelW / 2 + 10), lineY, x + w, lineY + 1, 0x6655606E);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), centerX, y + 3, 0xFFFFFFFF);
		}

		private String dailyRotationCountdown() {
			long remaining = Math.max(0L, SkinDailyShop.nextRotationEpochSecond() - Instant.now().getEpochSecond());
			long hours = remaining / 3600L;
			long minutes = remaining % 3600L / 60L;
			long seconds = remaining % 60L;
			return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
		}

		private int storefrontWidth() {
			int available = Math.max(120, width - 28);
			boolean compactStyle = "compact".equals(SkinShopConfig.style());
			int heightDriven = Math.max(compactStyle ? 480 : 540,
				Math.max(0, height - 40) * (compactStyle ? 2 : 9) / (compactStyle ? 1 : 4));
			return Math.min(available, Math.min(compactStyle ? 840 : 1040, heightDriven));
		}

		private int storefrontX() {
			return getX() + (width - storefrontWidth()) / 2;
		}

		private void renderCaseDetail(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			GexpressConfig.SkinCaseEntry skinCase = selectedCase;
			if (skinCase == null) return;
			int top = getY() + 16;
			drawGcoinBalance(context, client);
			backButton = new BackButton(getX() + 12, top, 52, 18);
			boolean backHovered = contains(mouseX, mouseY, backButton.x(), backButton.y(), backButton.width(), backButton.height());
			context.fill(backButton.x(), backButton.y(), backButton.x() + backButton.width(), backButton.y() + backButton.height(),
				backHovered ? 0x77333333 : 0x55222222);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Back"),
				backButton.x() + backButton.width() / 2, backButton.y() + 5, 0xFFFFFFFF);

			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(skinCase.displayName()),
				getX() + width / 2, top + 4, 0xFFFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal("Owned: " + caseCount(client, skinCase.id())).formatted(Formatting.GOLD),
				getX() + width / 2, top + 20, 0xFFFFD56E);

			int caseY = getY() + Math.max(112, Math.min(156, height / 4 + 18));
			String caseAnimation = Util.getMeasuringTimeMs() - selectedCaseOpenedAt < 950L ? "fall" : "static";
			drawCasePreview(context, getX() + width / 2, caseY, 1.62F, caseAnimation);

			int gridX = getX() + 28;
			int gridY = caseY + 104;
			int gridW = width - 56;
			int gridBottom = getY() + height - 50;
			int cardW = Math.max(96, Math.min(132, gridW / Math.max(1, gridW / 124)));
			int columns = Math.max(1, gridW / (cardW + 10));
			int cardH = 76;
			int rowGap = 10;
			List<CaseDropPreview> drops = visibleDrops(skinCase);
			int rows = (drops.size() + columns - 1) / columns;
			int contentHeight = 24 + Math.max(1, rows) * (cardH + rowGap);
			int viewport = Math.max(0, gridBottom - gridY);
			scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport)));

			context.drawTextWithShadow(client.textRenderer, Text.literal("Possible Drops"),
				gridX, gridY - 18, 0xFFFFFFFF);
			context.enableScissor(gridX, gridY, gridX + gridW, gridBottom);
			int totalWeight = skinCase.totalWeight();
			int x = gridX;
			int y = gridY - scroll;
			for (int i = 0; i < drops.size(); i++) {
				CaseDropPreview reward = drops.get(i);
				if (y + cardH >= gridY && y <= gridBottom) {
					drawCaseDropTile(context, client, reward, totalWeight, x, y, cardW, cardH);
				}
				if ((i + 1) % columns == 0) {
					x = gridX;
					y += cardH + rowGap;
				} else {
					x += cardW + 10;
				}
			}
			context.disableScissor();

			int buttonW = 136;
			int buttonH = 22;
			int buttonX = getX() + width / 2 - buttonW / 2;
			int buttonY = getY() + height - 36;
			boolean hasCase = caseCount(client, skinCase.id()) > 0;
			boolean canBuy = gcoinBalance(client) >= skinCase.price();
			boolean enabled = hasCase || canBuy;
			boolean hovered = enabled && contains(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH);
			String buttonText = hasCase ? "Open Case" : canBuy ? "Buy Case" : "Need " + skinCase.price() + " G'Coins";
			context.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH,
				enabled ? (hovered ? 0xAAE0B65A : 0x88B9893D) : 0x55333333);
			context.drawBorder(buttonX, buttonY, buttonW, buttonH, enabled ? 0xFFFFD56E : 0x66777777);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(buttonText),
				buttonX + buttonW / 2, buttonY + 7, enabled ? 0xFFFFFFFF : 0xFF999999);
			caseOpenButton = new CaseOpenButton(buttonX, buttonY, buttonW, buttonH, skinCase.id(), enabled);
		}

		private void drawCaseDropTile(DrawContext context, MinecraftClient client, CaseDropPreview reward,
				int totalWeight, int x, int y, int w, int h) {
			int color = reward.color();
			context.fill(x, y, x + w, y + h, 0x55272E36);
			context.drawBorder(x, y, w, h, color);
			context.fill(x + 1, y + h - 4, x + w - 1, y + h - 1, color);
			drawRewardPreviewIcon(context, reward.type(), reward.skin(), reward.mysteryKnife(), x + w / 2, y + 29, 2.25F);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(textRendererTrim(client, reward.displayName(), w - 8)),
				x + w / 2, y + h - 28, 0xFFFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(reward.type().displayName() + " - " + chanceText(reward.weight(), totalWeight)).formatted(Formatting.GRAY),
				x + w / 2, y + h - 15, 0xFFB8C3CC);
		}

		private String chanceText(int weight, int totalWeight) {
			if (totalWeight <= 0) return "0.0%";
			int tenths = Math.max(0, weight) * 1000 / totalWeight;
			return (tenths / 10) + "." + (tenths % 10) + "%";
		}

		private List<CaseDropPreview> visibleDrops(GexpressConfig.SkinCaseEntry skinCase) {
			if (skinCase == null || skinCase.rewards().isEmpty()) return List.of();
			List<CaseDropPreview> drops = new ArrayList<>();
			int knifeWeight = 0;
			for (GexpressConfig.SkinCaseReward reward : skinCase.rewards()) {
				if (reward.type() == WeaponSkinType.KNIFE) {
					knifeWeight += Math.max(0, reward.weight());
					continue;
				}
				drops.add(new CaseDropPreview(reward.type(), reward.skin(), Math.max(0, reward.weight()), false));
			}
			if (knifeWeight > 0) {
				drops.add(new CaseDropPreview(WeaponSkinType.KNIFE, WeaponSkin.DEFAULT, knifeWeight, true));
			}
			return drops;
		}

		private void drawSkinTile(DrawContext context, MinecraftClient client, WeaponSkin skin, boolean selected,
				int x, int y, int mouseX, int mouseY) {
			boolean hovered = contains(mouseX, mouseY, x, y, TILE_WIDTH, TILE_HEIGHT);
			context.fill(x, y, x + TILE_WIDTH, y + TILE_HEIGHT, selected ? 0x77404B55 : hovered ? 0x55353D45 : 0x44272E36);
			context.drawBorder(x, y, TILE_WIDTH, TILE_HEIGHT, selected ? 0xFFFFFFFF : 0x7790A0AA);
			drawItemPreview(context, previewStack(selectedType, skin), x + TILE_WIDTH / 2, y + 27, 2.5F);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(skin.displayName()),
				x + TILE_WIDTH / 2, y + TILE_HEIGHT - 18, 0xFFFFFFFF);
			context.fill(x + 8, y + TILE_HEIGHT - 5, x + TILE_WIDTH - 8, y + TILE_HEIGHT - 3, 0xFF000000 | skin.color());
			skinTiles.add(new SkinTile(x, y, TILE_WIDTH, TILE_HEIGHT, selectedType, skin));
		}

		private void drawCaseSectionFrame(DrawContext context, MinecraftClient client, String title, String subtitle,
				int x, int y, int w, int h) {
			int accent = SkinShopConfig.accentArgb();
			context.fill(x, y, x + w, y + h, SkinShopConfig.panelArgb(
				"compact".equals(SkinShopConfig.style()) ? 0x66 : 0x44));
			context.drawBorder(x, y, w, h, withAlpha(accent, 0x88));
			context.fill(x + 1, y + 1, x + w - 1, y + 29, SkinShopConfig.panelArgb(0x77));
			context.fill(x + 1, y + 1, x + 4, y + 29, accent);
			context.drawTextWithShadow(client.textRenderer, Text.literal(title), x + 12, y + 8, 0xFFFFFFFF);
			context.drawTextWithShadow(client.textRenderer, Text.literal(textRendererTrim(client, subtitle, w - 24)).formatted(Formatting.GRAY),
				x + 12, y + 20, 0xFFB8C3CC);
		}

		private List<GexpressConfig.SkinCaseEntry> ownedCaseSlots(MinecraftClient client,
				List<GexpressConfig.SkinCaseEntry> cases) {
			List<GexpressConfig.SkinCaseEntry> slots = new ArrayList<>();
			for (GexpressConfig.SkinCaseEntry skinCase : cases) {
				int count = caseCount(client, skinCase.id());
				for (int i = 0; i < count; i++) {
					slots.add(skinCase);
				}
			}
			return slots;
		}

		private void drawOwnedCases(DrawContext context, MinecraftClient client, List<GexpressConfig.SkinCaseEntry> cases,
				int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
			context.enableScissor(x, clipTop, x + w, clipBottom);
			if (cases.isEmpty()) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("No cases owned").formatted(Formatting.GRAY),
					x + w / 2, Math.max(clipTop + 26, y + 24), 0xFFB8C3CC);
			}
			int columns = Math.max(1, (w - 20) / 112);
			int gap = 8;
			int cardW = Math.max(96, Math.min(132, (w - 20 - (columns - 1) * gap) / columns));
			int cardH = 82;
			for (int i = 0; i < cases.size(); i++) {
				GexpressConfig.SkinCaseEntry skinCase = cases.get(i);
				int col = i % columns;
				int row = i / columns;
				int cardX = x + 10 + col * (cardW + gap);
				int cardY = y + row * (cardH + 10);
				if (cardY + cardH >= clipTop && cardY <= clipBottom) {
					drawOwnedCaseTile(context, client, skinCase, cardX, cardY, cardW, cardH, mouseX, mouseY);
				}
			}
			context.disableScissor();
		}

		private void drawStoreCases(DrawContext context, MinecraftClient client, List<GexpressConfig.SkinCaseEntry> cases,
				int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
			context.enableScissor(x, clipTop, x + w, clipBottom);
			if (cases.isEmpty()) {
				context.drawCenteredTextWithShadow(client.textRenderer,
					Text.literal("No cases configured").formatted(Formatting.GRAY),
					x + w / 2, Math.max(clipTop + 26, y + 24), 0xFFB8C3CC);
			}
			for (GexpressConfig.SkinCaseEntry skinCase : cases) {
				if (y + 74 >= clipTop && y <= clipBottom) {
					drawStoreCaseTile(context, client, skinCase, x + 10, y, w - 20, 74, mouseX, mouseY);
				}
				y += 84;
			}
			context.disableScissor();
		}

		private void drawOwnedCaseTile(DrawContext context, MinecraftClient client, GexpressConfig.SkinCaseEntry skinCase,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			if (y + h < getY() || y > getY() + height) return;
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			context.fill(x, y, x + w, y + h, hovered ? 0x66404850 : 0x44313740);
			context.drawBorder(x, y, w, h, hovered ? 0xFFE0B65A : 0x8890A0AA);
			drawCasePreview(context, x + w / 2, y + 36, hovered ? 0.64F : 0.59F);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(textRendererTrim(client, skinCase.displayName(), w - 12)),
				x + w / 2, y + h - 16, 0xFFFFFFFF);
			caseTiles.add(new CaseTile(x, y, w, h, skinCase.id()));
		}

		private void drawStoreCaseTile(DrawContext context, MinecraftClient client, GexpressConfig.SkinCaseEntry skinCase,
				int x, int y, int w, int h, int mouseX, int mouseY) {
			if (y + h < getY() || y > getY() + height) return;
			boolean hovered = contains(mouseX, mouseY, x, y, w, h);
			int balance = gcoinBalance(client);
			boolean canBuy = balance >= skinCase.price();
			context.fill(x, y, x + w, y + h, hovered ? 0x66353D45 : 0x44313740);
			context.drawBorder(x, y, w, h, hovered ? 0xFFD7E4F0 : 0x7790A0AA);
			drawCasePreview(context, x + 74, y + h / 2 + 7, 0.56F);
			context.drawTextWithShadow(client.textRenderer, Text.literal(textRendererTrim(client, skinCase.displayName(), w - 148)),
				x + 138, y + 18, 0xFFFFFFFF);
			drawCoinPrice(context, client, skinCase.price(), x + 138, y + 36, canBuy ? 0xFFFFD56E : 0xFF777777);
			int buttonW = 52;
			int buttonX = x + w - buttonW - 8;
			int buttonY = y + (h - 20) / 2;
			boolean buttonHovered = contains(mouseX, mouseY, buttonX, buttonY, buttonW, 20);
			context.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 20,
				canBuy ? (buttonHovered ? 0xAAE0B65A : 0x88B9893D) : 0x55333333);
			context.drawBorder(buttonX, buttonY, buttonW, 20, canBuy ? 0xFFFFD56E : 0x66777777);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Buy"),
				buttonX + buttonW / 2, buttonY + 6, canBuy ? 0xFFFFFFFF : 0xFF999999);
			caseTiles.add(new CaseTile(x, y, w, h, skinCase.id()));
			caseBuyTiles.add(new CaseBuyTile(buttonX, buttonY, buttonW, 20, skinCase.id()));
		}

		private void drawCoinPrice(DrawContext context, MinecraftClient client, int price, int x, int y, int color) {
			String text = Integer.toString(price);
			context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, color);
			context.drawTexture(GCOIN_TEXTURE, x + client.textRenderer.getWidth(text) + 4, y - 1, 9, 9,
				0.0F, 0.0F, 9, 9, 9, 9);
		}

		private List<WeaponSkin> unlocked(WeaponSkinType type) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.world == null) return List.of(WeaponSkin.DEFAULT);
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			Set<WeaponSkin> skins = component == null ? Set.of(WeaponSkin.DEFAULT) : component.unlocked(client.player.getUuid(), type);
			return skins.stream()
				.map(skin -> skin.logical(type))
				.distinct()
				.filter(skin -> skin.visibleInPicker(type))
				.sorted(Comparator.comparing((WeaponSkin skin) -> skin != WeaponSkin.DEFAULT)
					.thenComparing(WeaponSkin::displayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		}

		private WeaponSkin equipped(WeaponSkinType type) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.world == null) return WeaponSkin.DEFAULT;
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			return component == null ? WeaponSkin.DEFAULT : component.equipped(client.player.getUuid(), type);
		}

		private boolean isUnlocked(MinecraftClient client, WeaponSkinType type, WeaponSkin skin) {
			if (client == null || client.player == null || client.world == null) return false;
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			return component != null && component.isUnlocked(client.player.getUuid(), type, skin);
		}

		private ItemStack previewStack(WeaponSkinType type, WeaponSkin skin) {
			ItemStack stack = (type == WeaponSkinType.KNIFE ? WatheItems.KNIFE : WatheItems.REVOLVER).getDefaultStack();
			NbtCompound tag = new NbtCompound();
			WeaponSkin preview = skin == null ? WeaponSkin.DEFAULT : skin.logical(type);
			tag.putString(DevWeaponModels.SKIN_PREVIEW_KEY, preview.id());
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
			return stack;
		}

		private void drawItemPreview(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
			context.getMatrices().push();
			context.getMatrices().translate(centerX, centerY, 120.0F);
			context.getMatrices().scale(scale, scale, 1.0F);
			context.drawItem(stack, -8, -8);
			context.getMatrices().pop();
		}

		private void drawRewardPreviewIcon(DrawContext context, WeaponSkinType type, WeaponSkin skin,
				boolean hidden, int centerX, int centerY, float scale) {
			drawItemPreview(context, previewStack(type, skin), centerX, centerY, scale);
			if (!hidden) return;
			context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
				Text.literal("?").formatted(Formatting.DARK_GRAY), centerX, centerY - 4, 0xAA222222);
		}

		private void drawCasePreview(DrawContext context, int centerX, int centerY, float scale) {
			drawCasePreview(context, centerX, centerY, scale, "static");
		}

		private void drawCasePreview(DrawContext context, int centerX, int centerY, float scale, String animation) {
			drawCaseStack(context, GexpressCaseItem.stack(animation), centerX, centerY, scale);
		}

		private void drawCaseStack(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
			drawCaseStack(context, stack, centerX, centerY, scale, 180.0F);
		}

		private void drawCaseStack(DrawContext context, ItemStack stack, int centerX, int centerY, float scale, float z) {
			float itemScale = Math.max(1.0F, scale * 4.85F);
			context.getMatrices().push();
			context.getMatrices().translate(centerX, centerY + Math.round(2.0F * scale), z);
			context.getMatrices().scale(itemScale, itemScale, 1.0F);
			context.drawItem(stack, -8, -8);
			context.getMatrices().pop();
		}

		private void renderOpeningAnimation(DrawContext context, MinecraftClient client) {
			long elapsed = Util.getMeasuringTimeMs() - openingStartedAt;
			if (!openingCommandSent) {
				openingCommandSent = true;
				openCase(openingCase.id());
			}
			long reelStart = CASE_ZOOM_MS + CASE_OPEN_MS;
			long revealStart = reelStart + CASE_REEL_MS;
			long doneAt = revealStart + CASE_REVEAL_MS;
			if (elapsed > doneAt) {
				finishCaseOpening();
				return;
			}

			int screenW = context.getScaledWindowWidth();
			int screenH = context.getScaledWindowHeight();
			context.fill(0, 0, screenW, screenH, 0xB8000000);
			if (elapsed < reelStart) {
				renderCaseZoom(context, client, elapsed, reelStart);
			} else if (elapsed < revealStart) {
				ensureOpeningReel();
				renderCaseReel(context, client, elapsed - reelStart);
			} else {
				renderCaseReveal(context, client);
			}
		}

		private void renderCaseZoom(DrawContext context, MinecraftClient client, long elapsed, long reelStart) {
			int screenW = context.getScaledWindowWidth();
			int screenH = context.getScaledWindowHeight();
			int centerX = screenW / 2;
			int titleY = Math.max(28, screenH / 13);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(openingCase.displayName()),
				centerX, titleY, 0xFFFFFFFF);
			String hint = elapsed < CASE_ZOOM_MS ? "Opening case..." : "Unlocking...";
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(hint).formatted(Formatting.GOLD),
				centerX, titleY + 18, 0xFFFFD56E);
			if (elapsed < CASE_ZOOM_MS) {
				float progress = Math.min(1.0F, elapsed / (float) CASE_ZOOM_MS);
				float eased = easeOutCubic(progress);
				int caseY = Math.round(lerp(screenH / 2.0F + 22.0F, screenH / 2.0F, eased));
				drawCaseStack(context, openingClosedStack, centerX, caseY, lerp(1.42F, 2.12F, eased));
				return;
			}
			drawCaseStack(context, openingOpenStack, centerX, screenH / 2, 2.12F);
		}

		private void renderCaseReel(DrawContext context, MinecraftClient client, long reelElapsed) {
			int screenW = context.getScaledWindowWidth();
			int screenH = context.getScaledWindowHeight();
			int centerX = screenW / 2;
			int titleY = Math.max(28, screenH / 13);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(openingCase.displayName()),
				centerX, titleY, 0xFFFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Opening...").formatted(Formatting.GOLD),
				centerX, titleY + 18, 0xFFFFD56E);
			int reelW = Math.max(280, Math.min(screenW - 220, 1120));
			int reelH = 132;
			int reelX = centerX - reelW / 2;
			int caseY = screenH / 2 + 16;
			int reelY = Math.max(titleY + 118, caseY - reelH / 2);
			drawCaseStack(context, openingOpenedStack, centerX, caseY, 2.04F, 70.0F);

			context.getMatrices().push();
			context.getMatrices().translate(0.0F, 0.0F, 240.0F);
			context.fill(reelX, reelY, reelX + reelW, reelY + reelH, 0x55212A34);
			context.drawBorder(reelX, reelY, reelW, reelH, 0x8890A0AA);
			context.enableScissor(reelX + 1, reelY + 1, reelX + reelW - 1, reelY + reelH - 1);
			int cardW = 116;
			float progress = Math.min(1.0F, reelElapsed / (float) CASE_REEL_MS);
			double ease = 1.0D - Math.pow(1.0D - progress, 4.5D);
			double baseX = reelX + reelW / 2.0D - openingTargetIndex * cardW - cardW / 2.0D
				+ (1.0D - ease) * 3150.0D;
			for (int i = 0; i < openingReel.size(); i++) {
				CaseRewardPreview reward = openingReel.get(i);
				drawRewardCard(context, client, reward, Math.round((float) baseX + i * cardW), reelY + 10, cardW - 8, 104);
			}
			context.disableScissor();
			int center = reelX + reelW / 2;
			context.fill(center - 2, reelY - 6, center + 2, reelY + reelH + 6, 0xFFFFD56E);
			context.getMatrices().pop();
		}

		private void renderCaseReveal(DrawContext context, MinecraftClient client) {
			CaseRewardPreview reward = openingResult != null ? openingResult : fallbackOpeningResult();
			int screenW = context.getScaledWindowWidth();
			int screenH = context.getScaledWindowHeight();
			int centerX = screenW / 2;
			int titleY = Math.max(34, screenH / 12);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Case Opened"),
				centerX, titleY, 0xFFFFFFFF);
			if (reward == null) {
				context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Waiting for reward...").formatted(Formatting.GOLD),
					centerX, screenH / 2, 0xFFFFD56E);
				return;
			}
			if (!openingRevealSoundPlayed) {
				openingRevealSoundPlayed = true;
				playCaseSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7F, 1.18F);
			}
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(reward.skin().displayName()),
				centerX, titleY + 28, 0xFFFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(reward.type().displayName()).formatted(Formatting.GRAY),
				centerX, titleY + 44, 0xFFB8C3CC);
			drawItemPreview(context, previewStack(reward.type(), reward.skin()),
				centerX, screenH / 2, reward.type() == WeaponSkinType.KNIFE ? 8.2F : 8.8F);
			String status = openingNewUnlock
				? "Unlocked"
				: "Duplicate - refunded " + openingDuplicateRefund + " G'Coins";
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(status).formatted(Formatting.GOLD),
				centerX, screenH - 74, 0xFFFFD56E);
		}

		private void ensureOpeningReel() {
			if (!openingReel.isEmpty() || openingCase == null) return;
			List<CaseRewardPreview> reel = new ArrayList<>();
			openingTargetIndex = 42;
			for (int i = 0; i < openingTargetIndex; i++) {
				CaseRewardPreview reward = weightedReelPreview(openingCase);
				if (reward != null) reel.add(reward);
			}
			CaseRewardPreview target = openingResult == null ? weightedReelPreview(openingCase) : openingResult.asMysteryPreview();
			if (target == null) target = new CaseRewardPreview(WeaponSkinType.GUN, WeaponSkin.DEFAULT);
			reel.add(target);
			for (int i = 0; i < 12; i++) {
				CaseRewardPreview reward = weightedReelPreview(openingCase);
				if (reward != null) reel.add(reward);
			}
			openingReel = reel;
		}

		private void acceptCaseResult(SkinCaseResultPayload payload) {
			if (payload == null || openingCase == null || !openingCase.id().equals(payload.caseId())) return;
			WeaponSkinType type = WeaponSkinType.byId(payload.typeId());
			WeaponSkin skin = WeaponSkin.byId(payload.skinId());
			if (type == null || skin == null) return;
			openingResult = new CaseRewardPreview(type, skin);
			openingNewUnlock = payload.newUnlock();
			openingDuplicateRefund = payload.duplicateRefund();
			openingReel = List.of();
		}

		private CaseRewardPreview fallbackOpeningResult() {
			if (openingResult != null) return openingResult;
			if (openingReel.isEmpty() || openingTargetIndex < 0 || openingTargetIndex >= openingReel.size()) return null;
			return openingReel.get(openingTargetIndex);
		}

		private void finishCaseOpening() {
			openingCase = null;
			openingResult = null;
			openingReel = List.of();
			openingClosedStack = ItemStack.EMPTY;
			openingOpenStack = ItemStack.EMPTY;
			openingOpenedStack = ItemStack.EMPTY;
			openingTargetIndex = 0;
			openingCommandSent = false;
			openingNewUnlock = true;
			openingDuplicateRefund = 0;
			openingRevealSoundPlayed = false;
			selectedCase = null;
			showingCases = true;
			scroll = 0;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null && client.currentScreen instanceof CaseOpeningScreen screen && screen.panel == this) {
				client.setScreen(screen.parent);
			}
		}

		private void cancelCaseOpening() {
			openingCase = null;
			openingResult = null;
			openingReel = List.of();
			openingClosedStack = ItemStack.EMPTY;
			openingOpenStack = ItemStack.EMPTY;
			openingOpenedStack = ItemStack.EMPTY;
			openingTargetIndex = 0;
			openingCommandSent = false;
			openingNewUnlock = true;
			openingDuplicateRefund = 0;
			openingRevealSoundPlayed = false;
			selectedCase = null;
			showingCases = true;
			scroll = 0;
		}

		private CaseRewardPreview weightedReelPreview(GexpressConfig.SkinCaseEntry skinCase) {
			GexpressConfig.SkinCaseReward reward = weightedPreview(skinCase);
			if (reward == null) return null;
			return new CaseRewardPreview(reward.type(),
				reward.type() == WeaponSkinType.KNIFE ? WeaponSkin.DEFAULT : reward.skin(),
				reward.type() == WeaponSkinType.KNIFE);
		}

		private float easeOutCubic(float value) {
			float clamped = Math.max(0.0F, Math.min(1.0F, value));
			float inverse = 1.0F - clamped;
			return 1.0F - inverse * inverse * inverse;
		}

		private float lerp(float start, float end, float progress) {
			return start + (end - start) * Math.max(0.0F, Math.min(1.0F, progress));
		}

		private void drawRewardCard(DrawContext context, MinecraftClient client, CaseRewardPreview reward,
				int x, int y, int w, int h) {
			int color = reward.color();
			context.fill(x, y, x + w, y + h, 0x55272E36);
			context.drawBorder(x, y, w, h, color);
			drawRewardPreviewIcon(context, reward.type(), reward.skin(), reward.mysteryKnife(), x + w / 2, y + 28, 2.25F);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(textRendererTrim(client, reward.displayName(), w - 8)),
				x + w / 2, y + h - 26, 0xFFFFFFFF);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(reward.type().displayName()).formatted(Formatting.GRAY),
				x + w / 2, y + h - 13, 0xFFB8C3CC);
		}

		private void drawGcoinBalance(DrawContext context, MinecraftClient client) {
			drawGcoinBalance(context, client, getX() + width);
		}

		private void drawGcoinBalance(DrawContext context, MinecraftClient client, int rightEdge) {
			if (!showGcoin(client) || client.player == null || client.world == null) return;
			GcoinComponent gcoins = GcoinComponent.KEY.getNullable(client.world);
			int balance = gcoins == null ? 0 : gcoins.balance(client.player.getUuid());
			String text = "G'Coin: " + balance;
			int textWidth = client.textRenderer.getWidth(text);
			int x = rightEdge - textWidth - 26;
			int y = getY() + 10;
			context.fill(x - 6, y - 4, x + textWidth + 26, y + 15, 0x55331F08);
			context.drawBorder(x - 6, y - 4, textWidth + 32, 19, 0x88E0B65A);
			context.drawTexture(GCOIN_TEXTURE, x, y - 2, 12, 12, 0.0F, 0.0F, 9, 9, 9, 9);
			context.drawTextWithShadow(client.textRenderer, Text.literal(text), x + 17, y, 0xFFFFD56E);
		}

		private boolean showGcoin(MinecraftClient client) {
			return client != null && client.world != null;
		}

		private int gcoinBalance(MinecraftClient client) {
			if (client == null || client.player == null || client.world == null) return 0;
			GcoinComponent gcoins = GcoinComponent.KEY.getNullable(client.world);
			return gcoins == null ? 0 : gcoins.balance(client.player.getUuid());
		}

		private int ownedCaseTotal(MinecraftClient client) {
			if (client == null || client.player == null || client.world == null) return 0;
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			if (component == null) return 0;
			return component.caseCounts(client.player.getUuid()).values().stream().mapToInt(Integer::intValue).sum();
		}

		private int caseCount(MinecraftClient client, String caseId) {
			if (client == null || client.player == null || client.world == null) return 0;
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			return component == null ? 0 : component.caseCount(client.player.getUuid(), caseId);
		}

		private void startCaseOpening(GexpressConfig.SkinCaseEntry skinCase) {
			if (skinCase == null || skinCase.rewards().isEmpty()) return;
			openingCase = skinCase;
			openingResult = null;
			openingReel = List.of();
			openingClosedStack = GexpressCaseItem.stack("static");
			openingOpenStack = GexpressCaseItem.stack("open");
			openingOpenedStack = GexpressCaseItem.stack("opened");
			openingTargetIndex = 0;
			openingStartedAt = Util.getMeasuringTimeMs();
			openingNewUnlock = false;
			openingDuplicateRefund = 0;
			openingCommandSent = false;
			openingRevealSoundPlayed = false;
			selectedCase = null;
			showingCases = true;
			scroll = 0;
			playCaseSound(SoundEvents.BLOCK_BARREL_OPEN, 0.85F, 0.88F);
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null && !(client.currentScreen instanceof CaseOpeningScreen)) {
				client.setScreen(new CaseOpeningScreen(parentScreen, this));
			}
		}

		private GexpressConfig.SkinCaseReward weightedPreview(GexpressConfig.SkinCaseEntry skinCase) {
			int total = skinCase.totalWeight();
			if (total <= 0) return null;
			int cursor = caseAnimationRandom.nextInt(total);
			for (GexpressConfig.SkinCaseReward reward : skinCase.rewards()) {
				cursor -= Math.max(0, reward.weight());
				if (cursor < 0) return reward;
			}
			return skinCase.rewards().isEmpty() ? null : skinCase.rewards().getLast();
		}

		private void playCaseSound(SoundEvent event, float volume, float pitch) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.getSoundManager() == null) return;
			client.getSoundManager().play(PositionedSoundInstance.master(event, volume, pitch));
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (openingCase != null) return true;
			if (selectedCase == null) {
				for (SkinTab tab : tabs) {
					if (!tab.contains(mouseX, mouseY)) continue;
					showingCases = tab.mode() == SkinTabMode.STORE;
					if (tab.mode() == SkinTabMode.KNIVES) selectedType = WeaponSkinType.KNIFE;
					if (tab.mode() == SkinTabMode.GUNS) selectedType = WeaponSkinType.GUN;
					scroll = 0;
					return true;
				}
			}
			if (backButton != null && backButton.contains(mouseX, mouseY)) {
				if (selectedCase != null) {
					selectedCase = null;
					showingCases = true;
				}
				scroll = 0;
				return true;
			}
			if (selectedCase != null) {
				if (caseOpenButton != null && caseOpenButton.enabled() && caseOpenButton.contains(mouseX, mouseY)) {
					MinecraftClient client = MinecraftClient.getInstance();
					if (caseCount(client, selectedCase.id()) > 0) startCaseOpening(selectedCase);
					else buyCase(selectedCase.id());
					return true;
				}
				return super.mouseClicked(mouseX, mouseY, button);
			}
			for (CaseCarouselButton carouselButton : caseCarouselButtons) {
				if (!carouselButton.contains(mouseX, mouseY)) continue;
				cycleFeaturedCase(carouselButton.direction());
				return true;
			}
			for (CaseCarouselDot dot : caseCarouselDots) {
				if (!dot.contains(mouseX, mouseY)) continue;
				featuredCaseIndex = dot.index();
				return true;
			}
			for (DailyOfferTile tile : dailyOfferTiles) {
				if (!tile.enabled() || !tile.contains(mouseX, mouseY)) continue;
				buyDailyOffer(tile.type(), tile.skin());
				return true;
			}
			for (CaseBuyTile tile : caseBuyTiles) {
				if (!tile.contains(mouseX, mouseY)) continue;
				buyCase(tile.caseId());
				return true;
			}
			for (CaseTile tile : caseTiles) {
				if (!tile.contains(mouseX, mouseY)) continue;
				GexpressConfig.SkinCaseEntry skinCase = GexpressConfig.getSkinCaseEntry(tile.caseId());
				if (skinCase != null) {
					selectedCase = skinCase;
					selectedCaseOpenedAt = Util.getMeasuringTimeMs();
					scroll = 0;
				}
				return true;
			}
			for (SkinTile tile : skinTiles) {
				if (!tile.contains(mouseX, mouseY)) continue;
				equip(tile.type(), tile.skin());
				return true;
			}
			for (TypeBox box : typeBoxes) {
				if (!box.contains(mouseX, mouseY)) continue;
				selectedType = box.type();
				scroll = 0;
				return true;
			}
			for (CaseBox box : caseBoxes) {
				if (!box.contains(mouseX, mouseY)) continue;
				showingCases = true;
				selectedCase = null;
				selectedType = null;
				scroll = 0;
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (mouseX < getX() || mouseX >= getX() + width
					|| mouseY < getY() || mouseY >= getY() + height) {
				return false;
			}
			if (selectedCase == null && showingCases) {
				if (caseCarouselArea != null && caseCarouselArea.contains(mouseX, mouseY)
						&& (verticalAmount != 0.0D || horizontalAmount != 0.0D)) {
					double amount = verticalAmount != 0.0D ? verticalAmount : -horizontalAmount;
					cycleFeaturedCase(amount > 0.0D ? -1 : 1);
				}
				return true;
			}
			scroll = Math.max(0, scroll - (int) Math.round(verticalAmount * 20.0D));
			return true;
		}

		private void cycleFeaturedCase(int direction) {
			List<GexpressConfig.SkinCaseEntry> cases = GexpressConfig.getSkinCaseEntries();
			if (cases.size() < 2) return;
			featuredCaseIndex = Math.floorMod(featuredCaseIndex + direction, cases.size());
		}

		private void equip(WeaponSkinType type, WeaponSkin skin) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			client.player.networkHandler.sendChatCommand("g skins equip " + type.id() + " " + skin.id());
		}

		private void openCase(String caseId) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			client.player.networkHandler.sendChatCommand("g skins case open " + caseId);
		}

		private void buyCase(String caseId) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			client.player.networkHandler.sendChatCommand("g skins case buy " + caseId);
		}

		private void buyDailyOffer(WeaponSkinType type, WeaponSkin skin) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			client.player.networkHandler.sendChatCommand("g skins shop buy " + type.id() + " " + skin.id());
		}

		private String textRendererTrim(MinecraftClient client, String value, int width) {
			return client.textRenderer.trimToWidth(value == null ? "" : value, Math.max(24, width));
		}

		private boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}

		private static int withAlpha(int argb, int alpha) {
			return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}

		private static final class CaseOpeningScreen extends Screen {
			private final Screen parent;
			private final SkinsPanelWidget panel;

			private CaseOpeningScreen(Screen parent, SkinsPanelWidget panel) {
				super(Text.literal("G'Express Case"));
				this.parent = parent;
				this.panel = panel;
			}

			@Override
			public void render(DrawContext context, int mouseX, int mouseY, float delta) {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client == null) return;
				panel.renderOpeningAnimation(context, client);
			}

			@Override
			public boolean shouldPause() {
				return false;
			}

			@Override
			public void close() {
				panel.cancelCaseOpening();
				MinecraftClient client = MinecraftClient.getInstance();
				if (client != null) client.setScreen(parent);
			}
		}
	}

	private record TypeBox(int x, int y, int width, int height, WeaponSkinType type) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseBox(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private enum SkinTabMode { STORE, KNIVES, GUNS }

	private record SkinTab(int x, int y, int width, int height, SkinTabMode mode) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record SkinTile(int x, int y, int width, int height, WeaponSkinType type, WeaponSkin skin) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseTile(int x, int y, int width, int height, String caseId) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseCarouselButton(int x, int y, int width, int height, int direction) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseCarouselDot(int x, int y, int width, int height, int index) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseCarouselArea(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseBuyTile(int x, int y, int width, int height, String caseId) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record DailyOfferTile(int x, int y, int width, int height,
			WeaponSkinType type, WeaponSkin skin, boolean enabled) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseOpenButton(int x, int y, int width, int height, String caseId, boolean enabled) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record CaseDropPreview(WeaponSkinType type, WeaponSkin skin, int weight, boolean mysteryKnife) {
		private String displayName() {
			return mysteryKnife ? "Knife" : skin.displayName();
		}

		private int color() {
			return 0xFF000000 | (mysteryKnife ? 0xE0B65A : skin.color());
		}
	}

	private record CaseRewardPreview(WeaponSkinType type, WeaponSkin skin, boolean mysteryKnife) {
		private CaseRewardPreview(WeaponSkinType type, WeaponSkin skin) {
			this(type, skin, false);
		}

		private CaseRewardPreview asMysteryPreview() {
			return type == WeaponSkinType.KNIFE ? new CaseRewardPreview(type, WeaponSkin.DEFAULT, true) : this;
		}

		private String displayName() {
			return mysteryKnife ? "Knife" : skin.displayName();
		}

		private int color() {
			return 0xFF000000 | (mysteryKnife ? 0xE0B65A : skin.color());
		}
	}

	private record BackButton(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
