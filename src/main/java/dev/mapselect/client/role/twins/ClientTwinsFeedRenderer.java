package dev.mapselect.client.role.twins;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.mapselect.MapSelect;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.render.VeilFramebuffer;
import dev.mapselect.entity.TwinBodyEntity;
import dev.mapselect.mixin.client.CameraAccessor;
import dev.mapselect.mixin.client.MinecraftClientFramebufferAccessor;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;

/**
 * Renders the other body through the already-loaded main WorldRenderer.
 *
 * A second WorldRenderer cannot safely be created in this modpack: resource reload
 * hooks validate that only the primary renderer owns the shared world resources.
 * The feed is therefore rendered as a sequential off-screen pass immediately
 * before the normal world pass.
 */
public final class ClientTwinsFeedRenderer {
	private static final int FEED_WIDTH = 320;
	private static final int FEED_HEIGHT = 180;
	private static final int HUD_WIDTH = 240;
	private static final int HUD_HEIGHT = 135;
	private static final double FEED_FOV = 76.0D;
	private static final double CAMERA_FACE_OFFSET = 0.35D;
	private static AdvancedFbo feedFramebuffer;
	private static Framebuffer feedRenderTarget;
	private static final Camera feedCamera = new Camera();
	private static boolean renderingFeed;
	private static boolean frameReady;
	private static boolean loggedRenderFailure;

	private ClientTwinsFeedRenderer() {}

	static void register() {
		HudRenderCallback.EVENT.register(ClientTwinsFeedRenderer::renderHud);
	}

	static void release() {
		frameReady = false;
		loggedRenderFailure = false;
		if (feedFramebuffer != null) {
			feedFramebuffer.free();
			feedFramebuffer = null;
		}
		feedRenderTarget = null;
	}

	public static boolean isRenderingFeed() {
		return renderingFeed;
	}

