package dev.mapselect.client;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.registry.MapSelectItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

public class C4BackFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private final ItemStack c4Stack;

	public C4BackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
		this.c4Stack = new ItemStack(MapSelectItems.C4);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
	                   AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
	                   float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (entity.isInvisible()) return;
		if (!C4BackComponent.hasC4(entity)) return;

		matrices.push();
		this.getContextModel().body.rotate(matrices);
		// Body-local space. These values are exposed in the dev-only G'Express Options tab.
		matrices.translate(GexpressConfig.getC4BackOffsetX(), GexpressConfig.getC4BackOffsetY(), GexpressConfig.getC4BackOffsetZ());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_X, GexpressConfig.getC4BackRotationX());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Y, GexpressConfig.getC4BackRotationY());
		rotateIfNeeded(matrices, RotationAxis.POSITIVE_Z, GexpressConfig.getC4BackRotationZ());
		slantIfNeeded(matrices, GexpressConfig.getC4BackSlant());
		float scale = GexpressConfig.getC4BackScale();
		matrices.scale(scale, scale, scale);

		MinecraftClient mc = MinecraftClient.getInstance();
		ItemRenderer ir = mc.getItemRenderer();
		ir.renderItem(this.c4Stack, ModelTransformationMode.FIXED, light,
			OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, mc.world, 0);
		matrices.pop();
	}

	private static void rotateIfNeeded(MatrixStack matrices, RotationAxis axis, float degrees) {
		if (degrees != 0.0F) {
			matrices.multiply(axis.rotationDegrees(degrees));
		}
	}

	private static void slantIfNeeded(MatrixStack matrices, float degrees) {
		if (degrees != 0.0F) {
			matrices.multiply(new Quaternionf().rotationAxis((float) Math.toRadians(degrees), 0.70710677F, 0.0F, 0.70710677F));
		}
	}
}
