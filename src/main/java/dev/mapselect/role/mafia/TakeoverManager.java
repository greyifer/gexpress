package dev.mapselect.role.mafia;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.mapselect.game.GexpressGameModes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TakeoverManager {
	private static final Map<UUID, TakeoverSide> GODFATHER_SIDES = new LinkedHashMap<>();

	private TakeoverManager() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
			if (!GexpressGameModes.isTakeover(game)) clear();
		});
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear());
	}

	public static void assignSides(List<ServerPlayerEntity> godfathers) {
		clear();
		if (godfathers != null) {
			TakeoverSide[] sides = TakeoverSide.values();
			for (int i = 0; i < godfathers.size() && i < sides.length; i++) {
				ServerPlayerEntity godfather = godfathers.get(i);
				if (godfather != null) GODFATHER_SIDES.put(godfather.getUuid(), sides[i]);
			}
		}
	}

	public static boolean isTrackedGodfather(UUID godfatherId) {
		return godfatherId != null && GODFATHER_SIDES.containsKey(godfatherId);
	}

	public static TakeoverSide sideForGodfather(UUID godfatherId) {
		return godfatherId == null ? null : GODFATHER_SIDES.get(godfatherId);
	}

	public static void clear() {
		GODFATHER_SIDES.clear();
	}
}
