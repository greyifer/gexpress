package dev.mapselect.mixin;

import dev.doctor4t.wathe.item.GrenadeItem;
import dev.mapselect.game.GrenadeThrowPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = GrenadeItem.class, remap = false)
public abstract class GrenadeThrowSpeedMixin {
    @ModifyArg(method = "method_7836", at = @At(value = "INVOKE",
        target = "Ldev/doctor4t/wathe/entity/GrenadeEntity;method_24919(Lnet/minecraft/class_1297;FFFFF)V"), index = 4, remap = false)
    private float gexpress$longerThrow(float original) { return GrenadeThrowPhysics.SPEED; }
    // Keep throws aligned with the guide instead of adding a random deviation after aiming.
    @ModifyArg(method = "method_7836", at = @At(value = "INVOKE",
        target = "Ldev/doctor4t/wathe/entity/GrenadeEntity;method_24919(Lnet/minecraft/class_1297;FFFFF)V"), index = 5, remap = false)
    private float gexpress$accurateThrow(float original) { return 0.0F; }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="method_7836",at=@At(value="INVOKE",target="Lnet/minecraft/class_1937;method_8649(Lnet/minecraft/class_1297;)Z",remap=false))
    private boolean gexpress$throwFromHand(net.minecraft.world.World world,net.minecraft.entity.Entity grenade,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original,
        @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.entity.player.PlayerEntity player,
        @com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.util.Hand hand){
        grenade.setPosition(GrenadeThrowPhysics.origin(player,hand,1));return original.call(world,grenade);
    }
}
