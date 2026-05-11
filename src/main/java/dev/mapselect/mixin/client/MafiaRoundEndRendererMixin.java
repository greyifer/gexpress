package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.EndScreenLayoutConfig;
import dev.mapselect.role.GexpressRoleAnnouncementTexts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoundTextRenderer.class, remap = false, priority = 450)
public abstract class MafiaRoundEndRendererMixin {
	private static final int END_DURATION = 200;
	private static int gexpress$mafiaTotal;
	private static int gexpress$mafiaRendered;
	private static boolean gexpress$renderMafia;

	@Inject(method = "renderHud", at = @At("HEAD"))
	private static void gexpress$prepareMafiaSection(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci) {
		gexpress$reset();
		if (player == null || player.getWorld() == null) return;

		int endTime = RoundTextRendererAccessor.gexpress$getEndTime();
		GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
		if (endTime <= 0 || endTime >= END_DURATION - (GameConstants.FADE_TIME * 2)
				|| game.isRunning() || game.getGameMode() == WatheGameModes.DISCOVERY
				|| game.getGameMode() == WatheGameModes.LOOSE_ENDS) {
			return;
		}

		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.getWorld());
		if (roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;

		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry.role() == GexpressRoleAnnouncementTexts.MAFIA) {
				gexpress$mafiaTotal++;
			}
		}
		gexpress$renderMafia = gexpress$mafiaTotal > 0;
	}

	@Inject(method = "renderHud", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent;getPlayers()Ljava/util/List;",
		ordinal = 2))
	private static void gexpress$drawMafiaTitle(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci) {
		if (!gexpress$renderMafia) return;

		EndScreenLayoutConfig.Section layout = EndScreenLayoutConfig.mafia();
		Text title = GexpressRoleAnnouncementTexts.MAFIA.titleText;
		int y = gexpress$mafiaTitleY(layout);
		context.drawTextWithShadow(renderer, title, layout.x - renderer.getWidth(title) / 2, y, 0xFFFFFF);
	}

	@Inject(method = "renderHud", at = @At(value = "INVOKE",
		target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
		ordinal = 1))
	private static void gexpress$translateMafiaPlayers(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci,
			@Local(ordinal = 0) GameRoundEndComponent.RoundEndData entry) {
		if (!gexpress$renderMafia || entry.role() != GexpressRoleAnnouncementTexts.MAFIA) return;

		EndScreenLayoutConfig.Section layout = EndScreenLayoutConfig.mafia();
		int columns = Math.max(1, layout.columns);
		int index = gexpress$mafiaRendered++;
		int visualWidth = (columns - 1) * 24 + 16;
		int visualX = layout.x - visualWidth / 2 + (index % columns) * 24;
		int visualY = gexpress$mafiaTitleY(layout) + 14 + (index / columns) * 24;
		context.getMatrices().translate(visualX / 2.0, visualY / 2.0, 0);
	}

	@Inject(method = "renderHud", at = @At("TAIL"))
	private static void gexpress$drawRoundRoleRoster(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci) {
		if (player == null || player.getWorld() == null) return;
		int endTime = RoundTextRendererAccessor.gexpress$getEndTime();
		GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
		if (endTime <= 0 || endTime >= END_DURATION - (GameConstants.FADE_TIME * 2)
				|| game.isRunning()) return;
		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.getWorld());
		if (roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;
		int rowHeight = 18;
		int maxRows = Math.max(1, (context.getScaledWindowHeight() - 16) / rowHeight);
		int leftX = 8;
		int rightX = Math.max(leftX + 170, context.getScaledWindowWidth() - 174);
		int y = 8;
		int index = 0;
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null || entry.role() == null) continue;
			int columnX = index < maxRows ? leftX : rightX;
			int rowY = y + (index % maxRows) * rowHeight;
			Role actualRole = game.getRole(entry.player().getId());
			drawRosterRow(context, renderer, entry.player(), actualRole == null
				? entry.role().titleText : Text.literal(titleCase(actualRole.identifier().getPath())), columnX, rowY);
			index++;
			if (index >= maxRows * 2) break;
		}
	}

	private static void drawRosterRow(DrawContext context, TextRenderer renderer, GameProfile profile, Text role,
			int x, int y) {
		context.fill(x - 2, y - 1, x + 164, y + 17, 0x66000000);
		drawHead(context, profile, x, y + 1);
		String name = trim(renderer, profile.getName(), 74);
		String roleName = trim(renderer, role == null ? "" : role.getString(), 72);
		context.drawTextWithShadow(renderer, Text.literal(name), x + 18, y, 0xFFFFFFFF);
		context.drawTextWithShadow(renderer, Text.literal(roleName), x + 18, y + 8, 0xFFB9D7FF);
	}

	private static void drawHead(DrawContext context, GameProfile profile, int x, int y) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(profile.getId()).texture();
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(profile.getId());
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, 14, 14, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, 14, 14, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static String trim(TextRenderer renderer, String value, int width) {
		String out = value == null ? "" : value;
		while (out.length() > 1 && renderer.getWidth(out) > width) out = out.substring(0, out.length() - 1);
		return out;
	}

	private static String titleCase(String raw) {
		if (raw == null || raw.isBlank()) return "";
		String[] parts = raw.replace('-', '_').split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.toString();
	}

	private static int gexpress$mafiaTitleY(EndScreenLayoutConfig.Section layout) {
		return layout.y;
	}

	private static void gexpress$reset() {
		gexpress$mafiaTotal = 0;
		gexpress$mafiaRendered = 0;
		gexpress$renderMafia = false;
	}
}
