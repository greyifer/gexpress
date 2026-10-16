package dev.mapselect.mixin.client;

import dev.mapselect.client.role.covenant.ClientCovenantState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerEntityRenderer.class)
public abstract class CovenantBatFormRendererMixin {
	@Unique private BatEntity gexpress$batFormEntity;
	@Unique private Object gexpress$batFormWorld;
	@Unique private final Map<UUID, Float> gexpress$batYawByPlayer = new HashMap<>();

	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"), cancellable = true)
	private void gexpress$renderBatForm(AbstractClientPlayerEntity player, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (!ClientCovenantState.isBatForm(player)) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return;
		BatEntity bat = gexpress$batFormEntity(client);
		if (bat == null) return;
		float renderYaw = gexpress$stableBatYaw(player);
		bat.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), renderYaw, 0.0F);
		bat.prevYaw = renderYaw;
		bat.prevPitch = 0.0F;
		bat.setHeadYaw(renderYaw);
		bat.prevHeadYaw = renderYaw;
		bat.setBodyYaw(renderYaw);
		bat.prevBodyYaw = renderYaw;
		bat.setRoosting(false);
		bat.setVelocity(player.getVelocity());
		bat.age = player.age;
		bat.roostingAnimationState.stop();
		bat.flyingAnimationState.startIfNotRunning(bat.age);
		client.getEntityRenderDispatcher().render(bat, 0.0D, 0.35D, 0.0D, renderYaw, tickDelta,
			matrices, vertexConsumers, light);
		ci.cancel();
	}

	@Unique
	private float gexpress$stableBatYaw(AbstractClientPlayerEntity player) {
		Vec3d velocity = player.getVelocity();
		double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
		float desired = horizontalSpeedSq > 0.0004D
			? (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z))
			: player.bodyYaw;
		float previous = gexpress$batYawByPlayer.getOrDefault(player.getUuid(), desired);
		float next = MathHelper.lerpAngleDegrees(0.22F, previous, desired);
		gexpress$batYawByPlayer.put(player.getUuid(), next);
		return next;
	}

	@Unique
	private BatEntity gexpress$batFormEntity(MinecraftClient client) {
		if (gexpress$batFormEntity != null && gexpress$batFormWorld == client.world) {
			return gexpress$batFormEntity;
		}
		gexpress$batFormWorld = client.world;
		gexpress$batYawByPlayer.clear();
		gexpress$batFormEntity = EntityType.BAT.create(client.world);
		if (gexpress$batFormEntity != null) {
			gexpress$batFormEntity.setRoosting(false);
			gexpress$batFormEntity.setNoGravity(true);
			gexpress$batFormEntity.flyingAnimationState.startIfNotRunning(gexpress$batFormEntity.age);
		}
		return gexpress$batFormEntity;
	}
}
