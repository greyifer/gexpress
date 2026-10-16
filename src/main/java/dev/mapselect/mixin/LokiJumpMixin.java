package dev.mapselect.mixin;

import dev.mapselect.role.loki.LokiGravity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LokiJumpMixin {
    @Shadow protected abstract float getJumpVelocity();
    @Inject(method="jump",at=@At("HEAD"),cancellable=true)
    private void gexpress$jumpAwayFromCeiling(CallbackInfo ci){LivingEntity entity=(LivingEntity)(Object)this;if(!LokiGravity.flipped(entity))return;Vec3d velocity=entity.getVelocity();entity.setVelocity(velocity.x,-getJumpVelocity(),velocity.z);entity.velocityDirty=true;ci.cancel();}
}
