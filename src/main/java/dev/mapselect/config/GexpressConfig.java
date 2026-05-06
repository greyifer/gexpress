package dev.mapselect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.mapselect.MapSelect;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod-wide config persisted as JSON at config/gexpress.json. In multiplayer, the server is
 * authoritative: clients push edits, the server clamps/saves them, and then broadcasts the
 * accepted values back to connected clients.
 */
public final class GexpressConfig {
	private GexpressConfig() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("gexpress.json");

	// Defaults are deliberately chosen to feel like grenade-tier economy.
	public static int c4Price = 500;
	public static int c4FuseSeconds = 15;
	public static int c4FirstBeepSeconds = 3;
	/** Percent chance a Pliers defuse attempt clips the wrong wire and detonates the C4. */
	public static int wrongWirePercent = 20;
	/** Cost in coins for a Grenade in the Bomb Specialist's shop. */
	public static int grenadePrice = 150;
	/** Coins paid every Wathe passive-income tick to killer-team roles. */
	public static int passiveIncomeKiller = 5;
	/** Coins paid every Wathe passive-income tick to civilian roles. */
	public static int passiveIncomeCivilian = 0;
	/** Coins paid every Wathe passive-income tick to neutral roles. */
	public static int passiveIncomeNeutral = 5;
	/** Legacy synced value; Vigilantes now use the civilian passive-income bucket. */
	public static int passiveIncomeVigilante = 0;
	/** Coins paid every Wathe passive-income tick to Mafia family roles. */
	public static int passiveIncomeMafia = 5;
	/** Seconds before a Medic can move or re-apply their protective shield. */
	public static int medicShieldCooldownSeconds = 90;
	/** Whether a blocked knife hit consumes a Medic shield. */
	public static boolean medicShieldKnifeBreaks = false;
	/** Seconds The Silent stays in Shadow March before snapping back. */
	public static int silentShadowDurationSeconds = 8;
	/** Seconds The Silent must wait after Shadow March ends. */
	public static int silentShadowCooldownSeconds = 35;
	/** Seconds before a Warlock can move their mark to another player. */
	public static int warlockMarkCooldownSeconds = 20;
	/** Seconds before a Warlock can force another marked-player kill. */
	public static int warlockKillCooldownSeconds = 45;
	/** Seconds before a fresh Juggernaut can kill with their starting weapons. */
	public static int juggernautInitialCooldownSeconds = 60;
	/** Seconds removed from the Juggernaut weapon cooldown at each odd Juggernaut stage. */
	public static int juggernautCooldownReductionSeconds = 20;
	/** Lowest Juggernaut weapon cooldown after repeated kills. */
	public static int juggernautMinimumCooldownSeconds = 10;
	/** Seconds before the Juggernaut stage shield can block again. */
	public static int juggernautShieldRechargeSeconds = 60;
	/** Seconds the Trickster's skin shuffle and global voice mute lasts. */
	public static int tricksterSwapDurationSeconds = 25;
	/** Seconds the Harlequin waits after Masquerade ends. */
	public static int tricksterMasqueradeCooldownSeconds = 35;
	/** Seconds before Harlequin can shuffle train carts again. */
	public static int tricksterDancingCartsCooldownSeconds = 45;
	/** Number of times each Harlequin can shuffle train carts per round. */
	public static int tricksterDancingCartsMaxUses = 1;
	/** Seconds the Puppetmaster can control a target. */
	public static int puppetmasterControlDurationSeconds = 15;
	/** Seconds the Puppetmaster must wait after control ends. */
	public static int puppetmasterControlCooldownSeconds = 45;
	/** Whether Puppetmaster ability picks a random target instead of opening the target menu. */
	public static boolean puppetmasterRandomTarget = false;
	/** Whether a controlled puppet can kill the Puppetmaster's abandoned body. */
	public static boolean puppetmasterCanKillOwnBody = false;
	/** Maximum block range for Puppetmaster's target menu. */
	public static int puppetmasterControlRange = 16;
	/** Number of successful controls each Puppetmaster can start per round. */
	public static int puppetmasterMaxUses = 3;
	/** Seconds before the Pelican can swallow another player. */
	public static int pelicanEatCooldownSeconds = 20;
	/** Percent of the round lobby the Pelican must swallow to win. */
	public static int pelicanEatPercentage = 80;
	/** Food items a Hungry player may carry from platters at once. */
	public static int hungryFoodLimit = 2;
	/** Drink items a Thirsty player may carry from trays at once. */
	public static int thirstyDrinkLimit = 2;
	/** Number of task completions before a Snitch learns the killer roster. */
	public static int snitchTasksRequired = 3;
	/** Remaining tasks at which killers are warned and shown the Snitch. 0 disables the warning. */
	public static int snitchWarningTasksRemaining = 1;
	/** Seconds a Time Master rewinds the active round. */
	public static int timeMasterRewindSeconds = 10;
	/** Seconds before a Time Master can rewind again. */
	public static int timeMasterCooldownSeconds = 120;
	/** Total number of rewinds each Time Master can use in a round. */
	public static int timeMasterMaxUses = 1;
	/** Seconds a Time Master's Freeze holds a target in place. */
	public static int timeMasterFreezeDurationSeconds = 4;
	/** Seconds before a Time Master can freeze another player. */
	public static int timeMasterFreezeCooldownSeconds = 30;
	/** Total number of freezes each Time Master can use in a round. */
	public static int timeMasterFreezeMaxUses = 3;
	/** Maximum block range for Time Master's Freeze target search. */
	public static int timeMasterFreezeRange = 8;
	/** Seconds before Scatter Brain can scatter players again. */
	public static int scatterBrainCooldownSeconds = 60;
	/** Maximum number of players the Tracker can track at once. */
	public static int trackerMaxTargets = 3;
	/** Maximum block range for Tracker target search. */
	public static int trackerRange = 24;
	/** Seconds before Tracker can change a tracked target again. */
	public static int trackerCooldownSeconds = 10;
	/** Maximum block range for Altruist's revive target search. */
	public static int altruistRange = 4;
	/** Seconds before Bounty Hunter's current bounty expires and changes. */
	public static int bountyHunterBountyIntervalSeconds = 60;
	/** Coins paid to a Bounty Hunter for killing their current bounty. */
	public static int bountyHunterRewardGold = 200;
	/** Weapon cooldown penalty in seconds when a Bounty Hunter misses their bounty window. */
	public static int bountyHunterFailCooldownSeconds = 90;
	public static int skincrawlerBodyMaxAgeSeconds = 90;
	public static int skincrawlerCooldownSeconds = 90;
	public static int skincrawlerStunSeconds = 5;
	public static int skincrawlerRange = 4;
	public static int spyBugCost = 100;
	public static int spyBugDurationSeconds = 120;
	public static int spyBugRange = 16;
	public static int squeakerPitchPercent = 135;
	public static int masqueradePitchMinPercent = 80;
	public static int masqueradePitchMaxPercent = 145;
	/** Coins paid by the Godfather for one Bullet. */
	public static int godfatherBulletPrice = 75;
	/** Bullets a Godfather starts with. */
	public static int godfatherStartingBullets = 1;
	/** Bullets a Godfather can keep loaded in the revolver. */
	public static int godfatherMaxLoadedBullets = 3;
	/** Coins Mafia roles start with when they join the family. */
	public static int mafiaStartingGold = 100;
	/** Minimum lobby size required before Godfather can be naturally assigned. */
	public static int mafiaMinimumPlayers = 15;
	/** Coins Godfather starts with. */
	public static int godfatherStartingGold = 100;
	/** Coins Mafioso starts with. */
	public static int mafiosoStartingGold = 100;
	/** Coins Janitor starts with. */
	public static int janitorStartingGold = 100;
	/** Maximum block range for Godfather recruitment. */
	public static int mafiaRecruitRange = 16;
	/** Seconds before a dead Mafioso or Janitor slot can be refilled. */
	public static int mafiaReplacementCooldownSeconds = 120;
	/** Seconds Mafioso and Janitor revolver kills put the revolver on cooldown. */
	public static int mafiaRevolverKillCooldownSeconds = 60;
	/** Maximum block range for Janitor body cleanup. */
	public static int janitorCleanRange = 4;
	/** Seconds before Janitor can clean again after cleaning. */
	public static int janitorCleanCooldownSeconds = 20;
	/** Seconds cleaning a body puts the Janitor revolver on cooldown. */
	public static int janitorRevolverCooldownAfterCleanSeconds = 45;
	/** Seconds killing a player puts Janitor cleanup on cooldown. */
	public static int janitorCleanCooldownAfterKillSeconds = 45;
	/** Whether the player who died last round starts the next round with one breakable shield. */
	public static boolean lastDeathShieldEnabled = false;
	/** Whether G'Express uses fixed max Killer/Vigilante counts instead of per-player scaling. */
	public static boolean useCustomRoleCounts = true;
	/** Maximum number of normal killer-team players assigned by G'Express role assignment. */
	public static int maxKillerAmount = 64;
	/** Maximum number of Vigilantes assigned by G'Express role assignment. */
	public static int maxVigilanteAmount = 1;
	/** Number of players per killer when custom role counts are disabled. */
	public static int playersPerKiller = 6;
	/** Number of players per Vigilante when custom role counts are disabled. */
	public static int playersPerVigilante = 8;
	/** Synced C4 backpack model tuning. */
	public static float c4BackOffsetX = 0.0F;
	public static float c4BackOffsetY = 0.24F;
	public static float c4BackOffsetZ = 0.28F;
	public static float c4BackRotationX = 0.0F;
	public static float c4BackRotationY = 0.0F;
	public static float c4BackRotationZ = 0.0F;
	public static float c4BackSlant = 0.0F;
	public static float c4BackScale = 0.42F;
	/** Synced Spy bug model tuning. */
	public static float spyBugOffsetX = 0.0F;
	public static float spyBugOffsetY = 0.16F;
	public static float spyBugOffsetZ = 0.31F;
	public static float spyBugRotationX = 0.0F;
	public static float spyBugRotationY = 0.0F;
	public static float spyBugRotationZ = 0.0F;
	public static float spyBugSlant = 0.0F;
	public static float spyBugScale = 0.28F;
	public static List<String> c4PlacementPresets = new ArrayList<>();
	/** Per-role guidebook description overrides, keyed by role path (for example bomb_specialist). */
	public static Map<String, String> roleDescriptionOverrides = new LinkedHashMap<>();
	/** Synced Short-sighted entity visibility tuning. Stored under the old JSON name for config compatibility. */
	public static float shortSightedFogRange = 5.0F;
	/** Synced Medic shield flash tuning. */
	public static int medicShieldBlockFlashTicks = 18;
	public static int medicShieldBreakFlashTicks = 28;
	public static int medicShieldBlockFlashAlpha = 72;
	public static int medicShieldBreakFlashAlpha = 92;
	/** Synced opacity of The Silent's shadow form. */
	public static float silentShadowAlpha = 0.45F;
	/** Client-only ability cooldown HUD scale percentage. */
	public static int abilityHudScalePercent = 100;
	/** Client-only horizontal offset in scaled GUI pixels from the default ability HUD anchor. */
	public static int abilityHudOffsetX = 0;
	/** Client-only vertical offset in scaled GUI pixels from the default ability HUD anchor. */
	public static int abilityHudOffsetY = 0;

