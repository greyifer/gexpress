package dev.mapselect.client.render;

import dev.doctor4t.wathe.item.GrenadeItem;
import dev.mapselect.game.GrenadeThrowPhysics;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Local aiming guide; no simulated entities, particles, or network traffic. */
public final class GrenadeTrajectoryRenderer {
    private static List<Vec3d> path = List.of();
    private static Vec3d impactNormal;
    private static long nextTrace;
    private GrenadeTrajectoryRenderer() {}
    public static void register() { WorldRenderEvents.BEFORE_DEBUG_RENDER.register(GrenadeTrajectoryRenderer::render); }
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (!dev.mapselect.config.GexpressConfig.grenadeTrajectoryPreview || player == null || client.world == null || player.isSpectator() || client.currentScreen != null
            || client.options.hudHidden || client.getCameraEntity() != player
            || !(player.getMainHandStack().getItem() instanceof GrenadeItem
                || player.getOffHandStack().getItem() instanceof GrenadeItem)
            || context.matrixStack() == null || context.consumers() == null) {
            path = List.of(); nextTrace = 0; return;
        }
        long now = System.nanoTime();
        // Cap world queries at 40 Hz even on clients rendering hundreds of frames per second.
        if (now >= nextTrace) { trace(client); nextTrace = now + 25_000_000L; }
        if (path.size() < 2) return;
        var matrices = context.matrixStack().peek();
        var lines = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        Vec3d camera = context.camera().getPos();
        for (int i = 1; i < path.size(); i++) line(matrices, lines, path.get(i-1).subtract(camera), path.get(i).subtract(camera));
        if (impactNormal != null) {
            Vec3d center = path.getLast().add(impactNormal.multiply(0.025)).subtract(camera);
            Vec3d reference = Math.abs(impactNormal.y) > 0.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
            Vec3d u = impactNormal.crossProduct(reference).normalize().multiply(0.22);
            Vec3d v = impactNormal.crossProduct(u).normalize().multiply(0.22);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12, b = (i + 1) * Math.PI / 12;
                line(matrices, lines, center.add(u.multiply(Math.cos(a))).add(v.multiply(Math.sin(a))),
                    center.add(u.multiply(Math.cos(b))).add(v.multiply(Math.sin(b))));
            }
            line(matrices, lines, center.subtract(u), center.add(u));
            line(matrices, lines, center.subtract(v), center.add(v));
        }
    }
    private static void trace(MinecraftClient client) {
        var player = client.player;
        Vec3d position = GrenadeThrowPhysics.origin(player,player.getMainHandStack().getItem() instanceof GrenadeItem?net.minecraft.util.Hand.MAIN_HAND:net.minecraft.util.Hand.OFF_HAND,client.getRenderTickCounter().getTickDelta(false));
        Vec3d velocity = GrenadeThrowPhysics.launch(player.getRotationVec(1), player.getVelocity(), player.isOnGround());
        List<Vec3d> points = new ArrayList<>(65); points.add(position); impactNormal = null;
        for (int tick = 0; tick < 100; tick++) {
            Vec3d end = position.add(velocity);
            if (!client.world.getChunkManager().isChunkLoaded(BlockPos.ofFloored(end).getX() >> 4, BlockPos.ofFloored(end).getZ() >> 4)) break;
            BlockHitResult block = client.world.raycast(new RaycastContext(position, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d limit = block.getType() == HitResult.Type.MISS ? end : block.getPos();
            EntityHitResult entity = ProjectileUtil.getEntityCollision(client.world, player, position, limit,
                new Box(position, end).expand(1), candidate -> !candidate.isSpectator() && candidate.canHit()
                    && !candidate.isConnectedThroughVehicle(player), 0.3F);
            if (entity != null) {
                points.add(entity.getPos()); impactNormal = velocity.normalize().negate(); break;
            }
            if (block.getType() != HitResult.Type.MISS) {
                points.add(block.getPos()); impactNormal = Vec3d.of(block.getSide().getVector()); break;
            }
            points.add(end);
            boolean water = client.world.getFluidState(BlockPos.ofFloored(position)).isIn(FluidTags.WATER);
            velocity = GrenadeThrowPhysics.nextVelocity(velocity, water); position = end;
            if (position.y < client.world.getBottomY() - 1) break;
        }
        path = points;
    }
    // A square tube stays thick at every viewing angle; ordinary GL lines vary with render state.
    private static void line(MatrixStack.Entry matrix,VertexConsumer vertices,Vec3d from,Vec3d to){
        Vec3d direction=to.subtract(from).normalize();if(direction.lengthSquared()<.5)return;
        Vec3d reference=Math.abs(direction.y)>.9?new Vec3d(1,0,0):new Vec3d(0,1,0);
        Vec3d u=direction.crossProduct(reference).normalize().multiply(.025),v=direction.crossProduct(u).normalize().multiply(.025);
        Vec3d[] corners={u.add(v),u.subtract(v),u.negate().subtract(v),u.negate().add(v)};
        for(int face=0;face<4;face++){
            Vec3d a=corners[face],b=corners[(face+1)%4];
            vertex(matrix,vertices,from.add(a));vertex(matrix,vertices,to.add(a));vertex(matrix,vertices,to.add(b));vertex(matrix,vertices,from.add(b));
        }
    }
    private static void vertex(MatrixStack.Entry matrix,VertexConsumer vertices,Vec3d point){vertices.vertex(matrix,(float)point.x,(float)point.y,(float)point.z).color(255,255,255,95);}
}
