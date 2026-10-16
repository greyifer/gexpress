package dev.mapselect.client.role.pelican;

import dev.mapselect.network.role.pelican.PelicanChatPopupDismissPayload;
import dev.mapselect.network.role.pelican.PelicanChatPopupPayload;
import io.github.caspe.glib.api.client.ui.GlibUi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Renders swallowed-player chat as non-blocking HUD cards. Cards never open a screen or capture
 * the mouse; the owning Pelican can click their close buttons while the normal chat screen is open.
 */
public final class ClientPelicanChatPopups {
	private static final int PANEL = 0xF410151D;
	private static final int SURFACE = 0xE61A212B;
	private static final int SURFACE_HOVER = 0xF1242E3A;
	private static final int LINE = 0x70424C5B;
	private static final int ACCENT = 0xFF4CDB8A;
	private static final int OPEN_MILLIS = 260;
	private static final int TYPEWRITER_DELAY_MILLIS = 110;
	private static final int TYPEWRITER_CHAR_MILLIS = 28;
	private static final int DODGE_MOVE_MILLIS = 460;
	private static final float CLOSE_DODGE_CHANCE = 0.35F;
	private static final float MIN_DODGE_DISTANCE = 0.28F;
	private static final int HEAD_SIZE = 20;
	private static final int MIN_CARD_WIDTH = 158;
	private static final int MAX_CARD_WIDTH = 340;
	private static final int MIN_CARD_HEIGHT = 64;
	private static final float CARD_LAYER_DEPTH = 100.0F;
	private static final Map<UUID, PopupCard> POPUPS = new LinkedHashMap<>();
	private static Map<UUID, CardBounds> cachedLayout = Map.of();
	private static int layoutRevision;
	private static int cachedLayoutRevision = -1;
	private static int cachedLayoutWidth = -1;
	private static int cachedLayoutHeight = -1;
	private static int delayedErrorToneTicks = -1;

