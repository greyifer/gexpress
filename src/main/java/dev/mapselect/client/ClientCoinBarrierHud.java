package dev.mapselect.client;

import dev.mapselect.block.CoinBarrierBlock;
import dev.mapselect.client.screen.WeIcons;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ClientCoinBarrierHud {
	private ClientCoinBarrierHud() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(ClientCoinBarrierHud::renderWorld);
	}

	private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		CoinBarrierBlock.Cluster cluster = targetCluster(client);
		if (cluster.positions().isEmpty() || client.textRenderer == null) return;
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) return;
		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		Vec3d center = cluster.center();
		double distanceSq = center.squaredDistanceTo(cameraPos);
		if (distanceSq > 32.0D * 32.0D) return;

		Text title = cluster.title().isBlank() ? null : Text.literal(cluster.title());
		Text price = Text.literal(cluster.price() + " " + WeIcons.COIN);
		TextRenderer renderer = client.textRenderer;
		matrices.push();
		matrices.translate(center.x - cameraPos.x, center.y - cameraPos.y + 0.35D, center.z - cameraPos.z);
		matrices.multiply(camera.getRotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);
		if (title != null) {
			drawCentered(renderer, title, -10.0F, 0xFFFFFFFF, matrices, consumers);
			drawCentered(renderer, price, 2.0F, 0xFFFFD56E, matrices, consumers);
		} else {
			drawCentered(renderer, price, 0.0F, 0xFFFFD56E, matrices, consumers);
		}
		matrices.pop();
	}

	private static void drawCentered(TextRenderer renderer, Text text, float y, int color, MatrixStack matrices,
			VertexConsumerProvider consumers) {
		float x = -renderer.getWidth(text) / 2.0F;
		renderer.draw(text, x, y, color, false, matrices.peek().getPositionMatrix(),
			consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x99000000, 0xF000F0);
	}

	private static CoinBarrierBlock.Cluster targetCluster(MinecraftClient client) {
		if (ClientHudVisibility.shouldHide(client) || client.player == null || client.world == null
				|| client.textRenderer == null || !(client.crosshairTarget instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK) {
			return CoinBarrierBlock.Cluster.EMPTY;
		}
		BlockPos pos = hit.getBlockPos();
		if (!client.world.getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER)) return CoinBarrierBlock.Cluster.EMPTY;
		return CoinBarrierBlock.scan(client.world, pos);
	}
}
