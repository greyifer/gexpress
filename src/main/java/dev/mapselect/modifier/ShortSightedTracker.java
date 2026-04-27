package dev.mapselect.modifier;

import dev.mapselect.MapSelect;
import dev.mapselect.network.ShortSightedSyncPayload;
import dev.mapselect.registry.MapSelectModifiers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side bridge between HML's {@code WorldModifierComponent} and the client's local
 * Short-sighted flag. Every half-second, diffs each player's current modifier state against
 * the last-known state and sends a {@link ShortSightedSyncPayload} only when it changed.
 * That gives near-instant (≤10 tick) client pickup of modifier application / removal without
 * tying the client to the HML classpath.
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li>Tick every 10 server ticks (half-second). A player gaining/losing the modifier is
 *       a rare event — we don't need to check every tick. Half-second latency is well below
 *       the threshold where the user would notice "fog didn't update right away".</li>
 *   <li>Per-player diff against a static map so we send only on change. The initial join's
 *       first tick sees {@code prev == null}, sends the current state, and the client flips
 *       from its default-false.</li>
 *   <li>Clean up the map on disconnect so stale UUIDs don't accumulate across a long uptime.</li>
 *   <li>Single-dimension (overworld) gate, same reasoning as {@link EodDistribution}: Wathe's
 *       games are overworld-only, other dimensions ticking would just burn cycles.</li>
 * </ul>
 *
 * <h2>S2C payload type registration</h2>
 * {@link PayloadTypeRegistry#playS2C()} is registered here even though it's shared: the
 * mixed client/server {@code ModInitializer} entrypoint calls this on both JVMs, so the
 * payload type is available for decoding on the client and encoding on the server without
 * a separate call in {@code ClientModInitializer}. Mirrors the pattern in
 * {@link dev.mapselect.network.GexpressConfigSyncHandler}.
 */
public final class ShortSightedTracker {
	private ShortSightedTracker() {}

	private static final Map<UUID, Boolean> lastState = new HashMap<>();
	private static final int CHECK_INTERVAL_TICKS = 10;

	private static int ticksSinceCheck = 0;

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ShortSightedSyncPayload.ID, ShortSightedSyncPayload.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(ShortSightedTracker::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				lastState.remove(handler.getPlayer().getUuid());
			}
		});
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		ticksSinceCheck++;
		if (ticksSinceCheck < CHECK_INTERVAL_TICKS) return;
		ticksSinceCheck = 0;

		WorldModifierComponent mods = WorldModifierComponent.KEY.getNullable(world);
		if (mods == null) return;

		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean current = mods.isModifier(player, MapSelectModifiers.SHORT_SIGHTED);
			UUID id = player.getUuid();
			Boolean prev = lastState.get(id);
			if (prev == null || prev.booleanValue() != current) {
				ServerPlayNetworking.send(player, new ShortSightedSyncPayload(current));
				lastState.put(id, current);
				MapSelect.LOGGER.debug("Short-sighted sync -> {}: {}", player.getName().getString(), current);
			}
		}
	}
}
