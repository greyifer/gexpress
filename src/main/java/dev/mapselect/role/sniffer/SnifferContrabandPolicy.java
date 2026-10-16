package dev.mapselect.role.sniffer;

import net.minecraft.util.Identifier;

import java.util.Set;

/** Contraband carried by Wathe killers and weapon-bearing neutral add-on roles. */
public final class SnifferContrabandPolicy {
	private static final Set<String> EXACT_IDS = Set.of(
		"wathe:knife",
		"wathe:revolver",
		"wathe:derringer",
		"wathe:grenade",
		"wathe:thrown_grenade",
		"wathe:poison_vial",
		"wathe:scorpion",
		"wathe:firecracker",
		"wathe:lockpick",
		"wathe:crowbar",
		"wathe:body_bag",
		"wathe:blackout",
		"wathe:psycho_mode",
		"gexpress:c4",
		"gexpress:c4_detonator",
		"gexpress:bullet",
		"noellesroles:delusion_vial",
		"noellesroles:role_mine",
		"kinswathe:blowgun",
		"kinswathe:hunting_knife",
		"kinswathe:knockout_drug",
		"kinswathe:poison_injector",
		"kinswathe:sulfuric_acid_barrel",
		"starexpress:tape",
		"stupid_express:jerry_can",
		"stupid_express:lighter"
	);
	private static final Set<String> WEAPON_TERMS = Set.of(
		"knife", "revolver", "derringer", "grenade", "blowgun", "poison_injector", "role_mine", "c4"
	);

	private SnifferContrabandPolicy() {}

	public static boolean isContraband(Identifier id) {
		if (id == null || "minecraft".equals(id.getNamespace())) return false;
		String value = id.toString();
		if (EXACT_IDS.contains(value)) return true;
		String path = id.getPath();
		if (path.startsWith("fake_") || path.startsWith("counterfeit_") || path.startsWith("icon_") || path.endsWith("_empty")) return false;
		for (String term : WEAPON_TERMS) {
			if (path.contains(term)) return true;
		}
		return false;
	}
}
