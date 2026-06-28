package dev.mapselect.block;

import dev.mapselect.mixin.DisplayEntityAccessor;
import dev.mapselect.mixin.TextDisplayEntityAccessor;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoinBarrierTextDisplayManager {
	private static final String TAG = "gexpress_coin_barrier_label";
	private static final String KEY_TAG_PREFIX = "gexpress_coin_barrier_label_key_";
	private static final String COIN = "\uE211";
	private static final int SCAN_RADIUS = 24;
	private static final int SYNC_INTERVAL_TICKS = 2;
	private static final double FACE_OFFSET = 0.12D;

	private CoinBarrierTextDisplayManager() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(CoinBarrierTextDisplayManager::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getTime() % SYNC_INTERVAL_TICKS != 0L) return;
		if (world.getPlayers().isEmpty()) {
			removeLoadedLabels(world);
			return;
		}
		sync(world);
	}

	private static void sync(ServerWorld world) {
		Map<String, Label> desired = desiredLabels(world);
		Set<String> handled = new HashSet<>();

		for (Entity entity : world.iterateEntities()) {
			if (!(entity instanceof TextDisplayEntity display) || !entity.getCommandTags().contains(TAG)) continue;
			String key = labelKey(entity);
			Label label = key == null ? null : desired.get(key);
			if (label == null || !handled.add(key)) {
				entity.discard();
				continue;
			}
			update(display, label);
		}

		for (Map.Entry<String, Label> entry : desired.entrySet()) {
			if (handled.contains(entry.getKey())) continue;
			spawn(world, entry.getValue());
		}
	}

	private static Map<String, Label> desiredLabels(ServerWorld world) {
		Set<BlockPos> visited = new HashSet<>();
		Map<String, Label> labels = new HashMap<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			for (BlockPos pos : BlockPos.iterateOutwards(player.getBlockPos(), SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
				BlockPos immutable = pos.toImmutable();
				if (visited.contains(immutable) || !isLoaded(world, immutable)
						|| !world.getBlockState(immutable).isOf(MapSelectBlocks.COIN_BARRIER)) {
					continue;
				}
				CoinBarrierBlock.Cluster cluster = CoinBarrierBlock.scan(world, immutable);
				if (cluster.positions().isEmpty()) continue;
				visited.addAll(cluster.positions());
				for (Label label : labelsFor(world, cluster)) {
					labels.put(label.key(), label);
				}
			}
		}
		return labels;
	}

	private static List<Label> labelsFor(ServerWorld world, CoinBarrierBlock.Cluster cluster) {
		Bounds bounds = Bounds.of(cluster.positions());
		String title = cluster.title().isBlank() ? "Red Ribbon" : cluster.title();
		Text text = labelText(title, cluster.price());
		String clusterKey = clusterKey(cluster.positions());
		float displayWidth = (float) Math.max(2.0D, Math.max(bounds.widthX(), bounds.widthZ()) + 1.0D);
		float displayHeight = 1.0F;
		RibbonSpan span = ribbonSpan(world, cluster.positions());
		if (span != null) {
			Vec3d midpoint = span.start().add(span.end()).multiply(0.5D);
			Vec3d direction = span.end().subtract(span.start());
			Vec3d normal = new Vec3d(-direction.z, 0.0D, direction.x);
			if (normal.lengthSquared() < 1.0E-6D) normal = new Vec3d(0.0D, 0.0D, 1.0D);
			normal = normal.normalize();
			double labelY = Math.max(span.start().y, span.end().y) + 0.65D;
			Vec3d positive = new Vec3d(midpoint.x, labelY, midpoint.z).add(normal.multiply(FACE_OFFSET));
			Vec3d negative = new Vec3d(midpoint.x, labelY, midpoint.z).subtract(normal.multiply(FACE_OFFSET));
			float positiveYaw = (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
			return List.of(
				new Label(clusterKey + "_positive", positive, positiveYaw, text, displayWidth, displayHeight),
				new Label(clusterKey + "_negative", negative, positiveYaw + 180.0F, text, displayWidth, displayHeight)
			);
		}
		double labelY = bounds.maxY() + 0.35D;
		if (bounds.widthX() >= bounds.widthZ()) {
			return List.of(
				new Label(clusterKey + "_north", new Vec3d(bounds.midX(), labelY, bounds.minZ() - FACE_OFFSET),
					180.0F, text, displayWidth, displayHeight),
				new Label(clusterKey + "_south", new Vec3d(bounds.midX(), labelY, bounds.maxZ() + FACE_OFFSET),
					0.0F, text, displayWidth, displayHeight)
			);
		}
		return List.of(
			new Label(clusterKey + "_west", new Vec3d(bounds.minX() - FACE_OFFSET, labelY, bounds.midZ()),
				90.0F, text, displayWidth, displayHeight),
			new Label(clusterKey + "_east", new Vec3d(bounds.maxX() + FACE_OFFSET, labelY, bounds.midZ()),
				-90.0F, text, displayWidth, displayHeight)
		);
	}

	private static RibbonSpan ribbonSpan(ServerWorld world, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (world.getBlockEntity(pos) instanceof CoinBarrierBlockEntity barrier && barrier.hasRibbonSpan()) {
				return new RibbonSpan(barrier.ribbonStart(), barrier.ribbonEnd());
			}
		}
		return null;
	}

	private static void spawn(ServerWorld world, Label label) {
		TextDisplayEntity display = new TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
		display.addCommandTag(TAG);
		display.addCommandTag(KEY_TAG_PREFIX + label.key());
		display.setNoGravity(true);
		display.setSilent(true);
		display.setInvulnerable(true);
		display.refreshPositionAndAngles(label.pos().x, label.pos().y, label.pos().z, label.yaw(), 0.0F);
		update(display, label);
		world.spawnEntity(display);
	}

	private static void update(TextDisplayEntity display, Label label) {
		display.refreshPositionAndAngles(label.pos().x, label.pos().y, label.pos().z, label.yaw(), 0.0F);
		DisplayEntityAccessor displayAccessor = (DisplayEntityAccessor) display;
		displayAccessor.gexpress$setBillboardMode(DisplayEntity.BillboardMode.FIXED);
		displayAccessor.gexpress$setViewRange(32.0F);
		displayAccessor.gexpress$setDisplayWidth(label.displayWidth());
		displayAccessor.gexpress$setDisplayHeight(label.displayHeight());
		displayAccessor.gexpress$setShadowRadius(0.0F);
		displayAccessor.gexpress$setShadowStrength(0.0F);
		displayAccessor.gexpress$setBrightness(Brightness.FULL);
		displayAccessor.gexpress$setTeleportDuration(1);

		TextDisplayEntityAccessor textAccessor = (TextDisplayEntityAccessor) display;
		textAccessor.gexpress$setText(label.text());
		textAccessor.gexpress$setLineWidth(220);
		textAccessor.gexpress$setTextOpacity((byte) -1);
		textAccessor.gexpress$setBackground(0x00000000);
		textAccessor.gexpress$setDisplayFlags((byte) TextDisplayEntity.SHADOW_FLAG);
	}

	private static void removeLoadedLabels(ServerWorld world) {
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof TextDisplayEntity && entity.getCommandTags().contains(TAG)) {
				entity.discard();
			}
		}
	}

	private static String labelKey(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(KEY_TAG_PREFIX)) return tag.substring(KEY_TAG_PREFIX.length());
		}
		return null;
	}

	private static boolean isLoaded(ServerWorld world, BlockPos pos) {
		return world.isInBuildLimit(pos)
			&& world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
	}

	private static Text labelText(String title, int price) {
		MutableText text = Text.literal(title).formatted(Formatting.WHITE);
		text.append(Text.literal("\n"));
		text.append(Text.literal(price + " " + COIN).formatted(Formatting.GOLD));
		return text;
	}

	private static String clusterKey(List<BlockPos> positions) {
		List<BlockPos> sorted = new ArrayList<>(positions);
		sorted.sort(Comparator.comparingInt(BlockPos::getX)
			.thenComparingInt(BlockPos::getY)
			.thenComparingInt(BlockPos::getZ));
		long hash = 0xcbf29ce484222325L;
		for (BlockPos pos : sorted) {
			hash = fnv(hash, pos.getX());
			hash = fnv(hash, pos.getY());
			hash = fnv(hash, pos.getZ());
		}
		return Long.toUnsignedString(hash, 16);
	}

	private static long fnv(long hash, int value) {
		long out = hash;
		out ^= value;
		out *= 0x100000001b3L;
		return out;
	}

	private record Label(String key, Vec3d pos, float yaw, Text text, float displayWidth, float displayHeight) {}
	private record RibbonSpan(Vec3d start, Vec3d end) {}

	private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		private static Bounds of(List<BlockPos> positions) {
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
		private double height() { return maxY - minY; }
	}
}