	public static final int C4_PRICE_MIN = 0;
	public static final int C4_PRICE_MAX = 9999;
	public static final int C4_FUSE_MIN = 1;
	public static final int C4_FUSE_MAX = 120;
	public static final int C4_FIRST_BEEP_MIN = 0;
	public static final int C4_FIRST_BEEP_MAX = 120;
	public static final int WRONG_WIRE_MIN = 0;
	public static final int WRONG_WIRE_MAX = 100;
	public static final int GRENADE_PRICE_MIN = 0;
	public static final int GRENADE_PRICE_MAX = 9999;
	public static final int PASSIVE_INCOME_MIN = 0;
	public static final int PASSIVE_INCOME_MAX = 9999;
	public static final int MEDIC_SHIELD_COOLDOWN_MIN = 0;
	public static final int MEDIC_SHIELD_COOLDOWN_MAX = 600;
	public static final int SILENT_SHADOW_DURATION_MIN = 1;
	public static final int SILENT_SHADOW_DURATION_MAX = 60;
	public static final int SILENT_SHADOW_COOLDOWN_MIN = 0;
	public static final int SILENT_SHADOW_COOLDOWN_MAX = 600;
	public static final int WARLOCK_MARK_COOLDOWN_MIN = 0;
	public static final int WARLOCK_MARK_COOLDOWN_MAX = 600;
	public static final int WARLOCK_KILL_COOLDOWN_MIN = 0;
	public static final int WARLOCK_KILL_COOLDOWN_MAX = 600;
	public static final int JUGGERNAUT_INITIAL_COOLDOWN_MIN = 0;
	public static final int JUGGERNAUT_INITIAL_COOLDOWN_MAX = 600;
	public static final int JUGGERNAUT_COOLDOWN_REDUCTION_MIN = 0;
	public static final int JUGGERNAUT_COOLDOWN_REDUCTION_MAX = 600;
	public static final int JUGGERNAUT_MINIMUM_COOLDOWN_MIN = 0;
	public static final int JUGGERNAUT_MINIMUM_COOLDOWN_MAX = 600;
	public static final int JUGGERNAUT_SHIELD_RECHARGE_MIN = 1;
	public static final int JUGGERNAUT_SHIELD_RECHARGE_MAX = 600;
	public static final int TRICKSTER_SWAP_DURATION_MIN = 1;
	public static final int TRICKSTER_SWAP_DURATION_MAX = 300;
	public static final int TRICKSTER_MASQUERADE_COOLDOWN_MIN = 0;
	public static final int TRICKSTER_MASQUERADE_COOLDOWN_MAX = 600;
	public static final int TRICKSTER_DANCING_CARTS_COOLDOWN_MIN = 0;
	public static final int TRICKSTER_DANCING_CARTS_COOLDOWN_MAX = 600;
	public static final int TRICKSTER_DANCING_CARTS_MAX_USES_MIN = 0;
	public static final int TRICKSTER_DANCING_CARTS_MAX_USES_MAX = 10;
	public static final int PUPPETMASTER_CONTROL_DURATION_MIN = 1;
	public static final int PUPPETMASTER_CONTROL_DURATION_MAX = 120;
	public static final int PUPPETMASTER_CONTROL_COOLDOWN_MIN = 0;
	public static final int PUPPETMASTER_CONTROL_COOLDOWN_MAX = 600;
	public static final int PUPPETMASTER_CONTROL_RANGE_MIN = 1;
	public static final int PUPPETMASTER_CONTROL_RANGE_MAX = 64;
	public static final int PUPPETMASTER_MAX_USES_MIN = 0;
	public static final int PUPPETMASTER_MAX_USES_MAX = 10;
	public static final int PELICAN_EAT_COOLDOWN_MIN = 0;
	public static final int PELICAN_EAT_COOLDOWN_MAX = 600;
	public static final int PELICAN_EAT_PERCENTAGE_MIN = 10;
	public static final int PELICAN_EAT_PERCENTAGE_MAX = 100;
	public static final int HUNGRY_FOOD_LIMIT_MIN = 1;
	public static final int HUNGRY_FOOD_LIMIT_MAX = 9;
	public static final int THIRSTY_DRINK_LIMIT_MIN = 1;
	public static final int THIRSTY_DRINK_LIMIT_MAX = 9;
	public static final int SNITCH_TASKS_REQUIRED_MIN = 1;
	public static final int SNITCH_TASKS_REQUIRED_MAX = 25;
	public static final int SNITCH_WARNING_TASKS_REMAINING_MIN = 0;
	public static final int SNITCH_WARNING_TASKS_REMAINING_MAX = 25;
	public static final int TIME_MASTER_REWIND_SECONDS_MIN = 1;
	public static final int TIME_MASTER_REWIND_SECONDS_MAX = 60;
	public static final int TIME_MASTER_COOLDOWN_SECONDS_MIN = 0;
	public static final int TIME_MASTER_COOLDOWN_SECONDS_MAX = 900;
	public static final int TIME_MASTER_MAX_USES_MIN = 0;
	public static final int TIME_MASTER_MAX_USES_MAX = 10;
	public static final int TIME_MASTER_FREEZE_DURATION_SECONDS_MIN = 1;
	public static final int TIME_MASTER_FREEZE_DURATION_SECONDS_MAX = 30;
	public static final int TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MIN = 0;
	public static final int TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MAX = 600;
	public static final int TIME_MASTER_FREEZE_MAX_USES_MIN = 0;
	public static final int TIME_MASTER_FREEZE_MAX_USES_MAX = 10;
	public static final int TIME_MASTER_FREEZE_RANGE_MIN = 1;
	public static final int TIME_MASTER_FREEZE_RANGE_MAX = 32;
	public static final int SCATTER_BRAIN_COOLDOWN_SECONDS_MIN = 0;
	public static final int SCATTER_BRAIN_COOLDOWN_SECONDS_MAX = 900;
	public static final int TRACKER_MAX_TARGETS_MIN = 1;
	public static final int TRACKER_MAX_TARGETS_MAX = 16;
	public static final int TRACKER_RANGE_MIN = 1;
	public static final int TRACKER_RANGE_MAX = 96;
	public static final int TRACKER_COOLDOWN_SECONDS_MIN = 0;
	public static final int TRACKER_COOLDOWN_SECONDS_MAX = 600;
	public static final int ALTRUIST_RANGE_MIN = 1;
	public static final int ALTRUIST_RANGE_MAX = 16;
	public static final int BOUNTY_HUNTER_INTERVAL_SECONDS_MIN = 10;
	public static final int BOUNTY_HUNTER_INTERVAL_SECONDS_MAX = 600;
	public static final int BOUNTY_HUNTER_REWARD_GOLD_MIN = 0;
	public static final int BOUNTY_HUNTER_REWARD_GOLD_MAX = 9999;
	public static final int BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MIN = 0;
	public static final int BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MAX = 900;
	public static final int SKINCRAWLER_BODY_MAX_AGE_SECONDS_MIN = 1;
	public static final int SKINCRAWLER_BODY_MAX_AGE_SECONDS_MAX = 600;
	public static final int SKINCRAWLER_COOLDOWN_SECONDS_MIN = 0;
	public static final int SKINCRAWLER_COOLDOWN_SECONDS_MAX = 900;
	public static final int SKINCRAWLER_STUN_SECONDS_MIN = 1;
	public static final int SKINCRAWLER_STUN_SECONDS_MAX = 60;
	public static final int SKINCRAWLER_RANGE_MIN = 1;
	public static final int SKINCRAWLER_RANGE_MAX = 16;
	public static final int SPY_BUG_COST_MIN = 0;
	public static final int SPY_BUG_COST_MAX = 9999;
	public static final int SPY_BUG_DURATION_SECONDS_MIN = 10;
	public static final int SPY_BUG_DURATION_SECONDS_MAX = 900;
	public static final int SPY_BUG_RANGE_MIN = 1;
	public static final int SPY_BUG_RANGE_MAX = 64;
	public static final int VOICE_PITCH_PERCENT_MIN = 50;
	public static final int VOICE_PITCH_PERCENT_MAX = 200;
	public static final int GODFATHER_BULLET_PRICE_MIN = 0;
	public static final int GODFATHER_BULLET_PRICE_MAX = 9999;
	public static final int GODFATHER_STARTING_BULLETS_MIN = 0;
	public static final int GODFATHER_STARTING_BULLETS_MAX = 64;
	public static final int GODFATHER_MAX_LOADED_BULLETS_MIN = 1;
	public static final int GODFATHER_MAX_LOADED_BULLETS_MAX = 12;
	public static final int MAFIA_STARTING_GOLD_MIN = 0;
	public static final int MAFIA_STARTING_GOLD_MAX = 9999;
	public static final int MAFIA_MINIMUM_PLAYERS_MIN = 1;
	public static final int MAFIA_MINIMUM_PLAYERS_MAX = 64;
	public static final int MAFIA_RECRUIT_RANGE_MIN = 1;
	public static final int MAFIA_RECRUIT_RANGE_MAX = 96;
	public static final int MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MIN = 0;
	public static final int MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MAX = 900;
	public static final int MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MIN = 0;
	public static final int MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MAX = 900;
	public static final int JANITOR_CLEAN_RANGE_MIN = 1;
	public static final int JANITOR_CLEAN_RANGE_MAX = 16;
	public static final int JANITOR_CLEAN_COOLDOWN_SECONDS_MIN = 0;
	public static final int JANITOR_CLEAN_COOLDOWN_SECONDS_MAX = 900;
	public static final int JANITOR_REVOLVER_COOLDOWN_AFTER_CLEAN_SECONDS_MIN = 0;
	public static final int JANITOR_REVOLVER_COOLDOWN_AFTER_CLEAN_SECONDS_MAX = 900;
	public static final int JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MIN = 0;
	public static final int JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MAX = 900;
	public static final int MAX_KILLER_AMOUNT_MIN = 1;
	public static final int MAX_KILLER_AMOUNT_MAX = 64;
	public static final int MAX_VIGILANTE_AMOUNT_MIN = 0;
	public static final int MAX_VIGILANTE_AMOUNT_MAX = 64;
	public static final int PLAYERS_PER_KILLER_MIN = 1;
	public static final int PLAYERS_PER_KILLER_MAX = 64;
	public static final int PLAYERS_PER_VIGILANTE_MIN = 1;
	public static final int PLAYERS_PER_VIGILANTE_MAX = 64;
	public static final float C4_BACK_OFFSET_MIN = -1.0F;
	public static final float C4_BACK_OFFSET_MAX = 1.0F;
	public static final float C4_BACK_ROTATION_MIN = -180.0F;
	public static final float C4_BACK_ROTATION_MAX = 180.0F;
	public static final float C4_BACK_SCALE_MIN = 0.05F;
	public static final float C4_BACK_SCALE_MAX = 2.0F;
	public static final float SHORT_SIGHTED_FOG_RANGE_MIN = 0.5F;
	public static final float SHORT_SIGHTED_FOG_RANGE_MAX = 32.0F;
	public static final float SHORT_SIGHTED_ENTITY_RANGE_MIN = SHORT_SIGHTED_FOG_RANGE_MIN;
	public static final float SHORT_SIGHTED_ENTITY_RANGE_MAX = SHORT_SIGHTED_FOG_RANGE_MAX;
	public static final int MEDIC_SHIELD_FLASH_TICKS_MIN = 1;
	public static final int MEDIC_SHIELD_FLASH_TICKS_MAX = 100;
	public static final int MEDIC_SHIELD_FLASH_ALPHA_MIN = 0;
	public static final int MEDIC_SHIELD_FLASH_ALPHA_MAX = 255;
	public static final float SILENT_SHADOW_ALPHA_MIN = 0.05F;
	public static final float SILENT_SHADOW_ALPHA_MAX = 1.0F;
	public static final int ABILITY_HUD_SCALE_MIN = 75;
	public static final int ABILITY_HUD_SCALE_MAX = 175;
	public static final int ABILITY_HUD_OFFSET_MIN = -320;
	public static final int ABILITY_HUD_OFFSET_MAX = 320;

