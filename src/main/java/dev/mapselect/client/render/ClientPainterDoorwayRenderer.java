package dev.mapselect.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.mapselect.client.role.painter.ClientPainterState;
import dev.mapselect.client.text.GexpressRoleTexts;
import dev.mapselect.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

public final class ClientPainterDoorwayRenderer {
	private static final double MAX_DISTANCE = 40.0D;
	private static final double OPEN_SLIDE_OFFSET = 12.5D / 16.0D;
	private static final float OPEN_KEYFRAME_START_SECONDS = 0.10F;
	private static final float OPEN_KEYFRAME_END_SECONDS = 0.70F;
	private static final double PORTAL_TRACE_WIDTH = 1.00D;
	private static final double PORTAL_TRACE_HEIGHT = 2.00D;
	private static final double TRACE_DEPTH = 2.0D / 16.0D;
	private static final double OUTLINE_TRACE_WIDTH = 1.25D;
	private static final double OUTLINE_TRACE_HEIGHT = 2.04D;
	private static final double TRACE_EDGE = 0.026D;
	private static final double HOVER_TRACE_STEP = 0.12D;
	private static final double HOVER_HALF_WIDTH = 0.72D;
	private static final double HOVER_HALF_HEIGHT = 1.10D;
	private static final double HOVER_HALF_DEPTH = 0.75D;
	private static final double REMOTE_PORTAL_CLIP_OFFSET = 0.08D;
	private static final double DESTINATION_VIEW_OFFSET = 8.0D / 16.0D;
	private static final double DOORWAY_COLOR_LERP_TICKS = 18.0D;
	private static final double PLAYER_EYE_VIEW_HEIGHT = 1.62D;
	private static final double PORTAL_HALF_WIDTH = 0.62D;
	private static final double PORTAL_MIN_Y_INSET = 0.05D;
	private static final double PORTAL_MAX_Y_INSET = 1.95D;
	private static final double DOORWAY_RANGE = 4.5D;
	private static boolean renderingPortalContent;
	private static boolean skipPortalWorldClear;
	private static boolean stencilUnavailable;

	private ClientPainterDoorwayRenderer() {}

