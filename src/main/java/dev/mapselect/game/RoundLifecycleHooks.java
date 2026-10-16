package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.GameEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.function.Consumer;

public final class RoundLifecycleHooks {
	private RoundLifecycleHooks() {}

	public static void clear(Runnable clear) {
		if (clear == null) return;
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearIfSafe(world, clear));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearIfSafe(world, clear));
	}

	public static void clearWorld(Consumer<World> clear) {
		if (clear == null) return;
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear.accept(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear.accept(world));
	}

	private static void clearIfSafe(World world, Runnable clear) {
		if (world instanceof ServerWorld serverWorld && GexpressWorlds.hasOtherActiveGameWorld(serverWorld)) {
			return;
		}
		clear.run();
	}
}
