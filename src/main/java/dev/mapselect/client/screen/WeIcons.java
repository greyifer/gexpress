package dev.mapselect.client.screen;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeIcons {
	private WeIcons() {}

	private static final Pattern ICON_TOKEN = Pattern.compile("\\{icon:([a-zA-Z_]+)\\}");

	public static final String COIN = "\uE211";

	private static final Map<String, String> NAME_TO_CHAR = Map.<String, String>ofEntries(
		Map.entry("knife", "\uE100"),
		Map.entry("revolver", "\uE101"),
		Map.entry("lighter", "\uE102"),
		Map.entry("lockpick", "\uE103"),
		Map.entry("master_key", "\uE104"),
		Map.entry("role_mine", "\uE105"),
		Map.entry("tape", "\uE106"),
		Map.entry("jerry_can", "\uE107"),
		Map.entry("defense_vial", "\uE108"),
		Map.entry("delusion_vial", "\uE109"),
		Map.entry("psycho_mode", "\uE10A"),
		Map.entry("bat", "\uE10B"),
		Map.entry("blowgun", "\uE10C"),
		Map.entry("body_bag", "\uE10D"),
		Map.entry("crowbar", "\uE10E"),
		Map.entry("dream_imprint", "\uE10F"),
		Map.entry("c4", "\uE215"),
		Map.entry("firecracker", "\uE200"),
		Map.entry("grenade", "\uE201"),
		Map.entry("hunting_knife", "\uE202"),
		Map.entry("knockout_drug", "\uE203"),
		Map.entry("medical_kit", "\uE204"),
		Map.entry("note", "\uE205"),
		Map.entry("pan", "\uE206"),
		Map.entry("pill", "\uE207"),
		Map.entry("poison_injector", "\uE208"),
		Map.entry("poison_vial", "\uE209"),
		Map.entry("sulfuric_acid_barrel", "\uE210"),
		Map.entry("coin", "\uE211"),
		Map.entry("killer", "\uE212"),
		Map.entry("civilian", "\uE213"),
		Map.entry("neutral", "\uE214"),
		Map.entry("scorpion", "\uE217"),
		Map.entry("blackout", "\uE218"),
		Map.entry("phone", "\uE219"),
		Map.entry("icon_ability_cooldown_refresh", "\uE21A"),
		Map.entry("icon_potion_effect_refresh", "\uE21B"),
		Map.entry("icon_weapon_cooldown_refresh", "\uE21C"),
		Map.entry("adrenaline", "\uE300"),
		Map.entry("athletic", "\uE301"),
		Map.entry("autopsy", "\uE302"),
		Map.entry("avarice", "\uE303"),
		Map.entry("cannibal", "\uE304"),
		Map.entry("clean", "\uE305"),
		Map.entry("imprint", "\uE306"),
		Map.entry("instinct", "\uE307"),
		Map.entry("invisibility", "\uE308"),
		Map.entry("judgement", "\uE309"),
		Map.entry("last_words", "\uE310"),
		Map.entry("morph", "\uE311"),
		Map.entry("nemesis", "\uE312"),
		Map.entry("psychosis", "\uE313"),
		Map.entry("question", "\uE314"),
		Map.entry("recall", "\uE315"),
		Map.entry("revive", "\uE316"),
		Map.entry("sense", "\uE317"),
		Map.entry("starstruck", "\uE318"),
		Map.entry("swap", "\uE319"),
		Map.entry("time", "\uE321"),
		Map.entry("undercover", "\uE322"),
		Map.entry("voodoo", "\uE323"),
		Map.entry("enabled", "\uE400"),
		Map.entry("disabled", "\uE401")
	);

	public static String charFor(String name) {
		return NAME_TO_CHAR.get(name);
	}

	public static String replaceTokens(String raw) {
		if (raw == null || raw.isEmpty()) return raw;
		Matcher m = ICON_TOKEN.matcher(raw);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String replacement = NAME_TO_CHAR.getOrDefault(m.group(1), "");
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}
}
