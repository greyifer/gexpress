package dev.mapselect.registry;

import dev.mapselect.MapSelect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class MapSelectSounds {
	private MapSelectSounds() {}

	public static final Identifier C4_BEEP_ID = Identifier.of(MapSelect.MOD_ID, "c4_beep");
	public static final SoundEvent C4_BEEP = SoundEvent.of(C4_BEEP_ID);
	public static final Identifier GREYIFER_PLUSH_HONK_ID = Identifier.of(MapSelect.MOD_ID, "block.greyifer_plush.honk");
	public static final SoundEvent GREYIFER_PLUSH_HONK = SoundEvent.of(GREYIFER_PLUSH_HONK_ID);
	public static final Identifier IWY_PLUSH_HONK_ID = Identifier.of(MapSelect.MOD_ID, "block.iwy_plush.honk");
	public static final SoundEvent IWY_PLUSH_HONK = SoundEvent.of(IWY_PLUSH_HONK_ID);
	public static final Identifier LUX_PLUSH_HONK_ID = Identifier.of(MapSelect.MOD_ID, "block.lux_plush.honk");
	public static final SoundEvent LUX_PLUSH_HONK = SoundEvent.of(LUX_PLUSH_HONK_ID);
	public static final Identifier WTFJIMJIM_PLUSH_HONK_ID = Identifier.of(MapSelect.MOD_ID, "block.wtfjimjim_plush.honk");
	public static final SoundEvent WTFJIMJIM_PLUSH_HONK = SoundEvent.of(WTFJIMJIM_PLUSH_HONK_ID);
	public static final Identifier PIZZA_PLUSH_HONK_ID = Identifier.of(MapSelect.MOD_ID, "block.pizza_plush.honk");
	public static final SoundEvent PIZZA_PLUSH_HONK = SoundEvent.of(PIZZA_PLUSH_HONK_ID);
	public static final Identifier JEVIL_LAUGH_ID = Identifier.of(MapSelect.MOD_ID, "jevil_laugh");
	public static final SoundEvent JEVIL_LAUGH = SoundEvent.of(JEVIL_LAUGH_ID);
	public static final Identifier MAFIA_ID = Identifier.of(MapSelect.MOD_ID, "mafia");
	public static final SoundEvent MAFIA = SoundEvent.of(MAFIA_ID);
	public static final Identifier CUPID_ID = Identifier.of(MapSelect.MOD_ID, "cupid");
	public static final SoundEvent CUPID = SoundEvent.of(CUPID_ID);
	public static final Identifier MUSIC_DISC_DERAILED_ID = Identifier.of(MapSelect.MOD_ID, "music_disc.derailed");
	public static final SoundEvent MUSIC_DISC_DERAILED = SoundEvent.of(MUSIC_DISC_DERAILED_ID);
	public static final Identifier MUSIC_DISC_AERISTHEME_ID = Identifier.of(MapSelect.MOD_ID, "music_disc.aeristheme");
	public static final SoundEvent MUSIC_DISC_AERISTHEME = SoundEvent.of(MUSIC_DISC_AERISTHEME_ID);
	public static final Identifier AERISTHEME_LOOP_ID = Identifier.of(MapSelect.MOD_ID, "aeristheme_loop");
	public static final SoundEvent AERISTHEME_LOOP = SoundEvent.of(AERISTHEME_LOOP_ID);

	public static void register() {
		Registry.register(Registries.SOUND_EVENT, C4_BEEP_ID, C4_BEEP);
		Registry.register(Registries.SOUND_EVENT, GREYIFER_PLUSH_HONK_ID, GREYIFER_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, IWY_PLUSH_HONK_ID, IWY_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, LUX_PLUSH_HONK_ID, LUX_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, WTFJIMJIM_PLUSH_HONK_ID, WTFJIMJIM_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, PIZZA_PLUSH_HONK_ID, PIZZA_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, JEVIL_LAUGH_ID, JEVIL_LAUGH);
		Registry.register(Registries.SOUND_EVENT, MAFIA_ID, MAFIA);
		Registry.register(Registries.SOUND_EVENT, CUPID_ID, CUPID);
		Registry.register(Registries.SOUND_EVENT, MUSIC_DISC_DERAILED_ID, MUSIC_DISC_DERAILED);
		Registry.register(Registries.SOUND_EVENT, MUSIC_DISC_AERISTHEME_ID, MUSIC_DISC_AERISTHEME);
		Registry.register(Registries.SOUND_EVENT, AERISTHEME_LOOP_ID, AERISTHEME_LOOP);
	}
}
