package dev.mapselect.mixin.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.ClientNeutralWinState;
import dev.mapselect.client.EndScreenLayoutConfig;
import dev.mapselect.role.GexpressRoleAnnouncementTexts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = RoundTextRenderer.class, remap = false)
public abstract class MafiaRoundEndRendererMixin {
	private static final int END_DURATION = 200;

	@Inject(method = "renderHud", at = @At("HEAD"), cancellable = true)
	private static void gexpress$renderMafiaEndSection(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci) {
		if (player == null || player.getWorld() == null) return;
		int endTime = RoundTextRendererAccessor.gexpress$getEndTime();
		GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
		if (endTime <= 0 || endTime >= END_DURATION - (GameConstants.FADE_TIME * 2)
				|| game.isRunning() || game.getGameMode() == WatheGameModes.DISCOVERY
				|| game.getGameMode() == WatheGameModes.LOOSE_ENDS) {
			return;
		}

		GameRoundEndComponent roundEnd = GameRoundEndComponent.KEY.get(player.getWorld());
		if (roundEnd.getWinStatus() == GameFunctions.WinStatus.NONE || !hasMafia(roundEnd)) return;

		RoleAnnouncementTexts.RoleAnnouncementText role = RoundTextRendererAccessor.gexpress$getRole();
		UUID winnerId = game.getLooseEndWinner();
		PlayerEntity winner = winnerId == null ? null : player.getWorld().getPlayerByUuid(winnerId);
		Text endText = ClientNeutralWinState.endText(winnerId);
		if (endText == null) {
			endText = role.getEndText(roundEnd.getWinStatus(), winner == null ? Text.empty() : winner.getDisplayName());
		}
		if (endText == null) return;

		ci.cancel();
		context.getMatrices().push();
		context.getMatrices().translate(context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f - 40, 0);
		context.getMatrices().push();
		context.getMatrices().scale(2.6f, 2.6f, 1f);
		context.drawTextWithShadow(renderer, endText, -renderer.getWidth(endText) / 2, -12, 0xFFFFFF);
		context.getMatrices().pop();
		context.getMatrices().push();
		context.getMatrices().scale(1.2f, 1.2f, 1f);
		MutableText winMessage = Text.translatable("game.win." + roundEnd.getWinStatus().name().toLowerCase());
		context.drawTextWithShadow(renderer, winMessage, -renderer.getWidth(winMessage) / 2, -4, 0xFFFFFF);
		context.getMatrices().pop();

		List<GameRoundEndComponent.RoundEndData> civilians = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> vigilantes = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> neutrals = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> killers = new ArrayList<>();
		List<GameRoundEndComponent.RoundEndData> mafia = new ArrayList<>();
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry.role() == RoleAnnouncementTexts.CIVILIAN) civilians.add(entry);
			else if (entry.role() == RoleAnnouncementTexts.VIGILANTE) vigilantes.add(entry);
			else if (entry.role() == RoleAnnouncementTexts.KILLER) killers.add(entry);
			else if (entry.role() == GexpressRoleAnnouncementTexts.MAFIA) mafia.add(entry);
			else if (entry.role() != RoleAnnouncementTexts.BLANK) neutrals.add(entry);
		}

		EndScreenLayoutConfig.Section civilianLayout = EndScreenLayoutConfig.civilians();
		EndScreenLayoutConfig.Section vigilanteLayout = EndScreenLayoutConfig.vigilantes();
		EndScreenLayoutConfig.Section neutralLayout = EndScreenLayoutConfig.neutrals();
		EndScreenLayoutConfig.Section killerLayout = EndScreenLayoutConfig.killers();
		EndScreenLayoutConfig.Section mafiaLayout = EndScreenLayoutConfig.mafia();

		drawColumn(context, renderer, RoleAnnouncementTexts.CIVILIAN.titleText, civilians,
			civilianLayout.x, civilianLayout.y, civilianLayout.columns);
		drawColumn(context, renderer, RoleAnnouncementTexts.VIGILANTE.titleText, vigilantes,
			vigilanteLayout.x, vigilanteLayout.y, vigilanteLayout.columns);
		drawColumn(context, renderer, Text.translatable("gui.gexpress.end_section.neutrals")
			.withColor(0xFFAA00), neutrals, neutralLayout.x, neutralLayout.y, neutralLayout.columns);
		drawColumn(context, renderer, RoleAnnouncementTexts.KILLER.titleText, killers,
			killerLayout.x, killerLayout.y, killerLayout.columns);
		int topBottom = Math.max(Math.max(bottom(civilians, civilianLayout),
			bottom(vigilantes, vigilanteLayout)),
			Math.max(bottom(neutrals, neutralLayout), bottom(killers, killerLayout)));
		int mafiaY = Math.max(mafiaLayout.y, topBottom + 10);
		drawColumn(context, renderer, GexpressRoleAnnouncementTexts.MAFIA.titleText, mafia,
			mafiaLayout.x, mafiaY, mafiaLayout.columns);
		context.getMatrices().pop();
	}

	private static boolean hasMafia(GameRoundEndComponent roundEnd) {
		for (GameRoundEndComponent.RoundEndData entry : roundEnd.getPlayers()) {
			if (entry.role() == GexpressRoleAnnouncementTexts.MAFIA) return true;
		}
		return false;
	}

	private static void drawColumn(DrawContext context, TextRenderer renderer, Text title,
			List<GameRoundEndComponent.RoundEndData> entries, int centerX, int y, int columns) {
		context.drawTextWithShadow(renderer, title, centerX - renderer.getWidth(title) / 2, y, 0xFFFFFF);
		int startX = centerX - (columns * 12) / 2;
		for (int i = 0; i < entries.size(); i++) {
			int x = startX + (i % columns) * 12;
			int rowY = y + 10 + (i / columns) * 12;
			drawHead(context, renderer, entries.get(i), x, rowY);
		}
	}

	private static int rows(List<GameRoundEndComponent.RoundEndData> entries, int columns) {
		if (entries.isEmpty()) return 0;
		return (entries.size() + columns - 1) / columns;
	}

	private static int bottom(List<GameRoundEndComponent.RoundEndData> entries,
			EndScreenLayoutConfig.Section layout) {
		return layout.y + 20 + rows(entries, layout.columns) * 12;
	}

	private static void drawHead(DrawContext context, TextRenderer renderer,
			GameRoundEndComponent.RoundEndData entry, int x, int y) {
		GameProfile profile = entry.player();
		PlayerListEntry playerEntry = WatheClient.PLAYER_ENTRIES_CACHE.get(profile.getId());
		context.getMatrices().push();
		context.getMatrices().scale(2f, 2f, 1f);
		context.getMatrices().translate(x, y, 0);
		if (playerEntry != null) {
			Identifier texture = playerEntry.getSkinTextures().texture();
			if (texture != null) {
				RenderSystem.enableBlend();
				context.getMatrices().push();
				context.getMatrices().translate(8, 0, 0);
				float offColour = entry.wasDead() ? 0.4f : 1f;
				RenderSystem.setShaderColor(1f, offColour, offColour, 1f);
				context.drawTexture(texture, 0, 0, 8, 8, 8, 8, 64, 64);
				context.getMatrices().translate(-0.5, -0.5, 0);
				context.getMatrices().scale(1.125f, 1.125f, 1f);
				context.drawTexture(texture, 0, 0, 40, 8, 8, 8, 64, 64);
				RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
				context.getMatrices().pop();
			}
		}
		if (entry.wasDead()) {
			context.getMatrices().translate(13, 0, 0);
			context.getMatrices().scale(2f, 1f, 1f);
			context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 0, 0xE10000, false);
			context.drawText(renderer, "x", -renderer.getWidth("x") / 2, 1, 0x550000, false);
		}
		context.getMatrices().pop();
	}
}
