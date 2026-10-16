package dev.mapselect.client.poll;

import dev.mapselect.client.screen.LobbyPollBuilderScreen;
import dev.mapselect.client.screen.LobbyPollVoteScreen;
import dev.mapselect.network.poll.LobbyPollBuilderOpenPayload;
import dev.mapselect.network.poll.LobbyPollStatusPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ClientLobbyPollState {
	private static final int TAB_W = 18;
	private static final int TAB_H = 42;
	private static LobbyPollStatusPayload current =
		new LobbyPollStatusPayload(false, "", "", 0, 0, 0, 0);
	private static boolean minimized;
	private static String activeSignature = "";

	private ClientLobbyPollState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LobbyPollBuilderOpenPayload.ID, (payload, context) ->
			context.client().execute(() -> context.client()
				.setScreen(new LobbyPollBuilderScreen(context.client().currentScreen))));
		ClientPlayNetworking.registerGlobalReceiver(LobbyPollStatusPayload.ID, (payload, context) ->
			context.client().execute(() -> applyStatus(context.client(), payload)));
		HudRenderCallback.EVENT.register(ClientLobbyPollState::renderHud);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) ->
				renderPersistentTab(context, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, button) ->
				!tryOpenFromTab(mouseX, mouseY, button,
					client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight()));
		});
	}

	public static LobbyPollStatusPayload current() {
		return current;
	}

	private static void applyStatus(MinecraftClient client, LobbyPollStatusPayload payload) {
		String nextSignature = pollSignature(payload);
		if (!nextSignature.equals(activeSignature)) {
			activeSignature = nextSignature;
			minimized = false;
		}
		current = payload;
		Screen screen = client.currentScreen;
		if (!payload.active()) {
			minimized = false;
			activeSignature = "";
			if (screen instanceof LobbyPollVoteScreen) {
				client.setScreen(null);
			}
			return;
		}
		if (screen instanceof LobbyPollVoteScreen voteScreen) {
			voteScreen.refresh(payload);
		} else if (!minimized && screen == null) {
			client.setScreen(new LobbyPollVoteScreen(payload));
		}
	}

	public static void hideVoteScreen(MinecraftClient client) {
		minimizeVoteScreen(client);
	}

	public static void minimizeVoteScreen(MinecraftClient client) {
		minimized = true;
		if (client != null && client.currentScreen instanceof LobbyPollVoteScreen voteScreen) {
			voteScreen.beginMinimize();
		}
	}

	public static void finishMinimize(MinecraftClient client, LobbyPollVoteScreen screen) {
		minimized = true;
		if (client != null && client.currentScreen == screen) {
			client.setScreen(null);
		}
	}

	public static void openVoteScreen(MinecraftClient client) {
		if (client == null || !current.active()) return;
		minimized = false;
		client.setScreen(new LobbyPollVoteScreen(current));
	}

	public static boolean shouldRenderTab(MinecraftClient client) {
		return client != null && current.active() && !(client.currentScreen instanceof LobbyPollVoteScreen);
	}

	public static void renderPersistentTab(DrawContext context, int mouseX, int mouseY) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!shouldRenderTab(client)) return;
		int x = tabX(context.getScaledWindowWidth());
		int y = tabY(context.getScaledWindowHeight());
		boolean hovered = inside(mouseX, mouseY, x, y, TAB_W, TAB_H);
		context.fill(x, y, x + TAB_W, y + TAB_H, hovered ? 0xEE2D3846 : 0xDD151B24);
		context.drawBorder(x, y, TAB_W, TAB_H, hovered ? 0xFFFFFFFF : 0xAAE7EEF8);
		context.drawCenteredTextWithShadow(client.textRenderer,
			Text.literal("<").formatted(Formatting.BOLD),
			x + TAB_W / 2, y + TAB_H / 2 - 4, 0xFFFFFFFF);
	}

	public static boolean tryOpenFromTab(double mouseX, double mouseY, int button, int width, int height) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (button != 0 || !shouldRenderTab(client)) return false;
		if (!inside(mouseX, mouseY, tabX(width), tabY(height), TAB_W, TAB_H)) return false;
		openVoteScreen(client);
		return true;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.currentScreen != null) return;
		renderPersistentTab(context, Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	private static int tabX(int width) {
		return width - TAB_W;
	}

	private static int tabY(int height) {
		return Math.max(18, height / 2 - TAB_H / 2);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private static String pollSignature(LobbyPollStatusPayload payload) {
		if (payload == null || !payload.active()) return "";
		return payload.title() + "\n" + payload.details();
	}
}
