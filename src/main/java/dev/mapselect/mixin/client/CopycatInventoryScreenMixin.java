package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.mapselect.MapSelect;
import dev.mapselect.client.ClientCopycatState;
import dev.mapselect.network.CopycatStatePayload.StoredAbility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
import java.util.Locale;
import java.util.UUID;

@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class CopycatInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
	@Unique private static final Identifier GEXPRESS$COPYCAT_SLOT =
		Identifier.of(MapSelect.MOD_ID, "textures/gui/copycat_slot.png");
	@Unique private static final int GEXPRESS$SLOT_SIZE = 32;
	@Unique private static final int GEXPRESS$SLOT_GAP = 28;
	@Unique private final List<CopycatClickTarget> gexpress$copycatClickTargets = new ArrayList<>();

	protected CopycatInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Inject(method = "render", at = @At("TAIL"), remap = false)
	private void gexpress$renderCopycatMenu(DrawContext context, int mouseX, int mouseY, float delta,
			CallbackInfo ci) {
		gexpress$copycatClickTargets.clear();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ClientCopycatState.shouldShowInventoryMenu(client)) return;

		List<StoredAbility> stored = ClientCopycatState.storedAbilities();
		int selected = ClientCopycatState.selectedIndex();
		int totalW = GEXPRESS$SLOT_SIZE * 3 + GEXPRESS$SLOT_GAP * 2;
		int startX = this.width / 2 - totalW / 2;
		int slotY = Math.max(70, Math.min(this.height - 82, this.height - 176));
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select a copied ability."),
			this.width / 2, slotY - 24, 0xFFC9B5FF);

		for (int i = 0; i < 3; i++) {
			StoredAbility ability = i < stored.size() ? stored.get(i) : null;
			int slotX = startX + i * (GEXPRESS$SLOT_SIZE + GEXPRESS$SLOT_GAP);
			boolean hovered = mouseX >= slotX - 6 && mouseX < slotX + GEXPRESS$SLOT_SIZE + 6
				&& mouseY >= slotY - 6 && mouseY < slotY + GEXPRESS$SLOT_SIZE + 40;
			boolean active = ability != null && i == selected;
			context.drawTexture(GEXPRESS$COPYCAT_SLOT, slotX, slotY, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE,
				0.0F, 0.0F, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE, GEXPRESS$SLOT_SIZE);
			if (hovered) context.drawBorder(slotX - 2, slotY - 2, GEXPRESS$SLOT_SIZE + 4, GEXPRESS$SLOT_SIZE + 4, 0xFFC9B5FF);
			if (active) context.drawBorder(slotX - 3, slotY - 3, GEXPRESS$SLOT_SIZE + 6, GEXPRESS$SLOT_SIZE + 6, 0xFFFFD869);
			if (ability != null) {
				gexpress$drawHead(context, ability.sourceId(), slotX + 5, slotY + 5, 22);
				gexpress$copycatClickTargets.add(CopycatClickTarget.stored(slotX - 6, slotY - 6,
					GEXPRESS$SLOT_SIZE + 12, GEXPRESS$SLOT_SIZE + 40, i));
			}
			String source = ability == null ? "Empty" : ability.sourceName();
			String role = ability == null ? "" : gexpress$roleName(ability.roleId());
			context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(source, 72)), slotX + GEXPRESS$SLOT_SIZE / 2,
				slotY + GEXPRESS$SLOT_SIZE + 8, ability == null ? 0xFF7F6B91 : 0xFFFFFFFF);
			if (ability != null) {
				context.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal(this.textRenderer.trimToWidth(role, 82)), slotX + GEXPRESS$SLOT_SIZE / 2,
					slotY + GEXPRESS$SLOT_SIZE + 20, gexpress$roleColor(ability.roleId()));
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (CopycatClickTarget target : gexpress$copycatClickTargets) {
			if (!target.contains(mouseX, mouseY)) continue;
			if (target.storedIndex() >= 0) {
				if (button == 1) {
					ClientCopycatState.activateStored(target.storedIndex());
				} else if (button == 0) {
					ClientCopycatState.selectStored(target.storedIndex());
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Unique
	private static void gexpress$drawHead(DrawContext context, UUID playerId, int x, int y, int size) {
		MinecraftClient client = MinecraftClient.getInstance();
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		if (client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	@Unique
	private static String gexpress$roleName(Identifier id) {
		if (id == null) return "Unknown";
		String key = "announcement.role." + id.getNamespace() + "." + id.getPath();
		Text translated = Text.translatable(key);
		String value = translated.getString();
		return key.equals(value) ? gexpress$titleCase(id.getPath()) : value;
	}

	@Unique
	private static int gexpress$roleColor(Identifier id) {
		for (Role role : WatheRoles.ROLES) {
			if (role != null && id != null && id.equals(role.identifier())) return 0xFF000000 | role.color();
		}
		return 0xFFC9B5FF;
	}

	@Unique
	private static String gexpress$titleCase(String raw) {
		String[] parts = raw.replace('-', '_').split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.toString();
	}

	private record CopycatClickTarget(int x, int y, int width, int height, int storedIndex) {
		private static CopycatClickTarget stored(int x, int y, int width, int height, int index) {
			return new CopycatClickTarget(x, y, width, height, index);
		}

		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
}
