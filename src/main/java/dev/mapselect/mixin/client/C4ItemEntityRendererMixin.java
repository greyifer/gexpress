package dev.mapselect.mixin.client;

import dev.mapselect.client.C4ModelTransforms;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class C4ItemEntityRendererMixin {
	@Shadow @Final private ItemRenderer itemRenderer;

	@Inject(
		method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void gexpress$renderC4AsPlantedCharge(ItemEntity entity, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		ItemStack stack = entity.getStack();
		if (!stack.isOf(MapSelectItems.C4)) return;

		matrices.push();
		if (entity.hasNoGravity()) {
			C4ModelTransforms.rotateToSurface(matrices, entity.getYaw(tickDelta), entity.getPitch(tickDelta));
		} else {
			rotateToVelocity(matrices, entity, tickDelta);
		}
		int presetCount = GexpressConfig.getC4PlacementPresetCount();
		int presetIndex = C4PlacementPreset.indexFor(entity.getUuid(), presetCount);
		if (entity.hasNoGravity()) {
			C4ModelTransforms.applySurfacePlacement(matrices, GexpressConfig.getC4PlacementPreset(presetIndex));
		} else {
			C4ModelTransforms.applyPlacement(matrices, GexpressConfig.getC4PlacementPreset(presetIndex));
		}
		this.itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light,
			OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		ci.cancel();
	}

	@Unique
	private static void rotateToVelocity(MatrixStack matrices, ItemEntity entity, float tickDelta) {
		Vec3d velocity = entity.getVelocity();
		if (velocity.lengthSquared() <= 1.0E-6D) {
			C4ModelTransforms.rotateToSurface(matrices, entity.getYaw(tickDelta), entity.getPitch(tickDelta));
			return;
		}
		double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		float flightYaw = (float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN);
		float flightPitch = (float) (-MathHelper.atan2(velocity.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
		C4ModelTransforms.rotateToSurface(matrices, flightYaw, flightPitch);
	}
}
