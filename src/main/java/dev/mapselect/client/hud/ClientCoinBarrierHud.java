package dev.mapselect.client.hud;

import dev.mapselect.block.CoinBarrierBlock;
import dev.mapselect.client.screen.WeIcons;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientCoinBarrierHud {
	private static final double MAX_LABEL_DISTANCE = 32.0D;
	private static final int SCAN_RADIUS = 24;
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final double FACE_OFFSET = 0.18D;
	private static List<CoinBarrierBlock.Cluster> cachedClusters = List.of();
	private static BlockPos cachedOrigin = BlockPos.ORIGIN;
	private static long nextScanTick = Long.MIN_VALUE;

	private ClientCoinBarrierHud() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(ClientCoinBarrierHud::renderWorld);
		WorldRenderEvents.LAST.register(ClientCoinBarrierHud::renderWorld);
		HudRenderCallback.EVENT.register(ClientCoinBarrierHud::renderHudFallback);
	}

	private static void renderHudFallback(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client == null || client.player == null
				|| client.world == null || client.textRenderer == null || client.currentScreen != null
				|| !(client.crosshairTarget instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK
				|| !client.world.getBlockState(hit.getBlockPos()).isOf(MapSelectBlocks.COIN_BARRIER)) {
			return;
		}
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(client.world, hit.getBlockPos());
		if (cluster.positions().isEmpty()) return;
		String title = cluster.title().isBlank() ? "Barrier" : cluster.title();
		String price = cluster.price() + " " + WeIcons.COIN;
		int x = context.getScaledWindowWidth() / 2;
		int y = context.getScaledWindowHeight() / 2 - 32;
		drawCentered(context, client, title, x, y, 0xFFFFFFFF);
		drawCentered(context, client, price, x, y + 12, 0xFFFFD56E);
	}

	private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null || client.world == null || client.textRenderer == null) return;
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		boolean flushConsumers = false;
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
			flushConsumers = true;
		}
		if (matrices == null || consumers == null) return;
		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		int rendered = 0;

		for (CoinBarrierBlock.Cluster cluster : nearbyClusters(client)) {
			if (rendered >= 24) break;
			if (cluster.center().squaredDistanceTo(cameraPos) > MAX_LABEL_DISTANCE * MAX_LABEL_DISTANCE) continue;
			renderClusterLabel(cluster, cameraPos, matrices, consumers);
			rendered++;
		}
		if (flushConsumers && consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private static List<CoinBarrierBlock.Cluster> nearbyClusters(MinecraftClient client) {
		BlockPos origin = client.player.getBlockPos();
		long time = client.world.getTime();
		if (time < nextScanTick && origin.getManhattanDistance(cachedOrigin) <= 2) {
			return cachedClusters;
		}

		Set<BlockPos> visited = new HashSet<>();
		List<CoinBarrierBlock.Cluster> clusters = new ArrayList<>();

		for (BlockPos pos : BlockPos.iterateOutwards(origin, SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
			if (clusters.size() >= 24) break;
			BlockPos immutable = pos.toImmutable();
			if (visited.contains(immutable) || !client.world.getBlockState(immutable).isOf(MapSelectBlocks.COIN_BARRIER)) continue;
			CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(client.world, immutable);
			if (cluster.positions().isEmpty()) continue;
			visited.addAll(cluster.positions());
			clusters.add(cluster);
		}
		cachedClusters = List.copyOf(clusters);
		cachedOrigin = origin.toImmutable();
		nextScanTick = time + SCAN_INTERVAL_TICKS;
		return cachedClusters;
	}

	private static void renderClusterLabel(CoinBarrierBlock.Cluster cluster, Vec3d cameraPos,
			MatrixStack matrices, VertexConsumerProvider consumers) {
		String title = cluster.title().isBlank() ? "Barrier" : cluster.title();
		String price = cluster.price() + " " + WeIcons.COIN;
		for (Vec3d labelPos : labelPositions(Bounds.of(cluster.positions()))) {
			if (labelPos.squaredDistanceTo(cameraPos) > MAX_LABEL_DISTANCE * MAX_LABEL_DISTANCE) continue;
			matrices.push();
			matrices.translate(labelPos.x - cameraPos.x, labelPos.y - cameraPos.y, labelPos.z - cameraPos.z);
			DebugRenderer.drawString(matrices, consumers, title, 0.0D, 0.16D, 0.0D,
				0xFFFFFFFF, 0.04F, true, 0.0F, true);
			DebugRenderer.drawString(matrices, consumers, price, 0.0D, -0.16D, 0.0D,
				0xFFFFD56E, 0.04F, true, 0.0F, true);
			matrices.pop();
		}
	}

	private static List<Vec3d> labelPositions(Bounds bounds) {
		if (bounds.widthX() >= bounds.widthZ()) {
			return List.of(
				new Vec3d(bounds.midX(), bounds.midY(), bounds.minZ() - FACE_OFFSET),
				new Vec3d(bounds.midX(), bounds.midY(), bounds.maxZ() + FACE_OFFSET)
			);
		}
		return List.of(
			new Vec3d(bounds.minX() - FACE_OFFSET, bounds.midY(), bounds.midZ()),
			new Vec3d(bounds.maxX() + FACE_OFFSET, bounds.midY(), bounds.midZ())
		);
	}

	private static void drawCentered(DrawContext context, MinecraftClient client, String text, int x, int y, int color) {
		if (text == null || text.isBlank()) return;
		context.drawTextWithShadow(client.textRenderer, Text.literal(text),
			x - client.textRenderer.getWidth(text) / 2, y, color);
	}

	private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		private static Bounds of(List<BlockPos> positions) {
			if (positions.isEmpty()) return new Bounds(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (BlockPos pos : positions) {
				minX = Math.min(minX, pos.getX());
				minY = Math.min(minY, pos.getY());
				minZ = Math.min(minZ, pos.getZ());
				maxX = Math.max(maxX, pos.getX() + 1.0D);
				maxY = Math.max(maxY, pos.getY() + 1.0D);
				maxZ = Math.max(maxZ, pos.getZ() + 1.0D);
			}
			return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
		}

		private double midX() { return (minX + maxX) * 0.5D; }
		private double midY() { return (minY + maxY) * 0.5D; }
		private double midZ() { return (minZ + maxZ) * 0.5D; }
		private double widthX() { return maxX - minX; }
		private double widthZ() { return maxZ - minZ; }
	}
}
