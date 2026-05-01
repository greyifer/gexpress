package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.ClientNeutralWinState;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(value = RoundTextRenderer.class, remap = false)
public abstract class NeutralWinTextMixin {
	@Redirect(method = "renderHud", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/client/gui/RoleAnnouncementTexts$RoleAnnouncementText;getEndText(Ldev/doctor4t/wathe/game/GameFunctions$WinStatus;Lnet/minecraft/text/Text;)Lnet/minecraft/text/Text;"))
	private static Text gexpress$neutralLooseEndWinText(RoleAnnouncementTexts.RoleAnnouncementText role,
			GameFunctions.WinStatus status, Text winner, TextRenderer renderer, ClientPlayerEntity player,
			DrawContext context) {
		if (status == GameFunctions.WinStatus.LOOSE_END && player != null && player.getWorld() != null) {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
			UUID winnerId = game == null ? null : game.getLooseEndWinner();
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
		return role.getEndText(status, winner);
	}
}
