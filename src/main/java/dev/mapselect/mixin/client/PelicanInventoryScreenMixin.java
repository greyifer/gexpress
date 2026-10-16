package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.client.role.pelican.PelicanInventoryInput;
import dev.mapselect.network.role.pelican.VultureProgressPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class PelicanInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler>
		implements PelicanInventoryInput {
	@Unique private static final Identifier GEXPRESS$PELICAN_SLOT =
		Identifier.of(MapSelect.MOD_ID, "textures/gui/pelican_slot.png");
	@Unique private static final int GEXPRESS$TEXTURE_SIZE = 30;
	@Unique private static final int GEXPRESS$SLOT_SIZE = 32;
	@Unique private static final int GEXPRESS$SLOT_STEP = 40;
	@Unique private static final int GEXPRESS$MAX_VISIBLE_PLAYERS = 8;
	@Unique private final List<PelicanDigestClickTarget> gexpress$pelicanTargets = new ArrayList<>();
	@Unique private final List<PelicanScrollClickTarget> gexpress$pelicanScrollButtons = new ArrayList<>();
	@Unique private int gexpress$pelicanScrollOffset;
	@Unique private int gexpress$pelicanVisiblePlayers = 1;

	protected PelicanInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void gexpress$renderPelicanMenu(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		gexpress$pelicanTargets.clear();
		gexpress$pelicanScrollButtons.clear();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientVultureState.shouldShowInventoryMenu(client)) return;

		List<VultureProgressPayload.BellyEntry> belly = ClientVultureState.bellyEntries();
		if (belly.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("gui.gexpress.pelican.no_targets"), this.width / 2,
				Math.min(this.height - 40, this.height / 2 + 64), 0xFFA8B58B);
			return;
		}

		int widthLimitedSlots = Math.max(1, (this.width - 76) / GEXPRESS$SLOT_STEP);
		gexpress$pelicanVisiblePlayers = Math.max(1, Math.min(belly.size(),
			Math.min(GEXPRESS$MAX_VISIBLE_PLAYERS, widthLimitedSlots)));
		int maximumOffset = Math.max(0, belly.size() - gexpress$pelicanVisiblePlayers);
		gexpress$pelicanScrollOffset = Math.max(0,
			Math.min(maximumOffset, gexpress$pelicanScrollOffset));
		int rowWidth = gexpress$pelicanVisiblePlayers * GEXPRESS$SLOT_STEP
			- (GEXPRESS$SLOT_STEP - GEXPRESS$SLOT_SIZE);
		int startX = this.width / 2 - rowWidth / 2;
		int startY = Math.max(48, Math.min(this.height - 62, this.height / 2 + 54));
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.translatable("gui.gexpress.pelican.digest_title"), this.width / 2,
			startY - 29, 0xFFC5DF5C);

		int cooldownTicks = ClientVultureState.digestCooldownTicks();
		Text status = cooldownTicks > 0
			? Text.translatable("gui.gexpress.pelican.digest_cooldown", (cooldownTicks + 19) / 20)
			: Text.translatable("gui.gexpress.pelican.digest_hint");
		context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2,
			startY - 16, cooldownTicks > 0 ? 0xFFFFA060 : 0xFFA8B58B);

		for (int i = 0; i < gexpress$pelicanVisiblePlayers; i++) {
			VultureProgressPayload.BellyEntry entry = belly.get(gexpress$pelicanScrollOffset + i);
			int x = startX + i * GEXPRESS$SLOT_STEP;
			int y = startY;
			boolean hovered = mouseX >= x && mouseX < x + GEXPRESS$SLOT_SIZE
				&& mouseY >= y && mouseY < y + GEXPRESS$SLOT_SIZE + 14;
			boolean ready = entry.digestionPercent() >= 100;

			context.drawTexture(GEXPRESS$PELICAN_SLOT, x, y, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE,
				0.0F, 0.0F, GEXPRESS$TEXTURE_SIZE, GEXPRESS$TEXTURE_SIZE,
				GEXPRESS$TEXTURE_SIZE, GEXPRESS$TEXTURE_SIZE);
			gexpress$drawHead(context, client, entry.playerId(), x + 8, y + 8);
			int barWidth = Math.round(GEXPRESS$SLOT_SIZE * (entry.digestionPercent() / 100.0F));
			context.fill(x, y + GEXPRESS$SLOT_SIZE + 1, x + GEXPRESS$SLOT_SIZE,
				y + GEXPRESS$SLOT_SIZE + 3, 0xB027301B);
			if (barWidth > 0) {
				context.fill(x, y + GEXPRESS$SLOT_SIZE + 1, x + barWidth,
					y + GEXPRESS$SLOT_SIZE + 3, ready ? 0xFFB8F05C : 0xFF718C2F);
			}
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal(entry.digestionPercent() + "%"), x + GEXPRESS$SLOT_SIZE / 2,
				y + GEXPRESS$SLOT_SIZE + 5, ready ? 0xFFD9FF91 : 0xFFBAC49E);

			if (ready) {
				context.drawBorder(x - 2, y - 2, GEXPRESS$SLOT_SIZE + 4,
					GEXPRESS$SLOT_SIZE + 4, cooldownTicks <= 0 ? 0xFFD9FF65 : 0xFFFFA060);
			} else if (hovered) {
				context.drawBorder(x - 1, y - 1, GEXPRESS$SLOT_SIZE + 2,
					GEXPRESS$SLOT_SIZE + 2, 0xFFA9C45B);
			}
			if (hovered) {
				Text tooltip = ready && cooldownTicks <= 0
					? Text.translatable("gui.gexpress.pelican.digest_ready", entry.name())
					: Text.translatable("gui.gexpress.pelican.digest_progress", entry.name(),
						entry.digestionPercent());
				context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
			}
			gexpress$pelicanTargets.add(new PelicanDigestClickTarget(x, y,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE + 14, entry.playerId()));
		}

		if (maximumOffset > 0) {
			gexpress$drawScrollArrow(context, mouseX, mouseY, Math.max(2, startX - 24), startY + 7,
				-1, gexpress$pelicanScrollOffset > 0);
			gexpress$drawScrollArrow(context, mouseX, mouseY,
				Math.min(this.width - 20, startX + rowWidth + 6), startY + 7,
				1, gexpress$pelicanScrollOffset < maximumOffset);
		}
	}

	@Unique
	private void gexpress$drawScrollArrow(DrawContext context, int mouseX, int mouseY, int x, int y,
			int direction, boolean enabled) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
		context.fill(x, y, x + 18, y + 18, enabled
			? (hovered ? 0xE0526428 : 0xC01B2411) : 0x804A4E40);
		context.drawBorder(x, y, 18, 18, enabled ? 0xFFC5DF5C : 0xFF747A68);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(direction < 0 ? "<" : ">"),
			x + 9, y + 5, enabled ? 0xFFE5FFAD : 0xFF848978);
		if (enabled) {
			gexpress$pelicanScrollButtons.add(new PelicanScrollClickTarget(x, y, 18, 18, direction));
		}
	}

	@Override
	public boolean gexpress$handlePelicanMouse(double mouseX, double mouseY, int button) {
		if (!ClientVultureState.shouldShowInventoryMenu(MinecraftClient.getInstance()) || button != 0) {
			return false;
		}
		for (PelicanScrollClickTarget target : gexpress$pelicanScrollButtons) {
			if (!target.contains(mouseX, mouseY)) continue;
			gexpress$scrollPlayers(target.direction());
			return true;
		}
		for (PelicanDigestClickTarget target : gexpress$pelicanTargets) {
			if (!target.contains(mouseX, mouseY)) continue;
			ClientVultureState.requestDigest(target.playerId());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
			double verticalAmount) {
		if (ClientVultureState.shouldShowInventoryMenu(MinecraftClient.getInstance())
				&& ClientVultureState.bellyEntries().size() > gexpress$pelicanVisiblePlayers) {
			double amount = Math.abs(horizontalAmount) > 0.001D ? horizontalAmount : verticalAmount;
			if (Math.abs(amount) > 0.001D) {
				gexpress$scrollPlayers(amount > 0.0D ? -1 : 1);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Unique
	private void gexpress$scrollPlayers(int direction) {
		int maximumOffset = Math.max(0,
			ClientVultureState.bellyEntries().size() - Math.max(1, gexpress$pelicanVisiblePlayers));
		gexpress$pelicanScrollOffset = Math.max(0,
			Math.min(maximumOffset, gexpress$pelicanScrollOffset + Integer.signum(direction)));
	}

	@Unique
	private static void gexpress$drawHead(DrawContext context, MinecraftClient client, UUID playerId,
			int x, int y) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		if (client != null && client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		PlayerSkinDrawer.draw(context, texture, x, y, 16);
	}

	private record PelicanDigestClickTarget(int x, int y, int width, int height, UUID playerId) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record PelicanScrollClickTarget(int x, int y, int width, int height, int direction) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
