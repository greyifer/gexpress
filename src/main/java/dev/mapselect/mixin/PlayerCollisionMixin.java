package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerCollisionMixin {
	@Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
	private void gexpress$keepWathePlayerCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof PlayerEntity) || !(other instanceof PlayerEntity)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(self.getWorld());
		if (game != null && game.isRunning()) {
			cir.setReturnValue(true);
		}
	}
}
