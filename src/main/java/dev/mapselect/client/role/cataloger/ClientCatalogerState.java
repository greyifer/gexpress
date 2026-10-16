package dev.mapselect.client.role.cataloger;

import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.network.role.cataloger.CatalogerDossierPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.UUID;

public final class ClientCatalogerState {
	private static List<CatalogerDossierPayload.Entry> passengers = List.of();
	private static int evilCount;
	private static int civilianCount;
	private static String exactRoleNamespace = "";
	private static String exactRolePath = "";
	private static boolean active;
	private static float slide;
	private static Object stateWorld;

	private ClientCatalogerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CatalogerDossierPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientCatalogerState::tick);
		HudRenderCallback.EVENT.register(ClientCatalogerState::render);
	}

	private static void apply(CatalogerDossierPayload payload) {
		stateWorld = MinecraftClient.getInstance().world;
		active = payload.active();
		passengers = List.copyOf(payload.passengers());
		evilCount = payload.evilCount();
		civilianCount = payload.civilianCount();
		exactRoleNamespace = payload.exactRoleNamespace();
		exactRolePath = payload.exactRolePath();
	}

	private static void tick(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (stateWorld != null && world != stateWorld) reset();
		if (stateWorld == null && world != null) stateWorld = world;
		boolean visible = active && passengers.size() == 5 && client != null && client.player != null
			&& ClientRoleRevealState.canShowRoleHud(client);
		slide = MathHelper.lerp(0.16F, slide, visible ? 1.0F : 0.0F);
	}

	private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.textRenderer == null || slide <= 0.015F) return;
		TextRenderer text = client.textRenderer;
		int width = Math.min(226, Math.max(176, context.getScaledWindowWidth() - 18));
		int height = 180;
		int visibleX = context.getScaledWindowWidth() - width - 10;
		int hiddenX = context.getScaledWindowWidth() + 8;
		float eased = 1.0F - (float) Math.pow(1.0F - slide, 3.0D);
		int x = Math.round(MathHelper.lerp(eased, hiddenX, visibleX));
		int y = Math.max(10, (context.getScaledWindowHeight() - height) / 2);
		int alpha = Math.max(0, Math.min(255, Math.round(slide * 255.0F)));

		drawPaper(context, x, y, width, height, alpha);
		int ink = withAlpha(0x2C2217, alpha);
		drawCentered(context, text, "CATALOG", x, y + 10, width, ink);
		context.fill(x + 10, y + 24, x + width - 10, y + 26, withAlpha(0x8A6D45, alpha));

		int nameY = y + 31;
		for (int i = 0; i < passengers.size() && i < 5; i++) {
			CatalogerDossierPayload.Entry passenger = passengers.get(i);
			context.fill(x + 12, nameY - 1, x + width - 12, nameY + 15,
				withAlpha(i % 2 == 0 ? 0xD8C590 : 0xE1D09F, alpha / 3));
			drawHead(context, client, passenger.playerId(), x + 14, nameY, 13, alpha);
			String name = trim(text, passenger.playerName(), width - 49);
			context.drawText(text, name, x + 33, nameY + 2, ink, false);
			nameY += 17;
		}

		int cluesY = y + 124;
		context.fill(x + 10, cluesY - 6, x + width - 10, cluesY - 4, withAlpha(0x8A6D45, alpha));
		drawClue(context, text, countLine(evilCount, "evil"), x + 13, cluesY, 0x7E2828, alpha);
		drawClue(context, text, countLine(civilianCount, "civilian"),
			x + 13, cluesY + 12, 0x335F75, alpha);
		drawWrappedClue(context, text, "One of them is the " + roleName(),
			x + 13, cluesY + 24, width - 33, 0x4B355F, alpha);
	}

	private static void drawPaper(DrawContext context, int x, int y, int width, int height, int alpha) {
		context.fill(x + 4, y + 5, x + width + 4, y + height + 5, withAlpha(0x000000, alpha / 3));
		context.fill(x + 2, y, x + width - 8, y + height, withAlpha(0xD7C28D, alpha));
		context.fill(x, y + 3, x + width - 5, y + height - 4, withAlpha(0xE8D8A8, alpha));
		context.fill(x + 4, y + 5, x + width - 10, y + height - 8, withAlpha(0xF1E3B8, alpha));
		int edge = withAlpha(0x725B38, alpha);
		context.fill(x + 2, y, x + width - 14, y + 2, edge);
		context.fill(x, y + 4, x + 2, y + height - 5, edge);
		context.fill(x + 3, y + height - 3, x + width - 8, y + height, edge);
		context.fill(x + width - 8, y + 12, x + width - 5, y + height - 4, edge);
		context.fill(x + width - 14, y, x + width - 5, y + 10, withAlpha(0xB69C65, alpha));
		context.fill(x + width - 14, y, x + width - 12, y + 11, edge);
		context.fill(x + width - 14, y + 9, x + width - 5, y + 11, edge);
	}

	private static void drawClue(DrawContext context, TextRenderer text, String clue,
			int x, int y, int color, int alpha) {
		context.fill(x, y + 3, x + 3, y + 6, withAlpha(color, alpha));
		context.drawText(text, clue, x + 7, y, withAlpha(0x3B2B1D, alpha), false);
	}

	private static void drawWrappedClue(DrawContext context, TextRenderer text, String clue,
			int x, int y, int maxWidth, int color, int alpha) {
		context.fill(x, y + 3, x + 3, y + 6, withAlpha(color, alpha));
		List<OrderedText> lines = text.wrapLines(Text.literal(clue), maxWidth - 7);
		for (int i = 0; i < lines.size() && i < 2; i++) {
			context.drawText(text, lines.get(i), x + 7, y + i * 10,
				withAlpha(0x3B2B1D, alpha), false);
		}
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId,
			int x, int y, int size, int alpha) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		if (client != null && client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.fill(x - 1, y - 1, x + size + 1, y + size + 1, withAlpha(0x5B452B, alpha));
		PlayerSkinDrawer.draw(context, texture, x, y, size);
	}

	private static String countLine(int count, String alignment) {
		if (count <= 0) return "No " + alignment + " players";
		if (count == 1) return "1 " + alignment + " player";
		return "1 or more " + alignment + " players";
	}

	private static String roleName() {
		if (exactRolePath.isBlank()) return "Unknown";
		String key = "announcement.role." + exactRoleNamespace + "." + exactRolePath;
		if (I18n.hasTranslation(key)) return Text.translatable(key).getString();
		StringBuilder out = new StringBuilder();
		for (String part : exactRolePath.replace('-', '_').split("_+")) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	private static String trim(TextRenderer text, String value, int maxWidth) {
		String out = value == null ? "" : value;
		while (out.length() > 1 && text.getWidth(out) > maxWidth) out = out.substring(0, out.length() - 1);
		return out;
	}

	private static void drawCentered(DrawContext context, TextRenderer text, String value,
			int x, int y, int width, int color) {
		context.drawText(text, value, x + (width - text.getWidth(value)) / 2, y, color, false);
	}

	private static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	private static void reset() {
		active = false;
		passengers = List.of();
		evilCount = 0;
		civilianCount = 0;
		exactRoleNamespace = "";
		exactRolePath = "";
		slide = 0.0F;
		stateWorld = null;
	}
}
