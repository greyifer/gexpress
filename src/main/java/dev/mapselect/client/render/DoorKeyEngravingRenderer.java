package dev.mapselect.client.render;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DoorKeyEngravingRenderer {
	private static final int SCAN_RADIUS = 18;
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final double MAX_DISTANCE = 28.0D;
	private static final double SURFACE_OFFSET = 0.072D;
	private static final double OPEN_SLIDE_OFFSET = 14.0D / 16.0D;
	private static final float OPEN_KEYFRAME_START_SECONDS = 0.10F;
	private static final float OPEN_KEYFRAME_END_SECONDS = 0.70F;
	private static final float TEXT_SCALE = 0.0105F;
	private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
	private static List<DoorBlockEntity> cachedDoors = List.of();
	private static BlockPos cachedOrigin = BlockPos.ORIGIN;
	private static long nextScanTick = Long.MIN_VALUE;

	private DoorKeyEngravingRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(DoorKeyEngravingRenderer::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.textRenderer == null) return;
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
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		double renderTime = client.world.getTime() + tickDelta;
		for (DoorBlockEntity door : nearbyDoors(client)) {
			String label = labelForKey(door.getKeyName());
			if (label.isBlank()) continue;
			Vec3d center = doorPanelCenter(door, renderTime);
			if (center.squaredDistanceTo(cameraPos) > MAX_DISTANCE * MAX_DISTANCE) continue;
			Direction facing = door.getFacing();
			renderOnSide(client.textRenderer, matrices, consumers, cameraPos, center, facing, label);
			renderOnSide(client.textRenderer, matrices, consumers, cameraPos, center, facing.getOpposite(), label);
		}
		if (flushConsumers && consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
	}

	private static List<DoorBlockEntity> nearbyDoors(MinecraftClient client) {
		BlockPos origin = client.player.getBlockPos();
		long time = client.world.getTime();
		if (time < nextScanTick && origin.getManhattanDistance(cachedOrigin) <= 2) return cachedDoors;

		List<DoorBlockEntity> doors = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterateOutwards(origin, SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
			BlockEntity blockEntity = client.world.getBlockEntity(pos);
			if (blockEntity instanceof DoorBlockEntity door && !labelForKey(door.getKeyName()).isBlank()) {
				doors.add(door);
			}
		}
		cachedDoors = List.copyOf(doors);
		cachedOrigin = origin.toImmutable();
		nextScanTick = time + SCAN_INTERVAL_TICKS;
		return cachedDoors;
	}

	private static void renderOnSide(TextRenderer textRenderer, MatrixStack matrices, VertexConsumerProvider consumers,
			Vec3d cameraPos, Vec3d center, Direction side, String label) {
		Vec3d normal = Vec3d.of(side.getVector());
		Vec3d pos = center.add(normal.multiply(SURFACE_OFFSET));
		if (cameraPos.subtract(pos).dotProduct(normal) <= 0.0D) return;
		String text = trimToWidth(textRenderer, label, 92);
		int width = textRenderer.getWidth(text);

		matrices.push();
		matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - side.asRotation()));
		matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		float x = -width / 2.0F;
		textRenderer.draw(text, x + 0.8F, 0.8F, 0xAA000000, false, matrix, consumers,
			TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		textRenderer.draw(text, x, 0.0F, 0xFFFFFFFF, false, matrix, consumers,
			TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		matrices.pop();
	}

	private static Vec3d doorPanelCenter(DoorBlockEntity door, double renderTime) {
		Vec3d center = Vec3d.ofCenter(door.getPos()).add(0.0D, 0.72D, 0.0D);
		float openProgress = slideProgress(door);
		if (openProgress <= 0.001F) return center;
		Vec3d slide = Vec3d.of(door.getFacing().rotateYClockwise().getVector());
		return center.add(slide.multiply(OPEN_SLIDE_OFFSET * openProgress));
	}

	private static float slideProgress(DoorBlockEntity door) {
		boolean open = door.isOpen();
		if (door.state == null || !door.state.isRunning()) return open ? 1.0F : 0.0F;
		float seconds = door.state.getTimeRunning() / 1000.0F;
		float t = (seconds - OPEN_KEYFRAME_START_SECONDS)
			/ (OPEN_KEYFRAME_END_SECONDS - OPEN_KEYFRAME_START_SECONDS);
		float eased = watheDoorEase(t);
		return open ? eased : 1.0F - eased;
	}

	private static float watheDoorEase(float keyframeProgress) {
		float t = Math.max(0.0F, Math.min(1.0F, keyframeProgress));
		return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * t);
	}

	private static String labelForKey(String value) {
		String clean = cleanLabel(value);
		if (clean.isBlank()) return "";
		Matcher matcher = FIRST_NUMBER.matcher(clean);
		return matcher.find() ? matcher.group() : clean;
	}

	private static String trimToWidth(TextRenderer textRenderer, String value, int width) {
		if (textRenderer.getWidth(value) <= width) return value;
		String suffix = "...";
		int suffixWidth = textRenderer.getWidth(suffix);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			if (textRenderer.getWidth(out.toString()) + textRenderer.getWidth(String.valueOf(value.charAt(i))) + suffixWidth > width) break;
			out.append(value.charAt(i));
		}
		return out + suffix;
	}

	private static String cleanLabel(String value) {
		return value == null ? "" : value.trim();
	}

}
