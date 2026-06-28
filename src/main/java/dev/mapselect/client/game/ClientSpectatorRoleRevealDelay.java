package dev.mapselect.client.game;
import dev.mapselect.client.role.pelican.ClientVultureState;


import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

public final class ClientSpectatorRoleRevealDelay {
	private static final long DELAY_TICKS = 20L * 60L;
	private static long spectatorSinceTick = Long.MIN_VALUE;
	private static Object trackedWorld;
	private static boolean wasSpectating;

	private ClientSpectatorRoleRevealDelay() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientSpectatorRoleRevealDelay::tick);
		HudRenderCallback.EVENT.register(ClientSpectatorRoleRevealDelay::render);
	}

	public static boolean canSeeHoveredRoles(MinecraftClient client) {
		if (ClientVultureState.isLocalStashed(client)) return false;
		if (isPreRoleRevealSpectator(client)) return false;
		if (!isDelayedSpectator(client)) return true;
		if (client == null || client.world == null || spectatorSinceTick == Long.MIN_VALUE) return false;
		return client.world.getTime() - spectatorSinceTick >= DELAY_TICKS;
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		return player != null && player != client.player
			&& shouldUseInstinctReveal(client);
	}

	public static boolean isWaitingForRoleReveal(MinecraftClient client) {
		return isPreRoleRevealSpectator(client)
			|| (isDelayedSpectator(client) && !canSeeHoveredRoles(client));
	}

	public static boolean shouldMaskRoleColors(MinecraftClient client) {
		return isPreRoleRevealSpectator(client) || isWaitingForRoleReveal(client);
	}

	public static boolean shouldUseInstinctReveal(MinecraftClient client) {
		return shouldMaskRoleColors(client)
			&& WatheClient.isInstinctEnabled();
	}

	public static int glowColor() {
		return 0x48FF66;
	}

	public static int glowColor(AbstractClientPlayerEntity player) {
		return glowColor();
	}

	public static Integer revealedRoleColor(MinecraftClient client, PlayerEntity player) {
		if (client == null || client.player == null || client.world == null || player == null
				|| player == client.player || !shouldUseRevealedRoleInstinct(client)) {
			return null;
		}
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(player);
			return role == null ? null : role.color();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean shouldUseRevealedRoleInstinct(MinecraftClient client) {
		return isDelayedSpectator(client)
			&& canSeeHoveredRoles(client)
			&& WatheClient.isInstinctEnabled();
	}

	private static long remainingTicks(MinecraftClient client) {
		if (!isDelayedSpectator(client) || client == null || client.world == null
				|| spectatorSinceTick == Long.MIN_VALUE) return 0L;
		return Math.max(0L, DELAY_TICKS - (client.world.getTime() - spectatorSinceTick));
	}

	private static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		long remaining = remainingTicks(client);
		if (remaining <= 0L) return;
		String text = "Role reveal in " + Math.max(1L, (remaining + 19L) / 20L) + "s";
		int x = context.getScaledWindowWidth() / 2 - client.textRenderer.getWidth(text) / 2;
		int y = 14;
		context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFB8FFC3);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			reset();
			return;
		}
		if (trackedWorld != client.world) {
			trackedWorld = client.world;
			spectatorSinceTick = Long.MIN_VALUE;
			wasSpectating = false;
		}
		if (!isRoundRunning(client)) {
			spectatorSinceTick = Long.MIN_VALUE;
			wasSpectating = false;
			return;
		}
		boolean spectating = isDelayedSpectator(client);
		if (spectating && !wasSpectating) {
			spectatorSinceTick = client.world.getTime();
		} else if (!spectating) {
			spectatorSinceTick = Long.MIN_VALUE;
		}
		wasSpectating = spectating;
	}

	private static boolean isDelayedSpectator(MinecraftClient client) {
		if (client == null || client.player == null || client.interactionManager == null) return false;
		if (ClientVultureState.isLocalStashed(client)) return false;
		if (client.interactionManager.getCurrentGameMode() == GameMode.CREATIVE) return false;
		try {
			return !GameFunctions.isPlayerAliveAndSurvival(client.player);
		} catch (Throwable ignored) {
			return client.player.isSpectator();
		}
	}

	private static boolean isPreRoleRevealSpectator(MinecraftClient client) {
		return isRoundRunning(client)
			&& isDelayedSpectator(client)
			&& !ClientRoleRevealState.isRoleRevealSettled();
	}

	private static boolean isRoundRunning(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.isRunning();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void reset() {
		trackedWorld = null;
		spectatorSinceTick = Long.MIN_VALUE;
		wasSpectating = false;
	}
}
