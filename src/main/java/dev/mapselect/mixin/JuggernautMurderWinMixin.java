package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import dev.mapselect.role.copycat.CopycatManager;
import dev.mapselect.role.covenant.CovenantManager;
import dev.mapselect.role.cupid.CupidManager;
import dev.mapselect.role.juggernaut.JuggernautManager;
import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.role.pelican.PelicanManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MurderGameMode.class, remap = false)
public abstract class JuggernautMurderWinMixin {
	@Inject(method = "tickServerGameLoop", at = @At("HEAD"), cancellable = true)
	private void gexpress$soloJuggernautWin(ServerWorld world, GameWorldComponent game, CallbackInfo ci) {
		if (PelicanManager.handleMurderTick(world, game)
				|| CupidManager.handleMurderTick(world, game)
				|| LoversManager.handleMurderTick(world, game)
				|| MafiaManager.handleMurderTick(world, game)
				|| CovenantManager.handleMurderTick(world, game)
				|| CopycatManager.handleMurderTick(world, game)
				|| JuggernautManager.handleMurderTick(world, game)) {
			ci.cancel();
		}
	}
}
