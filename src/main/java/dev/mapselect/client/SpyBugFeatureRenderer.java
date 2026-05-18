package dev.mapselect.client;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.role.spy.SpyBugComponent;
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

public class SpyBugFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private final ItemStack bugStack;

	public SpyBugFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
		this.bugStack = new ItemStack(MapSelectItems.SPY_BUG);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
	                   AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
	                   float tickDelta, float animationProgress, float headYaw, float headPitch) {
		C4PlacementPreset previewPreset = ClientModelAttachmentPreview.spyBugPreset(entity);
		if (entity.isInvisible() && previewPreset == null) return;
		if (!SpyBugComponent.hasBug(entity) && previewPreset == null) return;

		matrices.push();
		this.getContextModel().body.rotate(matrices);
		C4ModelTransforms.applyPlacement(matrices, previewPreset == null
			? GexpressConfig.getSpyBugPlacementPreset()
			: previewPreset);

		MinecraftClient mc = MinecraftClient.getInstance();
		ItemRenderer itemRenderer = mc.getItemRenderer();
		itemRenderer.renderItem(this.bugStack, ModelTransformationMode.FIXED, light,
			OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, mc.world, 0);
		matrices.pop();
	}
}
