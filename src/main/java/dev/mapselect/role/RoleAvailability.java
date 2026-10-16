package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.List;

/**
 * Central server-side check for roles that administrators have disabled through HML.
 * Assignment paths outside HML's initial role roll must use this check themselves.
 */
public final class RoleAvailability {
	private RoleAvailability() {}

	public static boolean isEnabled(Role role) {
		return role != null && isEnabled(role.identifier());
	}

	public static boolean isEnabled(Identifier roleId) {
		if (roleId == null) return false;
		List<String> disabled = HarpyModLoaderConfig.HANDLER.instance().disabled;
		Integer maximum = Harpymodloader.ROLE_MAX.get(roleId);
		return isConfiguredEnabled(roleId.toString(), disabled, maximum);
	}

	/** Mafia members are recruited by Godfather after the draft, never drafted directly. */
	public static boolean isDraftEnabled(Role role) {
		return isEnabled(role) && isDraftSelectable(role.identifier().toString());
	}

	static boolean isDraftSelectable(String roleId) {
		if (roleId == null || roleId.isBlank()) return false;
		return switch (roleId) {
			case "gexpress:mafioso", "gexpress:janitor", "gexpress:pickpocket", "gexpress:burglar" -> false;
			default -> true;
		};
	}

	/**
	 * Checks whether a role may be created by a mid-round mechanic. Runtime-only roles can
	 * intentionally have a zero initial-spawn maximum, so that maximum must not disable the
	 * mechanic that creates them.
	 */
	public static boolean isRuntimeEnabled(Role role) {
		return role != null && isRuntimeEnabled(role.identifier());
	}

	public static boolean isRuntimeEnabled(Identifier roleId) {
		if (roleId == null) return false;
		return isConfiguredRuntimeEnabled(
			roleId.toString(), HarpyModLoaderConfig.HANDLER.instance().disabled);
	}

	static boolean isConfiguredEnabled(String roleId, List<String> disabled, Integer maximum) {
		if (!isConfiguredRuntimeEnabled(roleId, disabled)) return false;
		return maximum == null || maximum > 0;
	}

	static boolean isConfiguredRuntimeEnabled(String roleId, List<String> disabled) {
		if (roleId == null || roleId.isBlank()) return false;
		return disabled == null || !disabled.contains(roleId);
	}
}
