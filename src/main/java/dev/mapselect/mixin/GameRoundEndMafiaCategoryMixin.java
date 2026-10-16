package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.mapselect.role.RoundEndRoleRosterSync;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GameRoundEndComponent.class, remap = false)
public abstract class GameRoundEndMafiaCategoryMixin {
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setRoundEndData", at = @At("HEAD"), argsOnly = true, remap = false)
	private List<ServerPlayerEntity> gexpress$omitTwinActors(List<ServerPlayerEntity> players) {
		return players.stream().filter(player -> !(player instanceof dev.mapselect.entity.TwinFakePlayerEntity)).toList();
	}
	@Shadow
	private World world;

	@Inject(method = "setRoundEndData", at = @At("RETURN"))
	private void gexpress$sendSpecificRoleRoster(List<ServerPlayerEntity> players,
			dev.doctor4t.wathe.game.GameFunctions.WinStatus winStatus, CallbackInfo ci) {
		RoundEndRoleRosterSync.send(world, players);
	}
}