	public static int getC4Price() {
		return Math.max(C4_PRICE_MIN, Math.min(C4_PRICE_MAX, c4Price));
	}

	public static int getC4FuseSeconds() {
		return Math.max(C4_FUSE_MIN, Math.min(C4_FUSE_MAX, c4FuseSeconds));
	}

	public static int getC4FirstBeepSeconds() {
		return Math.max(C4_FIRST_BEEP_MIN, Math.min(C4_FIRST_BEEP_MAX, c4FirstBeepSeconds));
	}

	public static int getWrongWirePercent() {
		return Math.max(WRONG_WIRE_MIN, Math.min(WRONG_WIRE_MAX, wrongWirePercent));
	}

	public static int getGrenadePrice() {
		return Math.max(GRENADE_PRICE_MIN, Math.min(GRENADE_PRICE_MAX, grenadePrice));
	}

	public static int getPassiveIncomeKiller() {
		return Math.max(PASSIVE_INCOME_MIN, Math.min(PASSIVE_INCOME_MAX, passiveIncomeKiller));
	}

	public static int getPassiveIncomeCivilian() {
		return Math.max(PASSIVE_INCOME_MIN, Math.min(PASSIVE_INCOME_MAX, passiveIncomeCivilian));
	}

	public static int getPassiveIncomeNeutral() {
		return Math.max(PASSIVE_INCOME_MIN, Math.min(PASSIVE_INCOME_MAX, passiveIncomeNeutral));
	}

	public static int getPassiveIncomeVigilante() {
		return getPassiveIncomeCivilian();
	}

	public static int getPassiveIncomeMafia() {
		return Math.max(PASSIVE_INCOME_MIN, Math.min(PASSIVE_INCOME_MAX, passiveIncomeMafia));
	}

	public static int getMedicShieldCooldownSeconds() {
		return Math.max(MEDIC_SHIELD_COOLDOWN_MIN,
			Math.min(MEDIC_SHIELD_COOLDOWN_MAX, medicShieldCooldownSeconds));
	}

	public static boolean doesMedicShieldKnifeBreaks() {
		return medicShieldKnifeBreaks;
	}

	public static int getSilentShadowDurationSeconds() {
		return Math.max(SILENT_SHADOW_DURATION_MIN,
			Math.min(SILENT_SHADOW_DURATION_MAX, silentShadowDurationSeconds));
	}

	public static int getSilentShadowCooldownSeconds() {
		return Math.max(SILENT_SHADOW_COOLDOWN_MIN,
			Math.min(SILENT_SHADOW_COOLDOWN_MAX, silentShadowCooldownSeconds));
	}

	public static int getWarlockMarkCooldownSeconds() {
		return Math.max(WARLOCK_MARK_COOLDOWN_MIN,
			Math.min(WARLOCK_MARK_COOLDOWN_MAX, warlockMarkCooldownSeconds));
	}

	public static int getWarlockKillCooldownSeconds() {
		return Math.max(WARLOCK_KILL_COOLDOWN_MIN,
			Math.min(WARLOCK_KILL_COOLDOWN_MAX, warlockKillCooldownSeconds));
	}

	public static int getJuggernautInitialCooldownSeconds() {
		return Math.max(JUGGERNAUT_INITIAL_COOLDOWN_MIN,
			Math.min(JUGGERNAUT_INITIAL_COOLDOWN_MAX, juggernautInitialCooldownSeconds));
	}

	public static int getJuggernautCooldownReductionSeconds() {
		return Math.max(JUGGERNAUT_COOLDOWN_REDUCTION_MIN,
			Math.min(JUGGERNAUT_COOLDOWN_REDUCTION_MAX, juggernautCooldownReductionSeconds));
	}

	public static int getJuggernautMinimumCooldownSeconds() {
		return Math.max(JUGGERNAUT_MINIMUM_COOLDOWN_MIN,
			Math.min(JUGGERNAUT_MINIMUM_COOLDOWN_MAX, juggernautMinimumCooldownSeconds));
	}

	public static int getJuggernautShieldRechargeSeconds() {
		return Math.max(JUGGERNAUT_SHIELD_RECHARGE_MIN,
			Math.min(JUGGERNAUT_SHIELD_RECHARGE_MAX, juggernautShieldRechargeSeconds));
	}

	public static int getTricksterSwapDurationSeconds() {
		return Math.max(TRICKSTER_SWAP_DURATION_MIN,
			Math.min(TRICKSTER_SWAP_DURATION_MAX, tricksterSwapDurationSeconds));
	}

	public static int getTricksterMasqueradeCooldownSeconds() {
		return Math.max(TRICKSTER_MASQUERADE_COOLDOWN_MIN,
			Math.min(TRICKSTER_MASQUERADE_COOLDOWN_MAX, tricksterMasqueradeCooldownSeconds));
	}

	public static int getTricksterDancingCartsCooldownSeconds() {
		return Math.max(TRICKSTER_DANCING_CARTS_COOLDOWN_MIN,
			Math.min(TRICKSTER_DANCING_CARTS_COOLDOWN_MAX, tricksterDancingCartsCooldownSeconds));
	}

	public static int getTricksterDancingCartsMaxUses() {
		return Math.max(TRICKSTER_DANCING_CARTS_MAX_USES_MIN,
			Math.min(TRICKSTER_DANCING_CARTS_MAX_USES_MAX, tricksterDancingCartsMaxUses));
	}

	public static int getPuppetmasterControlDurationSeconds() {
		return Math.max(PUPPETMASTER_CONTROL_DURATION_MIN,
			Math.min(PUPPETMASTER_CONTROL_DURATION_MAX, puppetmasterControlDurationSeconds));
	}

	public static int getPuppetmasterControlCooldownSeconds() {
		return Math.max(PUPPETMASTER_CONTROL_COOLDOWN_MIN,
			Math.min(PUPPETMASTER_CONTROL_COOLDOWN_MAX, puppetmasterControlCooldownSeconds));
	}

	public static boolean isPuppetmasterRandomTarget() {
		return puppetmasterRandomTarget;
	}

	public static boolean canPuppetmasterKillOwnBody() {
		return puppetmasterCanKillOwnBody;
	}

	public static int getPuppetmasterControlRange() {
		return Math.max(PUPPETMASTER_CONTROL_RANGE_MIN,
			Math.min(PUPPETMASTER_CONTROL_RANGE_MAX, puppetmasterControlRange));
	}

	public static int getPuppetmasterMaxUses() {
		return Math.max(PUPPETMASTER_MAX_USES_MIN,
			Math.min(PUPPETMASTER_MAX_USES_MAX, puppetmasterMaxUses));
	}

	public static int getPelicanEatCooldownSeconds() {
		return Math.max(PELICAN_EAT_COOLDOWN_MIN,
			Math.min(PELICAN_EAT_COOLDOWN_MAX, pelicanEatCooldownSeconds));
	}

	public static int getPelicanEatPercentage() {
		return Math.max(PELICAN_EAT_PERCENTAGE_MIN,
			Math.min(PELICAN_EAT_PERCENTAGE_MAX, pelicanEatPercentage));
	}

	public static int getHungryFoodLimit() {
		return Math.max(HUNGRY_FOOD_LIMIT_MIN, Math.min(HUNGRY_FOOD_LIMIT_MAX, hungryFoodLimit));
	}

	public static int getThirstyDrinkLimit() {
		return Math.max(THIRSTY_DRINK_LIMIT_MIN, Math.min(THIRSTY_DRINK_LIMIT_MAX, thirstyDrinkLimit));
	}

	public static int getSnitchTasksRequired() {
		return Math.max(SNITCH_TASKS_REQUIRED_MIN,
			Math.min(SNITCH_TASKS_REQUIRED_MAX, snitchTasksRequired));
	}

	public static int getSnitchWarningTasksRemaining() {
		return Math.max(SNITCH_WARNING_TASKS_REMAINING_MIN,
			Math.min(SNITCH_WARNING_TASKS_REMAINING_MAX, snitchWarningTasksRemaining));
	}

	public static int getTimeMasterRewindSeconds() {
		return Math.max(TIME_MASTER_REWIND_SECONDS_MIN,
			Math.min(TIME_MASTER_REWIND_SECONDS_MAX, timeMasterRewindSeconds));
	}

	public static int getTimeMasterCooldownSeconds() {
		return Math.max(TIME_MASTER_COOLDOWN_SECONDS_MIN,
			Math.min(TIME_MASTER_COOLDOWN_SECONDS_MAX, timeMasterCooldownSeconds));
	}

	public static int getTimeMasterMaxUses() {
		return Math.max(TIME_MASTER_MAX_USES_MIN,
			Math.min(TIME_MASTER_MAX_USES_MAX, timeMasterMaxUses));
	}

	public static int getTimeMasterFreezeDurationSeconds() {
		return Math.max(TIME_MASTER_FREEZE_DURATION_SECONDS_MIN,
			Math.min(TIME_MASTER_FREEZE_DURATION_SECONDS_MAX, timeMasterFreezeDurationSeconds));
	}

	public static int getTimeMasterFreezeCooldownSeconds() {
		return Math.max(TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MIN,
			Math.min(TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MAX, timeMasterFreezeCooldownSeconds));
	}

	public static int getTimeMasterFreezeMaxUses() {
		return Math.max(TIME_MASTER_FREEZE_MAX_USES_MIN,
			Math.min(TIME_MASTER_FREEZE_MAX_USES_MAX, timeMasterFreezeMaxUses));
	}

	public static int getTimeMasterFreezeRange() {
		return Math.max(TIME_MASTER_FREEZE_RANGE_MIN,
			Math.min(TIME_MASTER_FREEZE_RANGE_MAX, timeMasterFreezeRange));
	}

	public static int getScatterBrainCooldownSeconds() {
		return Math.max(SCATTER_BRAIN_COOLDOWN_SECONDS_MIN,
			Math.min(SCATTER_BRAIN_COOLDOWN_SECONDS_MAX, scatterBrainCooldownSeconds));
	}

	public static int getTrackerMaxTargets() {
		return Math.max(TRACKER_MAX_TARGETS_MIN,
			Math.min(TRACKER_MAX_TARGETS_MAX, trackerMaxTargets));
	}

	public static int getTrackerRange() {
		return Math.max(TRACKER_RANGE_MIN, Math.min(TRACKER_RANGE_MAX, trackerRange));
	}

	public static int getTrackerCooldownSeconds() {
		return Math.max(TRACKER_COOLDOWN_SECONDS_MIN,
			Math.min(TRACKER_COOLDOWN_SECONDS_MAX, trackerCooldownSeconds));
	}

	public static int getAltruistRange() {
		return Math.max(ALTRUIST_RANGE_MIN, Math.min(ALTRUIST_RANGE_MAX, altruistRange));
	}

	public static int getBountyHunterBountyIntervalSeconds() {
		return Math.max(BOUNTY_HUNTER_INTERVAL_SECONDS_MIN,
			Math.min(BOUNTY_HUNTER_INTERVAL_SECONDS_MAX, bountyHunterBountyIntervalSeconds));
	}

	public static int getBountyHunterRewardGold() {
		return Math.max(BOUNTY_HUNTER_REWARD_GOLD_MIN,
			Math.min(BOUNTY_HUNTER_REWARD_GOLD_MAX, bountyHunterRewardGold));
	}

