package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import dev.mapselect.modifier.EodDistribution;
import dev.mapselect.modifier.NightVisionManager;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;

/**
 * Registers G'Express-side Harpy Mod Loader modifiers alongside WatheExtended's own set
 * (Introverted, Taxed, Adaptive). Registration order / signature mirrors
 * {@code WatheExtendedModifiers.initialize}: constructor is
 * {@code (Identifier id, int color, ArrayList cannotApplyTo, ArrayList canOnlyApplyTo,
 * boolean killerOnly, boolean civilianOnly)}.
 *
 * <ul>
 *   <li><b>EOD Specialist</b> - civilian-only. Bearer is handed a pair of Pliers at game
 *       start (see {@link EodDistribution}) and can attempt to defuse C4 attached to any
 *       other player. Success chance is {@code 100 - wrongWirePercent}; a failure instantly
 *       detonates the charge. The counter-play to the Bomb Specialist role.</li>
 *   <li><b>Short-sighted</b> - applies to any role. On the client, entities beyond the
 *       configured range are not rendered, hiding players, dropped items, and bodies (see
 *       {@link dev.mapselect.mixin.client.ShortSightedEntityRenderMixin}).</li>
 *   <li><b>Night Vision</b> - applies to any role. The bearer receives a hidden Night Vision
 *       effect while the modifier is active (see {@link NightVisionManager}).</li>
 * </ul>
 */
public final class MapSelectModifiers {
	public static final Identifier EOD_SPECIALIST_ID  = Identifier.of(MapSelect.MOD_ID, "eod_specialist");
	public static final Identifier SHORT_SIGHTED_ID   = Identifier.of(MapSelect.MOD_ID, "short_sighted");
	public static final Identifier NIGHT_VISION_ID    = Identifier.of(MapSelect.MOD_ID, "night_vision");
	public static final Identifier HUNGRY_ID          = Identifier.of(MapSelect.MOD_ID, "hungry");
	public static final Identifier THIRSTY_ID         = Identifier.of(MapSelect.MOD_ID, "thirsty");
	public static final Identifier MUTED_ID           = Identifier.of(MapSelect.MOD_ID, "muted");
	public static final Identifier PARANOID_ID        = Identifier.of(MapSelect.MOD_ID, "paranoid");
	public static final Identifier SQUEAKER_ID        = Identifier.of(MapSelect.MOD_ID, "squeaker");

	public static Modifier EOD_SPECIALIST;
	public static Modifier SHORT_SIGHTED;
	public static Modifier NIGHT_VISION;
	public static Modifier HUNGRY;
	public static Modifier THIRSTY;
	public static Modifier MUTED;
	public static Modifier PARANOID;
	public static Modifier SQUEAKER;

	public static void register() {
		// Cool steely blue - reads as "tools / defusal" vs the Bomb Specialist's hot TNT-orange.
		EOD_SPECIALIST = HMLModifiers.registerModifier(new Modifier(
			EOD_SPECIALIST_ID,
			0x4A90C2,
			new ArrayList<>(),
			new ArrayList<>(),
			false,   // killerOnly
			true     // civilianOnly - only innocents defuse bombs
		));

		// Muted brown-gray - evocative of tunnel-vision / narrow sight.
		SHORT_SIGHTED = HMLModifiers.registerModifier(new Modifier(
			SHORT_SIGHTED_ID,
			0x7A6A5A,
			new ArrayList<>(),
			new ArrayList<>(),
			false,   // killerOnly
			false    // civilianOnly - anyone can be stuck with bad eyesight
		));

		// Luminous green - a clean read for "can see in the dark".
		NIGHT_VISION = HMLModifiers.registerModifier(new Modifier(
			NIGHT_VISION_ID,
			0x78F06A,
			new ArrayList<>(),
			new ArrayList<>(),
			false,   // killerOnly
			false    // civilianOnly - any role can see better in the dark
		));

		HUNGRY = HMLModifiers.registerModifier(new Modifier(
			HUNGRY_ID,
			0xD18A2F,
			new ArrayList<>(),
			new ArrayList<>(),
			false,
			false
		));

		THIRSTY = HMLModifiers.registerModifier(new Modifier(
			THIRSTY_ID,
			0x4AA8E8,
			new ArrayList<>(),
			new ArrayList<>(),
			false,
			false
		));

		MUTED = HMLModifiers.registerModifier(new Modifier(
			MUTED_ID,
			0x8E8E8E,
			new ArrayList<>(),
			new ArrayList<>(),
			false,
			false
		));

		PARANOID = HMLModifiers.registerModifier(new Modifier(
			PARANOID_ID,
			0xA44CE2,
			new ArrayList<>(),
			new ArrayList<>(),
			false,
			false
		));

		SQUEAKER = HMLModifiers.registerModifier(new Modifier(
			SQUEAKER_ID,
			0xF2D94C,
			new ArrayList<>(),
			new ArrayList<>(),
			false,
			false
		));
	}

	private MapSelectModifiers() {}
}
