package dev.mapselect.mixin.client;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.ClientNeutralWinState;
import dev.mapselect.client.ClientRoundEndRoleRoster;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(value = RoundTextRenderer.class, remap = false, priority = 450)
public abstract class MafiaRoundEndRendererMixin {
	private static final int END_DURATION = 200;
	private static final int SECTION_HEAD_SIZE = 16;
	private static final int SECTION_HEAD_STEP_X = 24;
	private static final int SECTION_HEAD_STEP_Y = 22;
	private static final int SECTION_CIVILIAN = 0;
	private static final int SECTION_VIGILANTE = 1;
	private static final int SECTION_NEUTRAL = 2;
	private static final int SECTION_KILLER = 3;
	private static final int SECTION_MAFIA = 4;
	private static final int SECTION_COVENANT = 5;
	private static final Set<String> GEXPRESS$MAFIA_ROLES = Set.of(
		"gexpress:godfather", "gexpress:mafioso", "gexpress:janitor", "gexpress:pickpocket", "gexpress:burglar"
	);
	private static final Set<String> GEXPRESS$COVENANT_ROLES = Set.of("gexpress:dracula", "gexpress:vampire");

	@Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
	private static void gexpress$drawRoundRoleRoster(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci) {
		if (player == null || player.getWorld() == null) return;
		int endTime = RoundTextRendererAccessor.gexpress$getEndTime();
		GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
		if (endTime <= 0 || endTime >= END_DURATION - (GameConstants.FADE_TIME * 2)
				|| game.isRunning() || game.getGameMode() == WatheGameModes.DISCOVERY
				|| game.getGameMode() == WatheGameModes.LOOSE_ENDS) return;
		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.getWorld());
		if (roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE) return;

		Text endText = gexpress$endText(player, game, roundEnd);
		if (endText == null) return;

