package dev.mapselect.currency;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.world.ServerWorld;

public final class GcoinManager {
	private GcoinManager() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				if (handler.player.getWorld() instanceof ServerWorld world) {
					GcoinComponent.KEY.sync(world);
				}
			}));
	}
}
