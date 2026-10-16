package dev.mapselect.mixin;

import dev.mapselect.role.silent.SilentShadowComponent;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Shadow March movement survive the server's post-move wall-overlap correction. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SilentShadowNetworkHandlerMixin {
	@Shadow public ServerPlayerEntity player;

	@Inject(method = "isPlayerNotCollidingWithBlocks", at = @At("HEAD"), cancellable = true)
	private void gexpress$allowShadowInsideBlocks(WorldView world, Box oldBox,
			double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (SilentShadowComponent.isShadowed(player)
				&& Math.abs(y - player.getY()) <= 1.0E-5D) {
			// false means the destination did not introduce a forbidden new block overlap.
			// Only bypass this after the vertical move was accepted; a blocked floor or ceiling
			// leaves player.getY() different, so vanilla still corrects that packet.
			cir.setReturnValue(false);
		}
	}
}
