package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.mafia.MafiaManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = GameRoundEndComponent.class, remap = false)
public abstract class GameRoundEndWinnerMixin {
	@Shadow
	private World world;

	@Shadow
	public abstract GameFunctions.WinStatus getWinStatus();

	@Inject(method = "didWin", at = @At("HEAD"), cancellable = true)
	private void gexpress$looseEndWinnerWins(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
		if (getWinStatus() != GameFunctions.WinStatus.LOOSE_END) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || uuid == null || game.getLooseEndWinner() == null) return;
		if (uuid.equals(game.getLooseEndWinner())
				|| MafiaManager.isSameFamily(uuid, game.getLooseEndWinner())) {
			cir.setReturnValue(true);
		}
	}
}
