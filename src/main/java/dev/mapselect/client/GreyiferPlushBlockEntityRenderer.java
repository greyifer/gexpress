package dev.mapselect.client;

import dev.mapselect.block.GreyiferPlushBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

public class GreyiferPlushBlockEntityRenderer implements BlockEntityRenderer<GreyiferPlushBlockEntity> {
	private final BlockRenderManager renderManager;

	public GreyiferPlushBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.renderManager = context.getRenderManager();
	}

	@Override
	public void render(GreyiferPlushBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
		matrices.push();

		double squash = entity.squash;
		double previousSquash = squash * 3.0D;
		float squeeze = (float) Math.pow(1.0D - 1.0D / (1.0D + MathHelper.lerp(tickDelta, previousSquash, squash)), 2.0D);

		matrices.scale(1.0F, 1.0F - squeeze, 1.0F);
		matrices.translate(0.5D, 0.0D, 0.5D);
		matrices.scale(1.0F + squeeze / 2.0F, 1.0F, 1.0F + squeeze / 2.0F);
		matrices.translate(-0.5D, 0.0D, -0.5D);

		BlockState state = entity.getCachedState();
		this.renderManager.getModelRenderer().render(
			matrices.peek(),
			consumers.getBuffer(RenderLayers.getEntityBlockLayer(state, false)),
			state,
			this.renderManager.getModel(state),
			1.0F,
			1.0F,
			1.0F,
			light,
			overlay
		);

		matrices.pop();
	}
}