	public static void register() {
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ClientPainterDoorwayRenderer::render);
	}

	public static boolean isRenderingPortalContent() {
		return renderingPortalContent;
	}

	public static boolean shouldSkipPortalWorldClear() {
		return renderingPortalContent && skipPortalWorldClear;
	}

	private static boolean isImmersivePortalsAvailable() {
		if (FabricLoader.getInstance().isModLoaded("immersive_portals")
				|| FabricLoader.getInstance().isModLoaded("iportal")) {
			return true;
		}
		try {
			Class.forName("qouteall.imm_ptl.core.portal.Portal", false,
				ClientPainterDoorwayRenderer.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	private static void render(WorldRenderContext context) {
		if (renderingPortalContent) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) return;

		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		float tickDelta = context.tickCounter().getTickDelta(true);
		double renderTime = client.world.getTime() + tickDelta;
		for (ClientPainterState.DoorwayTint doorway : ClientPainterState.doorways()) {
			Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
			if (center.squaredDistanceTo(cameraPos) > MAX_DISTANCE * MAX_DISTANCE) continue;
			if (!canSeeDoorway(client, doorway.lowerPos(), center, cameraPos)) continue;
			boolean sourceSide = isOnSourceSide(cameraPos, center, doorway.through());
			Direction portalSide = doorway.through().getOpposite();
			Direction traceSide = sourceSide
				? portalSide
				: visibleSide(cameraPos, center, doorway.facing());
			DoorTrace trace = outlineTrace(cameraPos, center, traceSide);
			boolean renderedPortal = sourceSide && renderPortalView(context, doorway, center, portalSide);
			renderDoorTrace(matrices, trace, rainbowDoorwayColor(renderTime, doorway.lowerPos()),
				doorway.remaining(), false, renderedPortal);
		}

		DoorwayTarget hoverTarget = hoverTarget(client);
		if (hoverTarget != null && !ClientPainterState.isDoorwayActive(hoverTarget.lowerPos())) {
			Vec3d center = doorwayCenter(hoverTarget.lowerPos(), hoverTarget.facing());
			if (center.squaredDistanceTo(cameraPos) <= MAX_DISTANCE * MAX_DISTANCE) {
				renderDoorTrace(matrices,
					outlineTrace(cameraPos, center, visibleSide(cameraPos, center, hoverTarget.facing())),
					rainbowDoorwayColor(renderTime, hoverTarget.lowerPos()), 1.0F, true, false);
			}
		}
	}

	private static boolean renderPortalView(WorldRenderContext context, ClientPainterState.DoorwayTint doorway,
			Vec3d sourceCenter, Direction visibleSide) {
		if (isImmersivePortalsAvailable()) return false;
		if (stencilUnavailable || doorway.destinationLowerPos() == null || doorway.destination() == null) return false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || context.worldRenderer() == null || context.camera() == null) return false;
		if (!ensureStencilFramebuffer(client)) return false;

		Camera camera = context.camera();
		Vec3d oldCameraPos = camera.getPos();
		float oldYaw = camera.getYaw();
		float oldPitch = camera.getPitch();
		Matrix4f oldPositionMatrix = new Matrix4f(context.positionMatrix());
		Matrix4f projectionMatrix = new Matrix4f(context.projectionMatrix());
		DoorTrace stencilTrace = portalTrace(oldCameraPos, sourceCenter, visibleSide);
		Vec3d portalCameraPos = portalCameraPos(doorway, oldCameraPos);
		float portalYaw = oldYaw + doorwayYawDelta(doorway);
		int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		Object oldIrisPipeline = GexpressIrisCompat.capturePipeline(context.worldRenderer());
		if (GexpressIrisCompat.isIrisLoaded() && oldIrisPipeline == null) return false;

		try {
			client.getFramebuffer().beginWrite(false);
			GL11.glEnable(GL11.GL_STENCIL_TEST);
			RenderSystem.stencilMask(0xFF);
			RenderSystem.clearStencil(0);
			RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

			RenderSystem.disableCull();
			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(false);
			RenderSystem.colorMask(false, false, false, false);
			RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
			RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
			GL11.glDepthFunc(GL11.GL_ALWAYS);
			drawStencilFace(context.matrixStack(), stencilTrace);
			GL11.glDepthFunc(oldDepthFunc);

			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.depthMask(true);
			RenderSystem.enableCull();

			client.getFramebuffer().beginWrite(false);
			GL11.glEnable(GL11.GL_STENCIL_TEST);
			RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
			RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
			RenderSystem.stencilMask(0x00);
			clearDepthOfPortalViewArea(context.matrixStack(), stencilTrace);
			drawPortalBackdrop(context.matrixStack(), stencilTrace);
			boolean rendered = renderPortalContentDirect(context, doorway, portalCameraPos, portalYaw,
				projectionMatrix);
			if (!rendered) return false;
			restoreDepthOfPortalSurface(context.matrixStack(), stencilTrace);
			return true;
		} catch (Throwable ignored) {
			stencilUnavailable = true;
			return false;
		} finally {
			renderingPortalContent = false;
			((CameraAccessor) camera).gexpress$setPos(oldCameraPos);
			((CameraAccessor) camera).gexpress$setRotation(oldYaw, oldPitch);
			configureRenderDispatchers(client, camera);
			GexpressIrisCompat.restorePipeline(context.worldRenderer(), oldIrisPipeline);
			context.worldRenderer().setupFrustum(oldCameraPos, oldPositionMatrix, projectionMatrix);
			GL11.glDepthRange(0.0D, 1.0D);
			GL11.glDepthFunc(oldDepthFunc);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.depthMask(true);
			RenderSystem.stencilMask(0xFF);
			GL11.glDisable(GL11.GL_STENCIL_TEST);
			RenderSystem.enableDepthTest();
			RenderSystem.enableCull();
			client.getFramebuffer().beginWrite(false);
		}
	}

	private static boolean renderPortalContentDirect(WorldRenderContext context,
			ClientPainterState.DoorwayTint doorway, Vec3d portalCameraPos, float portalYaw,
			Matrix4f projectionMatrix) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || context.worldRenderer() == null
				|| context.camera() == null) {
			return false;
		}
		Camera camera = context.camera();
		Vec3d oldCameraPos = camera.getPos();
		float oldYaw = camera.getYaw();
		float oldPitch = camera.getPitch();
		int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		try {
			client.getFramebuffer().beginWrite(false);
			GL11.glEnable(GL11.GL_STENCIL_TEST);
			RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
			RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
			RenderSystem.stencilMask(0x00);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();

			renderingPortalContent = true;
			skipPortalWorldClear = true;
			((CameraAccessor) camera).gexpress$setPos(portalCameraPos);
			((CameraAccessor) camera).gexpress$setRotation(portalYaw, oldPitch);
			configureRenderDispatchers(client, camera);
			Matrix4f portalPositionMatrix = new Matrix4f()
				.rotation(camera.getRotation().conjugate(new Quaternionf()));
			Vec3d destinationPlane = destinationPortalPlaneCenter(doorway.destinationLowerPos(),
				doorway.destinationFacing(), doorway.destinationThrough());
			PainterDoorwayFrontClipping.setup(destinationPlane, doorway.destinationThrough(),
				portalCameraPos, portalPositionMatrix);
			context.worldRenderer().setupFrustum(portalCameraPos, portalPositionMatrix, projectionMatrix);
			context.worldRenderer().render(context.tickCounter(), false, camera, client.gameRenderer,
				client.gameRenderer.getLightmapTextureManager(), portalPositionMatrix, projectionMatrix);
			return true;
		} catch (Throwable ignored) {
			return false;
		} finally {
			PainterDoorwayFrontClipping.disable();
			renderingPortalContent = false;
			skipPortalWorldClear = false;
			((CameraAccessor) camera).gexpress$setPos(oldCameraPos);
			((CameraAccessor) camera).gexpress$setRotation(oldYaw, oldPitch);
			configureRenderDispatchers(client, camera);
			GL11.glDepthFunc(oldDepthFunc);
			client.getFramebuffer().beginWrite(false);
		}
	}

	private static void configureRenderDispatchers(MinecraftClient client, Camera camera) {
		if (client == null || client.world == null || camera == null) return;
		client.getEntityRenderDispatcher().configure(client.world, camera, client.targetedEntity);
		client.getBlockEntityRenderDispatcher().configure(client.world, camera, client.crosshairTarget);
	}

	private static boolean ensureStencilFramebuffer(MinecraftClient client) {
		if (client == null) return false;
		Framebuffer framebuffer = client.getFramebuffer();
		if (!(framebuffer instanceof GexpressStencilFramebuffer stencilFramebuffer)) {
			stencilUnavailable = true;
			return false;
		}
		if (!stencilFramebuffer.gexpress$isStencilBufferEnabled()) {
			stencilFramebuffer.gexpress$setStencilBufferEnabledAndReload(true);
		}
		return stencilFramebuffer.gexpress$isStencilBufferEnabled();
	}

	private static Vec3d portalCameraPos(ClientPainterState.DoorwayTint doorway, Vec3d cameraPos) {
		if (doorway != null && doorway.destination() != null && doorway.destinationThrough() != null
				&& !doorway.destinationThrough().getAxis().isVertical()) {
			return doorway.destination()
				.add(0.0D, PLAYER_EYE_VIEW_HEIGHT, 0.0D)
				.add(Vec3d.of(doorway.destinationThrough().getVector()).multiply(REMOTE_PORTAL_CLIP_OFFSET));
		}
		return transformedDoorwayViewPosition(doorway, cameraPos, REMOTE_PORTAL_CLIP_OFFSET);
	}

	private static Vec3d transformedDoorwayViewPosition(ClientPainterState.DoorwayTint doorway, Vec3d pos,
			double extraThroughOffset) {
		Vec3d sourceCenter = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
		Vec3d destinationCenter = destinationPortalPlaneCenter(doorway.destinationLowerPos(),
			doorway.destinationFacing(), doorway.destinationThrough());
		Vec3d relative = pos.subtract(sourceCenter);
		Vec3d sourceForward = horizontalVector(doorway.through());
		Vec3d sourceRight = horizontalVector(doorway.through().rotateYClockwise());
		Vec3d destinationForward = horizontalVector(doorway.destinationThrough());
		Vec3d destinationRight = horizontalVector(doorway.destinationThrough().rotateYClockwise());
		double forward = -relative.dotProduct(sourceForward) + extraThroughOffset;
		double right = relative.dotProduct(sourceRight);
		return destinationCenter
			.add(destinationForward.multiply(forward))
			.add(destinationRight.multiply(right))
			.add(0.0D, relative.y, 0.0D);
	}

	private static float doorwayYawDelta(ClientPainterState.DoorwayTint doorway) {
		return MathHelper.wrapDegrees(doorway.destinationThrough().asRotation() - doorway.through().asRotation());
	}

	private static Vec3d doorwayCenter(BlockPos lower, Direction facing) {
		return Vec3d.ofCenter(lower).add(0.0D, 0.50D, 0.0D);
	}

	private static Vec3d sourcePortalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		return doorwayCenter(lower, facing);
	}

	private static Vec3d destinationPortalPlaneCenter(BlockPos lower, Direction facing, Direction through) {
		Vec3d center = doorwayCenter(lower, facing);
		if (through == null || through.getAxis().isVertical()) return center;
		return center.add(Vec3d.of(through.getVector()).multiply(DESTINATION_VIEW_OFFSET));
	}

	private static int rainbowDoorwayColor(double renderTime, BlockPos pos) {
		int offset = pos == null ? 0 : Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13, 9);
		double colorTime = renderTime / DOORWAY_COLOR_LERP_TICKS;
		int base = (int) Math.floor(colorTime);
		int first = GexpressRoleTexts.painterColor(base + offset);
		int second = GexpressRoleTexts.painterColor(base + offset + 1);
		float progress = (float) (colorTime - Math.floor(colorTime));
		progress = progress * progress * (3.0F - 2.0F * progress);
		return lerpColor(first, second, progress);
	}

	private static int lerpColor(int first, int second, float progress) {
		float t = MathHelper.clamp(progress, 0.0F, 1.0F);
		int red = Math.round(((first >>> 16) & 0xFF) + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * t);
		int green = Math.round(((first >>> 8) & 0xFF) + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * t);
		int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * t);
		return (red << 16) | (green << 8) | blue;
	}

	private static Vec3d horizontalVector(Direction direction) {
		if (direction == null || direction.getAxis().isVertical()) return Vec3d.ZERO;
		return new Vec3d(direction.getOffsetX(), 0.0D, direction.getOffsetZ()).normalize();
	}

	private static boolean isOnSourceSide(Vec3d pos, Vec3d center, Direction through) {
		if (pos == null || center == null || through == null || through.getAxis().isVertical()) return true;
		Vec3d normal = new Vec3d(through.getOffsetX(), 0.0D, through.getOffsetZ());
		return pos.subtract(center).dotProduct(normal) < 0.08D;
	}

	private static void drawStencilFace(MatrixStack matrices, DoorTrace trace) {
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION_COLOR);
		MatrixStack.Entry entry = matrices.peek();
		quad(buffer, entry, trace.frontTopLeft(), trace.frontBottomLeft(), trace.frontBottomRight(),
			trace.frontTopRight(), 255, 255, 255, 255);
		BufferRenderer.drawWithGlobalProgram(buffer.end());
	}

	private static void clearDepthOfPortalViewArea(MatrixStack matrices, DoorTrace trace) {
		int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.colorMask(false, false, false, false);
		RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
		RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		GL11.glDepthFunc(GL11.GL_ALWAYS);
		GL11.glDepthRange(1.0D, 1.0D);
		drawStencilFace(matrices, trace);
		GL11.glDepthRange(0.0D, 1.0D);
		GL11.glDepthFunc(oldDepthFunc);
		RenderSystem.colorMask(true, true, true, true);
	}

	private static void restoreDepthOfPortalSurface(MatrixStack matrices, DoorTrace trace) {
		int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.colorMask(false, false, false, false);
		RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
		RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		GL11.glDepthFunc(GL11.GL_ALWAYS);
		drawStencilFace(matrices, trace);
		GL11.glDepthFunc(oldDepthFunc);
		RenderSystem.colorMask(true, true, true, true);
	}

	private static void drawPortalBackdrop(MatrixStack matrices, DoorTrace trace) {
		int oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(false);
		GL11.glDepthFunc(GL11.GL_ALWAYS);
		RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
		RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION_COLOR);
		MatrixStack.Entry entry = matrices.peek();
		quad(buffer, entry, trace.frontTopLeft(), trace.frontBottomLeft(), trace.frontBottomRight(),
			trace.frontTopRight(), 8, 5, 12, 255);
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		GL11.glDepthFunc(oldDepthFunc);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
	}

	private static Vec3d doorCenter(MinecraftClient client, BlockPos lower, Direction facing, double renderTime) {
		Vec3d center = doorwayCenter(lower, facing);
		BlockEntity blockEntity = client.world.getBlockEntity(lower);
		if (blockEntity instanceof DoorBlockEntity door) {
			float openProgress = slideProgress(door);
			Vec3d slide = Vec3d.of(door.getFacing().rotateYClockwise().getVector());
			center = center.add(slide.multiply(OPEN_SLIDE_OFFSET * openProgress));
		} else {
			BlockState state = client.world.getBlockState(lower);
			if (state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.OPEN) && state.get(DoorBlock.OPEN)) {
				Vec3d slide = Vec3d.of(facing.rotateYClockwise().getVector());
				center = center.add(slide.multiply(OPEN_SLIDE_OFFSET));
			}
		}
		return center;
	}

	private static Direction visibleSide(Vec3d cameraPos, Vec3d center, Direction facing) {
		if (facing == null || facing.getAxis().isVertical()) return Direction.NORTH;
		Vec3d normal = Vec3d.of(facing.getVector());
		return cameraPos.subtract(center).dotProduct(normal) >= 0.0D ? facing : facing.getOpposite();
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

	private static DoorTrace portalTrace(Vec3d cameraPos, Vec3d center, Direction side) {
		return doorTrace(cameraPos, center, side, PORTAL_TRACE_WIDTH, PORTAL_TRACE_HEIGHT, TRACE_DEPTH);
	}

	private static DoorTrace outlineTrace(Vec3d cameraPos, Vec3d center, Direction side) {
		return doorTrace(cameraPos, center, side, OUTLINE_TRACE_WIDTH, OUTLINE_TRACE_HEIGHT, TRACE_DEPTH);
	}

	private static DoorTrace doorTrace(Vec3d cameraPos, Vec3d center, Direction side,
			double width, double height, double traceDepth) {
		Vec3d normal = Vec3d.of(side.getVector()).normalize();
		Vec3d right = Vec3d.of(side.rotateYClockwise().getVector()).normalize().multiply(width * 0.5D);
		Vec3d up = new Vec3d(0.0D, height * 0.5D, 0.0D);
		Vec3d depth = normal.multiply(traceDepth * 0.5D);
		Vec3d localCenter = center.subtract(cameraPos);
		Vec3d frontCenter = localCenter.add(depth);
		Vec3d backCenter = localCenter.subtract(depth);
		Vec3d frontTopLeft = frontCenter.subtract(right).add(up);
		Vec3d frontTopRight = frontCenter.add(right).add(up);
		Vec3d frontBottomRight = frontCenter.add(right).subtract(up);
		Vec3d frontBottomLeft = frontCenter.subtract(right).subtract(up);
		Vec3d backTopLeft = backCenter.subtract(right).add(up);
		Vec3d backTopRight = backCenter.add(right).add(up);
		Vec3d backBottomRight = backCenter.add(right).subtract(up);
		Vec3d backBottomLeft = backCenter.subtract(right).subtract(up);
		return new DoorTrace(frontTopLeft, frontTopRight, frontBottomRight, frontBottomLeft,
			backTopLeft, backTopRight, backBottomRight, backBottomLeft, right.normalize());
	}

	private static void renderDoorTrace(MatrixStack matrices, DoorTrace trace, int color,
			float remaining, boolean hoverPreview, boolean portalContentRendered) {
		if (portalContentRendered) return;
		float fade = Math.max(0.0F, Math.min(1.0F, remaining));
		int red = (color >>> 16) & 0xFF;
		int green = (color >>> 8) & 0xFF;
		int blue = color & 0xFF;
		int fillAlpha = 0;
		int borderAlpha = hoverPreview
			? Math.round(196.0F + 46.0F * fade)
			: Math.round(132.0F + 92.0F * fade);

		if (hoverPreview) {
			RenderSystem.disableDepthTest();
		} else {
			RenderSystem.enableDepthTest();
		}
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION_COLOR);
		MatrixStack.Entry entry = matrices.peek();

		if (fillAlpha > 0) {
			quad(buffer, entry, trace.frontTopLeft(), trace.frontBottomLeft(), trace.frontBottomRight(),
				trace.frontTopRight(), red, green, blue, fillAlpha);
			quad(buffer, entry, trace.backTopRight(), trace.backBottomRight(), trace.backBottomLeft(),
				trace.backTopLeft(), red, green, blue, fillAlpha);
			quad(buffer, entry, trace.frontTopLeft(), trace.backTopLeft(), trace.backBottomLeft(),
				trace.frontBottomLeft(), red, green, blue, fillAlpha);
			quad(buffer, entry, trace.frontTopRight(), trace.frontBottomRight(), trace.backBottomRight(),
				trace.backTopRight(), red, green, blue, fillAlpha);
			quad(buffer, entry, trace.frontTopLeft(), trace.frontTopRight(), trace.backTopRight(),
				trace.backTopLeft(), red, green, blue, fillAlpha);
			quad(buffer, entry, trace.frontBottomLeft(), trace.backBottomLeft(), trace.backBottomRight(),
				trace.frontBottomRight(), red, green, blue, fillAlpha);
		}

		drawFaceBorder(buffer, entry, trace.frontTopLeft(), trace.frontTopRight(),
			trace.frontBottomRight(), trace.frontBottomLeft(), trace.right(), red, green, blue, borderAlpha);
		drawFaceBorder(buffer, entry, trace.backTopRight(), trace.backTopLeft(),
			trace.backBottomLeft(), trace.backBottomRight(), trace.right().multiply(-1.0D),
			red, green, blue, Math.round(borderAlpha * 0.8F));
		drawConnectorEdge(buffer, entry, trace.frontTopLeft(), trace.backTopLeft(),
			trace.frontBottomLeft(), trace.backBottomLeft(), red, green, blue, Math.round(borderAlpha * 0.72F));
		drawConnectorEdge(buffer, entry, trace.frontTopRight(), trace.backTopRight(),
			trace.frontBottomRight(), trace.backBottomRight(), red, green, blue, Math.round(borderAlpha * 0.72F));

		BufferRenderer.drawWithGlobalProgram(buffer.end());
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
	}

	private static void drawFaceBorder(BufferBuilder buffer, MatrixStack.Entry entry,
			Vec3d topLeft, Vec3d topRight, Vec3d bottomRight, Vec3d bottomLeft, Vec3d right,
			int red, int green, int blue, int alpha) {
		Vec3d horizontalInset = right.normalize().multiply(TRACE_EDGE);
		Vec3d verticalInset = new Vec3d(0.0D, TRACE_EDGE, 0.0D);
		quad(buffer, entry, topLeft, topLeft.subtract(verticalInset),
			topRight.subtract(verticalInset), topRight, red, green, blue, alpha);
		quad(buffer, entry, bottomLeft.add(verticalInset), bottomLeft, bottomRight,
			bottomRight.add(verticalInset), red, green, blue, alpha);
		quad(buffer, entry, topLeft, bottomLeft, bottomLeft.add(horizontalInset),
			topLeft.add(horizontalInset), red, green, blue, alpha);
		quad(buffer, entry, topRight.subtract(horizontalInset), bottomRight.subtract(horizontalInset),
			bottomRight, topRight, red, green, blue, alpha);
	}

	private static void drawConnectorEdge(BufferBuilder buffer, MatrixStack.Entry entry,
			Vec3d frontTop, Vec3d backTop, Vec3d frontBottom, Vec3d backBottom,
			int red, int green, int blue, int alpha) {
		Vec3d verticalInset = new Vec3d(0.0D, TRACE_EDGE, 0.0D);
		quad(buffer, entry, frontTop, backTop, backTop.subtract(verticalInset),
			frontTop.subtract(verticalInset), red, green, blue, alpha);
		quad(buffer, entry, frontBottom.add(verticalInset), backBottom.add(verticalInset),
			backBottom, frontBottom, red, green, blue, alpha);
	}

	private static void quad(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
			int red, int green, int blue, int alpha) {
		vertex(buffer, entry, a, red, green, blue, alpha);
		vertex(buffer, entry, b, red, green, blue, alpha);
		vertex(buffer, entry, c, red, green, blue, alpha);
		vertex(buffer, entry, d, red, green, blue, alpha);
	}

	private static void vertex(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d pos,
			int red, int green, int blue, int alpha) {
		buffer.vertex(entry, (float) pos.x, (float) pos.y, (float) pos.z)
			.color(red, green, blue, alpha);
	}

	private static DoorwayTarget hoverTarget(MinecraftClient client) {
		if (!ClientPainterState.canShowDoorwayTarget(client)) return null;
		Vec3d start = client.player.getEyePos();
		Vec3d end = start.add(client.player.getRotationVec(1.0F).multiply(DOORWAY_RANGE));
		HitResult hit = client.world.raycast(new RaycastContext(start, end,
			RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
		double hitDistanceSquared = hit == null || hit.getType() == HitResult.Type.MISS
			? start.squaredDistanceTo(end)
			: start.squaredDistanceTo(hit.getPos());
		if (rayCrossesActiveDoorwayBefore(start, end, hitDistanceSquared)) return null;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			DoorwayTarget hitTarget = doorwayTargetAt(client, blockHit.getBlockPos());
			if (hitTarget != null) return hitTarget;
		}
		double searchDistance = Math.min(DOORWAY_RANGE, Math.sqrt(hitDistanceSquared) + HOVER_HALF_DEPTH + 0.25D);
		return doorwayTargetAlongRay(client, start, end, searchDistance);
	}

	private static DoorwayTarget doorwayTargetAlongRay(MinecraftClient client, Vec3d start, Vec3d end,
			double maxDistance) {
		if (client == null || client.world == null || start == null || end == null || maxDistance <= 0.0D) return null;
		Vec3d ray = end.subtract(start);
		double length = Math.min(maxDistance, ray.length());
		if (length <= 0.0D) return null;
		Vec3d direction = ray.normalize();
		DoorwayTarget scanned = doorwayTargetNearRay(client, start, direction, length);
		if (scanned != null) return scanned;
		Vec3d cursor = start;
		int steps = (int) Math.ceil(length / HOVER_TRACE_STEP);
		for (int i = 0; i <= steps; i++) {
			double distance = Math.min(length, i * HOVER_TRACE_STEP);
			cursor = start.add(direction.multiply(distance));
			DoorwayTarget target = closestDoorwayTargetAt(client, BlockPos.ofFloored(cursor));
			if (target != null && rayPassesDoorwayOpening(start, direction, length, target)) {
				return target;
			}
		}
		return null;
	}

	private static DoorwayTarget doorwayTargetNearRay(MinecraftClient client, Vec3d start, Vec3d direction,
			double maxDistance) {
		BlockPos origin = BlockPos.ofFloored(start);
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		DoorwayTarget best = null;
		double bestAlong = Double.MAX_VALUE;
		for (BlockPos candidate : BlockPos.iterateOutwards(origin, 6, 3, 6)) {
			DoorwayTarget target = doorwayTargetAt(client, candidate);
			if (target == null || !seen.add(target.lowerPos())) continue;
			double along = doorwayRayAlong(start, direction, maxDistance, target);
			if (along < 0.0D || along >= bestAlong) continue;
			best = target;
			bestAlong = along;
		}
		return best;
	}

	private static boolean rayPassesDoorwayOpening(Vec3d start, Vec3d direction, double maxDistance,
			DoorwayTarget target) {
		return doorwayRayAlong(start, direction, maxDistance, target) >= 0.0D;
	}

	private static double doorwayRayAlong(Vec3d start, Vec3d direction, double maxDistance, DoorwayTarget target) {
		if (start == null || direction == null || target == null) return -1.0D;
		Vec3d center = doorwayCenter(target.lowerPos(), target.facing());
		double along = MathHelper.clamp(center.subtract(start).dotProduct(direction), 0.0D, maxDistance);
		Vec3d closest = start.add(direction.multiply(along));
		Vec3d delta = closest.subtract(center);
		Direction side = visibleSide(start, center, target.facing());
		Vec3d normal = Vec3d.of(side.getVector()).normalize();
		Vec3d right = Vec3d.of(side.rotateYClockwise().getVector()).normalize();
		return Math.abs(delta.dotProduct(right)) <= HOVER_HALF_WIDTH
			&& Math.abs(delta.y) <= HOVER_HALF_HEIGHT
			&& Math.abs(delta.dotProduct(normal)) <= HOVER_HALF_DEPTH
				? along
				: -1.0D;
	}

	private static DoorwayTarget closestDoorwayTargetAt(MinecraftClient client, BlockPos pos) {
		if (client == null || client.world == null || pos == null) return null;
		for (BlockPos candidate : java.util.List.of(pos, pos.down(), pos.up(),
				pos.north(), pos.south(), pos.east(), pos.west())) {
			DoorwayTarget target = doorwayTargetAt(client, candidate);
			if (target != null) return target;
		}
		return null;
	}

	private static DoorwayTarget doorwayTargetAt(MinecraftClient client, BlockPos pos) {
		if (client == null || client.world == null || pos == null) return null;
		BlockPos lower = lowerDoorPos(client, pos);
		DoorBlockEntity door = doorBlockEntityNear(client, lower);
		if (door != null) {
			Direction facing = door.getFacing();
			BlockPos doorLower = lowerDoorPos(client, door.getPos());
			if (facing != null && !facing.getAxis().isVertical()) return new DoorwayTarget(doorLower.toImmutable(), facing);
		}
		BlockState state = client.world.getBlockState(lower);
		if (isDoorLikeState(state)) {
			Direction facing = state.contains(Properties.HORIZONTAL_FACING)
				? state.get(Properties.HORIZONTAL_FACING)
				: Direction.NORTH;
			return new DoorwayTarget(lower.toImmutable(), facing);
		}
		return null;
	}

	private static DoorBlockEntity doorBlockEntityNear(MinecraftClient client, BlockPos lower) {
		for (BlockPos candidate : java.util.List.of(lower, lower.up(), lower.down())) {
			BlockEntity blockEntity = client.world.getBlockEntity(candidate);
			if (blockEntity instanceof DoorBlockEntity door) return door;
		}
		return null;
	}

	private static boolean isDoorLikeState(BlockState state) {
		if (state == null || state.isAir()) return false;
		if (state.getBlock() instanceof DoorBlock) return true;
		if (!state.contains(Properties.HORIZONTAL_FACING)
				|| !state.contains(Properties.DOUBLE_BLOCK_HALF)
				|| !state.contains(Properties.OPEN)) {
			return false;
		}
		return Registries.BLOCK.getId(state.getBlock()).getPath().contains("door");
	}

	private static BlockPos lowerDoorPos(MinecraftClient client, BlockPos pos) {
		if (client == null || client.world == null || pos == null) return pos;
		BlockState state = client.world.getBlockState(pos);
		return state.contains(Properties.DOUBLE_BLOCK_HALF)
			&& state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
				? pos.down()
				: pos;
	}

	private static boolean canSeeDoorway(MinecraftClient client, BlockPos lower, Vec3d center, Vec3d cameraPos) {
		if (client == null || client.world == null || lower == null || center == null || cameraPos == null) return false;
		Direction through = null;
		for (ClientPainterState.DoorwayTint doorway : ClientPainterState.doorways()) {
			if (doorway != null && lower.equals(doorway.lowerPos())) {
				through = doorway.through();
				break;
			}
		}
		Vec3d right = through == null || through.getAxis().isVertical()
			? new Vec3d(1.0D, 0.0D, 0.0D)
			: Vec3d.of(through.rotateYClockwise().getVector()).normalize();
		Vec3d up = new Vec3d(0.0D, 0.62D, 0.0D);
		Vec3d side = right.multiply(0.38D);
		Vec3d[] samples = {
			center,
			center.add(side).add(up),
			center.subtract(side).add(up),
			center.add(side).subtract(up),
			center.subtract(side).subtract(up)
		};
		for (Vec3d sample : samples) {
			if (rayCanReachDoorway(client, lower, cameraPos, sample)) return true;
		}
		return false;
	}

	private static boolean rayCanReachDoorway(MinecraftClient client, BlockPos lower, Vec3d cameraPos, Vec3d target) {
		HitResult hit = client.world.raycast(new RaycastContext(cameraPos, target,
			RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) return true;
		if (blockHit.getPos().squaredDistanceTo(cameraPos) + 0.03D >= target.squaredDistanceTo(cameraPos)) return true;
		return sameDoor(client, blockHit.getBlockPos(), lower);
	}

	private static boolean rayCrossesActiveDoorwayBefore(Vec3d start, Vec3d end, double maxDistanceSquared) {
		for (ClientPainterState.DoorwayTint doorway : ClientPainterState.doorways()) {
			if (doorway == null || doorway.lowerPos() == null || doorway.through() == null) continue;
			Vec3d center = sourcePortalPlaneCenter(doorway.lowerPos(), doorway.facing(), doorway.through());
			double startSide = doorwaySide(start, center, doorway.through());
			double endSide = doorwaySide(end, center, doorway.through());
			if (startSide > 0.02D || endSide < -0.02D) continue;
			Vec3d collision = collisionPoint(start, end, startSide, endSide);
			if (start.squaredDistanceTo(collision) >= maxDistanceSquared - 0.01D) continue;
			if (withinPortalFace(collision, doorway.lowerPos(), doorway.through())) return true;
		}
		return false;
	}

	private static double doorwaySide(Vec3d pos, Vec3d center, Direction through) {
		Vec3d normal = new Vec3d(through.getOffsetX(), 0.0D, through.getOffsetZ());
		return pos.subtract(center).dotProduct(normal);
	}

	private static Vec3d collisionPoint(Vec3d previous, Vec3d current, double previousSide, double currentSide) {
		double denominator = previousSide - currentSide;
		double t = denominator == 0.0D ? 1.0D : MathHelper.clamp(previousSide / denominator, 0.0D, 1.0D);
		return previous.lerp(current, t);
	}

	private static boolean withinPortalFace(Vec3d pos, BlockPos lower, Direction through) {
		if (pos == null || lower == null || through == null || through.getAxis().isVertical()) return false;
		double localY = pos.y - lower.getY();
		if (localY < PORTAL_MIN_Y_INSET || localY > PORTAL_MAX_Y_INSET) return false;
		Direction rightDirection = through.rotateYClockwise();
		Vec3d right = new Vec3d(rightDirection.getOffsetX(), 0.0D, rightDirection.getOffsetZ());
		double horizontal = pos.subtract(Vec3d.ofCenter(lower)).dotProduct(right);
		return Math.abs(horizontal) <= PORTAL_HALF_WIDTH;
	}

	private static boolean sameDoor(MinecraftClient client, BlockPos hitPos, BlockPos lower) {
		if (client == null || hitPos == null || lower == null) return false;
		return lowerDoorPos(client, hitPos).equals(lower);
	}

	private record DoorwayTarget(BlockPos lowerPos, Direction facing) {}

	private record DoorTrace(Vec3d frontTopLeft, Vec3d frontTopRight, Vec3d frontBottomRight,
			Vec3d frontBottomLeft, Vec3d backTopLeft, Vec3d backTopRight, Vec3d backBottomRight,
			Vec3d backBottomLeft, Vec3d right) {}

}
