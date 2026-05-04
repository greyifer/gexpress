package dev.mapselect.registry;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import dev.mapselect.MapSelect;
import net.minecraft.util.Identifier;

public final class MapSelectRoles {
	public static final Identifier BOMB_SPECIALIST_ID = Identifier.of(MapSelect.MOD_ID, "bomb_specialist");
	public static final Identifier MEDIC_ID = Identifier.of(MapSelect.MOD_ID, "medic");
	public static final Identifier SNITCH_ID = Identifier.of(MapSelect.MOD_ID, "snitch");
	public static final Identifier SEER_ID = Identifier.of(MapSelect.MOD_ID, "seer");
	public static final Identifier TIME_MASTER_ID = Identifier.of(MapSelect.MOD_ID, "time_master");
	public static final Identifier THE_SILENT_ID = Identifier.of(MapSelect.MOD_ID, "the_silent");
	public static final Identifier WARLOCK_ID = Identifier.of(MapSelect.MOD_ID, "warlock");
	public static final Identifier JUGGERNAUT_ID = Identifier.of(MapSelect.MOD_ID, "juggernaut");
	public static final Identifier TRICKSTER_ID = Identifier.of(MapSelect.MOD_ID, "trickster");
	public static final Identifier PUPPETMASTER_ID = Identifier.of(MapSelect.MOD_ID, "puppetmaster");
	public static final Identifier BOUNTY_HUNTER_ID = Identifier.of(MapSelect.MOD_ID, "bounty_hunter");
	public static final Identifier VULTURE_ID = Identifier.of(MapSelect.MOD_ID, "pelican");
	public static final Identifier SCATTER_BRAIN_ID = Identifier.of(MapSelect.MOD_ID, "scatter_brain");
	public static final Identifier TRACKER_ID = Identifier.of(MapSelect.MOD_ID, "tracker");
	public static final Identifier ALTRUIST_ID = Identifier.of(MapSelect.MOD_ID, "altruist");
	public static final Identifier GODFATHER_ID = Identifier.of(MapSelect.MOD_ID, "godfather");
	public static final Identifier MAFIOSO_ID = Identifier.of(MapSelect.MOD_ID, "mafioso");
	public static final Identifier JANITOR_ID = Identifier.of(MapSelect.MOD_ID, "janitor");

	public static Role BOMB_SPECIALIST;
	public static Role MEDIC;
	public static Role SNITCH;
	public static Role SEER;
	public static Role TIME_MASTER;
	public static Role THE_SILENT;
	public static Role WARLOCK;
	public static Role JUGGERNAUT;
	public static Role TRICKSTER;
	public static Role PUPPETMASTER;
	public static Role BOUNTY_HUNTER;
	public static Role VULTURE;
	public static Role SCATTER_BRAIN;
	public static Role TRACKER;
	public static Role ALTRUIST;
	public static Role GODFATHER;
	public static Role MAFIOSO;
	public static Role JANITOR;

	public static void register() {
		// Killer-side role. Same build as vanilla Killer:
		//   isInnocent=false, canUseKiller=true, MoodType.FAKE,
		//   maxSprintTime=-1 (infinite stamina -> "Athletic"), canSeeTime=true
		BOMB_SPECIALIST = WatheRoles.registerRole(new Role(
			BOMB_SPECIALIST_ID,
			0xD14818, // dark red-orange (TNT-ish), distinct from Killer's muted red
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		MEDIC = WatheRoles.registerRole(new Role(
			MEDIC_ID,
			0x35D06F, // clear support green
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		SNITCH = WatheRoles.registerRole(new Role(
			SNITCH_ID,
			0xE6B83D, // gold civilian intel role
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		SEER = WatheRoles.registerRole(new Role(
			SEER_ID,
			0xD94B66, // red flash warning role
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		TIME_MASTER = WatheRoles.registerRole(new Role(
			TIME_MASTER_ID,
			0x47C7D8, // bright clockwork cyan civilian support role
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		THE_SILENT = WatheRoles.registerRole(new Role(
			THE_SILENT_ID,
			0x1C1726, // near-black violet, distinct from the normal Killer red
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		WARLOCK = WatheRoles.registerRole(new Role(
			WARLOCK_ID,
			0x7A1A91, // occult purple, still readable beside the other killer reds
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		JUGGERNAUT = WatheRoles.registerRole(new Role(
			JUGGERNAUT_ID,
			0x8F1F1F, // blood-steel red, but registered as neutral solo
			false,
			false,
			Role.MoodType.FAKE,
			-1,
			true
		));

		TRICKSTER = WatheRoles.registerRole(new Role(
			TRICKSTER_ID,
			0x30B06A, // stage-green, distinct from the Warlock purple
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		PUPPETMASTER = WatheRoles.registerRole(new Role(
			PUPPETMASTER_ID,
			0xB11226, // deep red for possession/control
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		BOUNTY_HUNTER = WatheRoles.registerRole(new Role(
			BOUNTY_HUNTER_ID,
			0xC8892F,
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		SCATTER_BRAIN = WatheRoles.registerRole(new Role(
			SCATTER_BRAIN_ID,
			0xE36A2E,
			false,
			true,
			Role.MoodType.FAKE,
			-1,
			true
		));

		VULTURE = WatheRoles.registerRole(new Role(
			VULTURE_ID,
			0x6F8A24, // pelican green, neutral solo
			false,
			false,
			Role.MoodType.FAKE,
			-1,
			true
		));

		TRACKER = WatheRoles.registerRole(new Role(
			TRACKER_ID,
			0x3E9CFF,
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		ALTRUIST = WatheRoles.registerRole(new Role(
			ALTRUIST_ID,
			0xF0D38C,
			true,
			false,
			Role.MoodType.REAL,
			GameConstants.getInTicks(0, 10),
			false
		));

		GODFATHER = WatheRoles.registerRole(new Role(
			GODFATHER_ID,
			0x6B6B6B,
			false,
			false,
			Role.MoodType.FAKE,
			-1,
			true
		));

		MAFIOSO = WatheRoles.registerRole(new Role(
			MAFIOSO_ID,
			0x4F4F4F,
			false,
			false,
			Role.MoodType.FAKE,
			-1,
			true
		));

		JANITOR = WatheRoles.registerRole(new Role(
			JANITOR_ID,
			0x7A7A7A,
			false,
			false,
			Role.MoodType.FAKE,
			-1,
			true
		));
	}

	private MapSelectRoles() {}
}