		context.getMatrices().peek().getPositionMatrix().identity();
		gexpress$drawEndTitle(context, renderer, roundEnd, endText);
		gexpress$drawEndSections(context, renderer, player, game, roundEnd);
		gexpress$drawRoundRoleRosterContent(renderer, player, context, game, roundEnd);
		ci.cancel();
	}

	private static void gexpress$drawRoundRoleRosterContent(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, GameWorldComponent game, GameRoundEndComponent roundEnd) {
		int rowHeight = 18;
		int maxRows = Math.max(1, (context.getScaledWindowHeight() - 72) / rowHeight);
		int leftX = 8;
		int rightX = Math.max(leftX + 170, context.getScaledWindowWidth() - 174);
		int y = 8;
		int index = 0;
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null || entry.role() == null) continue;
			if (ClientRoundEndRoleRoster.isSpectator(entry.player().getId())) continue;
			int columnX = index < maxRows ? leftX : rightX;
			int rowY = y + (index % maxRows) * rowHeight;
			Role actualRole = game.getRole(entry.player().getId());
			Text fallback = actualRole != null && actualRole.identifier() != null
				? Text.literal(titleCase(actualRole.identifier().getPath()))
				: entry.role().roleText == null ? entry.role().titleText : entry.role().roleText;
			Text roleText = ClientRoundEndRoleRoster.roleText(entry.player().getId(), fallback);
			int roleColor = ClientRoundEndRoleRoster.roleColor(entry.player().getId(),
				actualRole == null ? 0xFFB9D7FF : 0xFF000000 | actualRole.color());
			drawRosterRow(context, renderer, entry.player(), roleText, roleColor,
				ClientRoundEndRoleRoster.level(entry.player().getId()), columnX, rowY);
			index++;
			if (index >= maxRows * 2) break;
		}
	}

	private static Text gexpress$endText(ClientPlayerEntity player, GameWorldComponent game,
			GameRoundEndComponent roundEnd) {
		UUID winnerId = game.getLooseEndWinner();
		PlayerEntity winner = player.getWorld().getPlayerByUuid(winnerId == null ? UUID.randomUUID() : winnerId);
		Text winnerText = winner == null ? Text.empty() : winner.getDisplayName();
		RoleAnnouncementTexts.RoleAnnouncementText announcement = RoundTextRendererAccessor.gexpress$getRole();
		if (roundEnd.getWinStatus() == GameFunctions.WinStatus.LOOSE_END) {
			Text neutralText = ClientNeutralWinState.endText(winnerId);
			if (neutralText != null) return neutralText;
			Role winnerRole = winnerId == null ? null : game.getRole(winnerId);
			if (winnerRole != null) {
				if (MapSelectRoles.VULTURE_ID.equals(winnerRole.identifier())) {
					return Text.translatable("announcement.win.gexpress.pelican").withColor(winnerRole.color());
				}
				if (MapSelectRoles.JUGGERNAUT_ID.equals(winnerRole.identifier())) {
					return Text.translatable("announcement.win.gexpress.juggernaut").withColor(winnerRole.color());
				}
			}
		}
		return announcement == null ? null : announcement.getEndText(roundEnd.getWinStatus(), winnerText);
	}

	private static void gexpress$drawEndTitle(DrawContext context, TextRenderer renderer,
			GameRoundEndComponent roundEnd, Text endText) {
		context.getMatrices().push();
		context.getMatrices().translate(context.getScaledWindowWidth() / 2f,
			context.getScaledWindowHeight() / 2f - 40, 0);
		context.getMatrices().push();
		context.getMatrices().scale(2.6f, 2.6f, 1f);
		context.drawCenteredTextWithShadow(renderer, endText, 0, -12, 0xFFFFFF);
		context.getMatrices().pop();
		context.getMatrices().push();
		context.getMatrices().scale(1.2f, 1.2f, 1f);
		Text winMessage = Text.translatable("game.win." + roundEnd.getWinStatus().name().toLowerCase(Locale.ROOT));
		context.drawCenteredTextWithShadow(renderer, winMessage, 0, -4, 0xFFFFFF);
		context.getMatrices().pop();
		context.getMatrices().pop();
	}

	private static void gexpress$drawEndSections(DrawContext context, TextRenderer renderer,
			ClientPlayerEntity player, GameWorldComponent game, GameRoundEndComponent roundEnd) {
		List<GameRoundEndComponent.RoundEndData> civilians = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> vigilantes = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> neutrals = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> killers = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> mafia = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> covenant = new ArrayList<>();
		Map<UUID, String> roleIds = ClientRoundEndRoleRoster.roleIds();
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry == null || entry.player() == null) continue;
			if (ClientRoundEndRoleRoster.isSpectator(entry.player().getId())) continue;
			switch (gexpress$sectionFor(player, game, entry, roleIds)) {
				case SECTION_CIVILIAN -> civilians.add(entry);
				case SECTION_VIGILANTE -> vigilantes.add(entry);
				case SECTION_NEUTRAL -> neutrals.add(entry);
				case SECTION_KILLER -> killers.add(entry);
				case SECTION_MAFIA -> mafia.add(entry);
				case SECTION_COVENANT -> covenant.add(entry);
				default -> civilians.add(entry);
			}
		}

		int centerX = context.getScaledWindowWidth() / 2;
		int baseY = context.getScaledWindowHeight() / 2 - 18;
		int leftX = centerX - 62;
		int rightX = centerX + 48;
		int civiliansBottom = gexpress$drawEndSection(context, renderer,
			RoleAnnouncementTexts.CIVILIAN.titleText, civilians, leftX, baseY, 0xFF14D22A);
		int vigilantesBottom = gexpress$drawEndSection(context, renderer,
			RoleAnnouncementTexts.VIGILANTE.titleText, vigilantes, rightX, baseY, 0xFF18A9FF);
		int neutralY = civiliansBottom + 14;
		int killerY = vigilantesBottom + 14;
		int neutralsBottom = gexpress$drawEndSection(context, renderer,
			Text.translatable("gui.gexpress.end_section.neutrals"), neutrals, leftX, neutralY, 0xFFFF9C1A);
		int killersBottom = gexpress$drawEndSection(context, renderer,
			RoleAnnouncementTexts.KILLER.titleText, killers, rightX, killerY, 0xFFFF3535);

		int familyY = Math.max(neutralsBottom, killersBottom) + 14;
		if (!mafia.isEmpty() && !covenant.isEmpty()) {
			gexpress$drawEndSection(context, renderer, Text.literal("Mafia"), mafia, leftX, familyY, 0xFF8A8A8A);
			gexpress$drawEndSection(context, renderer, Text.literal("Covenant"), covenant, rightX, familyY, 0xFF8B0F1F);
			return;
		}
		if (!mafia.isEmpty()) {
			gexpress$drawEndSection(context, renderer, Text.literal("Mafia"), mafia, centerX, familyY, 0xFF8A8A8A);
		} else if (!covenant.isEmpty()) {
			gexpress$drawEndSection(context, renderer, Text.literal("Covenant"), covenant, centerX, familyY, 0xFF8B0F1F);
		}
	}

	private static int gexpress$sectionFor(ClientPlayerEntity player, GameWorldComponent game,
			GameRoundEndComponent.RoundEndData entry, Map<UUID, String> roleIds) {
		String roleId = roleIds.get(entry.player().getId());
		String normalized = roleId == null ? "" : roleId.toLowerCase(Locale.ROOT);
		if (GEXPRESS$MAFIA_ROLES.contains(normalized)) return SECTION_MAFIA;
		if (GEXPRESS$COVENANT_ROLES.contains(normalized)) return SECTION_COVENANT;

		Identifier id = normalized.isBlank() ? null : Identifier.tryParse(normalized);
		if (id != null) {
			if (MapSelectRoles.JUGGERNAUT_ID.equals(id) || MapSelectRoles.VULTURE_ID.equals(id)
					|| MapSelectRoles.COPYCAT_ID.equals(id)) {
				return SECTION_NEUTRAL;
			}
			for (Role role : dev.doctor4t.wathe.api.WatheRoles.ROLES) {
				if (role == null || role.identifier() == null || !id.equals(role.identifier())) continue;
				if (role == dev.doctor4t.wathe.api.WatheRoles.VIGILANTE) return SECTION_VIGILANTE;
				if (role.canUseKiller()) return SECTION_KILLER;
				if (role.isInnocent()) return SECTION_CIVILIAN;
				return SECTION_NEUTRAL;
			}
		}

		if (entry.role() == RoleAnnouncementTexts.VIGILANTE) return SECTION_VIGILANTE;
		if (entry.role() == RoleAnnouncementTexts.KILLER) return SECTION_KILLER;
		return SECTION_CIVILIAN;
	}

	private static int gexpress$drawEndSection(DrawContext context, TextRenderer renderer, Text title,
			List<GameRoundEndComponent.RoundEndData> profiles, int centerX, int y, int color) {
		context.drawCenteredTextWithShadow(renderer, title, centerX, y, color);
		int columns = Math.min(2, Math.max(1, profiles.size()));
		int startX = centerX - ((columns - 1) * SECTION_HEAD_STEP_X) / 2 - (SECTION_HEAD_SIZE / 2);
		for (int i = 0; i < profiles.size(); i++) {
			int headX = startX + (i % columns) * SECTION_HEAD_STEP_X;
			int headY = y + 12 + (i / columns) * SECTION_HEAD_STEP_Y;
			drawHead(context, profiles.get(i).player(), headX, headY);
			if (profiles.get(i).wasDead()) drawDeathMark(context, renderer, headX, headY);
		}
		int rows = Math.max(1, (profiles.size() + columns - 1) / columns);
		return y + 12 + (rows - 1) * SECTION_HEAD_STEP_Y + SECTION_HEAD_SIZE;
	}

	private static void drawRosterRow(DrawContext context, TextRenderer renderer, GameProfile profile, Text role,
			int roleColor, int level, int x, int y) {
		context.fill(x - 2, y - 1, x + 164, y + 17, 0x66000000);
		drawHead(context, profile, x, y + 1);
		String name = trim(renderer, ClientRoundEndRoleRoster.name(profile.getId(), profile.getName()), 80);
		String levelText = "LvL " + Math.max(1, level);
		String roleName = trim(renderer, role == null ? "" : role.getString(), 72);
		context.drawTextWithShadow(renderer, Text.literal(name), x + 18, y, 0xFFFFFFFF);
		context.drawTextWithShadow(renderer, Text.literal(levelText), x + 118, y, 0xFFB8B8B8);
		context.drawTextWithShadow(renderer, Text.literal(roleName), x + 18, y + 8, roleColor);
	}

	private static void drawHead(DrawContext context, GameProfile profile, int x, int y) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(profile.getId()).texture();
		Object cached = WatheClient.PLAYER_ENTRIES_CACHE.get(profile.getId());
		if (cached instanceof PlayerListEntry entry) {
			texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, SECTION_HEAD_SIZE, SECTION_HEAD_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, SECTION_HEAD_SIZE, SECTION_HEAD_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static void drawDeathMark(DrawContext context, TextRenderer renderer, int x, int y) {
		context.getMatrices().push();
		context.getMatrices().translate(x + SECTION_HEAD_SIZE / 2f, y + 1, 0);
		context.getMatrices().scale(2f, 1f, 1f);
		context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 0, 0xE10000, false);
		context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 1, 0x550000, false);
		context.getMatrices().pop();
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

}
