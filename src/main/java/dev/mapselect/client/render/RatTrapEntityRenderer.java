package dev.mapselect.client.render;

import dev.mapselect.entity.RatTrapEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Constant-size floor rendering whose alpha, rather than scale, fades over the trap's lifetime. */
public final class RatTrapEntityRenderer extends EntityRenderer<RatTrapEntity> {
	private static final Identifier TEXTURE = Identifier.of("noellesroles",
		"textures/item/role_mine.png");
	private static final float HALF_SIZE = 0.41F;

	public RatTrapEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 0.0F;
	}

	@Override
	public void render(RatTrapEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		int alpha = MathHelper.clamp(Math.round(entity.remainingOpacity(tickDelta) * 255.0F), 0, 255);
		if (alpha <= 0) return;
		matrices.push();
		matrices.translate(0.0F, 0.013F + (entity.hashCode() & 15) / 16000.0F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		vertex(entry, vertices, -HALF_SIZE, -HALF_SIZE, 0.0F, 1.0F, light, alpha);
		vertex(entry, vertices, -HALF_SIZE, HALF_SIZE, 0.0F, 0.0F, light, alpha);
		vertex(entry, vertices, HALF_SIZE, HALF_SIZE, 1.0F, 0.0F, light, alpha);
		vertex(entry, vertices, HALF_SIZE, -HALF_SIZE, 1.0F, 1.0F, light, alpha);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer vertices,
			float x, float z, float u, float v, int light, int alpha) {
		vertices.vertex(entry, x, 0.0F, z)
			.color(255, 255, 255, alpha)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(light)
			.normal(entry, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public Identifier getTexture(RatTrapEntity entity) {
		return TEXTURE;
	}
}
