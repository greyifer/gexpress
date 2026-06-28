package dev.mapselect.client.tutorial;

import dev.mapselect.client.screen.GexpressTutorialStore;
import dev.mapselect.network.tutorial.TutorialControlPayload;
import dev.mapselect.network.tutorial.TutorialStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import dev.mapselect.client.screen.GexpressTutorialPauseScreen;

public final class ClientTutorialExperience {
	private static boolean active;
	private static int stage;
	private static String instruction = "";
	private static int fadeRemaining;
	private static int fadeDuration;
	private static int axiomOpenDelay;

	private ClientTutorialExperience() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TutorialStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientTutorialExperience::tick);
		HudRenderCallback.EVENT.register(ClientTutorialExperience::render);
	}

	public static void start() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) client.setScreen(null);
		if (ClientPlayNetworking.canSend(TutorialControlPayload.ID)) {
			ClientPlayNetworking.send(new TutorialControlPayload(TutorialControlPayload.START));
		}
	}

	public static void editRoomWithAxiom() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) client.setScreen(null);
		if (ClientPlayNetworking.canSend(TutorialControlPayload.ID)) {
			ClientPlayNetworking.send(new TutorialControlPayload(TutorialControlPayload.EDIT_ROOM));
			axiomOpenDelay = 20;
		}
	}

	public static void saveRoom() {
		if (ClientPlayNetworking.canSend(TutorialControlPayload.ID)) {
			ClientPlayNetworking.send(new TutorialControlPayload(TutorialControlPayload.SAVE_ROOM));
		}
	}

	public static void requestExit() {
		active = false;
		axiomOpenDelay = 0;
		if (ClientPlayNetworking.canSend(TutorialControlPayload.ID)) {
			ClientPlayNetworking.send(new TutorialControlPayload(TutorialControlPayload.EXIT));
		}
	}

	public static boolean active() {
		return active;
	}

	public static boolean axiomInstalled() {
		return FabricLoader.getInstance().isModLoaded("axiom");
	}

	public static void openAxiom() {
		if (!axiomInstalled()) return;
		try {
			Class<?> editor = Class.forName("com.moulberry.axiom.editor.EditorUI");
			editor.getMethod("enable").invoke(null);
		} catch (ReflectiveOperationException ignored) {
			// Axiom is optional and has no stable editor-opening API.
		}
	}

	private static void apply(TutorialStatePayload payload) {
		active = payload.active();
		stage = payload.stage();
		instruction = payload.instruction();
		if (payload.fadeTicks() > 0) {
			fadeDuration = payload.fadeTicks();
			fadeRemaining = payload.fadeTicks();
		}
		if (payload.completed()) GexpressTutorialStore.markSeen();
	}

	private static void tick(MinecraftClient client) {
		if (fadeRemaining > 0) fadeRemaining--;
		if (axiomOpenDelay > 0 && --axiomOpenDelay == 0) openAxiom();
		if (client == null || client.player == null || client.world == null) {
			active = false;
			return;
		}
		if (active && client.currentScreen instanceof GameMenuScreen) {
			client.setScreen(new GexpressTutorialPauseScreen(client.currentScreen));
		} else if (active && client.options.sneakKey.isPressed() && client.options.dropKey.wasPressed()) {
			requestExit();
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null) return;
		if (active && !instruction.isBlank()) {
			Text title = Text.literal("G'Express Training " + (Math.min(stage, 4) + 1) + "/5");
			Text body = Text.literal(instruction);
			int width = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(body)) + 24;
			width = Math.min(width, context.getScaledWindowWidth() - 20);
			int x = (context.getScaledWindowWidth() - width) / 2;
			context.fill(x, 8, x + width, 42, 0xC010151C);
			context.fill(x, 8, x + width, 10, 0xFFE7C66A);
			context.drawCenteredTextWithShadow(client.textRenderer, title,
				context.getScaledWindowWidth() / 2, 15, 0xFFE7C66A);
			context.drawCenteredTextWithShadow(client.textRenderer,
				Text.literal(client.textRenderer.trimToWidth(instruction, width - 14)),
				context.getScaledWindowWidth() / 2, 28, 0xFFE8EDF2);
			context.drawTextWithShadow(client.textRenderer, Text.literal("Sneak + Drop to exit"),
				8, context.getScaledWindowHeight() - 18, 0xFF9DA9B6);
		}
		if (fadeRemaining > 0 && fadeDuration > 0) {
			float progress = 1.0F - fadeRemaining / (float) fadeDuration;
			float strength = 1.0F - Math.abs(progress * 2.0F - 1.0F);
			int alpha = Math.max(0, Math.min(255, Math.round(strength * 255.0F)));
			context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha << 24);
		}
	}
}
