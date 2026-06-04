package dev.mapselect.game;

import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;

public final class GexpressGameModes {
	public static final Identifier AMNESIA_ID = Identifier.of(MapSelect.MOD_ID, "amnesia");
	public static final Identifier TAKEOVER_ID = Identifier.of(MapSelect.MOD_ID, "takeover");
	public static final Identifier TEST_ID = Identifier.of(MapSelect.MOD_ID, "test");
	public static final int TEST_DEFAULT_START_MINUTES = 999;
	public static GameMode AMNESIA;
	public static GameMode TAKEOVER;
	public static GameMode TEST;

	private GexpressGameModes() {}

	public static void register() {
		AMNESIA = registerModdedMurderMode(AMNESIA_ID);
		TAKEOVER = registerModdedMurderMode(TAKEOVER_ID);
		TEST = registerModdedMurderMode(TEST_ID);
	}

	public static boolean isAmnesia(GameWorldComponent game) {
		return game != null && game.getGameMode() != null
			&& AMNESIA_ID.equals(game.getGameMode().identifier);
	}

	public static boolean isTakeover(GameWorldComponent game) {
		return game != null && game.getGameMode() != null
			&& TAKEOVER_ID.equals(game.getGameMode().identifier);
	}

	public static boolean isTest(GameWorldComponent game) {
		return game != null && isTest(game.getGameMode());
	}

	public static boolean isTest(GameMode mode) {
		return mode != null && TEST_ID.equals(mode.identifier);
	}

	private static GameMode registerModdedMurderMode(Identifier id) {
		GameMode existing = WatheGameModes.GAME_MODES.get(id);
		return existing == null
			? WatheGameModes.registerGameMode(id, new ModdedMurderGameMode(id))
			: existing;
	}
}
