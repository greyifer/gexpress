package dev.mapselect.mixin;

import dev.mapselect.game.CouchSleepHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class CouchSleepLivingEntityMixin {
	@Inject(method = "isSleepingInBed", at = @At("HEAD"), cancellable = true)
	private void gexpress$couchCountsAsSleepSurface(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		World world = entity.getWorld();
		entity.getSleepingPosition()
			.filter(pos -> CouchSleepHandler.isSleepableCouch(world, pos))
			.ifPresent(pos -> cir.setReturnValue(true));
	}

	@Inject(method = "getSleepingDirection", at = @At("HEAD"), cancellable = true)
	private void gexpress$useCouchSleepDirection(CallbackInfoReturnable<Direction> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		World world = entity.getWorld();
		BlockPos pos = entity.getSleepingPosition().orElse(null);
		if (pos == null) return;
		Direction direction = CouchSleepHandler.getCouchSleepingDirection(world, pos);
		if (direction != null) {
			cir.setReturnValue(direction);
		}
	}
}
