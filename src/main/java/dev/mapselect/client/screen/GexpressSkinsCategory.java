package dev.mapselect.client.screen;

import com.google.common.collect.ImmutableList;
import dev.doctor4t.wathe.index.WatheItems;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.CustomTabProvider;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import dev.mapselect.client.DevWeaponModels;
import dev.mapselect.skin.PlayerSkinComponent;
import dev.mapselect.skin.WeaponSkin;
import dev.mapselect.skin.WeaponSkinType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class GexpressSkinsCategory {
	private GexpressSkinsCategory() {}

	public static ConfigCategory build(Screen parent) {
		return new SkinsCategory();
	}

	private static final class SkinsCategory implements ConfigCategory, CustomTabProvider {
		private final Text name = Text.translatable("gui.gexpress.config.category.skins");
		private final Text tooltip = Text.translatable("gui.gexpress.config.category.skins.tooltip");

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
			return new SkinsTab(screen, tabArea, tooltip);
		}
	}

	private static final class SkinsTab implements TabExt {
		private final SkinsPanelWidget panel;
		private final ButtonWidget doneButton;
		private final Tooltip tooltip;

		private SkinsTab(YACLScreen screen, ScreenRect tabArea, Text tooltipText) {
			this.panel = new SkinsPanelWidget(tabArea.getLeft(), tabArea.getTop(), tabArea.width(), tabArea.height());
			this.doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> screen.finishOrSave())
				.size(Math.max(90, screen.width / 6), 20)
				.build();
			this.tooltip = Tooltip.of(tooltipText);
			refreshGrid(tabArea);
		}

		@Override
		public Text getTitle() {
			return Text.translatable("gui.gexpress.config.category.skins");
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

	private static final class SkinsPanelWidget extends ClickableWidget {
		private static final int TILE_WIDTH = 116;
		private static final int TILE_HEIGHT = 72;
		private static final int TILE_GAP = 8;
		private final List<TypeBox> typeBoxes = new ArrayList<>();
		private final List<SkinTile> skinTiles = new ArrayList<>();
		private BackButton backButton;
		private WeaponSkinType selectedType;
		private int scroll;

		private SkinsPanelWidget(int x, int y, int width, int height) {
			super(x, y, width, height, Text.translatable("gui.gexpress.config.category.skins"));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.textRenderer == null) return;
			typeBoxes.clear();
			skinTiles.clear();
			backButton = null;

			if (selectedType == null) {
				renderOverview(context, client, mouseX, mouseY);
			} else {
				renderPicker(context, client, mouseX, mouseY);
			}
		}

		private void renderOverview(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			int top = getY() + 24;
			int available = width - 36;
			boolean stacked = available < 360;
			int boxWidth = stacked ? available : (available - 14) / 2;
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

		private void renderPicker(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
			int top = getY() + 16;
			backButton = new BackButton(getX() + 12, top, 52, 18);
			boolean backHovered = contains(mouseX, mouseY, backButton.x(), backButton.y(), backButton.width(), backButton.height());
			context.fill(backButton.x(), backButton.y(), backButton.x() + backButton.width(), backButton.y() + backButton.height(),
				backHovered ? 0x77333333 : 0x55222222);
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Back"),
				backButton.x() + backButton.width() / 2, backButton.y() + 5, 0xFFFFFFFF);

			WeaponSkin equipped = equipped(selectedType);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(selectedType.displayName() + " Skins"),
				getX() + width / 2, top + 4, 0xFFFFFFFF);

			int listX = getX() + 14;
			int listY = getY() + 46;
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

		private List<WeaponSkin> unlocked(WeaponSkinType type) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.world == null) return List.of(WeaponSkin.DEFAULT);
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			Set<WeaponSkin> skins = component == null ? Set.of(WeaponSkin.DEFAULT) : component.unlocked(client.player.getUuid(), type);
			return skins.stream()
				.sorted(Comparator.comparingInt(Enum::ordinal))
				.toList();
		}

		private WeaponSkin equipped(WeaponSkinType type) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.world == null) return WeaponSkin.DEFAULT;
			PlayerSkinComponent component = PlayerSkinComponent.KEY.getNullable(client.world);
			return component == null ? WeaponSkin.DEFAULT : component.equipped(client.player.getUuid(), type);
		}

		private ItemStack previewStack(WeaponSkinType type, WeaponSkin skin) {
			ItemStack stack = (type == WeaponSkinType.KNIFE ? WatheItems.KNIFE : WatheItems.REVOLVER).getDefaultStack();
			if (skin != null && skin != WeaponSkin.DEFAULT) {
				NbtCompound tag = new NbtCompound();
				tag.putString(DevWeaponModels.SKIN_PREVIEW_KEY, skin.id());
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
			}
			return stack;
		}

		private void drawItemPreview(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
			context.getMatrices().push();
			context.getMatrices().translate(centerX, centerY, 120.0F);
			context.getMatrices().scale(scale, scale, 1.0F);
			context.drawItem(stack, -8, -8);
			context.getMatrices().pop();
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (backButton != null && backButton.contains(mouseX, mouseY)) {
				selectedType = null;
				scroll = 0;
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
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			if (selectedType == null || mouseX < getX() || mouseX >= getX() + width
					|| mouseY < getY() || mouseY >= getY() + height) {
				return false;
			}
			scroll = Math.max(0, scroll - (int) Math.round(verticalAmount * 20.0D));
			return true;
		}

		private void equip(WeaponSkinType type, WeaponSkin skin) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.player == null || client.player.networkHandler == null) return;
			client.player.networkHandler.sendChatCommand("g group skins equip " + type.id() + " " + skin.id());
		}

		private boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	private record TypeBox(int x, int y, int width, int height, WeaponSkinType type) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record SkinTile(int x, int y, int width, int height, WeaponSkinType type, WeaponSkin skin) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record BackButton(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
