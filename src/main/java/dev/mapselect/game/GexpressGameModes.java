package dev.mapselect.game;

import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;

public final class GexpressGameModes {
	public static final Identifier AMNESIA_ID = Identifier.of(MapSelect.MOD_ID, "amnesia");
	public static GameMode AMNESIA;

	private GexpressGameModes() {}

	public static void register() {
		GameMode existing = WatheGameModes.GAME_MODES.get(AMNESIA_ID);
		AMNESIA = existing == null
			? WatheGameModes.registerGameMode(AMNESIA_ID, new ModdedMurderGameMode(AMNESIA_ID))
			: existing;
	}

	public static boolean isAmnesia(GameWorldComponent game) {
		return game != null && game.getGameMode() != null
			&& AMNESIA_ID.equals(game.getGameMode().identifier);
	}
}
