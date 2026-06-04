package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public final class RoleTeams {
	private RoleTeams() {}

	public static boolean sameTeam(GameWorldComponent game, PlayerEntity first, PlayerEntity second) {
		String a = teamKey(game, first);
		String b = teamKey(game, second);
		return a != null && a.equals(b);
	}

	public static boolean compatibleLovers(GameWorldComponent game, PlayerEntity first, PlayerEntity second,
			boolean allowMixedSides) {
		if (allowMixedSides) return true;
		String a = sideKey(game, first);
		String b = sideKey(game, second);
		return a != null && a.equals(b);
	}

	public static String sideKey(GameWorldComponent game, PlayerEntity player) {
		Role role = role(game, player);
		if (role == null) return null;
		Identifier id = role.identifier();
		if (isMafia(id)) return "mafia";
		if (isCovenant(id)) return "covenant";
		if (role.isInnocent() && (game == null || !game.canUseKillerFeatures(player))) return "civilian";
		if (role.canUseKiller() || (game != null && game.canUseKillerFeatures(player))) return "killer";
		return "neutral";
	}

	public static String teamKey(GameWorldComponent game, PlayerEntity player) {
		Role role = role(game, player);
		if (role == null) return null;
		Identifier id = role.identifier();
		if (isMafia(id)) return "mafia";
		if (isCovenant(id)) return "covenant";
		if (role.isInnocent() && (game == null || !game.canUseKillerFeatures(player))) return "civilian";
		if (role.canUseKiller() || (game != null && game.canUseKillerFeatures(player))) return "killer";
		return "neutral:" + (id == null ? "unknown" : id.toString());
	}

	private static Role role(GameWorldComponent game, PlayerEntity player) {
		if (game == null || player == null) return null;
		return game.getRole(player);
	}

	private static boolean isMafia(Identifier id) {
		return MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id)
			|| MapSelectRoles.PICKPOCKET_ID.equals(id)
			|| MapSelectRoles.BURGLAR_ID.equals(id);
	}

	private static boolean isCovenant(Identifier id) {
		return MapSelectRoles.DRACULA_ID.equals(id) || MapSelectRoles.VAMPIRE_ID.equals(id);
	}
}
