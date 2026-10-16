package dev.mapselect.client.render;

import dev.mapselect.MapSelect;
import dev.mapselect.block.LargeWoodDoorBlock;
import dev.mapselect.block.LargeWoodDoorBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class LargeWoodDoorBlockEntityRenderer implements BlockEntityRenderer<LargeWoodDoorBlockEntity> {
	private static final Identifier TEXTURE = Identifier.of(MapSelect.MOD_ID,
		"textures/entity/large_wood_door/large_wood_door_entity_texture.png");
	private static final float TEXTURE_SIZE = 64.0F;
	private static final float PIXELS_PER_BLOCK = 16.0F;
	private static final float OPEN_KEYFRAME_START_SECONDS = 0.10F;
	private static final float OPEN_KEYFRAME_END_SECONDS = 0.70F;
	private static final float OPEN_SLIDE_PIXELS = 14.0F;
	private static final DoorCuboid[] CUBOIDS = {
		new DoorCuboid(-8.0F, -48.0F, -1.0F, 8.0F, 0.0F, 1.0F,
			uv(30.0F, 2.0F, 16.0F, 48.0F),
			uv(28.0F, 2.0F, 2.0F, 48.0F),
			uv(48.0F, 2.0F, 16.0F, 48.0F),
			uv(46.0F, 2.0F, 2.0F, 48.0F),
			uv(46.0F, 2.0F, -16.0F, -2.0F),
			uv(62.0F, 0.0F, -16.0F, 2.0F)),
		new DoorCuboid(-8.0F, -2.0F, -1.0F, 8.0F, 0.0F, 1.0F,
			uv(30.0F, 58.0F, 16.0F, 2.0F),
			uv(28.0F, 58.0F, 2.0F, 2.0F),
			uv(48.0F, 58.0F, 16.0F, 2.0F),
			uv(46.0F, 58.0F, 2.0F, 2.0F),
			uv(46.0F, 58.0F, -16.0F, -2.0F),
			uv(62.0F, 56.0F, -16.0F, 2.0F)),
		new DoorCuboid(-8.0F, -48.0F, -1.0F, 8.0F, -46.0F, 1.0F,
			uv(30.0F, 62.0F, 16.0F, 2.0F),
			uv(28.0F, 62.0F, 2.0F, 2.0F),
			uv(48.0F, 62.0F, 16.0F, 2.0F),
			uv(46.0F, 62.0F, 2.0F, 2.0F),
			uv(46.0F, 62.0F, -16.0F, -2.0F),
			uv(62.0F, 60.0F, -16.0F, 2.0F)),
		new DoorCuboid(-6.01F, -46.01F, -0.01F, 6.01F, -1.01F, 0.01F,
			uv(0.02F, 0.02F, 12.02F, 45.0F),
			uv(0.0F, 0.02F, 0.02F, 45.0F),
			uv(12.06F, 0.02F, 12.02F, 45.0F),
			uv(12.04F, 0.02F, 0.02F, 45.0F),
			uv(12.04F, 0.02F, -12.02F, -0.02F),
			uv(24.06F, 0.0F, -12.02F, 0.02F)),
		new DoorCuboid(-8.0F, -46.0F, -1.0F, -6.0F, -2.0F, 1.0F,
			uv(2.0F, 36.0F, 2.0F, 44.0F),
			uv(0.0F, 36.0F, 2.0F, 44.0F),
			uv(6.0F, 36.0F, 2.0F, 44.0F),
			uv(4.0F, 36.0F, 2.0F, 44.0F),
			uv(4.0F, 36.0F, -2.0F, -2.0F),
			uv(6.0F, 34.0F, -2.0F, 2.0F)),
		new DoorCuboid(6.0F, -46.0F, -1.0F, 8.0F, -2.0F, 1.0F,
			uv(10.0F, 36.0F, 2.0F, 44.0F),
			uv(8.0F, 36.0F, 2.0F, 44.0F),
			uv(14.0F, 36.0F, 2.0F, 44.0F),
			uv(12.0F, 36.0F, 2.0F, 44.0F),
			uv(12.0F, 36.0F, -2.0F, -2.0F),
			uv(14.0F, 34.0F, -2.0F, 2.0F))
	};

	public LargeWoodDoorBlockEntityRenderer(BlockEntityRendererFactory.Context context) {}

	@Override
	public void render(LargeWoodDoorBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (entity.state != null) {
			entity.state.update(entity.getAge() + tickDelta, 1.0F);
		}

		matrices.push();
		matrices.translate(0.5F, 1.5F, 0.5F);
		matrices.scale(1.0F, -1.0F, 1.0F);
		ClientJuggernautDoorAnimation.Pose brokenPose =
			ClientJuggernautDoorAnimation.sample(entity, entity.getAge() + tickDelta);
		Direction slideDirection = LargeWoodDoorBlock.openSlideDirection(entity.getFacing());
		float slideBlocks = brokenPose == null ? slidePixels(entity) / PIXELS_PER_BLOCK : 0.0F;
		float brokenY = brokenPose == null ? 0.0F : brokenPose.positionY() / PIXELS_PER_BLOCK;
		float brokenZ = brokenPose == null ? 0.0F : brokenPose.positionZ() / PIXELS_PER_BLOCK;
		matrices.translate(slideDirection.getOffsetX() * slideBlocks,
			24.0F / PIXELS_PER_BLOCK + brokenY,
			slideDirection.getOffsetZ() * slideBlocks + brokenZ);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYaw()));
		if (brokenPose != null) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(brokenPose.pitchRadians()));
		}

		VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		MatrixStack.Entry entry = matrices.peek();
		for (DoorCuboid cuboid : CUBOIDS) {
			cuboid.render(entry, vertices, light, overlay);
		}
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(LargeWoodDoorBlockEntity blockEntity) {
		return true;
	}

	private static float slidePixels(LargeWoodDoorBlockEntity entity) {
		boolean open = entity.isOpen();
		if (entity.state == null || !entity.state.isRunning()) {
			return open ? OPEN_SLIDE_PIXELS : 0.0F;
		}
		float seconds = entity.state.getTimeRunning() / 1000.0F;
		float t = (seconds - OPEN_KEYFRAME_START_SECONDS) / (OPEN_KEYFRAME_END_SECONDS - OPEN_KEYFRAME_START_SECONDS);
		float eased = watheDoorEase(t);
		return (open ? eased : 1.0F - eased) * OPEN_SLIDE_PIXELS;
	}

	private static float watheDoorEase(float keyframeProgress) {
		float t = MathHelper.clamp(keyframeProgress, 0.0F, 1.0F);
		return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * t);
	}

	private static Uv uv(float u, float v, float width, float height) {
		return new Uv(u / TEXTURE_SIZE, v / TEXTURE_SIZE, (u + width) / TEXTURE_SIZE, (v + height) / TEXTURE_SIZE);
	}

	private record DoorCuboid(float x1, float y1, float z1, float x2, float y2, float z2,
			Uv north, Uv east, Uv south, Uv west, Uv up, Uv down) {
		private void render(MatrixStack.Entry entry, VertexConsumer vertices, int light, int overlay) {
			float minX = x1 / PIXELS_PER_BLOCK;
			float minY = y1 / PIXELS_PER_BLOCK;
			float minZ = z1 / PIXELS_PER_BLOCK;
			float maxX = x2 / PIXELS_PER_BLOCK;
			float maxY = y2 / PIXELS_PER_BLOCK;
			float maxZ = z2 / PIXELS_PER_BLOCK;

			quad(entry, vertices, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, north, 0.0F, 0.0F, -1.0F, light, overlay);
			quad(entry, vertices, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, south, 0.0F, 0.0F, 1.0F, light, overlay);
			quad(entry, vertices, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, east, 1.0F, 0.0F, 0.0F, light, overlay);
			quad(entry, vertices, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, west, -1.0F, 0.0F, 0.0F, light, overlay);
			quad(entry, vertices, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, up, 0.0F, -1.0F, 0.0F, light, overlay);
			quad(entry, vertices, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, down, 0.0F, 1.0F, 0.0F, light, overlay);
		}
	}

	private record Uv(float u1, float v1, float u2, float v2) {}

	private static void quad(MatrixStack.Entry entry, VertexConsumer vertices,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4,
			Uv uv, float normalX, float normalY, float normalZ, int light, int overlay) {
		vertex(entry, vertices, x1, y1, z1, uv.u1, uv.v1, normalX, normalY, normalZ, light, overlay);
		vertex(entry, vertices, x2, y2, z2, uv.u1, uv.v2, normalX, normalY, normalZ, light, overlay);
		vertex(entry, vertices, x3, y3, z3, uv.u2, uv.v2, normalX, normalY, normalZ, light, overlay);
		vertex(entry, vertices, x4, y4, z4, uv.u2, uv.v1, normalX, normalY, normalZ, light, overlay);
	}

	private static void vertex(MatrixStack.Entry entry, VertexConsumer vertices, float x, float y, float z,
			float u, float v, float normalX, float normalY, float normalZ, int light, int overlay) {
		vertices.vertex(entry, x, y, z)
			.color(255, 255, 255, 255)
			.texture(u, v)
			.overlay(overlay)
			.light(light)
			.normal(entry, normalX, normalY, normalZ);
	}
}
