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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SilentShadowPlayerMoveEffectMixin {
	@Inject(method = "getMoveEffect", at = @At("HEAD"), cancellable = true)
	private void gexpress$muteShadowMoveSounds(CallbackInfoReturnable<Entity.MoveEffect> cir) {
		if (SilentShadowComponent.isShadowed((PlayerEntity) (Object) this)) {
			cir.setReturnValue(Entity.MoveEffect.EVENTS);
		}
	}

	@Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
	private void gexpress$muteShadowPlayerSteps(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (SilentShadowComponent.isShadowed((PlayerEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
