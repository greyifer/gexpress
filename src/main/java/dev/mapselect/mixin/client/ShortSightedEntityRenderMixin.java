package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientShortSightedState;
import dev.mapselect.client.ClientVultureState;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class ShortSightedEntityRenderMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void gexpress$hideDistantEntities(E entity, double x, double y, double z,
			float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || entity == null) return;
		if (ClientVultureState.shouldHideBellyEntity(client, entity)) {
			ci.cancel();
			return;
		}

		if (!ClientShortSightedState.isShortSighted()) return;
		Entity viewer = client.cameraEntity != null ? client.cameraEntity : client.player;
		if (viewer == null || entity == viewer) return;

		double range = GexpressConfig.getShortSightedEntityRange();
		double paddedRange = range + entity.getWidth() * 0.5D;
		if (viewer.squaredDistanceTo(entity) > paddedRange * paddedRange) {
			ci.cancel();
		}
	}
}
