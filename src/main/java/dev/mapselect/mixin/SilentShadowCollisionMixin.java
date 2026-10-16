package dev.mapselect.mixin;

import dev.mapselect.role.silent.SilentShadowComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class SilentShadowCollisionMixin {
	@Inject(
		method = "adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Ljava/util/List;)Lnet/minecraft/util/math/Vec3d;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void gexpress$phaseShadowMovement(Entity entity, Vec3d movement, Box box, World world,
			List<VoxelShape> collisions, CallbackInfoReturnable<Vec3d> cir) {
		if (!(entity instanceof PlayerEntity player) || !SilentShadowComponent.isShadowed(player)) return;
		if (Math.abs(movement.x) < 1.0E-9D && Math.abs(movement.z) < 1.0E-9D) return;
		Vec3d vertical = Entity.adjustMovementForCollisions(entity,
			new Vec3d(0.0D, movement.y, 0.0D), box, world, collisions);
		cir.setReturnValue(new Vec3d(movement.x, vertical.y, movement.z));
	}

	@Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
	private void gexpress$preventShadowSuffocation(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof PlayerEntity player && SilentShadowComponent.isShadowed(player)) {
			cir.setReturnValue(false);
		}
	}
}
