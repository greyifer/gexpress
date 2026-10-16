package dev.mapselect.game;

import dev.mapselect.MapSelect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

public final class ServerPerformanceMonitor {
	private static final double WARN_TICK_MS = 250.0D;
	private static final long WARN_COOLDOWN_NANOS = 30_000_000_000L;

	private static long lastTickNanos;
	private static long lastWarnNanos;
	private static double rollingTickMs = 50.0D;
	private static int spikeCount;

	private ServerPerformanceMonitor() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> reset());
		ServerTickEvents.END_SERVER_TICK.register(ServerPerformanceMonitor::tick);
	}

	private static void reset() {
		lastTickNanos = 0L;
		lastWarnNanos = 0L;
		rollingTickMs = 50.0D;
		spikeCount = 0;
	}

	private static void tick(MinecraftServer server) {
		long now = System.nanoTime();
		if (lastTickNanos != 0L) {
			double tickMs = (now - lastTickNanos) / 1_000_000.0D;
			rollingTickMs = rollingTickMs * 0.95D + tickMs * 0.05D;
			if (tickMs >= WARN_TICK_MS) {
				spikeCount++;
				if (now - lastWarnNanos >= WARN_COOLDOWN_NANOS) {
					MapSelect.LOGGER.warn("Server tick interval spike: {} ms (rolling avg {} ms, approx {} TPS, spikes {}).",
						format(tickMs), format(rollingTickMs), format(rollingTps()), spikeCount);
					lastWarnNanos = now;
				}
			}
		}
		lastTickNanos = now;
	}

	public static double rollingTickMs() {
		return rollingTickMs;
	}

	public static double rollingTps() {
		return Math.min(20.0D, 1000.0D / Math.max(1.0D, rollingTickMs));
	}

	public static int spikeCount() {
		return spikeCount;
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
