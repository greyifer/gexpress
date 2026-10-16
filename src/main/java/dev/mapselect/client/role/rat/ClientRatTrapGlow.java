package dev.mapselect.client.role.rat;

import dev.mapselect.entity.RatTrapEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/** Enables Minecraft's real entity outline only on the Rat's client. */
public final class ClientRatTrapGlow {
	private ClientRatTrapGlow() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientRatTrapGlow::tick);
	}

	public static boolean shouldGlow(RatTrapEntity trap) {
		MinecraftClient client = MinecraftClient.getInstance();
		return trap != null && client.player != null && client.world == trap.getWorld()
			&& ClientRatState.isLocalRat(client);
	}

	private static void tick(MinecraftClient client) {
		ClientWorld world = client == null ? null : client.world;
		if (world == null || client.player == null) return;
		boolean glow = client.player != null && ClientRatState.isLocalRat(client);
		for (RatTrapEntity trap : world.getEntitiesByClass(
			RatTrapEntity.class,
			client.player.getBoundingBox().expand(128.0),
			entity -> true
		)) {
			trap.setGlowing(glow);
		}
	}
}
