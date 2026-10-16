package dev.mapselect.game;

import net.minecraft.util.math.Vec3d;

/** Shared launch speed and vanilla thrown-projectile motion used by the aiming guide. */
public final class GrenadeThrowPhysics {
    public static final float SPEED = 0.6F;
    private GrenadeThrowPhysics() {}
    public static Vec3d origin(net.minecraft.entity.player.PlayerEntity player,net.minecraft.util.Hand hand,float delta){
        double yaw=Math.toRadians(player.getYaw(delta));
        double side=player.getMainArm()==net.minecraft.util.Arm.RIGHT?1:-1;if(hand==net.minecraft.util.Hand.OFF_HAND)side=-side;
        Vec3d eye=player.getLerpedPos(delta).add(0,player.getEyeY()-player.getY(),0);
        Vec3d wanted=eye.add(-Math.cos(yaw)*side*.28,-.32,-Math.sin(yaw)*side*.28).add(player.getRotationVec(delta).multiply(.25));
        var hit=player.getWorld().raycast(new net.minecraft.world.RaycastContext(eye,wanted,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        return hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS?wanted:hit.getPos().subtract(wanted.subtract(eye).normalize().multiply(.03));
    }
    public static Vec3d launch(Vec3d direction, Vec3d movement, boolean onGround) {
        return direction.normalize().multiply(SPEED).add(movement.x, onGround ? 0 : movement.y, movement.z);
    }
    public static Vec3d nextVelocity(Vec3d velocity, boolean water) {
        return velocity.multiply(water ? (double)0.8F : (double)0.99F).add(0, -0.03, 0);
    }
}