	public static int getBountyHunterFailCooldownSeconds() {
		return Math.max(BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MIN,
			Math.min(BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MAX, bountyHunterFailCooldownSeconds));
	}

	public static int getSkincrawlerBodyMaxAgeSeconds() {
		return Math.max(SKINCRAWLER_BODY_MAX_AGE_SECONDS_MIN,
			Math.min(SKINCRAWLER_BODY_MAX_AGE_SECONDS_MAX, skincrawlerBodyMaxAgeSeconds));
	}

	public static int getSkincrawlerCooldownSeconds() {
		return Math.max(SKINCRAWLER_COOLDOWN_SECONDS_MIN,
			Math.min(SKINCRAWLER_COOLDOWN_SECONDS_MAX, skincrawlerCooldownSeconds));
	}

	public static int getSkincrawlerStunSeconds() {
		return Math.max(SKINCRAWLER_STUN_SECONDS_MIN,
			Math.min(SKINCRAWLER_STUN_SECONDS_MAX, skincrawlerStunSeconds));
	}

	public static int getSkincrawlerRange() {
		return Math.max(SKINCRAWLER_RANGE_MIN, Math.min(SKINCRAWLER_RANGE_MAX, skincrawlerRange));
	}

	public static int getSpyBugCost() {
		return Math.max(SPY_BUG_COST_MIN, Math.min(SPY_BUG_COST_MAX, spyBugCost));
	}

	public static int getSpyBugDurationSeconds() {
		return Math.max(SPY_BUG_DURATION_SECONDS_MIN,
			Math.min(SPY_BUG_DURATION_SECONDS_MAX, spyBugDurationSeconds));
	}

	public static int getSpyBugRange() {
		return Math.max(SPY_BUG_RANGE_MIN, Math.min(SPY_BUG_RANGE_MAX, spyBugRange));
	}

	public static int getSqueakerPitchPercent() {
		return Math.max(VOICE_PITCH_PERCENT_MIN, Math.min(VOICE_PITCH_PERCENT_MAX, squeakerPitchPercent));
	}

	public static int getMasqueradePitchMinPercent() {
		return Math.max(VOICE_PITCH_PERCENT_MIN, Math.min(VOICE_PITCH_PERCENT_MAX, masqueradePitchMinPercent));
	}

	public static int getMasqueradePitchMaxPercent() {
		return Math.max(getMasqueradePitchMinPercent(),
			Math.min(VOICE_PITCH_PERCENT_MAX, masqueradePitchMaxPercent));
	}

	public static int getGodfatherBulletPrice() {
		return Math.max(GODFATHER_BULLET_PRICE_MIN,
			Math.min(GODFATHER_BULLET_PRICE_MAX, godfatherBulletPrice));
	}

	public static int getGodfatherStartingBullets() {
		return Math.max(GODFATHER_STARTING_BULLETS_MIN,
			Math.min(GODFATHER_STARTING_BULLETS_MAX, godfatherStartingBullets));
	}

	public static int getGodfatherMaxLoadedBullets() {
		return Math.max(GODFATHER_MAX_LOADED_BULLETS_MIN,
			Math.min(GODFATHER_MAX_LOADED_BULLETS_MAX, godfatherMaxLoadedBullets));
	}

	public static int getMafiaStartingGold() {
		return Math.max(MAFIA_STARTING_GOLD_MIN,
			Math.min(MAFIA_STARTING_GOLD_MAX, mafiaStartingGold));
	}

	public static int getMafiaMinimumPlayers() {
		return Math.max(MAFIA_MINIMUM_PLAYERS_MIN,
			Math.min(MAFIA_MINIMUM_PLAYERS_MAX, mafiaMinimumPlayers));
	}

	public static int getGodfatherStartingGold() {
		return Math.max(MAFIA_STARTING_GOLD_MIN,
			Math.min(MAFIA_STARTING_GOLD_MAX, godfatherStartingGold));
	}

	public static int getMafiosoStartingGold() {
		return Math.max(MAFIA_STARTING_GOLD_MIN,
			Math.min(MAFIA_STARTING_GOLD_MAX, mafiosoStartingGold));
	}

	public static int getJanitorStartingGold() {
		return Math.max(MAFIA_STARTING_GOLD_MIN,
			Math.min(MAFIA_STARTING_GOLD_MAX, janitorStartingGold));
	}

	public static int getMafiaRecruitRange() {
		return Math.max(MAFIA_RECRUIT_RANGE_MIN,
			Math.min(MAFIA_RECRUIT_RANGE_MAX, mafiaRecruitRange));
	}

	public static int getMafiaReplacementCooldownSeconds() {
		return Math.max(MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MIN,
			Math.min(MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MAX, mafiaReplacementCooldownSeconds));
	}

	public static int getMafiaRevolverKillCooldownSeconds() {
		return Math.max(MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MIN,
			Math.min(MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MAX, mafiaRevolverKillCooldownSeconds));
	}

	public static int getJanitorCleanRange() {
		return Math.max(JANITOR_CLEAN_RANGE_MIN, Math.min(JANITOR_CLEAN_RANGE_MAX, janitorCleanRange));
	}

	public static int getJanitorCleanCooldownSeconds() {
		return Math.max(JANITOR_CLEAN_COOLDOWN_SECONDS_MIN,
			Math.min(JANITOR_CLEAN_COOLDOWN_SECONDS_MAX, janitorCleanCooldownSeconds));
	}

	public static int getJanitorRevolverCooldownAfterCleanSeconds() {
		return Math.max(JANITOR_REVOLVER_COOLDOWN_AFTER_CLEAN_SECONDS_MIN,
			Math.min(JANITOR_REVOLVER_COOLDOWN_AFTER_CLEAN_SECONDS_MAX, janitorRevolverCooldownAfterCleanSeconds));
	}

	public static int getJanitorCleanCooldownAfterKillSeconds() {
		return Math.max(JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MIN,
			Math.min(JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MAX, janitorCleanCooldownAfterKillSeconds));
	}

	public static boolean isLastDeathShieldEnabled() {
		return lastDeathShieldEnabled;
	}

	public static boolean useCustomRoleCounts() {
		return useCustomRoleCounts;
	}

	public static int getMaxKillerAmount() {
		return Math.max(MAX_KILLER_AMOUNT_MIN, Math.min(MAX_KILLER_AMOUNT_MAX, maxKillerAmount));
	}

	public static int getMaxVigilanteAmount() {
		return Math.max(MAX_VIGILANTE_AMOUNT_MIN, Math.min(MAX_VIGILANTE_AMOUNT_MAX, maxVigilanteAmount));
	}

	public static int getPlayersPerKiller() {
		return Math.max(PLAYERS_PER_KILLER_MIN, Math.min(PLAYERS_PER_KILLER_MAX, playersPerKiller));
	}

	public static int getPlayersPerVigilante() {
		return Math.max(PLAYERS_PER_VIGILANTE_MIN,
			Math.min(PLAYERS_PER_VIGILANTE_MAX, playersPerVigilante));
	}

	public static float getC4BackOffsetX() {
		return clampFloat(c4BackOffsetX, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.0F);
	}

	public static float getC4BackOffsetY() {
		return clampFloat(c4BackOffsetY, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.24F);
	}

	public static float getC4BackOffsetZ() {
		return clampFloat(c4BackOffsetZ, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.28F);
	}

