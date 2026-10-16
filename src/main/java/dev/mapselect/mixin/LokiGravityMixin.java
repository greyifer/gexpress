package dev.mapselect.mixin;

import dev.mapselect.role.loki.LokiGravity;
import net.minecraft.entity.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class LokiGravityMixin {
    @Shadow private float standingEyeHeight;
    @Inject(method="getFinalGravity",at=@At("RETURN"),cancellable=true)
    private void gexpress$reverseGravity(CallbackInfoReturnable<Double> cir){if(LokiGravity.flipped((Entity)(Object)this))cir.setReturnValue(-cir.getReturnValueD());}
    @Inject(method="move",at=@At("TAIL"))
    private void gexpress$ceilingGround(MovementType type,Vec3d movement,CallbackInfo ci){
        Entity entity=(Entity)(Object)this;if(!LokiGravity.flipped(entity))return;
        entity.setOnGround(entity.verticalCollision&&movement.y>0);entity.fallDistance=0;
    }
    @Inject(method="getStandingEyeHeight",at=@At("RETURN"),cancellable=true)
    private void gexpress$upsideDownCameraHeight(CallbackInfoReturnable<Float> cir){Entity entity=(Entity)(Object)this;if(LokiGravity.flipped(entity))cir.setReturnValue(entity.getHeight()-cir.getReturnValueF());}
    @Inject(method="getEyeY",at=@At("RETURN"),cancellable=true)
    private void gexpress$upsideDownEyeY(CallbackInfoReturnable<Double> cir){Entity entity=(Entity)(Object)this;if(LokiGravity.flipped(entity))cir.setReturnValue(entity.getY()+entity.getHeight()-standingEyeHeight);}
    @Inject(method="getEyePos",at=@At("RETURN"),cancellable=true)
    private void gexpress$upsideDownEye(CallbackInfoReturnable<Vec3d> cir){Entity entity=(Entity)(Object)this;if(LokiGravity.flipped(entity))cir.setReturnValue(new Vec3d(entity.getX(),entity.getEyeY(),entity.getZ()));}
}
