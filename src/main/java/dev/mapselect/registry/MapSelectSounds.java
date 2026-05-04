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
	public static final Identifier JEVIL_LAUGH_ID = Identifier.of(MapSelect.MOD_ID, "jevil_laugh");
	public static final SoundEvent JEVIL_LAUGH = SoundEvent.of(JEVIL_LAUGH_ID);
	public static final Identifier MAFIA_ID = Identifier.of(MapSelect.MOD_ID, "mafia");
	public static final SoundEvent MAFIA = SoundEvent.of(MAFIA_ID);

	public static void register() {
		Registry.register(Registries.SOUND_EVENT, C4_BEEP_ID, C4_BEEP);
		Registry.register(Registries.SOUND_EVENT, GREYIFER_PLUSH_HONK_ID, GREYIFER_PLUSH_HONK);
		Registry.register(Registries.SOUND_EVENT, JEVIL_LAUGH_ID, JEVIL_LAUGH);
		Registry.register(Registries.SOUND_EVENT, MAFIA_ID, MAFIA);
	}
}
