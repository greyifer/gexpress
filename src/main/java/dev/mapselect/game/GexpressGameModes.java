package dev.mapselect.game;

import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.game.draft.DraftGameMode;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;

public final class GexpressGameModes {
	public static final Identifier AMNESIA_ID = Identifier.of(MapSelect.MOD_ID, "amnesia");
	public static final Identifier TAKEOVER_ID = Identifier.of(MapSelect.MOD_ID, "takeover");
	public static final Identifier TEST_ID = Identifier.of(MapSelect.MOD_ID, "test");
	public static final Identifier DRAFT_ID = Identifier.of(MapSelect.MOD_ID, "draft");
	public static final int TEST_DEFAULT_START_MINUTES = 999;
	public static GameMode AMNESIA;
	public static GameMode TAKEOVER;
	public static GameMode TEST;
	public static GameMode DRAFT;

	private GexpressGameModes() {}

	public static void register() {
		AMNESIA = registerModdedMurderMode(AMNESIA_ID);
		TAKEOVER = registerModdedMurderMode(TAKEOVER_ID);
		TEST = registerModdedMurderMode(TEST_ID);
		DRAFT = registerDraftMode();
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

	public static boolean isDraft(GameWorldComponent game) {
		return game != null && game.getGameMode() != null
			&& DRAFT_ID.equals(game.getGameMode().identifier);
	}

	public static boolean isDraft(GameMode mode) {
		return mode != null && DRAFT_ID.equals(mode.identifier);
	}

	private static GameMode registerDraftMode() {
		GameMode existing = WatheGameModes.GAME_MODES.get(DRAFT_ID);
		return existing == null
			? WatheGameModes.registerGameMode(DRAFT_ID, new DraftGameMode(DRAFT_ID))
			: existing;
	}

	private static GameMode registerModdedMurderMode(Identifier id) {
		GameMode existing = WatheGameModes.GAME_MODES.get(id);
		return existing == null
			? WatheGameModes.registerGameMode(id, new ModdedMurderGameMode(id))
			: existing;
	}
}
