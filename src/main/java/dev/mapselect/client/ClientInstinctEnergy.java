package dev.mapselect.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class ClientInstinctEnergy {
	private static final int MAX_ENERGY = 20 * 10;
	private static final int DRAIN_PER_TICK = 1;
	private static final int RECHARGE_PER_TICK = 1;
	private static int energy = MAX_ENERGY;
	private static int activeTicks;
	private static boolean rawRequestedThisTick;
	private static boolean rawRequestedLastTick;
	private static Object trackedWorld;

	private ClientInstinctEnergy() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientInstinctEnergy::tick);
	}

	public static boolean filter(boolean enabled) {
		if (enabled) rawRequestedThisTick = true;
		return activeTicks > 0;
	}

	public static boolean shouldShow() {
		return activeTicks > 0 || energy < MAX_ENERGY;
	}

	public static float progress() {
		return energy / (float) MAX_ENERGY;
	}

	public static boolean isEmpty() {
		return energy <= 0;
	}

	private static void tick(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (world == null || world != trackedWorld) {
			trackedWorld = world;
			energy = MAX_ENERGY;
			activeTicks = 0;
			rawRequestedThisTick = false;
			rawRequestedLastTick = false;
			return;
		}

		boolean tapped = rawRequestedThisTick && !rawRequestedLastTick;
		if (tapped && activeTicks <= 0 && energy >= MAX_ENERGY) {
			activeTicks = MAX_ENERGY;
		}

		if (activeTicks > 0) {
			activeTicks--;
			energy = Math.max(0, energy - DRAIN_PER_TICK);
		} else if (energy < MAX_ENERGY) {
			energy = Math.min(MAX_ENERGY, energy + RECHARGE_PER_TICK);
		}
		rawRequestedLastTick = rawRequestedThisTick;
		rawRequestedThisTick = false;
	}
}
