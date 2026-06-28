package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class RoleModifierTuningBridge {
	private static final Set<Identifier> rememberedRoleMax = new HashSet<>();
	private static final Set<Identifier> originalRoleMaxPresent = new HashSet<>();
	private static final Map<Identifier, Integer> originalRoleMax = new HashMap<>();
	private static final Set<Identifier> rememberedModifierMax = new HashSet<>();
	private static final Set<Identifier> originalModifierMaxPresent = new HashSet<>();
	private static final Map<Identifier, Integer> originalModifierMax = new HashMap<>();

	private RoleModifierTuningBridge() {}

	public static void applyConfiguredMaxima() {
		try {
			restoreManagedMaxima();
			applyDefaultMaxima();
			applyExplicitMaxima();
			disableRuntimeOnlyModifiers();
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to apply role/modifier tuning maxima: {}", t.toString());
		}
	}

	public static void prepareForGame() {
		prepareForGame(-1);
	}

	public static void prepareForGame(int playerCount) {
		try {
			RoleModifierTuningConfig.load();
			restoreManagedMaxima();
			applyDefaultMaxima();
			applyExplicitMaxima();
			applyRoleChanceGates();
			disableRuntimeOnlyModifiers();
			applySpecialRoleGates(playerCount);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to prepare role count tuning.", t);
		}
	}

	private static void applyDefaultMaxima() {
		for (Role role : WatheRoles.ROLES) {
			if (!isModdedAssignableRole(role)) continue;
			putRoleMax(role.identifier(), isStupidExpressPairRole(role.identifier())
				? fixedPairRoleMax(RoleModifierTuningConfig.DEFAULT_MAX)
				: RoleModifierTuningConfig.DEFAULT_MAX);
		}
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			if (modifier == null || modifier.identifier() == null) continue;
			putModifierMax(modifier.identifier(), isStupidExpressPairModifier(modifier.identifier())
				? fixedPairModifierRolls(RoleModifierTuningConfig.DEFAULT_MAX)
				: RoleModifierTuningConfig.DEFAULT_MAX);
		}
	}

	private static void applyExplicitMaxima() {
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.roleMaxEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null) putRoleMax(id, isStupidExpressPairRole(id)
				? fixedPairRoleMax(entry.getValue())
				: entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.modifierMaxEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null) putModifierMax(id, isStupidExpressPairModifier(id)
				? fixedPairModifierRolls(entry.getValue())
				: entry.getValue());
		}
	}

	private static void applyRoleChanceGates() {
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.roleChanceEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null && !passes(entry.getValue())) putRoleMax(id, 0);
		}
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.modifierChanceEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null && !passes(entry.getValue())) putModifierMax(id, 0);
		}
	}

	private static void putRoleMax(Identifier id, int max) {
		if (id == null) return;
		rememberRoleMax(id);
		Harpymodloader.ROLE_MAX.put(id, Math.max(RoleModifierTuningConfig.MAX_MIN,
			Math.min(RoleModifierTuningConfig.MAX_MAX, max)));
	}

	private static void putModifierMax(Identifier id, int max) {
		if (id == null) return;
		rememberModifierMax(id);
		Harpymodloader.MODIFIER_MAX.put(id, Math.max(RoleModifierTuningConfig.MAX_MIN,
			Math.min(RoleModifierTuningConfig.MAX_MAX, max)));
	}

	private static void applySpecialRoleGates(int playerCount) {
		boolean belowMinimum = playerCount >= 0 && playerCount < GexpressConfig.getMafiaMinimumPlayers();
		GexpressConfig.SpecialRoleOccurrence occurrence = GexpressConfig.getSpecialRoleOccurrence();

		if (belowMinimum || !occurrence.mafiaEnabled()) {
			disableRoles(MapSelectRoles.GODFATHER_ID, MapSelectRoles.MAFIOSO_ID, MapSelectRoles.JANITOR_ID,
				MapSelectRoles.PICKPOCKET_ID, MapSelectRoles.BURGLAR_ID);
		} else {
			disableRoles(MapSelectRoles.MAFIOSO_ID, MapSelectRoles.JANITOR_ID, MapSelectRoles.PICKPOCKET_ID,
				MapSelectRoles.BURGLAR_ID);
		}

		if (belowMinimum || !occurrence.covenantEnabled()) {
			disableRoles(MapSelectRoles.DRACULA_ID, MapSelectRoles.VAMPIRE_ID);
		} else {
			disableRoles(MapSelectRoles.VAMPIRE_ID);
		}
	}

	private static boolean isModdedAssignableRole(Role role) {
		return role != null
			&& role.identifier() != null
			&& !Harpymodloader.VANNILA_ROLES.contains(role)
			&& !Harpymodloader.NON_MURDER_ROLES.contains(role)
			&& !"vulture".equals(role.identifier().getPath());
	}

	private static void disableRoles(Identifier... ids) {
		for (Identifier id : ids) putRoleMax(id, 0);
	}

	private static void disableRuntimeOnlyModifiers() {
		putModifierMax(MapSelectModifiers.LOVERS_ID, 0);
	}

	private static void restoreManagedMaxima() {
		for (Identifier id : rememberedRoleMax) {
			if (originalRoleMaxPresent.contains(id)) {
				Harpymodloader.ROLE_MAX.put(id, originalRoleMax.get(id));
			} else {
				Harpymodloader.ROLE_MAX.remove(id);
			}
		}
		for (Identifier id : rememberedModifierMax) {
			if (originalModifierMaxPresent.contains(id)) {
				Harpymodloader.MODIFIER_MAX.put(id, originalModifierMax.get(id));
			} else {
				Harpymodloader.MODIFIER_MAX.remove(id);
			}
		}
	}

	private static void rememberRoleMax(Identifier id) {
		if (!rememberedRoleMax.add(id)) return;
		if (Harpymodloader.ROLE_MAX.containsKey(id)) {
			originalRoleMaxPresent.add(id);
			originalRoleMax.put(id, Harpymodloader.ROLE_MAX.get(id));
		}
	}

	private static void rememberModifierMax(Identifier id) {
		if (!rememberedModifierMax.add(id)) return;
		if (Harpymodloader.MODIFIER_MAX.containsKey(id)) {
			originalModifierMaxPresent.add(id);
			originalModifierMax.put(id, Harpymodloader.MODIFIER_MAX.get(id));
		}
	}

	private static Identifier parse(String raw) {
		if (raw == null) return null;
		return Identifier.tryParse(raw);
	}

	private static boolean passes(int chance) {
		int clamped = Math.max(RoleModifierTuningConfig.CHANCE_MIN,
			Math.min(RoleModifierTuningConfig.CHANCE_MAX, chance));
		return clamped >= RoleModifierTuningConfig.CHANCE_MAX
			|| (clamped > 0 && ThreadLocalRandom.current().nextInt(100) < clamped);
	}

	public static boolean isStupidExpressPairRole(Identifier id) {
		if (id == null) return false;
		return hasStupidExpressNamespace(id) && id.getPath().toLowerCase(java.util.Locale.ROOT).contains("initiate");
	}

	public static boolean isStupidExpressPairModifier(Identifier id) {
		if (id == null) return false;
		String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
		return hasStupidExpressNamespace(id) && (path.contains("lover") || path.contains("initiate"));
	}

	private static boolean hasStupidExpressNamespace(Identifier id) {
		return id != null && id.getNamespace().toLowerCase(java.util.Locale.ROOT).contains("stupid");
	}

	private static int fixedPairRoleMax(int configured) {
		return configured <= 0 ? 0 : 2;
	}

	private static int fixedPairModifierRolls(int configured) {
		return configured <= 0 ? 0 : 1;
	}
}