	static void prepare(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || feedFramebuffer != null) return;
		try {
			feedFramebuffer = AdvancedFbo.withSize(FEED_WIDTH, FEED_HEIGHT)
				.setFilter(true, false)
				.addColorTextureBuffer()
				.setDepthTextureBuffer()
				.setDebugLabel("Gexpress Twins Camera Feed")
				.build(true);
			feedRenderTarget = new VeilFramebuffer(feedFramebuffer);
			feedRenderTarget.setClearColor(0.02F, 0.025F, 0.035F, 1.0F);
		} catch (Throwable error) {
			frameReady = false;
			if (feedFramebuffer != null) feedFramebuffer.free();
			feedFramebuffer = null;
			feedRenderTarget = null;
			MapSelect.LOGGER.error("Could not initialize the Twins camera framebuffer.", error);
		}
	}

	/**
	 * Called by TwinsCameraFeedRenderMixin immediately before the normal
	 * WorldRenderer render invocation. This is sequential, never recursive.
	 */
	public static void renderBeforeMainWorld(WorldRenderer worldRenderer, RenderTickCounter tickCounter,
			Camera mainCamera, GameRenderer gameRenderer, LightmapTextureManager lightmap,
			Matrix4f mainPositionMatrix, Matrix4f mainProjectionMatrix) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (renderingFeed || client == null || client.player == null || client.world == null
				|| !ClientTwinsState.isLocalTwins(client)
				|| !ClientRoleRevealState.canShowRoleHud(client)) {
			frameReady = false;
			return;
		}
		prepare(client);
		Entity body = findOwnedBody(client);
		if (body == null || feedFramebuffer == null || feedRenderTarget == null
				|| mainCamera == null || worldRenderer == null) {
			frameReady = false;
			return;
		}

		Framebuffer mainFramebuffer = client.getFramebuffer();
		boolean projectionBackedUp = false;
		renderingFeed = true;
		try {
			((MinecraftClientFramebufferAccessor) client).gexpress$setFramebuffer(feedRenderTarget);
			feedFramebuffer.bind(true);
			feedFramebuffer.clear(0.02F, 0.025F, 0.035F, 1.0F,
				GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			float tickDelta = tickCounter.getTickDelta(true);
			feedCamera.update(client.world, body, false, false, tickDelta);
			Vec3d look = Vec3d.fromPolar(body.getPitch(), body.getYaw());
			Vec3d feedPos = body.getPos().add(0.0D, 1.62D, 0.0D)
				.add(look.multiply(CAMERA_FACE_OFFSET));
			((CameraAccessor) feedCamera).gexpress$setPos(feedPos);
			((CameraAccessor) feedCamera).gexpress$setRotation(body.getYaw(), body.getPitch());
			configureRenderDispatchers(client, feedCamera);

			Matrix4f feedPositionMatrix = new Matrix4f()
				.rotation(feedCamera.getRotation().conjugate(new Quaternionf()));
			Matrix4f feedProjectionMatrix = gameRenderer.getBasicProjectionMatrix(FEED_FOV);
			RenderSystem.backupProjectionMatrix();
			projectionBackedUp = true;
			RenderSystem.setProjectionMatrix(feedProjectionMatrix, VertexSorter.BY_DISTANCE);
			worldRenderer.setupFrustum(feedPos, feedPositionMatrix, feedProjectionMatrix);
			worldRenderer.render(tickCounter, false, feedCamera, gameRenderer, lightmap,
				feedPositionMatrix, feedProjectionMatrix);
			renderLocalPlayer(client, feedPos, feedPositionMatrix, tickDelta);
			frameReady = true;
			loggedRenderFailure = false;
		} catch (Throwable error) {
			frameReady = false;
			if (!loggedRenderFailure) {
				loggedRenderFailure = true;
				MapSelect.LOGGER.error("Could not render the Twins live camera frame.", error);
			}
		} finally {
			renderingFeed = false;
			if (projectionBackedUp) RenderSystem.restoreProjectionMatrix();
			configureRenderDispatchers(client, mainCamera);
			((MinecraftClientFramebufferAccessor) client).gexpress$setFramebuffer(mainFramebuffer);
			mainFramebuffer.beginWrite(true);
			worldRenderer.setupFrustum(mainCamera.getPos(), mainPositionMatrix, mainProjectionMatrix);
		}
	}

	private static void renderLocalPlayer(MinecraftClient client, Vec3d cameraPos,
			Matrix4f positionMatrix, float tickDelta) {
		if (client == null || client.player == null || client.player.isRemoved()) return;
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		try {
			modelViewStack.mul(positionMatrix);
			RenderSystem.applyModelViewMatrix();
			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(true);

			MatrixStack matrices = new MatrixStack();
			VertexConsumerProvider.Immediate consumers =
				client.getBufferBuilders().getEntityVertexConsumers();
			double x = MathHelper.lerp(tickDelta, client.player.lastRenderX, client.player.getX()) - cameraPos.x;
			double y = MathHelper.lerp(tickDelta, client.player.lastRenderY, client.player.getY()) - cameraPos.y;
			double z = MathHelper.lerp(tickDelta, client.player.lastRenderZ, client.player.getZ()) - cameraPos.z;
			float yaw = MathHelper.lerp(tickDelta, client.player.prevYaw, client.player.getYaw());
			int light = client.getEntityRenderDispatcher().getLight(client.player, tickDelta);
			client.getEntityRenderDispatcher().render(client.player, x, y, z, yaw, tickDelta,
				matrices, consumers, light);
			consumers.draw();
		} finally {
			modelViewStack.popMatrix();
			RenderSystem.applyModelViewMatrix();
		}
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!frameReady || feedFramebuffer == null || client == null || client.player == null
				|| !ClientTwinsState.isLocalTwins(client) || ClientHudVisibility.shouldHide(client)) {
			return;
		}
		int x = context.getScaledWindowWidth() - HUD_WIDTH - 8;
		int y = 8;

		RenderSystem.setShader(GameRenderer::getPositionTexProgram);
		RenderSystem.setShaderTexture(0, feedRenderTarget.getColorAttachment());
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION_TEXTURE);
		buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 1.0F);
		buffer.vertex(matrix, x, y + HUD_HEIGHT, 0.0F).texture(0.0F, 0.0F);
		buffer.vertex(matrix, x + HUD_WIDTH, y + HUD_HEIGHT, 0.0F).texture(1.0F, 0.0F);
		buffer.vertex(matrix, x + HUD_WIDTH, y, 0.0F).texture(1.0F, 1.0F);
		BufferRenderer.drawWithGlobalProgram(buffer.end());

		String swapKey = ClientAbilityKeys.displayName(ClientAbilityKeys.primaryBinding());
		String voiceKey = ClientAbilityKeys.displayName(ClientAbilityKeys.secondaryBinding());
		Text controls = Text.translatable("hud.gexpress.twins.controls", swapKey, voiceKey);
		int controlsY = y + HUD_HEIGHT;
		context.fill(x, controlsY, x + HUD_WIDTH, controlsY + 14, 0xB8000000);
		context.drawCenteredTextWithShadow(client.textRenderer, controls,
			x + HUD_WIDTH / 2, controlsY + 3, 0xFFD7ECFF);
	}

	private static Entity findOwnedBody(MinecraftClient client) {
		if (client.world == null || client.player == null) return null;
		Entity nativeBody = ClientTwinsBodies.localBody(client);
		if (nativeBody != null && nativeBody.isAlive()) return nativeBody;
		for (Entity entity : client.world.getEntities()) {
			if (entity instanceof TwinBodyEntity body
					&& body.isAlive() && !body.isRemoved()
					&& body.ownerId().filter(client.player.getUuid()::equals).isPresent()) {
				return body;
			}
		}
		return null;
	}

	private static void configureRenderDispatchers(MinecraftClient client, Camera camera) {
		if (client.world == null) return;
		client.getEntityRenderDispatcher().configure(client.world, camera, client.targetedEntity);
		client.getBlockEntityRenderDispatcher().configure(client.world, camera, client.crosshairTarget);
	}
}
