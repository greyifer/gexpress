package dev.mapselect.client;

import dev.mapselect.MapSelect;
import dev.mapselect.network.ShortSightedSyncPayload;
import dev.mapselect.registry.MapSelectModifiers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.agmas.harpymodloader.component.WorldModifierComponent;

/**
 * Client-local mirror of the player's Short-sighted modifier state. Updated by the
 * {@link ShortSightedSyncPayload} receiver registered in
 * {@link #register()} and read by {@code ShortSightedEntityRenderMixin} every frame.
 *
 * <p>The server-synced flag is the primary source, but the client also checks HML's
 * synced component directly. That covers the window where the modifier icon is present
 * before our half-second server diff has sent its first update.
 *
 * <p>The flag is {@code volatile} because the network callback runs on Fabric's networking
 * worker thread while the render path reads it on the render thread. {@code volatile} is
 * sufficient here - we don't need atomicity, just a fresh read.
 */
public final class ClientShortSightedState {
	private ClientShortSightedState() {}

	private static volatile boolean shortSighted = false;

	public static boolean isShortSighted() {
		return shortSighted || hasClientModifier();
	}

	private static boolean hasClientModifier() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.player == null) return false;
		try {
			WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(mc.world);
			return mods != null && mods.isModifier(mc.player, MapSelectModifiers.SHORT_SIGHTED);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Registers the S2C receiver. Must be called from the {@code ClientModInitializer} (or
	 * any other client-only entrypoint) - server JVMs don't have
	 * {@link ClientPlayNetworking} on their classpath.
	 */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ShortSightedSyncPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				shortSighted = payload.shortSighted();
				MapSelect.LOGGER.debug("[ClientShortSightedState] received sync: shortSighted={}", payload.shortSighted());
			});
		});
	}
}