	private ClientPelicanChatPopups() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PelicanChatPopupPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientPelicanChatPopups::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
			client.execute(ClientPelicanChatPopups::clearAll));
		HudRenderCallback.EVENT.register(ClientPelicanChatPopups::renderHud);
	}

	private static void apply(MinecraftClient client, PelicanChatPopupPayload payload) {
		if (client == null || payload == null) return;
		switch (payload.action()) {
			case ADD -> {
				PopupCard existing = POPUPS.get(payload.popupId());
				boolean fresh = existing == null;
				POPUPS.put(payload.popupId(), new PopupCard(
					payload.popupId(),
					payload.pelicanId(),
					payload.senderId(),
					payload.senderName(),
					payload.message(),
					payload.canClose(),
					fresh ? Util.getMeasuringTimeMs() : existing.openedAtMillis(),
					fresh ? randomCoordinate(payload.popupId(), 0x51A7E2D4L) : existing.moveStartX(),
					fresh ? randomCoordinate(payload.popupId(), 0x2C4B91F7L) : existing.moveStartY(),
					fresh ? randomCoordinate(payload.popupId(), 0x51A7E2D4L) : existing.moveTargetX(),
					fresh ? randomCoordinate(payload.popupId(), 0x2C4B91F7L) : existing.moveTargetY(),
					fresh ? 0L : existing.moveStartedAtMillis()
				));
				if (fresh) playErrorSound(client);
			}
			case REMOVE -> POPUPS.remove(payload.popupId());
			case CLEAR -> POPUPS.entrySet().removeIf(entry ->
				entry.getValue().pelicanId().equals(payload.pelicanId()));
		}
		layoutRevision++;
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.getNetworkHandler() == null) {
			clearAll();
			return;
		}
		if (delayedErrorToneTicks == 0) {
			delayedErrorToneTicks = -1;
			if (client.getSoundManager() != null) {
				client.getSoundManager().play(PositionedSoundInstance.master(
					SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.72F, 0.72F));
			}
		} else if (delayedErrorToneTicks > 0) {
			delayedErrorToneTicks--;
		}
	}

	private static void playErrorSound(MinecraftClient client) {
		if (client.getSoundManager() == null) return;
		client.getSoundManager().play(PositionedSoundInstance.master(
			SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.56F, 0.9F));
		delayedErrorToneTicks = 2;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || POPUPS.isEmpty()
				|| client.currentScreen instanceof ChatScreen
				|| client.currentScreen instanceof PelicanPopupInteractionScreen) {
			return;
		}
		renderCards(context, client, -1.0D, -1.0D, false);
	}

	public static void renderOverChat(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || POPUPS.isEmpty()
				|| !(client.currentScreen instanceof ChatScreen)) {
			return;
		}
		renderInteractive(context, mouseX, mouseY);
	}

	public static boolean handleChatClick(double mouseX, double mouseY, int button) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || !(client.currentScreen instanceof ChatScreen)) return false;
		return handleInteractiveClick(mouseX, mouseY, button);
	}

	public static void renderInteractive(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || POPUPS.isEmpty()) return;
		renderCards(context, client, mouseX, mouseY, true);
	}

	public static boolean handleInteractiveClick(double mouseX, double mouseY, int button) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (button != 0 || client == null || client.player == null || client.textRenderer == null
				|| POPUPS.isEmpty()) {
			return false;
		}
		List<PopupCard> cards = new ArrayList<>(POPUPS.values());
		Map<UUID, CardBounds> layout = layoutCards(client,
			client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
		for (int index = cards.size() - 1; index >= 0; index--) {
			PopupCard card = cards.get(index);
			CardBounds bounds = layout.get(card.popupId());
			if (bounds == null || !bounds.contains(mouseX, mouseY)) continue;
			if (card.canClose() && bounds.closeContains(mouseX, mouseY)
					&& ClientPlayNetworking.canSend(PelicanChatPopupDismissPayload.ID)) {
				if (tryDodgeClose(card)) return true;
				ClientPlayNetworking.send(new PelicanChatPopupDismissPayload(card.popupId()));
				return true;
			}
			// The top card owns its whole rectangle, so clicks cannot pass through it to the
			// close button of a card rendered underneath.
			return false;
		}
		return false;
	}

	private static boolean tryDodgeClose(PopupCard card) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (random.nextFloat() >= CLOSE_DODGE_CHANCE) return false;

		long now = Util.getMeasuringTimeMs();
		float currentX = card.anchorX(now);
		float currentY = card.anchorY(now);
		float targetX = currentX;
		float targetY = currentY;
		for (int attempt = 0; attempt < 8; attempt++) {
			targetX = randomCoordinate(random);
			targetY = randomCoordinate(random);
			float deltaX = targetX - currentX;
			float deltaY = targetY - currentY;
			if (deltaX * deltaX + deltaY * deltaY >= MIN_DODGE_DISTANCE * MIN_DODGE_DISTANCE) break;
		}
		POPUPS.put(card.popupId(), card.moveTo(targetX, targetY, now));
		layoutRevision++;
		return true;
	}

	private static void renderCards(DrawContext context, MinecraftClient client,
			double mouseX, double mouseY, boolean interactive) {
		if (client.textRenderer == null || POPUPS.isEmpty()) return;
		Map<UUID, CardBounds> layout = layoutCards(client,
			context.getScaledWindowWidth(), context.getScaledWindowHeight());
		int layer = 0;
		for (PopupCard card : POPUPS.values()) {
			CardBounds bounds = layout.get(card.popupId());
			if (bounds == null) continue;
			boolean closeHovered = interactive && card.canClose() && bounds.closeContains(mouseX, mouseY);
			// Each popup is flushed and placed on its own Z layer. This keeps the complete newer
			// card—including its panel—above every head and text glyph belonging to older cards.
			context.draw();
			renderCard(context, client, card, bounds, closeHovered, layer++ * CARD_LAYER_DEPTH);
		}
	}

	private static void renderCard(DrawContext context, MinecraftClient client, PopupCard card,
			CardBounds bounds, boolean closeHovered, float layerDepth) {
		long elapsed = Math.max(0L, Util.getMeasuringTimeMs() - card.openedAtMillis());
		float rawProgress = MathHelper.clamp(elapsed / (float) OPEN_MILLIS, 0.0F, 1.0F);
		float progress = GlibUi.easeOutCubic(rawProgress);
		float scale = 0.82F + 0.18F * progress;
		int x = bounds.x();
		int y = bounds.y();
		int alpha = Math.max(0, Math.min(255, Math.round(progress * 255.0F)));
		float centerX = x + bounds.width() / 2.0F;
		float centerY = y + bounds.height() / 2.0F;

		context.enableScissor(
			Math.max(0, x - 2),
			Math.max(0, y - 2),
			Math.min(context.getScaledWindowWidth(), x + bounds.width() + 6),
			Math.min(context.getScaledWindowHeight(), y + bounds.height() + 7)
		);
		context.getMatrices().push();
		context.getMatrices().translate(centerX, centerY, layerDepth);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(-centerX, -centerY, 0.0F);

		context.fill(x + 4, y + 5, x + bounds.width() + 4, y + bounds.height() + 5,
			GlibUi.withAlpha(0x80000000, alpha));
		GlibUi.drawPanel(context, x, y, bounds.width(), bounds.height(),
			GlibUi.withAlpha(PANEL, alpha), GlibUi.withAlpha(LINE, alpha));
		context.fill(x, y, x + bounds.width(), y + 3, GlibUi.withAlpha(ACCENT, alpha));

		drawHead(context, client, card.senderId(), x + 9, y + 9);
		context.drawTextWithShadow(client.textRenderer, Text.literal(card.senderName()),
			x + 36, y + 15, GlibUi.withAlpha(ACCENT, alpha));

		if (card.canClose()) {
			int closeLeft = x + bounds.width() - 25;
			if (closeHovered) {
				context.fill(closeLeft, y + 6, x + bounds.width() - 5, y + 25,
					GlibUi.withAlpha(SURFACE_HOVER, alpha));
			}
			context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("X"),
				x + bounds.width() - 15, y + 11,
				GlibUi.withAlpha(closeHovered ? ACCENT : 0xFFFFFFFF, alpha));
		}

		int messageTop = y + 37;
		context.fill(x + 8, messageTop - 3, x + bounds.width() - 8, y + bounds.height() - 8,
			GlibUi.withAlpha(SURFACE, alpha));
		context.fill(x + 8, messageTop - 3, x + 11, y + bounds.height() - 8,
			GlibUi.withAlpha(ACCENT, alpha));

		String visibleMessage = visiblePrefix(card.message(), elapsed);
		List<OrderedText> lines = client.textRenderer.wrapLines(
			Text.literal(visibleMessage), Math.max(20, bounds.width() - 30));
		int maxLines = Math.max(1, (bounds.height() - messageTop - 10) / 10);
		for (int line = 0; line < Math.min(maxLines, lines.size()); line++) {
			context.drawTextWithShadow(client.textRenderer, lines.get(line),
				x + 17, messageTop + line * 10, GlibUi.withAlpha(0xFFFFFFFF, alpha));
		}

		context.getMatrices().pop();
		context.disableScissor();
		// Text, player skins, and solid panels use different render layers. Flush each complete card
		// before starting the next one so a previous card's text cannot surface above a later panel.
		context.draw();
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y) {
		if (playerId == null) return;
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.fill(x - 1, y - 1, x + HEAD_SIZE + 1, y + HEAD_SIZE + 1, 0xFF315A43);
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE,
			8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, HEAD_SIZE, HEAD_SIZE,
			40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static CardBounds preferredBounds(MinecraftClient client, PopupCard card,
			int screenWidth, int screenHeight, long now) {
		int screenMaxWidth = Math.max(96, screenWidth - 16);
		int maxWidth = Math.min(MAX_CARD_WIDTH, screenMaxWidth);
		int lengthWidth = MIN_CARD_WIDTH + Math.min(MAX_CARD_WIDTH - MIN_CARD_WIDTH,
			Math.round((float) Math.sqrt(Math.max(0, card.message().codePointCount(
				0, card.message().length()))) * 11.0F));
		int headerWidth = 48 + client.textRenderer.getWidth(card.senderName()) + (card.canClose() ? 28 : 8);
		int width = clamp(Math.max(lengthWidth, headerWidth), Math.min(MIN_CARD_WIDTH, maxWidth), maxWidth);
		int lineCount = Math.max(1, client.textRenderer.wrapLines(
			Text.literal(card.message()), Math.max(20, width - 30)).size());
		int maxHeight = Math.max(MIN_CARD_HEIGHT, screenHeight - 16);
		int height = clamp(50 + lineCount * 10, MIN_CARD_HEIGHT, maxHeight);

		int availableX = Math.max(0, screenWidth - width - 16);
		int availableY = Math.max(0, screenHeight - height - 34);
		int x = 8 + Math.round(card.anchorX(now) * availableX);
		int y = 8 + Math.round(card.anchorY(now) * availableY);
		return new CardBounds(x, y, width, height);
	}

	private static Map<UUID, CardBounds> layoutCards(MinecraftClient client,
			int screenWidth, int screenHeight) {
		long now = Util.getMeasuringTimeMs();
		boolean moving = POPUPS.values().stream().anyMatch(card -> card.isMoving(now));
		if (!moving && cachedLayoutRevision == layoutRevision && cachedLayoutWidth == screenWidth
				&& cachedLayoutHeight == screenHeight) {
			return cachedLayout;
		}
		Map<UUID, CardBounds> layout = new LinkedHashMap<>();
		for (PopupCard card : POPUPS.values()) {
			layout.put(card.popupId(), preferredBounds(client, card, screenWidth, screenHeight, now));
		}
		Map<UUID, CardBounds> result = Map.copyOf(layout);
		if (!moving) {
			cachedLayout = result;
			cachedLayoutRevision = layoutRevision;
			cachedLayoutWidth = screenWidth;
			cachedLayoutHeight = screenHeight;
		}
		return result;
	}

	private static String visiblePrefix(String message, long elapsedMillis) {
		if (message == null || message.isEmpty()) return "";
		long typingMillis = Math.max(0L, elapsedMillis - TYPEWRITER_DELAY_MILLIS);
		int totalCodePoints = message.codePointCount(0, message.length());
		int visibleCodePoints = Math.min(totalCodePoints,
			(int) Math.min(Integer.MAX_VALUE, typingMillis / TYPEWRITER_CHAR_MILLIS));
		if (visibleCodePoints >= totalCodePoints) return message;
		int endIndex = message.offsetByCodePoints(0, Math.max(0, visibleCodePoints));
		return message.substring(0, endIndex);
	}

	private static float randomCoordinate(UUID popupId, long salt) {
		if (popupId == null) return 0.5F;
		Random random = new Random(popupId.getMostSignificantBits()
			^ Long.rotateLeft(popupId.getLeastSignificantBits(), 23) ^ salt);
		return 0.03F + random.nextFloat() * 0.94F;
	}

	private static float randomCoordinate(Random random) {
		return 0.03F + random.nextFloat() * 0.94F;
	}

	private static float smoothLerp(float start, float end, float progress) {
		float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
		float eased = clamped * clamped * (3.0F - 2.0F * clamped);
		return MathHelper.lerp(eased, start, end);
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) return max;
		return Math.max(min, Math.min(max, value));
	}

	public static void clearAll() {
		POPUPS.clear();
		layoutRevision++;
		delayedErrorToneTicks = -1;
	}

	private record PopupCard(UUID popupId, UUID pelicanId, UUID senderId, String senderName,
			String message, boolean canClose, long openedAtMillis,
			float moveStartX, float moveStartY, float moveTargetX, float moveTargetY,
			long moveStartedAtMillis) {
		private float anchorX(long now) {
			return smoothLerp(moveStartX, moveTargetX, moveProgress(now));
		}

		private float anchorY(long now) {
			return smoothLerp(moveStartY, moveTargetY, moveProgress(now));
		}

		private boolean isMoving(long now) {
			return moveStartedAtMillis > 0L && now - moveStartedAtMillis < DODGE_MOVE_MILLIS;
		}

		private float moveProgress(long now) {
			if (moveStartedAtMillis <= 0L) return 1.0F;
			return (now - moveStartedAtMillis) / (float) DODGE_MOVE_MILLIS;
		}

		private PopupCard moveTo(float targetX, float targetY, long now) {
			return new PopupCard(popupId, pelicanId, senderId, senderName, message, canClose,
				openedAtMillis, anchorX(now), anchorY(now), targetX, targetY, now);
		}
	}

	private record CardBounds(int x, int y, int width, int height) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width
				&& mouseY >= y && mouseY < y + height;
		}

		private boolean closeContains(double mouseX, double mouseY) {
			return mouseX >= x + width - 25 && mouseX < x + width - 5
				&& mouseY >= y + 6 && mouseY < y + 25;
		}
	}
}
