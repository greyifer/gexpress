package dev.mapselect.client.render;

import dev.mapselect.MapSelect;
import dev.mapselect.block.CoinBarrierBlock;
import dev.mapselect.block.CoinBarrierBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CoinBarrierBlockEntityRenderer implements BlockEntityRenderer<CoinBarrierBlockEntity> {
	private static final Identifier TEXTURE = Identifier.of(MapSelect.MOD_ID, "textures/misc/red_ribbon_strip.png");
	private static final double RIBBON_HEIGHT = 0.25D;
	private static final double RIBBON_DEPTH = 0.125D;
	private static final double MAX_DISTANCE = 96.0D;
	private static final long CACHE_TICKS = 10L;
	private static final Map<BlockPos, CachedRibbon> CACHE = new HashMap<>();

	public CoinBarrierBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(CoinBarrierBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light, int overlay) {
		World world = entity.getWorld();
		MinecraftClient client = MinecraftClient.getInstance();
		if (world == null || client == null || client.gameRenderer == null) return;
		CachedRibbon ribbon = cachedRibbon(world, entity.getPos());
		if (ribbon == null || !entity.getPos().equals(ribbon.owner())) return;
		Vec3d camera = client.gameRenderer.getCamera().getPos();
		if (ribbon.midpoint().squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) return;
		renderRibbon(entity.getPos(), ribbon.start(), ribbon.end(), matrices, consumers, light);
	}

	private static void renderRibbon(BlockPos origin, Vec3d start, Vec3d end,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		double length = start.distanceTo(end);
		if (length < 0.05D) return;
		int segments = Math.max(4, Math.min(192, (int) Math.ceil(length * 3.0D)));
		double sag = Math.min(0.55D, 0.08D + length * 0.025D);
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		MatrixStack.Entry entry = matrices.peek();

		for (int i = 0; i < segments; i++) {
			double t0 = i / (double) segments;
			double t1 = (i + 1) / (double) segments;
			Vec3d a = ribbonPoint(start, end, t0, sag);
			Vec3d b = ribbonPoint(start, end, t1, sag);
			Vec3d direction = b.subtract(a).normalize();
			Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
			Vec3d depth = horizontal.lengthSquared() < 1.0E-6D
				? new Vec3d(RIBBON_DEPTH * 0.5D, 0.0D, 0.0D)
				: new Vec3d(-horizontal.z, 0.0D, horizontal.x).normalize().multiply(RIBBON_DEPTH * 0.5D);
			Vec3d up = new Vec3d(0.0D, RIBBON_HEIGHT * 0.5D, 0.0D);
			Vec3d originVec = Vec3d.of(origin);
			Vec3d aTopFront = a.add(up).add(depth).subtract(originVec);
			Vec3d aBottomFront = a.subtract(up).add(depth).subtract(originVec);
			Vec3d bTopFront = b.add(up).add(depth).subtract(originVec);
			Vec3d bBottomFront = b.subtract(up).add(depth).subtract(originVec);
			Vec3d aTopBack = a.add(up).subtract(depth).subtract(originVec);
			Vec3d aBottomBack = a.subtract(up).subtract(depth).subtract(originVec);
			Vec3d bTopBack = b.add(up).subtract(depth).subtract(originVec);
			Vec3d bBottomBack = b.subtract(up).subtract(depth).subtract(originVec);
			Vec3d frontNormal = depth.normalize();
			float u0 = (float) t0;
			float u1 = (float) t1;
			quad(vertices, entry, aTopFront, aBottomFront, bBottomFront, bTopFront, u0, u1, frontNormal, light);
			quad(vertices, entry, bTopBack, bBottomBack, aBottomBack, aTopBack, u1, u0, frontNormal.multiply(-1.0D), light);
			quad(vertices, entry, aTopBack, aTopFront, bTopFront, bTopBack, u0, u1, new Vec3d(0.0D, 1.0D, 0.0D), light);
			quad(vertices, entry, aBottomFront, aBottomBack, bBottomBack, bBottomFront, u0, u1, new Vec3d(0.0D, -1.0D, 0.0D), light);
			quad(vertices, entry, aTopBack, aBottomBack, aBottomFront, aTopFront, 0.0F, 1.0F, direction.multiply(-1.0D), light);
			quad(vertices, entry, bTopFront, bBottomFront, bBottomBack, bTopBack, 0.0F, 1.0F, direction, light);
		}
	}

	private static Vec3d ribbonPoint(Vec3d start, Vec3d end, double t, double sag) {
		double y = -sag * 4.0D * t * (1.0D - t);
		return start.lerp(end, t).add(0.0D, y, 0.0D);
	}

	private static void quad(VertexConsumer vertices, MatrixStack.Entry entry,
			Vec3d topLeft, Vec3d bottomLeft, Vec3d bottomRight, Vec3d topRight,
			float u0, float u1, Vec3d normal, int light) {
		vertex(vertices, entry, topLeft, u0, 0.0F, normal, light);
		vertex(vertices, entry, bottomLeft, u0, 1.0F, normal, light);
		vertex(vertices, entry, bottomRight, u1, 1.0F, normal, light);
		vertex(vertices, entry, topRight, u1, 0.0F, normal, light);
	}

	private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d pos,
			float u, float v, Vec3d normal, int light) {
		vertices.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z)
			.color(255, 255, 255, 255)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(light)
			.normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private static CachedRibbon cachedRibbon(World world, BlockPos pos) {
		long time = world.getTime();
		CachedRibbon cached = CACHE.get(pos);
		if (cached != null && cached.expiresAt() >= time) return cached;
		CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(world, pos);
		if (cluster.positions().isEmpty()) return null;
		BlockPos owner = cluster.positions().stream().min(Comparator
			.comparingInt(BlockPos::getX)
			.thenComparingInt(BlockPos::getY)
			.thenComparingInt(BlockPos::getZ)).orElse(pos);
		RibbonSpan span = storedSpan(world, cluster.positions());
		if (span == null) span = inferredSpan(cluster.positions());
		CachedRibbon next = new CachedRibbon(owner, span.start(), span.end(), time + CACHE_TICKS);
		for (BlockPos clusterPos : cluster.positions()) CACHE.put(clusterPos.toImmutable(), next);
		return next;
	}

	private static RibbonSpan storedSpan(World world, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (world.getBlockEntity(pos) instanceof CoinBarrierBlockEntity barrier && barrier.hasRibbonSpan()) {
				return new RibbonSpan(barrier.ribbonStart(), barrier.ribbonEnd());
			}
		}
		return null;
	}

	private static RibbonSpan inferredSpan(List<BlockPos> positions) {
		BlockPos first = positions.getFirst();
		BlockPos a = farthest(first, positions);
		BlockPos b = farthest(a, positions);
		return new RibbonSpan(Vec3d.ofCenter(a), Vec3d.ofCenter(b));
	}

	private static BlockPos farthest(BlockPos origin, List<BlockPos> positions) {
		BlockPos farthest = origin;
		double best = -1.0D;
		for (BlockPos candidate : positions) {
			double distance = candidate.getSquaredDistance(origin);
			if (distance > best) {
				best = distance;
				farthest = candidate;
			}
		}
		return farthest;
	}

	@Override
	public boolean rendersOutsideBoundingBox(CoinBarrierBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return (int) MAX_DISTANCE;
	}

	private record RibbonSpan(Vec3d start, Vec3d end) {}

	private record CachedRibbon(BlockPos owner, Vec3d start, Vec3d end, long expiresAt) {
		private Vec3d midpoint() {
			return start.add(end).multiply(0.5D);
		}
	}
}
