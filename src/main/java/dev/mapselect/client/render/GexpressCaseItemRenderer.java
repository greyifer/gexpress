package dev.mapselect.client.render;

import dev.mapselect.item.GexpressCaseItem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class GexpressCaseItemRenderer extends GeoItemRenderer<GexpressCaseItem> {
	public GexpressCaseItemRenderer(GeoModel<GexpressCaseItem> model) {
		super(model);
		withScale(1.08F);
		useAlternateGuiLighting();
	}

	@Override
	public void preRender(MatrixStack matrices, GexpressCaseItem animatable, BakedGeoModel model,
			@Nullable VertexConsumerProvider vertexConsumers, @Nullable VertexConsumer buffer, boolean isReRender,
			float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(matrices, animatable, model, vertexConsumers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			matrices.translate(0.0F, -0.08F, 0.0F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0F));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(152.0F));
		}
	}
}
