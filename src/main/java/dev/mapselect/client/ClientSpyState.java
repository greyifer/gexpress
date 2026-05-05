package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.mapselect.client.screen.WeIcons;
import dev.mapselect.network.SpyFeedPayload;
import dev.mapselect.network.SpyStatusPayload;
import dev.mapselect.network.SpyUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientSpyState {
	private static final Deque<FeedLine> FEED = new ArrayDeque<>();
	private static boolean wasAbilityDown;
	private static long activeBugExpiresAtTick;
	private static String activeBugTargetName = "";

	private ClientSpyState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SpyFeedPayload.ID, (payload, context) ->
			context.client().execute(() -> addLine(payload.line())));
		ClientPlayNetworking.registerGlobalReceiver(SpyStatusPayload.ID, (payload, context) ->
			context.client().execute(() -> setStatus(payload.remainingTicks(), payload.targetName())));
		ClientTickEvents.END_CLIENT_TICK.register(ClientSpyState::tick);
		HudRenderCallback.EVENT.register(ClientSpyState::render);
	}

	private static void addLine(String line) {
		if (line == null || line.isBlank()) return;
		long now = now();
		FEED.addLast(new FeedLine(line, now + 20L * 8L));
		while (FEED.size() > 5) FEED.removeFirst();
	}

	private static void setStatus(int remainingTicks, String targetName) {
		if (remainingTicks <= 0) {
			activeBugExpiresAtTick = 0L;
			activeBugTargetName = "";
			return;
		}
		activeBugExpiresAtTick = now() + remainingTicks;
		activeBugTargetName = targetName == null ? "" : targetName;
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalSpy(client)) {
			wasAbilityDown = false;
			return;
		}
		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean down = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (down && !wasAbilityDown && ClientPlayNetworking.canSend(SpyUsePayload.ID)) {
			ClientPlayNetworking.send(new SpyUsePayload());
		}
		wasAbilityDown = down;
	}

	private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null || client.player == null || client.options.hudHidden) return;
		if (!ClientRoleRevealState.canShowRoleHud(client) || !isLocalSpy(client)) return;
		long now = now();
		FEED.removeIf(line -> line.expiresAtTick() <= now);
		drawBalance(context, client);
		drawBugTimer(context, client, now);
		if (FEED.isEmpty()) return;
		int y = context.getScaledWindowHeight() - 78;
		for (FeedLine line : FEED) {
			int width = client.textRenderer.getWidth(line.text()) + 8;
			int x = context.getScaledWindowWidth() - width - 8;
			context.fill(x - 2, y - 2, x + width, y + 11, 0x66000000);
			context.drawTextWithShadow(client.textRenderer, Text.literal(line.text()), x + 2, y, 0xFFE6D37A);
			y += 13;
		}
	}

	public static long activeRemainingTicks() {
		long remaining = activeBugExpiresAtTick - now();
		if (remaining <= 0L) {
			activeBugExpiresAtTick = 0L;
			activeBugTargetName = "";
			return 0L;
		}
		return remaining;
	}

	private static void drawBalance(DrawContext context, MinecraftClient client) {
		int balance = 0;
		try {
			balance = PlayerShopComponent.KEY.get(client.player).balance;
		} catch (Throwable ignored) {
			return;
		}
		String text = balance + " " + WeIcons.COIN;
		int x = context.getScaledWindowWidth() - client.textRenderer.getWidth(text) - 8;
		context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, 8, 0xFFFFD45A);
	}

	private static void drawBugTimer(DrawContext context, MinecraftClient client, long now) {
		long remainingTicks = activeBugExpiresAtTick - now;
		if (remainingTicks <= 0L) {
			activeBugExpiresAtTick = 0L;
			activeBugTargetName = "";
			return;
		}
		long seconds = Math.max(1L, (remainingTicks + 19L) / 20L);
		String target = activeBugTargetName == null || activeBugTargetName.isBlank() ? "target" : activeBugTargetName;
		String text = "Bug: " + target + " " + seconds + "s";
		int width = client.textRenderer.getWidth(text) + 8;
		int x = context.getScaledWindowWidth() - width - 8;
		int y = context.getScaledWindowHeight() - 94;
		context.fill(x - 2, y - 2, x + width, y + 11, 0x66000000);
		context.drawTextWithShadow(client.textRenderer, Text.literal(text), x + 2, y, 0xFF77C7FF);
	}

	private static boolean isLocalSpy(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.SPY_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static long now() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.world != null ? client.world.getTime() : 0L;
	}

	private record FeedLine(String text, long expiresAtTick) {}
}
