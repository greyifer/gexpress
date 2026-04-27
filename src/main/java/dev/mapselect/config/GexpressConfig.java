package dev.mapselect.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
	/** Seconds removed from the Juggernaut weapon cooldown after each kill. */
	public static int juggernautCooldownReductionSeconds = 10;
	/** Lowest Juggernaut weapon cooldown after repeated kills. */
	public static int juggernautMinimumCooldownSeconds = 10;
	/** Seconds the Trickster's skin shuffle and global voice mute lasts. */
	public static int tricksterSwapDurationSeconds = 25;
	/** Seconds the Puppetmaster can control a target. */
	public static int puppetmasterControlDurationSeconds = 15;
	/** Seconds the Puppetmaster must wait after control ends. */
	public static int puppetmasterControlCooldownSeconds = 45;
	/** Whether Puppetmaster ability picks a random target instead of opening the target menu. */
	public static boolean puppetmasterRandomTarget = false;
	/** Whether a controlled puppet can kill the Puppetmaster's abandoned body. */
	public static boolean puppetmasterCanKillOwnBody = false;
	/** Seconds before the Pelican can swallow another player. */
	public static int pelicanEatCooldownSeconds = 20;
	/** Number of task completions before a Snitch learns the killer roster. */
	public static int snitchTasksRequired = 3;
	/** Maximum number of normal killer-team players assigned by G'Express role assignment. */
	public static int maxKillerAmount = 64;
	/** Synced C4 backpack model tuning. */
	public static float c4BackOffsetX = 0.0F;
	public static float c4BackOffsetY = 0.24F;
	public static float c4BackOffsetZ = 0.28F;
	public static float c4BackRotationX = 0.0F;
	public static float c4BackRotationY = 0.0F;
	public static float c4BackRotationZ = 0.0F;
	public static float c4BackSlant = 0.0F;
	public static float c4BackScale = 0.42F;
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
	public static final int TRICKSTER_SWAP_DURATION_MIN = 1;
	public static final int TRICKSTER_SWAP_DURATION_MAX = 300;
	public static final int PUPPETMASTER_CONTROL_DURATION_MIN = 1;
	public static final int PUPPETMASTER_CONTROL_DURATION_MAX = 120;
	public static final int PUPPETMASTER_CONTROL_COOLDOWN_MIN = 0;
	public static final int PUPPETMASTER_CONTROL_COOLDOWN_MAX = 600;
	public static final int PELICAN_EAT_COOLDOWN_MIN = 0;
	public static final int PELICAN_EAT_COOLDOWN_MAX = 600;
	public static final int SNITCH_TASKS_REQUIRED_MIN = 1;
	public static final int SNITCH_TASKS_REQUIRED_MAX = 25;
	public static final int MAX_KILLER_AMOUNT_MIN = 0;
	public static final int MAX_KILLER_AMOUNT_MAX = 64;
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

	public static int getTricksterSwapDurationSeconds() {
		return Math.max(TRICKSTER_SWAP_DURATION_MIN,
			Math.min(TRICKSTER_SWAP_DURATION_MAX, tricksterSwapDurationSeconds));
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

	public static int getPelicanEatCooldownSeconds() {
		return Math.max(PELICAN_EAT_COOLDOWN_MIN,
			Math.min(PELICAN_EAT_COOLDOWN_MAX, pelicanEatCooldownSeconds));
	}

	public static int getSnitchTasksRequired() {
		return Math.max(SNITCH_TASKS_REQUIRED_MIN,
			Math.min(SNITCH_TASKS_REQUIRED_MAX, snitchTasksRequired));
	}

	public static int getMaxKillerAmount() {
		return Math.max(MAX_KILLER_AMOUNT_MIN, Math.min(MAX_KILLER_AMOUNT_MAX, maxKillerAmount));
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
			medicShieldCooldownSeconds = snap.medicShieldCooldownSeconds;
			medicShieldKnifeBreaks = snap.medicShieldKnifeBreaks;
			silentShadowDurationSeconds = snap.silentShadowDurationSeconds;
			silentShadowCooldownSeconds = snap.silentShadowCooldownSeconds;
			warlockMarkCooldownSeconds = snap.warlockMarkCooldownSeconds;
			warlockKillCooldownSeconds = snap.warlockKillCooldownSeconds;
			juggernautInitialCooldownSeconds = snap.juggernautInitialCooldownSeconds;
			juggernautCooldownReductionSeconds = snap.juggernautCooldownReductionSeconds;
			juggernautMinimumCooldownSeconds = snap.juggernautMinimumCooldownSeconds;
			tricksterSwapDurationSeconds = snap.tricksterSwapDurationSeconds;
			puppetmasterControlDurationSeconds = snap.puppetmasterControlDurationSeconds;
			puppetmasterControlCooldownSeconds = snap.puppetmasterControlCooldownSeconds;
			puppetmasterRandomTarget = snap.puppetmasterRandomTarget;
			puppetmasterCanKillOwnBody = snap.puppetmasterCanKillOwnBody;
			pelicanEatCooldownSeconds = snap.pelicanEatCooldownSeconds;
			snitchTasksRequired = snap.snitchTasksRequired;
			maxKillerAmount = snap.maxKillerAmount;
			c4BackOffsetX = snap.c4BackOffsetX;
			c4BackOffsetY = snap.c4BackOffsetY;
			c4BackOffsetZ = snap.c4BackOffsetZ;
			c4BackRotationX = snap.c4BackRotationX;
			c4BackRotationY = snap.c4BackRotationY;
			c4BackRotationZ = snap.c4BackRotationZ;
			c4BackSlant = snap.c4BackSlant;
			c4BackScale = snap.c4BackScale;
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
			snap.medicShieldCooldownSeconds = medicShieldCooldownSeconds;
			snap.medicShieldKnifeBreaks = medicShieldKnifeBreaks;
			snap.silentShadowDurationSeconds = silentShadowDurationSeconds;
			snap.silentShadowCooldownSeconds = silentShadowCooldownSeconds;
			snap.warlockMarkCooldownSeconds = warlockMarkCooldownSeconds;
			snap.warlockKillCooldownSeconds = warlockKillCooldownSeconds;
			snap.juggernautInitialCooldownSeconds = juggernautInitialCooldownSeconds;
			snap.juggernautCooldownReductionSeconds = juggernautCooldownReductionSeconds;
			snap.juggernautMinimumCooldownSeconds = juggernautMinimumCooldownSeconds;
			snap.tricksterSwapDurationSeconds = tricksterSwapDurationSeconds;
			snap.puppetmasterControlDurationSeconds = puppetmasterControlDurationSeconds;
			snap.puppetmasterControlCooldownSeconds = puppetmasterControlCooldownSeconds;
			snap.puppetmasterRandomTarget = puppetmasterRandomTarget;
			snap.puppetmasterCanKillOwnBody = puppetmasterCanKillOwnBody;
			snap.pelicanEatCooldownSeconds = pelicanEatCooldownSeconds;
			snap.snitchTasksRequired = snitchTasksRequired;
			snap.maxKillerAmount = maxKillerAmount;
			snap.c4BackOffsetX = c4BackOffsetX;
			snap.c4BackOffsetY = c4BackOffsetY;
			snap.c4BackOffsetZ = c4BackOffsetZ;
			snap.c4BackRotationX = c4BackRotationX;
			snap.c4BackRotationY = c4BackRotationY;
			snap.c4BackRotationZ = c4BackRotationZ;
			snap.c4BackSlant = c4BackSlant;
			snap.c4BackScale = c4BackScale;
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
			int grenadePrice, int medicShieldCooldownSeconds, boolean medicShieldKnifeBreaks,
			int silentShadowDurationSeconds, int silentShadowCooldownSeconds,
			int warlockMarkCooldownSeconds, int warlockKillCooldownSeconds,
			int juggernautInitialCooldownSeconds, int juggernautCooldownReductionSeconds,
			int juggernautMinimumCooldownSeconds, int tricksterSwapDurationSeconds,
			int puppetmasterControlDurationSeconds, int puppetmasterControlCooldownSeconds,
			boolean puppetmasterRandomTarget, int pelicanEatCooldownSeconds, int snitchTasksRequired, int maxKillerAmount,
			float c4BackOffsetX, float c4BackOffsetY, float c4BackOffsetZ,
			float c4BackRotationX, float c4BackRotationY, float c4BackRotationZ,
			float c4BackSlant, float c4BackScale,
			float shortSightedFogRange,
			int medicShieldBlockFlashTicks, int medicShieldBreakFlashTicks,
			int medicShieldBlockFlashAlpha, int medicShieldBreakFlashAlpha,
			float silentShadowAlpha) {
		GexpressConfig.c4Price = c4Price;
		GexpressConfig.c4FuseSeconds = c4FuseSeconds;
		GexpressConfig.c4FirstBeepSeconds = c4FirstBeepSeconds;
		GexpressConfig.wrongWirePercent = wrongWirePercent;
		GexpressConfig.grenadePrice = grenadePrice;
		GexpressConfig.medicShieldCooldownSeconds = medicShieldCooldownSeconds;
		GexpressConfig.medicShieldKnifeBreaks = medicShieldKnifeBreaks;
		GexpressConfig.silentShadowDurationSeconds = silentShadowDurationSeconds;
		GexpressConfig.silentShadowCooldownSeconds = silentShadowCooldownSeconds;
		GexpressConfig.warlockMarkCooldownSeconds = warlockMarkCooldownSeconds;
		GexpressConfig.warlockKillCooldownSeconds = warlockKillCooldownSeconds;
		GexpressConfig.juggernautInitialCooldownSeconds = juggernautInitialCooldownSeconds;
		GexpressConfig.juggernautCooldownReductionSeconds = juggernautCooldownReductionSeconds;
		GexpressConfig.juggernautMinimumCooldownSeconds = juggernautMinimumCooldownSeconds;
		GexpressConfig.tricksterSwapDurationSeconds = tricksterSwapDurationSeconds;
		GexpressConfig.puppetmasterControlDurationSeconds = puppetmasterControlDurationSeconds;
		GexpressConfig.puppetmasterControlCooldownSeconds = puppetmasterControlCooldownSeconds;
		GexpressConfig.puppetmasterRandomTarget = puppetmasterRandomTarget;
		GexpressConfig.pelicanEatCooldownSeconds = pelicanEatCooldownSeconds;
		GexpressConfig.snitchTasksRequired = snitchTasksRequired;
		GexpressConfig.maxKillerAmount = maxKillerAmount;
		GexpressConfig.c4BackOffsetX = c4BackOffsetX;
		GexpressConfig.c4BackOffsetY = c4BackOffsetY;
		GexpressConfig.c4BackOffsetZ = c4BackOffsetZ;
		GexpressConfig.c4BackRotationX = c4BackRotationX;
		GexpressConfig.c4BackRotationY = c4BackRotationY;
		GexpressConfig.c4BackRotationZ = c4BackRotationZ;
		GexpressConfig.c4BackSlant = c4BackSlant;
		GexpressConfig.c4BackScale = c4BackScale;
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
		medicShieldCooldownSeconds = getMedicShieldCooldownSeconds();
		silentShadowDurationSeconds = getSilentShadowDurationSeconds();
		silentShadowCooldownSeconds = getSilentShadowCooldownSeconds();
		warlockMarkCooldownSeconds = getWarlockMarkCooldownSeconds();
		warlockKillCooldownSeconds = getWarlockKillCooldownSeconds();
		juggernautInitialCooldownSeconds = getJuggernautInitialCooldownSeconds();
		juggernautCooldownReductionSeconds = getJuggernautCooldownReductionSeconds();
		juggernautMinimumCooldownSeconds = getJuggernautMinimumCooldownSeconds();
		tricksterSwapDurationSeconds = getTricksterSwapDurationSeconds();
		puppetmasterControlDurationSeconds = getPuppetmasterControlDurationSeconds();
		puppetmasterControlCooldownSeconds = getPuppetmasterControlCooldownSeconds();
		pelicanEatCooldownSeconds = getPelicanEatCooldownSeconds();
		snitchTasksRequired = getSnitchTasksRequired();
		maxKillerAmount = getMaxKillerAmount();
		c4BackOffsetX = getC4BackOffsetX();
		c4BackOffsetY = getC4BackOffsetY();
		c4BackOffsetZ = getC4BackOffsetZ();
		c4BackRotationX = getC4BackRotationX();
		c4BackRotationY = getC4BackRotationY();
		c4BackRotationZ = getC4BackRotationZ();
		c4BackSlant = getC4BackSlant();
		c4BackScale = getC4BackScale();
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
		int medicShieldCooldownSeconds = 90;
		boolean medicShieldKnifeBreaks = false;
		int silentShadowDurationSeconds = 8;
		int silentShadowCooldownSeconds = 35;
		int warlockMarkCooldownSeconds = 20;
		int warlockKillCooldownSeconds = 45;
		int juggernautInitialCooldownSeconds = 60;
		int juggernautCooldownReductionSeconds = 10;
		int juggernautMinimumCooldownSeconds = 10;
		int tricksterSwapDurationSeconds = 25;
		int puppetmasterControlDurationSeconds = 15;
		int puppetmasterControlCooldownSeconds = 45;
		boolean puppetmasterRandomTarget = false;
		boolean puppetmasterCanKillOwnBody = false;
		int pelicanEatCooldownSeconds = 20;
		int snitchTasksRequired = 3;
		int maxKillerAmount = 64;
		float c4BackOffsetX = 0.0F;
		float c4BackOffsetY = 0.24F;
		float c4BackOffsetZ = 0.28F;
		float c4BackRotationX = 0.0F;
		float c4BackRotationY = 0.0F;
		float c4BackRotationZ = 0.0F;
		float c4BackSlant = 0.0F;
		float c4BackScale = 0.42F;
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
