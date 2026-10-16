package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.role.doomsayer.ClientDoomsayerState;
import dev.mapselect.client.role.doomsayer.DoomsayerInventoryInput;
import dev.mapselect.network.role.doomsayer.DoomsayerStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class DoomsayerInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler>
		implements DoomsayerInventoryInput {
	@Unique private static final Identifier GEXPRESS$DOOMSAYER_SLOT =
		Identifier.of(MapSelect.MOD_ID, "textures/gui/doomsayer_slot.png");
	@Unique private static final int GEXPRESS$SLOT_SIZE = 32;
	@Unique private static final int GEXPRESS$SLOT_STEP = 36;
	@Unique private static final int GEXPRESS$MAX_VISIBLE_PLAYERS = 8;
	@Unique private static final int GEXPRESS$MAX_SUGGESTIONS = 7;

	@Unique private final List<DoomsayerPlayerClickTarget> gexpress$doomsayerPlayers = new ArrayList<>();
	@Unique private final List<DoomsayerRoleClickTarget> gexpress$doomsayerRoles = new ArrayList<>();
	@Unique private final List<DoomsayerScrollClickTarget> gexpress$doomsayerScrollButtons = new ArrayList<>();
	@Unique private TextFieldWidget gexpress$doomsayerInput;
	@Unique private UUID gexpress$doomsayerSelectedPlayer;
	@Unique private int gexpress$doomsayerScrollOffset;
	@Unique private int gexpress$doomsayerVisiblePlayers = 1;

	protected DoomsayerInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "init", at = @At("TAIL"), remap = false)
	private void gexpress$initDoomsayerMenu(CallbackInfo ci) {
		gexpress$ensureDoomsayerInput();
	}

	@Unique
	private void gexpress$ensureDoomsayerInput() {
		if (gexpress$doomsayerInput != null
				|| !ClientDoomsayerState.shouldShowInventoryMenu(MinecraftClient.getInstance())) return;
		int y = gexpress$roleInputY();
		gexpress$doomsayerInput = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, y,
			200, 18, Text.translatable("gui.gexpress.doomsayer.role_input"));
		gexpress$doomsayerInput.setMaxLength(64);
		gexpress$doomsayerInput.setPlaceholder(Text.translatable("gui.gexpress.doomsayer.role_input"));
		gexpress$doomsayerInput.visible = false;
		addDrawableChild(gexpress$doomsayerInput);
	}

	@Inject(method = "render", at = @At("HEAD"), remap = false)
	private void gexpress$prepareDoomsayerMenu(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		gexpress$ensureDoomsayerInput();
		boolean visible = ClientDoomsayerState.shouldShowInventoryMenu(MinecraftClient.getInstance())
			&& gexpress$doomsayerSelectedPlayer != null
			&& ClientDoomsayerState.target(gexpress$doomsayerSelectedPlayer) != null;
		if (gexpress$doomsayerSelectedPlayer != null && !visible) {
			gexpress$doomsayerSelectedPlayer = null;
		}
		if (gexpress$doomsayerInput != null) {
			gexpress$doomsayerInput.visible = visible;
			if (!visible) gexpress$doomsayerInput.setFocused(false);
		}
	}

	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void gexpress$renderDoomsayerMenu(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		gexpress$doomsayerPlayers.clear();
		gexpress$doomsayerRoles.clear();
		gexpress$doomsayerScrollButtons.clear();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientDoomsayerState.shouldShowInventoryMenu(client)) return;
		if (gexpress$doomsayerSelectedPlayer == null) {
			gexpress$renderPlayerChoices(context, mouseX, mouseY, client);
		} else {
			gexpress$renderRoleChoices(context, mouseX, mouseY, client);
		}
	}

	@Unique
	private void gexpress$renderPlayerChoices(DrawContext context, int mouseX, int mouseY,
			MinecraftClient client) {
		List<DoomsayerStatePayload.Target> targets = ClientDoomsayerState.targets();
		if (targets.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("gui.gexpress.doomsayer.no_targets"), this.width / 2,
				Math.min(this.height - 40, this.height / 2 + 64), 0xFF8FA79A);
			return;
		}
		int widthLimitedSlots = Math.max(1, (this.width - 76) / GEXPRESS$SLOT_STEP);
		gexpress$doomsayerVisiblePlayers = Math.max(1, Math.min(targets.size(),
			Math.min(GEXPRESS$MAX_VISIBLE_PLAYERS, widthLimitedSlots)));
		int maximumOffset = Math.max(0, targets.size() - gexpress$doomsayerVisiblePlayers);
		gexpress$doomsayerScrollOffset = Math.max(0,
			Math.min(maximumOffset, gexpress$doomsayerScrollOffset));
		int rowWidth = gexpress$doomsayerVisiblePlayers * GEXPRESS$SLOT_STEP
			- (GEXPRESS$SLOT_STEP - GEXPRESS$SLOT_SIZE);
		int startX = this.width / 2 - rowWidth / 2;
		int startY = Math.max(52, Math.min(this.height - 48, this.height / 2 + 60));
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.translatable("gui.gexpress.doomsayer.choose_player"), this.width / 2,
			startY - 28, 0xFF39F29B);
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.translatable("gui.gexpress.doomsayer.player_range",
				gexpress$doomsayerScrollOffset + 1,
				gexpress$doomsayerScrollOffset + gexpress$doomsayerVisiblePlayers,
				targets.size()), this.width / 2, startY - 15, 0xFF91A89C);

		for (int i = 0; i < gexpress$doomsayerVisiblePlayers; i++) {
			DoomsayerStatePayload.Target target = targets.get(gexpress$doomsayerScrollOffset + i);
			int x = startX + i * GEXPRESS$SLOT_STEP;
			int y = startY;
			boolean hovered = mouseX >= x && mouseX < x + GEXPRESS$SLOT_SIZE
				&& mouseY >= y && mouseY < y + GEXPRESS$SLOT_SIZE;
			context.drawTexture(GEXPRESS$DOOMSAYER_SLOT, x, y, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE,
				0.0F, 0.0F, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE);
			gexpress$drawHead(context, client, target.playerId(), x + 8, y + 8);
			if (hovered) {
				context.drawBorder(x - 1, y - 1, GEXPRESS$SLOT_SIZE + 2, GEXPRESS$SLOT_SIZE + 2,
					0xFF83FFC0);
				context.drawTooltip(this.textRenderer, Text.literal(target.name()), mouseX, mouseY);
			}
			gexpress$doomsayerPlayers.add(new DoomsayerPlayerClickTarget(x, y,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE, target.playerId()));
		}

		if (maximumOffset > 0) {
			gexpress$drawScrollArrow(context, mouseX, mouseY, Math.max(2, startX - 24), startY + 7,
				-1, gexpress$doomsayerScrollOffset > 0);
			gexpress$drawScrollArrow(context, mouseX, mouseY,
				Math.min(this.width - 20, startX + rowWidth + 6), startY + 7,
				1, gexpress$doomsayerScrollOffset < maximumOffset);
			int trackY = startY + GEXPRESS$SLOT_SIZE + 5;
			context.fill(startX, trackY, startX + rowWidth, trackY + 3, 0xB0193528);
			int thumbWidth = Math.max(12,
				Math.round(rowWidth * (gexpress$doomsayerVisiblePlayers / (float) targets.size())));
			int travel = Math.max(0, rowWidth - thumbWidth);
			int thumbX = startX + (maximumOffset == 0 ? 0
				: Math.round(travel * (gexpress$doomsayerScrollOffset / (float) maximumOffset)));
			context.fill(thumbX, trackY, thumbX + thumbWidth, trackY + 3, 0xFF39F29B);
		}
	}

	@Unique
	private void gexpress$drawScrollArrow(DrawContext context, int mouseX, int mouseY, int x, int y,
			int direction, boolean enabled) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
		context.fill(x, y, x + 18, y + 18, enabled
			? (hovered ? 0xE02D5E47 : 0xC012211B) : 0x80404A44);
		context.drawBorder(x, y, 18, 18, enabled ? 0xFF39F29B : 0xFF68736D);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(direction < 0 ? "<" : ">"),
			x + 9, y + 5, enabled ? 0xFFB8FFD8 : 0xFF7B8780);
		if (enabled) {
			gexpress$doomsayerScrollButtons.add(new DoomsayerScrollClickTarget(x, y, 18, 18, direction));
		}
	}

	@Unique
	private void gexpress$renderRoleChoices(DrawContext context, int mouseX, int mouseY,
			MinecraftClient client) {
		DoomsayerStatePayload.Target target = ClientDoomsayerState.target(gexpress$doomsayerSelectedPlayer);
		if (target == null || gexpress$doomsayerInput == null) return;
		int inputY = gexpress$roleInputY();
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.translatable("gui.gexpress.doomsayer.choose_role", target.name()),
			this.width / 2, inputY - 22, 0xFF39F29B);

		List<Role> suggestions = gexpress$suggestions(gexpress$doomsayerInput.getText());
		if (!suggestions.isEmpty()) {
			String typed = gexpress$doomsayerInput.getText();
			String first = suggestions.getFirst().identifier().getPath();
			gexpress$doomsayerInput.setSuggestion(first.length() >= typed.length()
				? first.substring(typed.length()) : "");
		} else {
			gexpress$doomsayerInput.setSuggestion("");
		}

		int listX = this.width / 2 - 100;
		int listY = inputY + 20;
		for (int i = 0; i < suggestions.size(); i++) {
			Role role = suggestions.get(i);
			int y = listY + i * 12;
			boolean hovered = mouseX >= listX && mouseX < listX + 200
				&& mouseY >= y && mouseY < y + 12;
			context.fill(listX, y, listX + 200, y + 12, hovered ? 0xD02D5E47 : 0xC012211B);
			context.drawTextWithShadow(this.textRenderer, role.identifier().getPath(), listX + 5, y + 2,
				0xFF000000 | role.color());
			gexpress$doomsayerRoles.add(new DoomsayerRoleClickTarget(listX, y, 200, 12, role));
		}
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.translatable("gui.gexpress.doomsayer.controls"), this.width / 2,
			Math.min(this.height - 12, listY + suggestions.size() * 12 + 5), 0xFF91A89C);
	}

	@Override
	public boolean gexpress$handleDoomsayerMouse(double mouseX, double mouseY, int button) {
		if (ClientDoomsayerState.shouldShowInventoryMenu(MinecraftClient.getInstance()) && button == 0) {
			for (DoomsayerScrollClickTarget target : gexpress$doomsayerScrollButtons) {
				if (!target.contains(mouseX, mouseY)) continue;
				gexpress$scrollPlayers(target.direction());
				return true;
			}
			for (DoomsayerRoleClickTarget target : gexpress$doomsayerRoles) {
				if (!target.contains(mouseX, mouseY)) continue;
				gexpress$submitRole(target.role());
				return true;
			}
			for (DoomsayerPlayerClickTarget target : gexpress$doomsayerPlayers) {
				if (!target.contains(mouseX, mouseY)) continue;
				gexpress$doomsayerSelectedPlayer = target.playerId();
				gexpress$ensureDoomsayerInput();
				if (gexpress$doomsayerInput != null) {
					gexpress$doomsayerInput.setText("");
					gexpress$doomsayerInput.visible = true;
					gexpress$doomsayerInput.setFocused(true);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
			double verticalAmount) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientDoomsayerState.shouldShowInventoryMenu(client)
				&& gexpress$doomsayerSelectedPlayer == null
				&& ClientDoomsayerState.targets().size() > gexpress$doomsayerVisiblePlayers) {
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
			ClientDoomsayerState.targets().size() - Math.max(1, gexpress$doomsayerVisiblePlayers));
		gexpress$doomsayerScrollOffset = Math.max(0,
			Math.min(maximumOffset, gexpress$doomsayerScrollOffset + Integer.signum(direction)));
	}

	@Override
	public boolean gexpress$handleDoomsayerKey(int keyCode, int scanCode, int modifiers) {
		if (ClientDoomsayerState.shouldShowInventoryMenu(MinecraftClient.getInstance())
				&& gexpress$doomsayerSelectedPlayer != null && gexpress$doomsayerInput != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				gexpress$doomsayerSelectedPlayer = null;
				gexpress$doomsayerInput.setText("");
				gexpress$doomsayerInput.setFocused(false);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				Role match = gexpress$bestMatch(gexpress$doomsayerInput.getText());
				if (match != null) gexpress$submitRole(match);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_TAB) {
				Role match = gexpress$bestMatch(gexpress$doomsayerInput.getText());
				if (match != null) gexpress$doomsayerInput.setText(match.identifier().getPath());
				return true;
			}
			if (gexpress$doomsayerInput.isFocused()) {
				gexpress$doomsayerInput.keyPressed(keyCode, scanCode, modifiers);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (gexpress$handleDoomsayerKey(keyCode, scanCode, modifiers)) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Unique
	private void gexpress$submitRole(Role role) {
		if (role == null || role.identifier() == null || gexpress$doomsayerSelectedPlayer == null) return;
		ClientDoomsayerState.submitGuess(gexpress$doomsayerSelectedPlayer, role.identifier().getPath());
		this.close();
	}

	@Unique
	private List<Role> gexpress$suggestions(String raw) {
		String query = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
		return ClientDoomsayerState.guessableRoles().stream()
			.filter(role -> query.isEmpty() || role.identifier().getPath().toLowerCase(Locale.ROOT).startsWith(query))
			.limit(GEXPRESS$MAX_SUGGESTIONS)
			.toList();
	}

	@Unique
	private Role gexpress$bestMatch(String raw) {
		String query = raw == null ? "" : raw.strip();
		if (query.isEmpty()) return null;
		List<Role> suggestions = gexpress$suggestions(query);
		for (Role role : suggestions) {
			if (role.identifier().getPath().equalsIgnoreCase(query)) return role;
		}
		return suggestions.isEmpty() ? null : suggestions.getFirst();
	}

	@Unique
	private int gexpress$roleInputY() {
		return Math.max(34, Math.min(this.height - 116, this.height / 2 + 54));
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

	private record DoomsayerPlayerClickTarget(int x, int y, int width, int height, UUID playerId) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record DoomsayerRoleClickTarget(int x, int y, int width, int height, Role role) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private record DoomsayerScrollClickTarget(int x, int y, int width, int height, int direction) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
