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
		int presetIndex = C4BackComponent.getPresetIndex(entity);
		C4ModelTransforms.applyPlacement(matrices, GexpressConfig.getC4PlacementPreset(presetIndex));

		MinecraftClient mc = MinecraftClient.getInstance();
		ItemRenderer ir = mc.getItemRenderer();
		ir.renderItem(this.c4Stack, ModelTransformationMode.FIXED, light,
			OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, mc.world, 0);
		matrices.pop();
	}
}
