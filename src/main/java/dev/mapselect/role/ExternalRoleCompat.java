package dev.mapselect.role;

import net.minecraft.util.Identifier;

public final class ExternalRoleCompat {
	private static final Identifier NOELLES_VOODOO_DEATH = Identifier.of("noellesroles", "voodoo");

	private ExternalRoleCompat() {}

	public static boolean isVoodooDeath(Identifier reason) {
		return NOELLES_VOODOO_DEATH.equals(reason);
	}
}
