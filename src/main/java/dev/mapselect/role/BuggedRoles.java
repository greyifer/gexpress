package dev.mapselect.role;

import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.util.Identifier;

import java.util.Set;

public final class BuggedRoles {
	private static final Set<Identifier> IDS = Set.of(
		MapSelectRoles.PUPPETMASTER_ID
	);

	private BuggedRoles() {}

	public static boolean isBugged(Identifier id) {
		return id != null && IDS.contains(id);
	}

	public static boolean isBugged(String rawId) {
		if (rawId == null || rawId.isBlank()) return false;
		Identifier id = Identifier.tryParse(rawId);
		if (id != null && isBugged(id)) return true;
		String path = rawId;
		int split = rawId.indexOf(':');
		if (split >= 0 && split + 1 < rawId.length()) path = rawId.substring(split + 1);
		for (Identifier bugged : IDS) {
			if (bugged.getPath().equals(path)) return true;
		}
		return false;
	}

	public static Set<Identifier> ids() {
		return IDS;
	}
}
