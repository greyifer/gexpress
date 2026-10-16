package dev.mapselect.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.mapselect.block.LeaderboardBlockEntity;
import dev.mapselect.block.LeaderboardPanel;
import dev.mapselect.client.leaderboard.ClientLeaderboardState;
import dev.mapselect.leaderboard.LeaderboardCategory;
import dev.mapselect.leaderboard.LeaderboardLayout;
import dev.mapselect.network.leaderboard.LeaderboardSnapshotPayload;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class LeaderboardBlockEntityRenderer implements BlockEntityRenderer<LeaderboardBlockEntity> {
	private static final float TEXT_SCALE = LeaderboardLayout.TEXT_SCALE;
	private static final float SCREEN_MARGIN = LeaderboardLayout.SCREEN_MARGIN;
	private static final int HEADER_HEIGHT = LeaderboardLayout.HEADER_HEIGHT;
	private static final int FOOTER_HEIGHT = LeaderboardLayout.FOOTER_HEIGHT;
	private static final int ROW_HEIGHT = LeaderboardLayout.ROW_HEIGHT;
	private static final int HEAD_SIZE = 8;
	private static final int GLYPH_SIZE = 8;
	private static final int ROW_LEFT = 2;
	private static final int COLUMN_GAP = 4;
	private static final int NAME_VALUE_GAP = 6;
	private static final float CATEGORY_SLIDE_PIXELS = 12.0F;
	private static final long CATEGORY_TRANSITION_NANOS = 260_000_000L;
	private static final Identifier FONT_TEXTURE =
		Identifier.ofVanilla("textures/font/ascii.png");
	private static final int TITLE_COLOR = 0xFF70D7E7;
	private static final int RANK_COLOR = 0xFF87939A;
	private static final int NAME_COLOR = 0xFFE8F0F2;
	private static final int VALUE_COLOR = 0xFFFFC857;
	private static final int CONTROL_COLOR = 0xFF70D7E7;
	private static final int HOVER_COLOR = 0xFFFFC857;
	private static final double MAX_DISTANCE = 64.0D;
	private static final long DIRECT_RENDER_GRACE_NANOS = 100_000_000L;
	private static long lastDirectRenderNanos;
	private static boolean directRenderSeen;
	private static World fallbackWorld;
	private static BlockPos fallbackAnchor;
	private static final Map<BlockPos, CategoryAnimation> CATEGORY_ANIMATIONS = new HashMap<>();

	public LeaderboardBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(LeaderboardBlockEntityRenderer::renderFallback);
	}

	@Override
	public void render(LeaderboardBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || entity.getWorld() != client.world) return;
		resetFallbackWorld(client.world);
		LeaderboardPanel panel = LeaderboardPanel.find(client.world, entity.getPos());
		if (panel == null || !panel.isOrigin(entity.getPos())) return;
		LeaderboardBlockEntity controller = panel.controller(client.world);
		if (controller == null) return;

		Vec3d center = Vec3d.ofCenter(panel.origin())
			.add(Vec3d.of(panel.right().getVector()).multiply((panel.width() - 1) * 0.5D))
			.add(0.0D, (panel.height() - 1) * 0.5D, 0.0D);
		Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
		if (center.squaredDistanceTo(cameraPos) > MAX_DISTANCE * MAX_DISTANCE) return;
		if (cameraPos.subtract(center).dotProduct(Vec3d.of(panel.facing().getVector())) <= 0.0D) return;
		lastDirectRenderNanos = System.nanoTime();
		directRenderSeen = true;
		ClientLeaderboardState.DisplayState display = ClientLeaderboardState.display(client.world,
			panel.origin(), controller.category(), controller.scrollOffset());
		renderPanel(client, panel, display.category(), display.scrollOffset(),
			Vec3d.of(entity.getPos()), matrices, consumers);
	}

	private static void renderPanel(MinecraftClient client, LeaderboardPanel panel,
			LeaderboardCategory category, int scrollOffset, Vec3d coordinateOrigin,
			MatrixStack matrices, VertexConsumerProvider consumers) {
		int pixelWidth = LeaderboardLayout.pixelWidth(panel.width());
		int pixelHeight = LeaderboardLayout.pixelHeight(panel.height());
		int visibleRows = LeaderboardLayout.visibleRows(panel.height());
		HoverState hover = hoverState(client, panel, pixelWidth, pixelHeight, visibleRows);

		Direction facing = panel.facing();
		Direction right = panel.right();
		Vec3d screenTopLeft = Vec3d.ofCenter(panel.origin())
			.add(Vec3d.of(right.getVector()).multiply(panel.width() - 0.5D - SCREEN_MARGIN))
			.add(0.0D, panel.height() - 0.5D - SCREEN_MARGIN, 0.0D)
			.add(Vec3d.of(facing.getVector()).multiply(0.505D))
			.subtract(coordinateOrigin);

		matrices.push();
		matrices.translate(screenTopLeft.x, screenTopLeft.y, screenTopLeft.z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - facing.asRotation()));
		matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

		CategoryAnimation animation = CATEGORY_ANIMATIONS.computeIfAbsent(panel.origin(),
			ignored -> new CategoryAnimation(category, scrollOffset));
		animation.update(category, scrollOffset);
		float progress = animation.progress();
		if (animation.previous != null && progress < 1.0F) {
			float eased = smoothStep(progress);
			renderCategory(client, animation.previous, animation.previousScrollOffset,
				pixelWidth, pixelHeight, visibleRows, HoverState.NONE,
				-animation.direction * CATEGORY_SLIDE_PIXELS * eased, 1.0F - eased,
				matrices, consumers);
			renderCategory(client, category, scrollOffset, pixelWidth, pixelHeight, visibleRows, hover,
				animation.direction * CATEGORY_SLIDE_PIXELS * (1.0F - eased), eased,
				matrices, consumers);
		} else {
			animation.finish();
			renderCategory(client, category, scrollOffset, pixelWidth, pixelHeight, visibleRows, hover,
				0.0F, 1.0F, matrices, consumers);
		}
		matrices.pop();
	}

	private static void renderCategory(MinecraftClient client, LeaderboardCategory category,
			int scrollOffset, int pixelWidth, int pixelHeight, int visibleRows, HoverState hover,
			float xOffset, float opacity, MatrixStack matrices, VertexConsumerProvider consumers) {
		if (opacity <= 0.001F) return;
		List<LeaderboardSnapshotPayload.Entry> entries = ClientLeaderboardState.ranked(category);
		int maximumOffset = Math.max(0, entries.size() - visibleRows);
		int offset = Math.min(scrollOffset, maximumOffset);
		matrices.push();
		matrices.translate(xOffset, 0.0F, 0.0F);

		String title = trim(category.displayName().toUpperCase(Locale.ROOT), pixelWidth - 16);
		draw(title, (pixelWidth - textWidth(title)) / 2.0F, 1.0F,
			TITLE_COLOR, opacity, matrices, consumers);
		draw("-".repeat(Math.max(1, Math.min(128, pixelWidth / GLYPH_SIZE))), 1.0F,
			HEADER_HEIGHT - 4.0F, RANK_COLOR, opacity, matrices, consumers);

		int rankColumnWidth = textWidth("#" + Math.max(1, entries.size()));
		float headX = ROW_LEFT + rankColumnWidth + COLUMN_GAP;
		float nameX = headX + HEAD_SIZE + COLUMN_GAP;
		for (int row = 0; row < visibleRows && offset + row < entries.size(); row++) {
			LeaderboardSnapshotPayload.Entry entry = entries.get(offset + row);
			boolean hovered = hover.row() == row;
			int rank = offset + row + 1;
			float rowY = HEADER_HEIGHT + row * ROW_HEIGHT;
			String rankText = (hovered ? ">" : "#") + rank;
			String valueText = formatValue(entry, category);
			int valueWidth = textWidth(valueText);
			int nameWidth = Math.max(4,
				(int) (pixelWidth - nameX - valueWidth - NAME_VALUE_GAP - ROW_LEFT));
			String name = trim(entry.name(), nameWidth);
			renderHead(client, entry, headX, rowY, HEAD_SIZE, opacity, matrices, consumers);
			draw(rankText, ROW_LEFT, rowY,
				hovered ? HOVER_COLOR : RANK_COLOR, opacity, matrices, consumers);
			draw(name, nameX, rowY,
				hovered ? HOVER_COLOR : NAME_COLOR, opacity, matrices, consumers);
			draw(valueText, pixelWidth - valueWidth - 1.0F, rowY,
				hovered ? NAME_COLOR : VALUE_COLOR, opacity, matrices, consumers);
		}

		float footerY = pixelHeight - 9.0F;
		String previous = hover.previous() ? "[<]" : "<";
		draw(previous, 2.0F, footerY,
			hover.previous() ? HOVER_COLOR : CONTROL_COLOR, opacity, matrices, consumers);
		String page = entries.isEmpty()
			? "No records yet"
			: (offset + 1) + "-" + Math.min(entries.size(), offset + visibleRows) + " / " + entries.size();
		page = trim(page, pixelWidth - 30);
		draw(page, (pixelWidth - textWidth(page)) / 2.0F,
			footerY, RANK_COLOR, opacity, matrices, consumers);
		String next = hover.next() ? "[>]" : ">";
		draw(next, pixelWidth - textWidth(next) - 2.0F,
			footerY, hover.next() ? HOVER_COLOR : CONTROL_COLOR, opacity, matrices, consumers);
		matrices.pop();
	}

	private static void renderFallback(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		resetFallbackWorld(client.world);
		if (client.crosshairTarget instanceof BlockHitResult hit
				&& client.world.getBlockState(hit.getBlockPos()).isOf(MapSelectBlocks.LEADERBOARD)) {
			fallbackAnchor = hit.getBlockPos().toImmutable();
		}
		if (fallbackAnchor == null
				|| directRenderSeen
				&& System.nanoTime() - lastDirectRenderNanos <= DIRECT_RENDER_GRACE_NANOS) {
			return;
		}

		LeaderboardPanel panel = LeaderboardPanel.find(client.world, fallbackAnchor);
		if (panel == null) {
			fallbackAnchor = null;
			return;
		}
		Vec3d cameraPos = context.camera().getPos();
		Vec3d center = Vec3d.ofCenter(panel.origin())
			.add(Vec3d.of(panel.right().getVector()).multiply((panel.width() - 1) * 0.5D))
			.add(0.0D, (panel.height() - 1) * 0.5D, 0.0D);
		if (center.squaredDistanceTo(cameraPos) > MAX_DISTANCE * MAX_DISTANCE
				|| cameraPos.subtract(center).dotProduct(Vec3d.of(panel.facing().getVector())) <= 0.0D) {
			return;
		}

		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		boolean flushConsumers = false;
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
			flushConsumers = true;
		}
		if (matrices == null || consumers == null) return;
		LeaderboardBlockEntity controller = panel.controller(client.world);
		LeaderboardCategory defaultCategory = controller == null
			? LeaderboardCategory.KILLS : controller.category();
		int defaultScrollOffset = controller == null ? 0 : controller.scrollOffset();
		ClientLeaderboardState.DisplayState display = ClientLeaderboardState.display(client.world,
			panel.origin(), defaultCategory, defaultScrollOffset);
		LeaderboardCategory category = display.category();
		int scrollOffset = display.scrollOffset();
		renderPanel(client, panel, category, scrollOffset, cameraPos, matrices, consumers);
		if (flushConsumers && consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private static void resetFallbackWorld(World world) {
		if (fallbackWorld == world) return;
		fallbackWorld = world;
		fallbackAnchor = null;
		directRenderSeen = false;
		lastDirectRenderNanos = 0L;
		CATEGORY_ANIMATIONS.clear();
	}

	private static void renderHead(MinecraftClient client, LeaderboardSnapshotPayload.Entry player,
			float x, float y, float size, float opacity, MatrixStack matrices,
			VertexConsumerProvider consumers) {
		Identifier texture = skinTexture(client, player);
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
		MatrixStack.Entry matrix = matrices.peek();
		int alpha = Math.max(0, Math.min(255, Math.round(255.0F * opacity)));
		faceQuad(vertices, matrix, x, y, size, -0.02F,
			8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, alpha);
		faceQuad(vertices, matrix, x, y, size, -0.04F,
			40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F, alpha);
	}

	private static Identifier skinTexture(MinecraftClient client, LeaderboardSnapshotPayload.Entry player) {
		PlayerListEntry online = client.getNetworkHandler() == null
			? null
			: client.getNetworkHandler().getPlayerListEntry(player.playerId());
		if (online != null) return online.getSkinTextures().texture();
		try {
			GameProfile profile = new GameProfile(player.playerId(), player.name());
			if (!player.textureValue().isBlank()) {
				Property textures = player.textureSignature().isBlank()
					? new Property("textures", player.textureValue())
					: new Property("textures", player.textureValue(), player.textureSignature());
				profile.getProperties().put("textures", textures);
			}
			return client.getSkinProvider()
				.getSkinTextures(profile)
				.texture();
		} catch (Throwable ignored) {
			return DefaultSkinHelper.getSkinTextures(player.playerId()).texture();
		}
	}

	private static void faceQuad(VertexConsumer vertices, MatrixStack.Entry matrix, float x, float y,
			float size, float z, float u1, float v1, float u2, float v2, int alpha) {
		headVertex(vertices, matrix, x, y, z, u1, v1, alpha);
		headVertex(vertices, matrix, x, y + size, z, u1, v2, alpha);
		headVertex(vertices, matrix, x + size, y + size, z, u2, v2, alpha);
		headVertex(vertices, matrix, x + size, y, z, u2, v1, alpha);
	}

	private static void headVertex(VertexConsumer vertices, MatrixStack.Entry matrix, float x, float y,
			float z, float u, float v, int alpha) {
		vertices.vertex(matrix, x, y, z)
			.color(255, 255, 255, alpha)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
			.normal(matrix, 0.0F, 0.0F, -1.0F);
	}

	private static HoverState hoverState(MinecraftClient client, LeaderboardPanel panel,
			int pixelWidth, int pixelHeight, int visibleRows) {
		if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getSide() != panel.facing()) {
			return HoverState.NONE;
		}
		LeaderboardPanel hitPanel = LeaderboardPanel.find(client.world, hit.getBlockPos());
		if (hitPanel == null || !hitPanel.origin().equals(panel.origin())
				|| hitPanel.width() != panel.width() || hitPanel.height() != panel.height()) {
			return HoverState.NONE;
		}
		double pixelX = (panel.screenHorizontalCoordinate(hit.getPos()) - SCREEN_MARGIN) / TEXT_SCALE;
		double pixelY = (panel.height() - SCREEN_MARGIN - panel.verticalCoordinate(hit.getPos()))
			/ TEXT_SCALE;
		if (pixelX < 0.0D || pixelX > pixelWidth || pixelY < 0.0D || pixelY > pixelHeight) {
			return HoverState.NONE;
		}
		if (pixelY >= pixelHeight - FOOTER_HEIGHT) {
			return new HoverState(-1, pixelX <= 16.0D, pixelX >= pixelWidth - 16.0D);
		}
		if (pixelY >= HEADER_HEIGHT && pixelY < HEADER_HEIGHT + visibleRows * ROW_HEIGHT) {
			return new HoverState((int) ((pixelY - HEADER_HEIGHT) / ROW_HEIGHT), false, false);
		}
		return HoverState.NONE;
	}

	private static String formatValue(LeaderboardSnapshotPayload.Entry entry, LeaderboardCategory category) {
		long value = ClientLeaderboardState.value(entry, category);
		if (category != LeaderboardCategory.LONGEST_ALIVE) return Long.toString(value);
		long seconds = value / 20L;
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long remainingSeconds = seconds % 60L;
		return hours > 0L
			? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
			: String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
	}

	private static void draw(String text, float x, float y, int color, float opacity,
			MatrixStack matrices, VertexConsumerProvider consumers) {
		if (text == null || text.isEmpty() || opacity <= 0.001F) return;
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(FONT_TEXTURE));
		MatrixStack.Entry matrix = matrices.peek();
		int red = color >> 16 & 0xFF;
		int green = color >> 8 & 0xFF;
		int blue = color & 0xFF;
		int alpha = Math.max(0, Math.min(255, Math.round((color >>> 24) * opacity)));
		float cursor = x;
		for (int i = 0; i < text.length(); i++) {
			char character = text.charAt(i);
			int glyph = character >= 32 && character <= 126 ? character : '?';
			if (glyph != ' ') {
				int column = glyph & 15;
				int row = glyph >> 4;
				float u1 = column / 16.0F;
				float v1 = row / 16.0F;
				float u2 = (column + 1) / 16.0F;
				float v2 = (row + 1) / 16.0F;
				glyphQuad(vertices, matrix, cursor, y, -0.01F, u1, v1, u2, v2,
					red, green, blue, alpha);
			}
			cursor += GLYPH_SIZE;
		}
	}

	private static void glyphQuad(VertexConsumer vertices, MatrixStack.Entry matrix, float x, float y,
			float z, float u1, float v1, float u2, float v2,
			int red, int green, int blue, int alpha) {
		glyphVertex(vertices, matrix, x, y, z, u1, v1, red, green, blue, alpha);
		glyphVertex(vertices, matrix, x, y + GLYPH_SIZE, z, u1, v2, red, green, blue, alpha);
		glyphVertex(vertices, matrix, x + GLYPH_SIZE, y + GLYPH_SIZE, z, u2, v2,
			red, green, blue, alpha);
		glyphVertex(vertices, matrix, x + GLYPH_SIZE, y, z, u2, v1, red, green, blue, alpha);
	}

	private static void glyphVertex(VertexConsumer vertices, MatrixStack.Entry matrix, float x, float y,
			float z, float u, float v, int red, int green, int blue, int alpha) {
		vertices.vertex(matrix, x, y, z)
			.color(red, green, blue, alpha)
			.texture(u, v)
			.overlay(OverlayTexture.DEFAULT_UV)
			.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
			.normal(matrix, 0.0F, 0.0F, -1.0F);
	}

	private static int textWidth(String text) {
		return text == null ? 0 : text.length() * GLYPH_SIZE;
	}

	private static String trim(String value, int maximumWidth) {
		if (value == null || value.isBlank()) return "Unknown";
		if (textWidth(value) <= maximumWidth) return value;
		String suffix = "...";
		int availableCharacters = Math.max(0, maximumWidth / GLYPH_SIZE - suffix.length());
		return value.substring(0, Math.min(value.length(), availableCharacters)) + suffix;
	}

	private static float smoothStep(float value) {
		float clamped = Math.max(0.0F, Math.min(1.0F, value));
		return clamped * clamped * (3.0F - 2.0F * clamped);
	}

	@Override
	public boolean rendersOutsideBoundingBox(LeaderboardBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return (int) MAX_DISTANCE;
	}

	private record HoverState(int row, boolean previous, boolean next) {
		private static final HoverState NONE = new HoverState(-1, false, false);
	}

	private static final class CategoryAnimation {
		private LeaderboardCategory current;
		private LeaderboardCategory previous;
		private int currentScrollOffset;
		private int previousScrollOffset;
		private int direction = 1;
		private long startedNanos;

		private CategoryAnimation(LeaderboardCategory category, int scrollOffset) {
			current = category;
			currentScrollOffset = scrollOffset;
		}

		private void update(LeaderboardCategory category, int scrollOffset) {
			if (category != current) {
				previous = current;
				previousScrollOffset = currentScrollOffset;
				direction = current.cycle(1) == category ? 1 : -1;
				current = category;
				startedNanos = System.nanoTime();
			}
			currentScrollOffset = scrollOffset;
		}

		private float progress() {
			if (previous == null) return 1.0F;
			return Math.min(1.0F,
				(float) (System.nanoTime() - startedNanos) / CATEGORY_TRANSITION_NANOS);
		}

		private void finish() {
			previous = null;
		}
	}
}
