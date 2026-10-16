package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.client.role.inventory.RoleSelectionInventoryInput;
import dev.mapselect.client.role.mafia.ClientMafiaState;
import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import dev.mapselect.network.role.mafia.MafiaActionPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterTargetsPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
public abstract class MafiaPuppetInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler>
		implements RoleSelectionInventoryInput {
	@Unique private static final Identifier GEXPRESS$MAFIA_SLOT =
		Identifier.of(MapSelect.MOD_ID, "textures/gui/mafia_slot.png");
	@Unique private static final Identifier GEXPRESS$PUPPET_SLOT =
		Identifier.of(MapSelect.MOD_ID, "textures/gui/puppet_slot.png");
	@Unique private static final int GEXPRESS$SLOT_SIZE = 32;
	@Unique private static final int GEXPRESS$TEXTURE_SIZE = 30;
	@Unique private static final int GEXPRESS$MAFIA_STEP = 60;
	@Unique private static final int GEXPRESS$PUPPET_STEP = 42;
	@Unique private static final int GEXPRESS$MAX_VISIBLE_PUPPETS = 8;

	@Unique private static final List<MafiaRoleChoice> GEXPRESS$MAFIA_ROLES = List.of(
		new MafiaRoleChoice("Mafioso", "Family enforcer", WatheItems.KNIFE.getDefaultStack(),
			0xFFE35252, MafiaActionPayload.RECRUIT_MAFIOSO),
		new MafiaRoleChoice("Janitor", "Cleans bodies and evidence", WatheItems.BODY_BAG.getDefaultStack(),
			0xFF65D69A, MafiaActionPayload.RECRUIT_JANITOR),
		new MafiaRoleChoice("Pickpocket", "Steals coins for the family", Items.GOLD_NUGGET.getDefaultStack(),
			0xFFE6C45B, MafiaActionPayload.RECRUIT_PICKPOCKET),
		new MafiaRoleChoice("Burglar", "Break-in tool specialist", WatheItems.CROWBAR.getDefaultStack(),
			0xFF9DA8B3, MafiaActionPayload.RECRUIT_BURGLAR)
	);

	@Unique private final List<MafiaRoleClickTarget> gexpress$mafiaRoleTargets = new ArrayList<>();
	@Unique private final List<PuppetClickTarget> gexpress$puppetTargets = new ArrayList<>();
	@Unique private final List<ScrollClickTarget> gexpress$puppetScrollTargets = new ArrayList<>();
	@Unique private int gexpress$puppetScrollOffset;
	@Unique private int gexpress$visiblePuppets = 1;

	protected MafiaPuppetInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void gexpress$renderRoleSelection(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		gexpress$mafiaRoleTargets.clear();
		gexpress$puppetTargets.clear();
		gexpress$puppetScrollTargets.clear();
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientMafiaState.shouldShowRecruitInventoryMenu(client)) {
			gexpress$renderMafiaRoles(context, mouseX, mouseY);
		} else if (ClientPuppetmasterState.shouldShowTargetInventoryMenu(client)) {
			gexpress$renderPuppets(context, mouseX, mouseY, client);
		}
	}

	@Unique
	private void gexpress$renderMafiaRoles(DrawContext context, int mouseX, int mouseY) {
		int rowWidth = GEXPRESS$SLOT_SIZE + GEXPRESS$MAFIA_STEP * (GEXPRESS$MAFIA_ROLES.size() - 1);
		int startX = this.width / 2 - rowWidth / 2;
		int slotY = gexpress$slotY();
		String targetName = ClientMafiaState.recruitTargetName();
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal("Recruit " + targetName + " into the family"), this.width / 2,
			slotY - 28, 0xFFE7E7E7);
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal("Choose their Mafia role"), this.width / 2,
			slotY - 15, 0xFF9FA8B1);

		for (int i = 0; i < GEXPRESS$MAFIA_ROLES.size(); i++) {
			MafiaRoleChoice role = GEXPRESS$MAFIA_ROLES.get(i);
			int x = startX + i * GEXPRESS$MAFIA_STEP;
			boolean hovered = gexpress$contains(mouseX, mouseY, x, slotY,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE + 13);
			gexpress$drawSlot(context, GEXPRESS$MAFIA_SLOT, x, slotY);
			context.drawItem(role.icon(), x + 8, slotY + 8);
			if (hovered) {
				context.drawBorder(x - 1, slotY - 1, GEXPRESS$SLOT_SIZE + 2,
					GEXPRESS$SLOT_SIZE + 2, role.color());
				context.drawTooltip(this.textRenderer,
					Text.literal(role.name() + " — " + role.description()), mouseX, mouseY);
			}
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(role.name(), 58)),
				x + GEXPRESS$SLOT_SIZE / 2, slotY + GEXPRESS$SLOT_SIZE + 5,
				hovered ? role.color() : 0xFFE7E7E7);
			gexpress$mafiaRoleTargets.add(new MafiaRoleClickTarget(x, slotY,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE + 13, role.action()));
		}
	}

	@Unique
	private void gexpress$renderPuppets(DrawContext context, int mouseX, int mouseY,
			MinecraftClient client) {
		List<PuppetmasterTargetsPayload.Entry> targets = ClientPuppetmasterState.targetChoices();
		int slotY = gexpress$slotY();
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal("Choose a nearby puppet"), this.width / 2, slotY - 28, 0xFFFF3F91);
		if (targets.isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("No living players are in range"), this.width / 2,
				slotY + 10, 0xFF8B6A78);
			return;
		}

		int widthLimited = Math.max(1, (this.width - 76) / GEXPRESS$PUPPET_STEP);
		gexpress$visiblePuppets = Math.max(1, Math.min(targets.size(),
			Math.min(GEXPRESS$MAX_VISIBLE_PUPPETS, widthLimited)));
		int maximumOffset = Math.max(0, targets.size() - gexpress$visiblePuppets);
		gexpress$puppetScrollOffset = Math.max(0,
			Math.min(maximumOffset, gexpress$puppetScrollOffset));
		int rowWidth = GEXPRESS$SLOT_SIZE + GEXPRESS$PUPPET_STEP * (gexpress$visiblePuppets - 1);
		int startX = this.width / 2 - rowWidth / 2;

		for (int i = 0; i < gexpress$visiblePuppets; i++) {
			PuppetmasterTargetsPayload.Entry target = targets.get(gexpress$puppetScrollOffset + i);
			int x = startX + i * GEXPRESS$PUPPET_STEP;
			boolean hovered = gexpress$contains(mouseX, mouseY, x, slotY,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE + 13);
			gexpress$drawSlot(context, GEXPRESS$PUPPET_SLOT, x, slotY);
			gexpress$drawHead(context, client, target.id(), x + 6, slotY + 6, 20);
			if (hovered) {
				context.drawBorder(x - 1, slotY - 1, GEXPRESS$SLOT_SIZE + 2,
					GEXPRESS$SLOT_SIZE + 2, 0xFFFF5BA5);
				context.drawTooltip(this.textRenderer, Text.literal(target.name()), mouseX, mouseY);
			}
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(target.name(), 40)),
				x + GEXPRESS$SLOT_SIZE / 2, slotY + GEXPRESS$SLOT_SIZE + 5,
				hovered ? 0xFFFF9CC6 : 0xFFFFFFFF);
			gexpress$puppetTargets.add(new PuppetClickTarget(x, slotY,
				GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE + 13, target.id()));
		}

		if (maximumOffset > 0) {
			gexpress$drawScrollArrow(context, mouseX, mouseY, Math.max(2, startX - 24),
				slotY + 7, -1, gexpress$puppetScrollOffset > 0);
			gexpress$drawScrollArrow(context, mouseX, mouseY,
				Math.min(this.width - 20, startX + rowWidth + 6), slotY + 7,
				1, gexpress$puppetScrollOffset < maximumOffset);
		}
	}

	@Unique
	private void gexpress$drawScrollArrow(DrawContext context, int mouseX, int mouseY,
			int x, int y, int direction, boolean enabled) {
		boolean hovered = enabled && gexpress$contains(mouseX, mouseY, x, y, 18, 18);
		context.fill(x, y, x + 18, y + 18, enabled
			? (hovered ? 0xE05B1938 : 0xC0240A18) : 0x80473B40);
		context.drawBorder(x, y, 18, 18, enabled ? 0xFFFF3F91 : 0xFF75656C);
		context.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal(direction < 0 ? "<" : ">"), x + 9, y + 5,
			enabled ? 0xFFFFBAD6 : 0xFF8E7A82);
		if (enabled) gexpress$puppetScrollTargets.add(new ScrollClickTarget(x, y, 18, 18, direction));
	}

	@Unique
	private static void gexpress$drawSlot(DrawContext context, Identifier texture, int x, int y) {
		context.drawTexture(texture, x, y, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE,
			0.0F, 0.0F, GEXPRESS$TEXTURE_SIZE, GEXPRESS$TEXTURE_SIZE,
			GEXPRESS$TEXTURE_SIZE, GEXPRESS$TEXTURE_SIZE);
	}

	@Unique
	private static void gexpress$drawHead(DrawContext context, MinecraftClient client,
			UUID playerId, int x, int y, int size) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		if (client != null && client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	@Unique
	private int gexpress$slotY() {
		return Math.max(52, Math.min(this.height - 52, this.height / 2 + 60));
	}

	@Override
	public boolean gexpress$handleRoleSelectionMouse(double mouseX, double mouseY, int button) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (button != 0) return false;
		if (ClientMafiaState.shouldShowRecruitInventoryMenu(client)) {
			for (MafiaRoleClickTarget target : gexpress$mafiaRoleTargets) {
				if (!target.contains(mouseX, mouseY)) continue;
				ClientMafiaState.recruitAs(target.action());
				return true;
			}
		}
		if (ClientPuppetmasterState.shouldShowTargetInventoryMenu(client)) {
			for (ScrollClickTarget target : gexpress$puppetScrollTargets) {
				if (!target.contains(mouseX, mouseY)) continue;
				gexpress$puppetScrollOffset += target.direction();
				return true;
			}
			for (PuppetClickTarget target : gexpress$puppetTargets) {
				if (!target.contains(mouseX, mouseY)) continue;
				ClientPuppetmasterState.selectTarget(target.playerId());
				return true;
			}
		}
		return false;
	}

	@Unique
	private static boolean gexpress$contains(double mouseX, double mouseY,
			int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Unique private record MafiaRoleChoice(String name, String description, ItemStack icon,
			int color, int action) {}
	@Unique private record MafiaRoleClickTarget(int x, int y, int width, int height, int action) {
		private boolean contains(double mouseX, double mouseY) {
			return gexpress$contains(mouseX, mouseY, x, y, width, height);
		}
	}
	@Unique private record PuppetClickTarget(int x, int y, int width, int height, UUID playerId) {
		private boolean contains(double mouseX, double mouseY) {
			return gexpress$contains(mouseX, mouseY, x, y, width, height);
		}
	}
	@Unique private record ScrollClickTarget(int x, int y, int width, int height, int direction) {
		private boolean contains(double mouseX, double mouseY) {
			return gexpress$contains(mouseX, mouseY, x, y, width, height);
		}
	}
}