	public static float getC4BackRotationX() {
		return clampFloat(c4BackRotationX, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getC4BackRotationY() {
		return clampFloat(c4BackRotationY, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getC4BackRotationZ() {
		return clampFloat(c4BackRotationZ, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getC4BackSlant() {
		return clampFloat(c4BackSlant, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getC4BackScale() {
		return clampFloat(c4BackScale, C4_BACK_SCALE_MIN, C4_BACK_SCALE_MAX, 0.42F);
	}

	public static float getSpyBugOffsetX() {
		return clampFloat(spyBugOffsetX, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.0F);
	}

	public static float getSpyBugOffsetY() {
		return clampFloat(spyBugOffsetY, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.16F);
	}

	public static float getSpyBugOffsetZ() {
		return clampFloat(spyBugOffsetZ, C4_BACK_OFFSET_MIN, C4_BACK_OFFSET_MAX, 0.31F);
	}

	public static float getSpyBugRotationX() {
		return clampFloat(spyBugRotationX, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getSpyBugRotationY() {
		return clampFloat(spyBugRotationY, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getSpyBugRotationZ() {
		return clampFloat(spyBugRotationZ, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getSpyBugSlant() {
		return clampFloat(spyBugSlant, C4_BACK_ROTATION_MIN, C4_BACK_ROTATION_MAX, 0.0F);
	}

	public static float getSpyBugScale() {
		return clampFloat(spyBugScale, C4_BACK_SCALE_MIN, C4_BACK_SCALE_MAX, 0.28F);
	}

	public static C4PlacementPreset getSpyBugPlacementPreset() {
		return new C4PlacementPreset(
			getSpyBugOffsetX(),
			getSpyBugOffsetY(),
			getSpyBugOffsetZ(),
			getSpyBugRotationX(),
			getSpyBugRotationY(),
			getSpyBugRotationZ(),
			getSpyBugSlant(),
			getSpyBugScale()
		);
	}

	public static String getCurrentC4PlacementPresetString() {
		return currentC4PlacementPreset().toConfigString();
	}

	public static List<String> getC4PlacementPresetStrings() {
		return normalizeC4PresetStrings(c4PlacementPresets, true);
	}

	public static void setC4PlacementPresetStrings(List<String> values) {
		c4PlacementPresets = normalizeC4PresetStrings(values, false);
	}

	public static String getC4PlacementPresetsSyncString() {
		return String.join(";", normalizeC4PresetStrings(c4PlacementPresets, false));
	}

	public static void setC4PlacementPresetsSyncString(String raw) {
		if (raw == null || raw.isBlank()) {
			c4PlacementPresets = new ArrayList<>();
			return;
		}
		List<String> values = new ArrayList<>();
		for (String part : raw.split(";")) {
			values.add(part);
		}
		setC4PlacementPresetStrings(values);
	}

	public static int getC4PlacementPresetCount() {
		return getC4PlacementPresets().size();
	}

	public static C4PlacementPreset getC4PlacementPreset(int index) {
		List<C4PlacementPreset> presets = getC4PlacementPresets();
		if (presets.isEmpty()) return currentC4PlacementPreset();
		return presets.get(Math.floorMod(index, presets.size()));
	}

	public static List<C4PlacementPreset> getC4PlacementPresets() {
		List<String> strings = normalizeC4PresetStrings(c4PlacementPresets, true);
		List<C4PlacementPreset> presets = new ArrayList<>();
		for (String value : strings) {
			C4PlacementPreset preset = C4PlacementPreset.parse(value);
			if (preset != null) presets.add(preset);
		}
		if (presets.isEmpty()) presets.add(currentC4PlacementPreset());
		return presets;
	}

	public static String getRoleDescriptionOverride(String rolePath) {
		if (rolePath == null || rolePath.isBlank()) return "";
		return roleDescriptionOverrides.getOrDefault(rolePath, "");
	}

	public static void setRoleDescriptionOverride(String rolePath, String value) {
		if (rolePath == null || rolePath.isBlank()) return;
		String cleaned = value == null ? "" : value.strip();
		if (cleaned.isEmpty()) {
			roleDescriptionOverrides.remove(rolePath);
		} else {
			roleDescriptionOverrides.put(rolePath, cleaned);
		}
	}

	public static String getRoleDescriptionOverridesSyncString() {
		String json = GSON.toJson(normalizeRoleDescriptionOverrides(roleDescriptionOverrides));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public static void setRoleDescriptionOverridesSyncString(String raw) {
		if (raw == null || raw.isBlank()) {
			roleDescriptionOverrides = new LinkedHashMap<>();
			return;
		}
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(raw);
			Map<?, ?> decoded = GSON.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), Map.class);
			roleDescriptionOverrides = normalizeRoleDescriptionOverrides(decoded);
		} catch (IllegalArgumentException | JsonSyntaxException ignored) {
			roleDescriptionOverrides = new LinkedHashMap<>();
		}
	}

	public static float getShortSightedFogRange() {
		return clampFloat(shortSightedFogRange, SHORT_SIGHTED_FOG_RANGE_MIN, SHORT_SIGHTED_FOG_RANGE_MAX, 5.0F);
	}

	public static float getShortSightedEntityRange() {
		return getShortSightedFogRange();
	}

	public static int getMedicShieldBlockFlashTicks() {
		return Math.max(MEDIC_SHIELD_FLASH_TICKS_MIN,
			Math.min(MEDIC_SHIELD_FLASH_TICKS_MAX, medicShieldBlockFlashTicks));
	}

	public static int getMedicShieldBreakFlashTicks() {
		return Math.max(MEDIC_SHIELD_FLASH_TICKS_MIN,
			Math.min(MEDIC_SHIELD_FLASH_TICKS_MAX, medicShieldBreakFlashTicks));
	}

	public static int getMedicShieldBlockFlashAlpha() {
		return Math.max(MEDIC_SHIELD_FLASH_ALPHA_MIN,
			Math.min(MEDIC_SHIELD_FLASH_ALPHA_MAX, medicShieldBlockFlashAlpha));
	}

	public static int getMedicShieldBreakFlashAlpha() {
		return Math.max(MEDIC_SHIELD_FLASH_ALPHA_MIN,
			Math.min(MEDIC_SHIELD_FLASH_ALPHA_MAX, medicShieldBreakFlashAlpha));
	}

	public static float getSilentShadowAlpha() {
		return clampFloat(silentShadowAlpha, SILENT_SHADOW_ALPHA_MIN, SILENT_SHADOW_ALPHA_MAX, 0.45F);
	}

	public static int getAbilityHudScalePercent() {
		return Math.max(ABILITY_HUD_SCALE_MIN, Math.min(ABILITY_HUD_SCALE_MAX, abilityHudScalePercent));
	}

	public static int getAbilityHudOffsetX() {
		return Math.max(ABILITY_HUD_OFFSET_MIN, Math.min(ABILITY_HUD_OFFSET_MAX, abilityHudOffsetX));
	}

	public static int getAbilityHudOffsetY() {
		return Math.max(ABILITY_HUD_OFFSET_MIN, Math.min(ABILITY_HUD_OFFSET_MAX, abilityHudOffsetY));
	}

	public static void load() {
		try {
			if (!Files.exists(CONFIG_PATH)) {
				save();
				return;
			}
			String json = Files.readString(CONFIG_PATH);
			Snapshot snap = GSON.fromJson(json, Snapshot.class);
			if (snap == null) return;
			c4Price = snap.c4Price;
			c4FuseSeconds = snap.c4FuseSeconds;
			c4FirstBeepSeconds = snap.c4FirstBeepSeconds;
			wrongWirePercent = snap.wrongWirePercent;
			grenadePrice = snap.grenadePrice;
			passiveIncomeKiller = snap.passiveIncomeKiller;
			passiveIncomeCivilian = snap.passiveIncomeCivilian;
			passiveIncomeNeutral = snap.passiveIncomeNeutral;
			passiveIncomeVigilante = snap.passiveIncomeVigilante;
			passiveIncomeMafia = snap.passiveIncomeMafia;
			medicShieldCooldownSeconds = snap.medicShieldCooldownSeconds;
			medicShieldKnifeBreaks = snap.medicShieldKnifeBreaks;
			silentShadowDurationSeconds = snap.silentShadowDurationSeconds;
			silentShadowCooldownSeconds = snap.silentShadowCooldownSeconds;
			warlockMarkCooldownSeconds = snap.warlockMarkCooldownSeconds;
			warlockKillCooldownSeconds = snap.warlockKillCooldownSeconds;
			juggernautInitialCooldownSeconds = snap.juggernautInitialCooldownSeconds;
			juggernautCooldownReductionSeconds = snap.juggernautCooldownReductionSeconds;
			juggernautMinimumCooldownSeconds = snap.juggernautMinimumCooldownSeconds;
			juggernautShieldRechargeSeconds = snap.juggernautShieldRechargeSeconds;
			tricksterSwapDurationSeconds = snap.tricksterSwapDurationSeconds;
			tricksterMasqueradeCooldownSeconds = snap.tricksterMasqueradeCooldownSeconds;
			tricksterDancingCartsCooldownSeconds = snap.tricksterDancingCartsCooldownSeconds;
			tricksterDancingCartsMaxUses = snap.tricksterDancingCartsMaxUses;
			puppetmasterControlDurationSeconds = snap.puppetmasterControlDurationSeconds;
			puppetmasterControlCooldownSeconds = snap.puppetmasterControlCooldownSeconds;
			puppetmasterRandomTarget = snap.puppetmasterRandomTarget;
			puppetmasterCanKillOwnBody = snap.puppetmasterCanKillOwnBody;
			puppetmasterControlRange = snap.puppetmasterControlRange;
			puppetmasterMaxUses = snap.puppetmasterMaxUses;
			pelicanEatCooldownSeconds = snap.pelicanEatCooldownSeconds;
			pelicanEatPercentage = snap.pelicanEatPercentage;
			hungryFoodLimit = snap.hungryFoodLimit;
			thirstyDrinkLimit = snap.thirstyDrinkLimit;
			snitchTasksRequired = snap.snitchTasksRequired;
			snitchWarningTasksRemaining = snap.snitchWarningTasksRemaining;
			timeMasterRewindSeconds = snap.timeMasterRewindSeconds;
			timeMasterCooldownSeconds = snap.timeMasterCooldownSeconds;
			timeMasterMaxUses = snap.timeMasterMaxUses;
			timeMasterFreezeDurationSeconds = snap.timeMasterFreezeDurationSeconds;
			timeMasterFreezeCooldownSeconds = snap.timeMasterFreezeCooldownSeconds;
			timeMasterFreezeMaxUses = snap.timeMasterFreezeMaxUses;
			timeMasterFreezeRange = snap.timeMasterFreezeRange;
			scatterBrainCooldownSeconds = snap.scatterBrainCooldownSeconds;
			trackerMaxTargets = snap.trackerMaxTargets;
			trackerRange = snap.trackerRange;
			trackerCooldownSeconds = snap.trackerCooldownSeconds;
			altruistRange = snap.altruistRange;
			bountyHunterBountyIntervalSeconds = snap.bountyHunterBountyIntervalSeconds;
			bountyHunterRewardGold = snap.bountyHunterRewardGold;
			bountyHunterFailCooldownSeconds = snap.bountyHunterFailCooldownSeconds;
			skincrawlerBodyMaxAgeSeconds = snap.skincrawlerBodyMaxAgeSeconds;
			skincrawlerCooldownSeconds = snap.skincrawlerCooldownSeconds;
			skincrawlerStunSeconds = snap.skincrawlerStunSeconds;
			skincrawlerRange = snap.skincrawlerRange;
			spyBugCost = snap.spyBugCost;
			if (spyBugCost == 200) spyBugCost = 100;
			spyBugDurationSeconds = snap.spyBugDurationSeconds;
			spyBugRange = snap.spyBugRange;
			squeakerPitchPercent = snap.squeakerPitchPercent;
			masqueradePitchMinPercent = snap.masqueradePitchMinPercent;
			masqueradePitchMaxPercent = snap.masqueradePitchMaxPercent;
			godfatherBulletPrice = snap.godfatherBulletPrice;
			godfatherStartingBullets = snap.godfatherStartingBullets;
			godfatherMaxLoadedBullets = snap.godfatherMaxLoadedBullets;
			mafiaStartingGold = snap.mafiaStartingGold;
			mafiaMinimumPlayers = snap.mafiaMinimumPlayers;
			godfatherStartingGold = snap.godfatherStartingGold;
			mafiosoStartingGold = snap.mafiosoStartingGold;
			janitorStartingGold = snap.janitorStartingGold;
			mafiaRecruitRange = snap.mafiaRecruitRange;
			mafiaReplacementCooldownSeconds = snap.mafiaReplacementCooldownSeconds;
			mafiaRevolverKillCooldownSeconds = snap.mafiaRevolverKillCooldownSeconds;
			janitorCleanRange = snap.janitorCleanRange;
			janitorCleanCooldownSeconds = snap.janitorCleanCooldownSeconds;
			janitorRevolverCooldownAfterCleanSeconds = snap.janitorRevolverCooldownAfterCleanSeconds;
			janitorCleanCooldownAfterKillSeconds = snap.janitorCleanCooldownAfterKillSeconds;
			lastDeathShieldEnabled = snap.lastDeathShieldEnabled;
			useCustomRoleCounts = snap.useCustomRoleCounts;
			maxKillerAmount = snap.maxKillerAmount;
			maxVigilanteAmount = snap.maxVigilanteAmount;
			playersPerKiller = snap.playersPerKiller;
			playersPerVigilante = snap.playersPerVigilante;
			c4BackOffsetX = snap.c4BackOffsetX;
			c4BackOffsetY = snap.c4BackOffsetY;
			c4BackOffsetZ = snap.c4BackOffsetZ;
			c4BackRotationX = snap.c4BackRotationX;
			c4BackRotationY = snap.c4BackRotationY;
			c4BackRotationZ = snap.c4BackRotationZ;
			c4BackSlant = snap.c4BackSlant;
			c4BackScale = snap.c4BackScale;
			spyBugOffsetX = snap.spyBugOffsetX;
			spyBugOffsetY = snap.spyBugOffsetY;
			spyBugOffsetZ = snap.spyBugOffsetZ;
			spyBugRotationX = snap.spyBugRotationX;
			spyBugRotationY = snap.spyBugRotationY;
			spyBugRotationZ = snap.spyBugRotationZ;
			spyBugSlant = snap.spyBugSlant;
			spyBugScale = snap.spyBugScale;
			c4PlacementPresets = normalizeC4PresetStrings(snap.c4PlacementPresets, false);
			roleDescriptionOverrides = normalizeRoleDescriptionOverrides(snap.roleDescriptionOverrides);
			shortSightedFogRange = snap.shortSightedFogRange;
			medicShieldBlockFlashTicks = snap.medicShieldBlockFlashTicks;
			medicShieldBreakFlashTicks = snap.medicShieldBreakFlashTicks;
			medicShieldBlockFlashAlpha = snap.medicShieldBlockFlashAlpha;
			medicShieldBreakFlashAlpha = snap.medicShieldBreakFlashAlpha;
			silentShadowAlpha = snap.silentShadowAlpha;
			abilityHudScalePercent = snap.abilityHudScalePercent;
			abilityHudOffsetX = snap.abilityHudOffsetX;
			abilityHudOffsetY = snap.abilityHudOffsetY;
			clampInPlace();
		} catch (IOException | JsonSyntaxException e) {
			MapSelect.LOGGER.warn("Failed to load gexpress.json; keeping defaults.", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			clampInPlace();
			Snapshot snap = new Snapshot();
			snap.c4Price = c4Price;
			snap.c4FuseSeconds = c4FuseSeconds;
			snap.c4FirstBeepSeconds = c4FirstBeepSeconds;
			snap.wrongWirePercent = wrongWirePercent;
			snap.grenadePrice = grenadePrice;
			snap.passiveIncomeKiller = passiveIncomeKiller;
			snap.passiveIncomeCivilian = passiveIncomeCivilian;
			snap.passiveIncomeNeutral = passiveIncomeNeutral;
			snap.passiveIncomeVigilante = passiveIncomeVigilante;
			snap.passiveIncomeMafia = passiveIncomeMafia;
			snap.medicShieldCooldownSeconds = medicShieldCooldownSeconds;
			snap.medicShieldKnifeBreaks = medicShieldKnifeBreaks;
			snap.silentShadowDurationSeconds = silentShadowDurationSeconds;
			snap.silentShadowCooldownSeconds = silentShadowCooldownSeconds;
			snap.warlockMarkCooldownSeconds = warlockMarkCooldownSeconds;
			snap.warlockKillCooldownSeconds = warlockKillCooldownSeconds;
			snap.juggernautInitialCooldownSeconds = juggernautInitialCooldownSeconds;
			snap.juggernautCooldownReductionSeconds = juggernautCooldownReductionSeconds;
			snap.juggernautMinimumCooldownSeconds = juggernautMinimumCooldownSeconds;
			snap.juggernautShieldRechargeSeconds = juggernautShieldRechargeSeconds;
			snap.tricksterSwapDurationSeconds = tricksterSwapDurationSeconds;
			snap.tricksterMasqueradeCooldownSeconds = tricksterMasqueradeCooldownSeconds;
			snap.tricksterDancingCartsCooldownSeconds = tricksterDancingCartsCooldownSeconds;
			snap.tricksterDancingCartsMaxUses = tricksterDancingCartsMaxUses;
			snap.puppetmasterControlDurationSeconds = puppetmasterControlDurationSeconds;
			snap.puppetmasterControlCooldownSeconds = puppetmasterControlCooldownSeconds;
			snap.puppetmasterRandomTarget = puppetmasterRandomTarget;
			snap.puppetmasterCanKillOwnBody = puppetmasterCanKillOwnBody;
			snap.puppetmasterControlRange = puppetmasterControlRange;
			snap.puppetmasterMaxUses = puppetmasterMaxUses;
			snap.pelicanEatCooldownSeconds = pelicanEatCooldownSeconds;
			snap.pelicanEatPercentage = pelicanEatPercentage;
			snap.hungryFoodLimit = hungryFoodLimit;
			snap.thirstyDrinkLimit = thirstyDrinkLimit;
			snap.snitchTasksRequired = snitchTasksRequired;
			snap.snitchWarningTasksRemaining = snitchWarningTasksRemaining;
			snap.timeMasterRewindSeconds = timeMasterRewindSeconds;
			snap.timeMasterCooldownSeconds = timeMasterCooldownSeconds;
			snap.timeMasterMaxUses = timeMasterMaxUses;
			snap.timeMasterFreezeDurationSeconds = timeMasterFreezeDurationSeconds;
			snap.timeMasterFreezeCooldownSeconds = timeMasterFreezeCooldownSeconds;
			snap.timeMasterFreezeMaxUses = timeMasterFreezeMaxUses;
			snap.timeMasterFreezeRange = timeMasterFreezeRange;
			snap.scatterBrainCooldownSeconds = scatterBrainCooldownSeconds;
			snap.trackerMaxTargets = trackerMaxTargets;
			snap.trackerRange = trackerRange;
			snap.trackerCooldownSeconds = trackerCooldownSeconds;
			snap.altruistRange = altruistRange;
			snap.bountyHunterBountyIntervalSeconds = bountyHunterBountyIntervalSeconds;
			snap.bountyHunterRewardGold = bountyHunterRewardGold;
			snap.bountyHunterFailCooldownSeconds = bountyHunterFailCooldownSeconds;
			snap.skincrawlerBodyMaxAgeSeconds = skincrawlerBodyMaxAgeSeconds;
			snap.skincrawlerCooldownSeconds = skincrawlerCooldownSeconds;
			snap.skincrawlerStunSeconds = skincrawlerStunSeconds;
			snap.skincrawlerRange = skincrawlerRange;
			snap.spyBugCost = spyBugCost;
			snap.spyBugDurationSeconds = spyBugDurationSeconds;
			snap.spyBugRange = spyBugRange;
			snap.squeakerPitchPercent = squeakerPitchPercent;
			snap.masqueradePitchMinPercent = masqueradePitchMinPercent;
			snap.masqueradePitchMaxPercent = masqueradePitchMaxPercent;
			snap.godfatherBulletPrice = godfatherBulletPrice;
			snap.godfatherStartingBullets = godfatherStartingBullets;
			snap.godfatherMaxLoadedBullets = godfatherMaxLoadedBullets;
			snap.mafiaStartingGold = mafiaStartingGold;
			snap.mafiaMinimumPlayers = mafiaMinimumPlayers;
			snap.godfatherStartingGold = godfatherStartingGold;
			snap.mafiosoStartingGold = mafiosoStartingGold;
			snap.janitorStartingGold = janitorStartingGold;
			snap.mafiaRecruitRange = mafiaRecruitRange;
			snap.mafiaReplacementCooldownSeconds = mafiaReplacementCooldownSeconds;
			snap.mafiaRevolverKillCooldownSeconds = mafiaRevolverKillCooldownSeconds;
			snap.janitorCleanRange = janitorCleanRange;
			snap.janitorCleanCooldownSeconds = janitorCleanCooldownSeconds;
			snap.janitorRevolverCooldownAfterCleanSeconds = janitorRevolverCooldownAfterCleanSeconds;
			snap.janitorCleanCooldownAfterKillSeconds = janitorCleanCooldownAfterKillSeconds;
			snap.lastDeathShieldEnabled = lastDeathShieldEnabled;
			snap.useCustomRoleCounts = useCustomRoleCounts;
			snap.maxKillerAmount = maxKillerAmount;
			snap.maxVigilanteAmount = maxVigilanteAmount;
			snap.playersPerKiller = playersPerKiller;
			snap.playersPerVigilante = playersPerVigilante;
			snap.c4BackOffsetX = c4BackOffsetX;
			snap.c4BackOffsetY = c4BackOffsetY;
			snap.c4BackOffsetZ = c4BackOffsetZ;
			snap.c4BackRotationX = c4BackRotationX;
			snap.c4BackRotationY = c4BackRotationY;
			snap.c4BackRotationZ = c4BackRotationZ;
			snap.c4BackSlant = c4BackSlant;
			snap.c4BackScale = c4BackScale;
			snap.spyBugOffsetX = spyBugOffsetX;
			snap.spyBugOffsetY = spyBugOffsetY;
			snap.spyBugOffsetZ = spyBugOffsetZ;
			snap.spyBugRotationX = spyBugRotationX;
			snap.spyBugRotationY = spyBugRotationY;
			snap.spyBugRotationZ = spyBugRotationZ;
			snap.spyBugSlant = spyBugSlant;
			snap.spyBugScale = spyBugScale;
			snap.c4PlacementPresets = normalizeC4PresetStrings(c4PlacementPresets, false);
			snap.roleDescriptionOverrides = normalizeRoleDescriptionOverrides(roleDescriptionOverrides);
			snap.shortSightedFogRange = shortSightedFogRange;
			snap.medicShieldBlockFlashTicks = medicShieldBlockFlashTicks;
			snap.medicShieldBreakFlashTicks = medicShieldBreakFlashTicks;
			snap.medicShieldBlockFlashAlpha = medicShieldBlockFlashAlpha;
			snap.medicShieldBreakFlashAlpha = medicShieldBreakFlashAlpha;
			snap.silentShadowAlpha = silentShadowAlpha;
			snap.abilityHudScalePercent = abilityHudScalePercent;
			snap.abilityHudOffsetX = abilityHudOffsetX;
			snap.abilityHudOffsetY = abilityHudOffsetY;
			writeAtomically(CONFIG_PATH, GSON.toJson(snap));
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save gexpress.json.", e);
		}
	}

	public static void apply(int c4Price, int c4FuseSeconds, int c4FirstBeepSeconds, int wrongWirePercent,
			int grenadePrice, int passiveIncomeKiller, int passiveIncomeCivilian, int passiveIncomeNeutral,
			int passiveIncomeVigilante, int passiveIncomeMafia,
			int medicShieldCooldownSeconds, boolean medicShieldKnifeBreaks,
			int silentShadowDurationSeconds, int silentShadowCooldownSeconds,
			int warlockMarkCooldownSeconds, int warlockKillCooldownSeconds,
			int juggernautInitialCooldownSeconds, int juggernautCooldownReductionSeconds,
			int juggernautMinimumCooldownSeconds, int juggernautShieldRechargeSeconds,
			int tricksterSwapDurationSeconds, int tricksterMasqueradeCooldownSeconds,
			int tricksterDancingCartsCooldownSeconds,
			int tricksterDancingCartsMaxUses,
			int puppetmasterControlDurationSeconds, int puppetmasterControlCooldownSeconds,
			boolean puppetmasterRandomTarget, int puppetmasterControlRange, int puppetmasterMaxUses,
			int pelicanEatCooldownSeconds, int pelicanEatPercentage, int hungryFoodLimit, int thirstyDrinkLimit,
			int snitchTasksRequired,
			int snitchWarningTasksRemaining,
			int timeMasterRewindSeconds, int timeMasterCooldownSeconds, int timeMasterMaxUses,
			int timeMasterFreezeDurationSeconds, int timeMasterFreezeCooldownSeconds,
			int timeMasterFreezeMaxUses, int timeMasterFreezeRange,
			int scatterBrainCooldownSeconds, int trackerMaxTargets,
			int trackerRange, int trackerCooldownSeconds, int altruistRange,
			int skincrawlerBodyMaxAgeSeconds, int skincrawlerCooldownSeconds, int skincrawlerStunSeconds,
			int skincrawlerRange, int spyBugCost, int spyBugDurationSeconds, int spyBugRange,
			int squeakerPitchPercent, int masqueradePitchMinPercent, int masqueradePitchMaxPercent,
			boolean lastDeathShieldEnabled,
			int bountyHunterBountyIntervalSeconds, int bountyHunterRewardGold,
			int bountyHunterFailCooldownSeconds, int godfatherBulletPrice, int godfatherStartingBullets,
			int godfatherMaxLoadedBullets, int mafiaStartingGold, int mafiaMinimumPlayers,
			int godfatherStartingGold, int mafiosoStartingGold, int janitorStartingGold,
			int mafiaRecruitRange, int mafiaReplacementCooldownSeconds,
			int mafiaRevolverKillCooldownSeconds, int janitorCleanRange, int janitorCleanCooldownSeconds,
			int janitorRevolverCooldownAfterCleanSeconds, int janitorCleanCooldownAfterKillSeconds,
			boolean useCustomRoleCounts,
			int maxKillerAmount, int maxVigilanteAmount, int playersPerKiller, int playersPerVigilante,
			float c4BackOffsetX, float c4BackOffsetY, float c4BackOffsetZ,
			float c4BackRotationX, float c4BackRotationY, float c4BackRotationZ,
			float c4BackSlant, float c4BackScale,
			float spyBugOffsetX, float spyBugOffsetY, float spyBugOffsetZ,
			float spyBugRotationX, float spyBugRotationY, float spyBugRotationZ,
			float spyBugSlant, float spyBugScale, String c4PlacementPresets,
			String roleDescriptionOverrides,
			float shortSightedFogRange,
			int medicShieldBlockFlashTicks, int medicShieldBreakFlashTicks,
			int medicShieldBlockFlashAlpha, int medicShieldBreakFlashAlpha,
			float silentShadowAlpha) {
		GexpressConfig.c4Price = c4Price;
		GexpressConfig.c4FuseSeconds = c4FuseSeconds;
		GexpressConfig.c4FirstBeepSeconds = c4FirstBeepSeconds;
		GexpressConfig.wrongWirePercent = wrongWirePercent;
		GexpressConfig.grenadePrice = grenadePrice;
		GexpressConfig.passiveIncomeKiller = passiveIncomeKiller;
		GexpressConfig.passiveIncomeCivilian = passiveIncomeCivilian;
		GexpressConfig.passiveIncomeNeutral = passiveIncomeNeutral;
		GexpressConfig.passiveIncomeVigilante = passiveIncomeVigilante;
		GexpressConfig.passiveIncomeMafia = passiveIncomeMafia;
		GexpressConfig.medicShieldCooldownSeconds = medicShieldCooldownSeconds;
		GexpressConfig.medicShieldKnifeBreaks = medicShieldKnifeBreaks;
		GexpressConfig.silentShadowDurationSeconds = silentShadowDurationSeconds;
		GexpressConfig.silentShadowCooldownSeconds = silentShadowCooldownSeconds;
		GexpressConfig.warlockMarkCooldownSeconds = warlockMarkCooldownSeconds;
		GexpressConfig.warlockKillCooldownSeconds = warlockKillCooldownSeconds;
		GexpressConfig.juggernautInitialCooldownSeconds = juggernautInitialCooldownSeconds;
		GexpressConfig.juggernautCooldownReductionSeconds = juggernautCooldownReductionSeconds;
		GexpressConfig.juggernautMinimumCooldownSeconds = juggernautMinimumCooldownSeconds;
		GexpressConfig.juggernautShieldRechargeSeconds = juggernautShieldRechargeSeconds;
		GexpressConfig.tricksterSwapDurationSeconds = tricksterSwapDurationSeconds;
		GexpressConfig.tricksterMasqueradeCooldownSeconds = tricksterMasqueradeCooldownSeconds;
		GexpressConfig.tricksterDancingCartsCooldownSeconds = tricksterDancingCartsCooldownSeconds;
		GexpressConfig.tricksterDancingCartsMaxUses = tricksterDancingCartsMaxUses;
		GexpressConfig.puppetmasterControlDurationSeconds = puppetmasterControlDurationSeconds;
		GexpressConfig.puppetmasterControlCooldownSeconds = puppetmasterControlCooldownSeconds;
		GexpressConfig.puppetmasterRandomTarget = puppetmasterRandomTarget;
		GexpressConfig.puppetmasterControlRange = puppetmasterControlRange;
		GexpressConfig.puppetmasterMaxUses = puppetmasterMaxUses;
		GexpressConfig.pelicanEatCooldownSeconds = pelicanEatCooldownSeconds;
		GexpressConfig.pelicanEatPercentage = pelicanEatPercentage;
		GexpressConfig.hungryFoodLimit = hungryFoodLimit;
		GexpressConfig.thirstyDrinkLimit = thirstyDrinkLimit;
		GexpressConfig.snitchTasksRequired = snitchTasksRequired;
		GexpressConfig.snitchWarningTasksRemaining = snitchWarningTasksRemaining;
		GexpressConfig.timeMasterRewindSeconds = timeMasterRewindSeconds;
		GexpressConfig.timeMasterCooldownSeconds = timeMasterCooldownSeconds;
		GexpressConfig.timeMasterMaxUses = timeMasterMaxUses;
		GexpressConfig.timeMasterFreezeDurationSeconds = timeMasterFreezeDurationSeconds;
		GexpressConfig.timeMasterFreezeCooldownSeconds = timeMasterFreezeCooldownSeconds;
		GexpressConfig.timeMasterFreezeMaxUses = timeMasterFreezeMaxUses;
		GexpressConfig.timeMasterFreezeRange = timeMasterFreezeRange;
		GexpressConfig.scatterBrainCooldownSeconds = scatterBrainCooldownSeconds;
		GexpressConfig.trackerMaxTargets = trackerMaxTargets;
		GexpressConfig.trackerRange = trackerRange;
		GexpressConfig.trackerCooldownSeconds = trackerCooldownSeconds;
		GexpressConfig.altruistRange = altruistRange;
		GexpressConfig.skincrawlerBodyMaxAgeSeconds = skincrawlerBodyMaxAgeSeconds;
		GexpressConfig.skincrawlerCooldownSeconds = skincrawlerCooldownSeconds;
		GexpressConfig.skincrawlerStunSeconds = skincrawlerStunSeconds;
		GexpressConfig.skincrawlerRange = skincrawlerRange;
		GexpressConfig.spyBugCost = spyBugCost;
		GexpressConfig.spyBugDurationSeconds = spyBugDurationSeconds;
		GexpressConfig.spyBugRange = spyBugRange;
		GexpressConfig.squeakerPitchPercent = squeakerPitchPercent;
		GexpressConfig.masqueradePitchMinPercent = masqueradePitchMinPercent;
		GexpressConfig.masqueradePitchMaxPercent = masqueradePitchMaxPercent;
		GexpressConfig.bountyHunterBountyIntervalSeconds = bountyHunterBountyIntervalSeconds;
		GexpressConfig.bountyHunterRewardGold = bountyHunterRewardGold;
		GexpressConfig.bountyHunterFailCooldownSeconds = bountyHunterFailCooldownSeconds;
		GexpressConfig.godfatherBulletPrice = godfatherBulletPrice;
		GexpressConfig.godfatherStartingBullets = godfatherStartingBullets;
		GexpressConfig.godfatherMaxLoadedBullets = godfatherMaxLoadedBullets;
		GexpressConfig.mafiaStartingGold = mafiaStartingGold;
		GexpressConfig.mafiaMinimumPlayers = mafiaMinimumPlayers;
		GexpressConfig.godfatherStartingGold = godfatherStartingGold;
		GexpressConfig.mafiosoStartingGold = mafiosoStartingGold;
		GexpressConfig.janitorStartingGold = janitorStartingGold;
		GexpressConfig.mafiaRecruitRange = mafiaRecruitRange;
		GexpressConfig.mafiaReplacementCooldownSeconds = mafiaReplacementCooldownSeconds;
		GexpressConfig.mafiaRevolverKillCooldownSeconds = mafiaRevolverKillCooldownSeconds;
		GexpressConfig.janitorCleanRange = janitorCleanRange;
		GexpressConfig.janitorCleanCooldownSeconds = janitorCleanCooldownSeconds;
		GexpressConfig.janitorRevolverCooldownAfterCleanSeconds = janitorRevolverCooldownAfterCleanSeconds;
		GexpressConfig.janitorCleanCooldownAfterKillSeconds = janitorCleanCooldownAfterKillSeconds;
		GexpressConfig.lastDeathShieldEnabled = lastDeathShieldEnabled;
		GexpressConfig.useCustomRoleCounts = useCustomRoleCounts;
		GexpressConfig.maxKillerAmount = maxKillerAmount;
		GexpressConfig.maxVigilanteAmount = maxVigilanteAmount;
		GexpressConfig.playersPerKiller = playersPerKiller;
		GexpressConfig.playersPerVigilante = playersPerVigilante;
		GexpressConfig.c4BackOffsetX = c4BackOffsetX;
		GexpressConfig.c4BackOffsetY = c4BackOffsetY;
		GexpressConfig.c4BackOffsetZ = c4BackOffsetZ;
		GexpressConfig.c4BackRotationX = c4BackRotationX;
		GexpressConfig.c4BackRotationY = c4BackRotationY;
		GexpressConfig.c4BackRotationZ = c4BackRotationZ;
		GexpressConfig.c4BackSlant = c4BackSlant;
		GexpressConfig.c4BackScale = c4BackScale;
		GexpressConfig.spyBugOffsetX = spyBugOffsetX;
		GexpressConfig.spyBugOffsetY = spyBugOffsetY;
		GexpressConfig.spyBugOffsetZ = spyBugOffsetZ;
		GexpressConfig.spyBugRotationX = spyBugRotationX;
		GexpressConfig.spyBugRotationY = spyBugRotationY;
		GexpressConfig.spyBugRotationZ = spyBugRotationZ;
		GexpressConfig.spyBugSlant = spyBugSlant;
		GexpressConfig.spyBugScale = spyBugScale;
		GexpressConfig.setC4PlacementPresetsSyncString(c4PlacementPresets);
		GexpressConfig.setRoleDescriptionOverridesSyncString(roleDescriptionOverrides);
		GexpressConfig.shortSightedFogRange = shortSightedFogRange;
		GexpressConfig.medicShieldBlockFlashTicks = medicShieldBlockFlashTicks;
		GexpressConfig.medicShieldBreakFlashTicks = medicShieldBreakFlashTicks;
		GexpressConfig.medicShieldBlockFlashAlpha = medicShieldBlockFlashAlpha;
		GexpressConfig.medicShieldBreakFlashAlpha = medicShieldBreakFlashAlpha;
		GexpressConfig.silentShadowAlpha = silentShadowAlpha;
		clampInPlace();
	}

	private static void clampInPlace() {
		c4Price = getC4Price();
		c4FuseSeconds = getC4FuseSeconds();
		c4FirstBeepSeconds = getC4FirstBeepSeconds();
		wrongWirePercent = getWrongWirePercent();
		grenadePrice = getGrenadePrice();
		passiveIncomeKiller = getPassiveIncomeKiller();
		passiveIncomeCivilian = getPassiveIncomeCivilian();
		passiveIncomeNeutral = getPassiveIncomeNeutral();
		passiveIncomeVigilante = getPassiveIncomeVigilante();
		passiveIncomeMafia = getPassiveIncomeMafia();
		medicShieldCooldownSeconds = getMedicShieldCooldownSeconds();
		silentShadowDurationSeconds = getSilentShadowDurationSeconds();
		silentShadowCooldownSeconds = getSilentShadowCooldownSeconds();
		warlockMarkCooldownSeconds = getWarlockMarkCooldownSeconds();
		warlockKillCooldownSeconds = getWarlockKillCooldownSeconds();
		juggernautInitialCooldownSeconds = getJuggernautInitialCooldownSeconds();
		juggernautCooldownReductionSeconds = getJuggernautCooldownReductionSeconds();
		juggernautMinimumCooldownSeconds = getJuggernautMinimumCooldownSeconds();
		juggernautShieldRechargeSeconds = getJuggernautShieldRechargeSeconds();
		tricksterSwapDurationSeconds = getTricksterSwapDurationSeconds();
		tricksterMasqueradeCooldownSeconds = getTricksterMasqueradeCooldownSeconds();
		tricksterDancingCartsCooldownSeconds = getTricksterDancingCartsCooldownSeconds();
		tricksterDancingCartsMaxUses = getTricksterDancingCartsMaxUses();
		puppetmasterControlDurationSeconds = getPuppetmasterControlDurationSeconds();
		puppetmasterControlCooldownSeconds = getPuppetmasterControlCooldownSeconds();
		puppetmasterControlRange = getPuppetmasterControlRange();
		puppetmasterMaxUses = getPuppetmasterMaxUses();
		pelicanEatCooldownSeconds = getPelicanEatCooldownSeconds();
		pelicanEatPercentage = getPelicanEatPercentage();
		hungryFoodLimit = getHungryFoodLimit();
		thirstyDrinkLimit = getThirstyDrinkLimit();
		snitchTasksRequired = getSnitchTasksRequired();
		snitchWarningTasksRemaining = getSnitchWarningTasksRemaining();
		timeMasterRewindSeconds = getTimeMasterRewindSeconds();
		timeMasterCooldownSeconds = getTimeMasterCooldownSeconds();
		timeMasterMaxUses = getTimeMasterMaxUses();
		timeMasterFreezeDurationSeconds = getTimeMasterFreezeDurationSeconds();
		timeMasterFreezeCooldownSeconds = getTimeMasterFreezeCooldownSeconds();
		timeMasterFreezeMaxUses = getTimeMasterFreezeMaxUses();
		timeMasterFreezeRange = getTimeMasterFreezeRange();
		scatterBrainCooldownSeconds = getScatterBrainCooldownSeconds();
		trackerMaxTargets = getTrackerMaxTargets();
		trackerRange = getTrackerRange();
		trackerCooldownSeconds = getTrackerCooldownSeconds();
		altruistRange = getAltruistRange();
		bountyHunterBountyIntervalSeconds = getBountyHunterBountyIntervalSeconds();
		bountyHunterRewardGold = getBountyHunterRewardGold();
		bountyHunterFailCooldownSeconds = getBountyHunterFailCooldownSeconds();
		skincrawlerBodyMaxAgeSeconds = getSkincrawlerBodyMaxAgeSeconds();
		skincrawlerCooldownSeconds = getSkincrawlerCooldownSeconds();
		skincrawlerStunSeconds = getSkincrawlerStunSeconds();
		skincrawlerRange = getSkincrawlerRange();
		spyBugCost = getSpyBugCost();
		spyBugDurationSeconds = getSpyBugDurationSeconds();
		spyBugRange = getSpyBugRange();
		squeakerPitchPercent = getSqueakerPitchPercent();
		masqueradePitchMinPercent = getMasqueradePitchMinPercent();
		masqueradePitchMaxPercent = getMasqueradePitchMaxPercent();
		godfatherBulletPrice = getGodfatherBulletPrice();
		godfatherStartingBullets = getGodfatherStartingBullets();
		godfatherMaxLoadedBullets = getGodfatherMaxLoadedBullets();
		mafiaStartingGold = getMafiaStartingGold();
		mafiaMinimumPlayers = getMafiaMinimumPlayers();
		godfatherStartingGold = getGodfatherStartingGold();
		mafiosoStartingGold = getMafiosoStartingGold();
		janitorStartingGold = getJanitorStartingGold();
		mafiaRecruitRange = getMafiaRecruitRange();
		mafiaReplacementCooldownSeconds = getMafiaReplacementCooldownSeconds();
		mafiaRevolverKillCooldownSeconds = getMafiaRevolverKillCooldownSeconds();
		janitorCleanRange = getJanitorCleanRange();
		janitorCleanCooldownSeconds = getJanitorCleanCooldownSeconds();
		janitorRevolverCooldownAfterCleanSeconds = getJanitorRevolverCooldownAfterCleanSeconds();
		janitorCleanCooldownAfterKillSeconds = getJanitorCleanCooldownAfterKillSeconds();
		maxKillerAmount = getMaxKillerAmount();
		maxVigilanteAmount = getMaxVigilanteAmount();
		playersPerKiller = getPlayersPerKiller();
		playersPerVigilante = getPlayersPerVigilante();
		c4BackOffsetX = getC4BackOffsetX();
		c4BackOffsetY = getC4BackOffsetY();
		c4BackOffsetZ = getC4BackOffsetZ();
		c4BackRotationX = getC4BackRotationX();
		c4BackRotationY = getC4BackRotationY();
		c4BackRotationZ = getC4BackRotationZ();
		c4BackSlant = getC4BackSlant();
		c4BackScale = getC4BackScale();
		spyBugOffsetX = getSpyBugOffsetX();
		spyBugOffsetY = getSpyBugOffsetY();
		spyBugOffsetZ = getSpyBugOffsetZ();
		spyBugRotationX = getSpyBugRotationX();
		spyBugRotationY = getSpyBugRotationY();
		spyBugRotationZ = getSpyBugRotationZ();
		spyBugSlant = getSpyBugSlant();
		spyBugScale = getSpyBugScale();
		shortSightedFogRange = getShortSightedFogRange();
		medicShieldBlockFlashTicks = getMedicShieldBlockFlashTicks();
		medicShieldBreakFlashTicks = getMedicShieldBreakFlashTicks();
		medicShieldBlockFlashAlpha = getMedicShieldBlockFlashAlpha();
		medicShieldBreakFlashAlpha = getMedicShieldBreakFlashAlpha();
		silentShadowAlpha = getSilentShadowAlpha();
		abilityHudScalePercent = getAbilityHudScalePercent();
		abilityHudOffsetX = getAbilityHudOffsetX();
		abilityHudOffsetY = getAbilityHudOffsetY();
	}

	private static C4PlacementPreset currentC4PlacementPreset() {
		return new C4PlacementPreset(
			getC4BackOffsetX(),
			getC4BackOffsetY(),
			getC4BackOffsetZ(),
			getC4BackRotationX(),
			getC4BackRotationY(),
			getC4BackRotationZ(),
			getC4BackSlant(),
			getC4BackScale()
		);
	}

	private static List<String> normalizeC4PresetStrings(List<String> values, boolean fallbackToCurrent) {
		List<String> out = new ArrayList<>();
		if (values != null) {
			for (String raw : values) {
				C4PlacementPreset preset = C4PlacementPreset.parse(raw);
				if (preset != null) out.add(preset.toConfigString());
			}
		}
		if (out.isEmpty() && fallbackToCurrent) {
			out.add(currentC4PlacementPreset().toConfigString());
		}
		return out;
	}

	private static Map<String, String> normalizeRoleDescriptionOverrides(Map<?, ?> values) {
		Map<String, String> out = new LinkedHashMap<>();
		if (values != null) {
			for (Map.Entry<?, ?> entry : values.entrySet()) {
				String key = entry.getKey() instanceof String s ? s.strip() : "";
				String value = entry.getValue() instanceof String s ? s.strip() : "";
				if (!key.isEmpty() && !value.isEmpty()) out.put(key, value);
			}
		}
		return out;
	}

	private static float clampFloat(float value, float min, float max, float fallback) {
		if (!Float.isFinite(value)) return fallback;
		return Math.max(min, Math.min(max, value));
	}

	private static void writeAtomically(Path target, String json) throws IOException {
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, json);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static final class Snapshot {
		int c4Price = 500;
		int c4FuseSeconds = 15;
		int c4FirstBeepSeconds = 3;
		int wrongWirePercent = 20;
		int grenadePrice = 150;
		int passiveIncomeKiller = 5;
		int passiveIncomeCivilian = 0;
		int passiveIncomeNeutral = 5;
		int passiveIncomeVigilante = 0;
		int passiveIncomeMafia = 5;
		int medicShieldCooldownSeconds = 90;
		boolean medicShieldKnifeBreaks = false;
		int silentShadowDurationSeconds = 8;
		int silentShadowCooldownSeconds = 35;
		int warlockMarkCooldownSeconds = 20;
		int warlockKillCooldownSeconds = 45;
		int juggernautInitialCooldownSeconds = 60;
		int juggernautCooldownReductionSeconds = 20;
		int juggernautMinimumCooldownSeconds = 10;
		int juggernautShieldRechargeSeconds = 60;
		int tricksterSwapDurationSeconds = 25;
		int tricksterMasqueradeCooldownSeconds = 35;
		int tricksterDancingCartsCooldownSeconds = 45;
		int tricksterDancingCartsMaxUses = 1;
		int puppetmasterControlDurationSeconds = 15;
		int puppetmasterControlCooldownSeconds = 45;
		boolean puppetmasterRandomTarget = false;
		boolean puppetmasterCanKillOwnBody = false;
		int puppetmasterControlRange = 16;
		int puppetmasterMaxUses = 3;
		int pelicanEatCooldownSeconds = 20;
		int pelicanEatPercentage = 80;
		int hungryFoodLimit = 2;
		int thirstyDrinkLimit = 2;
		int snitchTasksRequired = 3;
		int snitchWarningTasksRemaining = 1;
		int timeMasterRewindSeconds = 10;
		int timeMasterCooldownSeconds = 120;
		int timeMasterMaxUses = 1;
		int timeMasterFreezeDurationSeconds = 4;
		int timeMasterFreezeCooldownSeconds = 30;
		int timeMasterFreezeMaxUses = 3;
		int timeMasterFreezeRange = 8;
		int scatterBrainCooldownSeconds = 60;
		int trackerMaxTargets = 3;
		int trackerRange = 24;
		int trackerCooldownSeconds = 10;
		int altruistRange = 4;
		int bountyHunterBountyIntervalSeconds = 60;
		int bountyHunterRewardGold = 200;
		int bountyHunterFailCooldownSeconds = 90;
		int skincrawlerBodyMaxAgeSeconds = 90;
		int skincrawlerCooldownSeconds = 90;
		int skincrawlerStunSeconds = 5;
		int skincrawlerRange = 4;
		int spyBugCost = 100;
		int spyBugDurationSeconds = 120;
		int spyBugRange = 16;
		int squeakerPitchPercent = 135;
		int masqueradePitchMinPercent = 80;
		int masqueradePitchMaxPercent = 145;
		int godfatherBulletPrice = 75;
		int godfatherStartingBullets = 1;
		int godfatherMaxLoadedBullets = 3;
		int mafiaStartingGold = 100;
		int mafiaMinimumPlayers = 15;
		int godfatherStartingGold = 100;
		int mafiosoStartingGold = 100;
		int janitorStartingGold = 100;
		int mafiaRecruitRange = 16;
		int mafiaReplacementCooldownSeconds = 120;
		int mafiaRevolverKillCooldownSeconds = 60;
		int janitorCleanRange = 4;
		int janitorCleanCooldownSeconds = 20;
		int janitorRevolverCooldownAfterCleanSeconds = 45;
		int janitorCleanCooldownAfterKillSeconds = 45;
		boolean lastDeathShieldEnabled = false;
		boolean useCustomRoleCounts = true;
		int maxKillerAmount = 64;
		int maxVigilanteAmount = 1;
		int playersPerKiller = 6;
		int playersPerVigilante = 8;
		float c4BackOffsetX = 0.0F;
		float c4BackOffsetY = 0.24F;
		float c4BackOffsetZ = 0.28F;
		float c4BackRotationX = 0.0F;
		float c4BackRotationY = 0.0F;
		float c4BackRotationZ = 0.0F;
		float c4BackSlant = 0.0F;
		float c4BackScale = 0.42F;
		float spyBugOffsetX = 0.0F;
		float spyBugOffsetY = 0.16F;
		float spyBugOffsetZ = 0.31F;
		float spyBugRotationX = 0.0F;
		float spyBugRotationY = 0.0F;
		float spyBugRotationZ = 0.0F;
		float spyBugSlant = 0.0F;
		float spyBugScale = 0.28F;
		List<String> c4PlacementPresets = new ArrayList<>();
		Map<String, String> roleDescriptionOverrides = new LinkedHashMap<>();
		float shortSightedFogRange = 5.0F;
		int medicShieldBlockFlashTicks = 18;
		int medicShieldBreakFlashTicks = 28;
		int medicShieldBlockFlashAlpha = 72;
		int medicShieldBreakFlashAlpha = 92;
		float silentShadowAlpha = 0.45F;
		int abilityHudScalePercent = 100;
		int abilityHudOffsetX = 0;
		int abilityHudOffsetY = 0;
	}
}
