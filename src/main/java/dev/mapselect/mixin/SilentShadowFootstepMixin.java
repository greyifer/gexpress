package dev.mapselect.mixin;

import dev.mapselect.role.silent.SilentShadowComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SilentShadowFootstepMixin {
	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void gexpress$muteShadowSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player && SilentShadowComponent.isShadowed(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "playCombinationStepSounds", at = @At("HEAD"), cancellable = true)
	private void gexpress$muteShadowCombinationSteps(BlockState primaryState, BlockState secondaryState, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player && SilentShadowComponent.isShadowed(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "playSecondaryStepSound", at = @At("HEAD"), cancellable = true)
	private void gexpress$muteShadowSecondarySteps(BlockState state, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player && SilentShadowComponent.isShadowed(player)) {
			ci.cancel();
		}
	}
}
