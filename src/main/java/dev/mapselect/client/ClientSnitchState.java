package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.SnitchProgressPayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.UUID;

public final class ClientSnitchState {
	private static int completedTasks;
	private static int requiredTasks = GexpressConfig.getSnitchTasksRequired();
	private static boolean receivedPayload;
	private static boolean showProgress;
	private static List<SnitchProgressPayload.InfoLine> infoLines = List.of();
	private static float progressAlpha;
	private static float progressX = 22.0F;
	private static float infoAlpha;
	private static int watheTaskVisibleTicks;

	private ClientSnitchState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SnitchProgressPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientSnitchState::tick);
		HudRenderCallback.EVENT.register((context, tickCounter) ->
			renderHudOverlay(context, MinecraftClient.getInstance().textRenderer));
	}

	public static boolean shouldAnnotateTaskText() {
		MinecraftClient client = MinecraftClient.getInstance();
		return shouldShowProgress(client);
	}

	public static Text taskProgressText() {
		return Text.literal("(" + progressString() + ")").formatted(Formatting.YELLOW);
	}

	public static void noteWatheTaskTextVisible() {
		watheTaskVisibleTicks = 3;
	}

	private static String progressString() {
		int required = Math.max(1, requiredTasks > 0 ? requiredTasks : GexpressConfig.getSnitchTasksRequired());
		int completed = Math.max(0, Math.min(completedTasks, required));
		return completed + "/" + required;
	}

	private static void apply(SnitchProgressPayload payload) {
		receivedPayload = true;
		completedTasks = payload.completedTasks();
		requiredTasks = payload.requiredTasks();
		showProgress = payload.showProgress();
		infoLines = List.copyOf(payload.infoLines());
	}

	private static void tick(MinecraftClient client) {
		if (!isRoundRunning(client)) {
			reset();
			return;
		}
		if (requiredTasks <= 0) {
			requiredTasks = GexpressConfig.getSnitchTasksRequired();
		}
		if (watheTaskVisibleTicks > 0) {
			watheTaskVisibleTicks--;
		}

		boolean revealReady = ClientRoleRevealState.canShowRoleHud(client);
		progressAlpha = MathHelper.lerp(0.25F, progressAlpha, shouldShowProgress(client) ? 1.0F : 0.0F);
		progressX = MathHelper.lerp(0.35F, progressX, progressTargetX(client));
		infoAlpha = MathHelper.lerp(0.25F, infoAlpha, revealReady && !infoLines.isEmpty() ? 1.0F : 0.0F);
	}

	public static void renderMoodOverlay(DrawContext context, TextRenderer renderer) {
		renderHudOverlay(context, renderer);
	}

	private static void renderHudOverlay(DrawContext context, TextRenderer renderer) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.options == null || client.options.hudHidden) return;
		if (renderer == null) return;

		if (progressAlpha > 0.02F && shouldShowProgress(client) && watheTaskVisibleTicks <= 0) {
			renderProgress(context, renderer, client);
		}
		if (infoAlpha > 0.02F && !infoLines.isEmpty() && isRoundRunning(client)
				&& ClientRoleRevealState.canShowRoleHud(client)) {
			renderInfoLines(context, renderer, client);
		}
	}

	public static boolean shouldGlow(Entity entity) {
		return entity != null && glowColor(entity) != null;
	}

	public static Integer glowColor(Entity entity) {
		if (entity == null || infoLines.isEmpty()
				|| !ClientRoleRevealState.canShowRoleHud(MinecraftClient.getInstance())) return null;
		UUID id = entity.getUuid();
		for (SnitchProgressPayload.InfoLine line : infoLines) {
			if (line.playerId().equals(id) && "Snitch".equals(line.roleName())) {
				return line.roleColor();
			}
		}
		return null;
	}

	private static boolean shouldShowProgress(MinecraftClient client) {
		if (!ClientRoleRevealState.canShowRoleHud(client)) return false;
		if (receivedPayload) {
			if (!showProgress) return false;
		} else if (!isLocalSnitch(client)) {
			return false;
		}
		try {
			return isRoundRunning(client) && WatheClient.isPlayerAliveAndInSurvival();
		} catch (Throwable ignored) {
			return showProgress;
		}
	}

	private static boolean isRoundRunning(MinecraftClient client) {
		if (client == null || client.world == null || client.player == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		return game != null && game.isRunning();
	}

	private static void reset() {
		completedTasks = 0;
		requiredTasks = GexpressConfig.getSnitchTasksRequired();
		receivedPayload = false;
		showProgress = false;
		infoLines = List.of();
		progressAlpha = 0.0F;
		progressX = 22.0F;
		infoAlpha = 0.0F;
		watheTaskVisibleTicks = 0;
	}

	private static void renderProgress(DrawContext context, TextRenderer renderer, MinecraftClient client) {
		int alpha = Math.max(0, Math.min(255, (int) (progressAlpha * 255.0F)));
		context.drawTextWithShadow(renderer, taskProgressText(), Math.round(progressX), 6, 0x00FFFF00 | (alpha << 24));
	}

	private static float progressTargetX(MinecraftClient client) {
		TextRenderer renderer = client == null ? null : client.textRenderer;
		if (renderer == null) return 22.0F;
		Text task = watheTaskVisibleTicks > 0 ? currentTaskText(client) : null;
		if (task != null) {
			return 22.0F + renderer.getWidth(task) + 4.0F;
		}
		return 22.0F;
	}

	private static void renderInfoLines(DrawContext context, TextRenderer renderer, MinecraftClient client) {
		int alpha = Math.max(0, Math.min(255, (int) (infoAlpha * 255.0F)));
		int maxWidth = 0;
		for (SnitchProgressPayload.InfoLine line : infoLines) {
			maxWidth = Math.max(maxWidth, 16 + renderer.getWidth(infoText(line)));
		}
		int x = Math.max(6, context.getScaledWindowWidth() - maxWidth - 8);
		int y = 8;
		for (SnitchProgressPayload.InfoLine line : infoLines) {
			Text text = infoText(line);
			int width = 16 + renderer.getWidth(text);
			drawHead(context, client, line.playerId(), x, y);
			context.drawTextWithShadow(renderer, text, x + 16, y + 3, 0x00FFFFFF | (alpha << 24));
			y += 17;
		}
	}

	private static Text infoText(SnitchProgressPayload.InfoLine line) {
		int color = line.roleName().equals("Snitch") ? 0xE6B83D : 0xFF3535;
		return Text.literal(line.playerName() + " is the " + line.roleName() + "!")
			.styled(style -> style.withColor(color));
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y) {
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		context.drawTexture(texture, x, y, 12, 12, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, 12, 12, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static Text currentTaskText(MinecraftClient client) {
		if (client == null || client.player == null) return null;
		try {
			PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(client.player);
			if (mood == null || mood.tasks == null || mood.tasks.isEmpty()) return null;
			for (PlayerMoodComponent.Task type : PlayerMoodComponent.Task.values()) {
				PlayerMoodComponent.TrainTask task = mood.tasks.get(type);
				if (task != null) {
					return Text.translatable("task." + (WatheClient.isKiller() ? "fake" : "feel"))
						.append(Text.translatable("task." + task.getName()));
				}
			}
			return null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean isLocalSnitch(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.SNITCH_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
