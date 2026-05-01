package dev.mapselect.mixin;

import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class PuppetmasterControlledMovementMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockControlledMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		if (PuppetmasterManager.isControlled(player) || VultureManager.isStashed(player)
				|| TimeMasterManager.isFrozen(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockControlledInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
		if (PuppetmasterManager.isControlled(player) || VultureManager.isStashed(player)
				|| TimeMasterManager.isFrozen(player)) {
			ci.cancel();
		}
	}
}
