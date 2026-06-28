package dev.mapselect.client.render;

import dev.mapselect.block.GoldBeveragePlateBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

public class GoldBeveragePlateBlockEntityRenderer implements BlockEntityRenderer<GoldBeveragePlateBlockEntity> {
	private final ItemRenderer itemRenderer;

	public GoldBeveragePlateBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(GoldBeveragePlateBlockEntity plate, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (plate.isDrink()) {
			renderItems(plate, matrices, vertexConsumers, light, overlay, 0.225F, false);
		} else {
			renderItems(plate, matrices, vertexConsumers, light, overlay, 0.0375F, true);
		}
	}

	private void renderItems(GoldBeveragePlateBlockEntity plate, MatrixStack matrices,
							 VertexConsumerProvider vertexConsumers, int light, int overlay,
							 float yOffset, boolean tiltFood) {
		List<ItemStack> items = plate.getStoredItems();
		int count = items.size();
		if (count == 0) return;

		for (int i = 0; i < count; i++) {
			ItemStack stack = items.get(i);
			if (stack == null || stack.isEmpty()) continue;

			double angle = Math.PI * 2.0D / count * i;
			double x = 0.5D + 0.25D * Math.cos(angle);
			double z = 0.5D + 0.25D * Math.sin(angle);
			float yaw = (float) Math.toDegrees(angle) + 90.0F;

			matrices.push();
			matrices.translate(x, yOffset, z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
			if (tiltFood) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(75.0F));
			matrices.scale(0.4F, 0.4F, 0.4F);
			itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, light, overlay, matrices,
				vertexConsumers, plate.getWorld(), 0);
			matrices.pop();
		}
	}
}
