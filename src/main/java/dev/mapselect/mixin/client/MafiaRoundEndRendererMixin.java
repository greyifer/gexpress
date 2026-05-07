package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.EndScreenLayoutConfig;
import dev.mapselect.role.GexpressRoleAnnouncementTexts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoundTextRenderer.class, remap = false, priority = 450)
public abstract class MafiaRoundEndRendererMixin {
	private static final int END_DURATION = 200;
	private static int gexpress$vigilanteTotal;
	private static int gexpress$killerTotal;
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
			RoleAnnouncementTexts.RoleAnnouncementText role = entry.role();
			if (role == RoleAnnouncementTexts.VIGILANTE) {
				gexpress$vigilanteTotal++;
			} else if (role == RoleAnnouncementTexts.KILLER) {
				gexpress$killerTotal++;
			} else if (role == GexpressRoleAnnouncementTexts.MAFIA) {
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
		target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent$RoundEndData;role()Ldev/doctor4t/wathe/client/gui/RoleAnnouncementTexts$RoleAnnouncementText;",
		ordinal = 1))
	private static void gexpress$translateMafiaPlayers(TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context, CallbackInfo ci,
			@Local(name = "entry") GameRoundEndComponent.RoundEndData entry) {
		if (!gexpress$renderMafia || entry.role() != GexpressRoleAnnouncementTexts.MAFIA) return;

		EndScreenLayoutConfig.Section layout = EndScreenLayoutConfig.mafia();
		int columns = Math.max(1, layout.columns);
		int index = gexpress$mafiaRendered++;
		int startX = layout.x - (columns * 12) / 2;
		int x = startX + (index % columns) * 12;
		int y = gexpress$mafiaTitleY(layout) + 10 + (index / columns) * 12;
		context.getMatrices().translate(x, y, 0);
	}

	private static int gexpress$mafiaTitleY(EndScreenLayoutConfig.Section layout) {
		return Math.max(layout.y, gexpress$defaultMafiaTitleY());
	}

	private static int gexpress$defaultMafiaTitleY() {
		int watheVigilanteTotal = 1 + gexpress$vigilanteTotal;
		int killerTitleY = 14 + 16 + 24 * (watheVigilanteTotal / 2);
		int killerRows = Math.max(1, (gexpress$killerTotal + 1) / 2);
		return killerTitleY + 16 + 24 * killerRows;
	}

	private static void gexpress$reset() {
		gexpress$vigilanteTotal = 0;
		gexpress$killerTotal = 0;
		gexpress$mafiaTotal = 0;
		gexpress$mafiaRendered = 0;
		gexpress$renderMafia = false;
	}
}
