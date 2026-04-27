package dev.mapselect.role;

import dev.mapselect.MapSelect;
import dev.mapselect.config.RoleModifierTuningConfig;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class RoleModifierTuningBridge {
	private static final Random RANDOM = new Random();
	private static final Set<Identifier> rememberedRoleMax = new HashSet<>();
	private static final Set<Identifier> rememberedModifierMax = new HashSet<>();
	private static final Set<Identifier> originalRoleMaxPresent = new HashSet<>();
	private static final Set<Identifier> originalModifierMaxPresent = new HashSet<>();
	private static final Map<Identifier, Integer> originalRoleMax = new HashMap<>();
	private static final Map<Identifier, Integer> originalModifierMax = new HashMap<>();

	private RoleModifierTuningBridge() {}

	public static void applyConfiguredMaxima() {
		try {
			restoreManagedMaxima();
			applyExplicitMaxima();
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to apply role/modifier tuning maxima: {}", t.toString());
		}
	}

	public static void prepareForGame() {
		try {
			RoleModifierTuningConfig.load();
			restoreManagedMaxima();
			applyExplicitMaxima();
			rollChances();
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to prepare role/modifier chance tuning.", t);
		}
	}

	private static void applyExplicitMaxima() {
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.roleMaxEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null) putRoleMax(id, entry.getValue());
		}
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.modifierMaxEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null && !isFixedPairLovers(id)) putModifierMax(id, entry.getValue());
		}
	}

	private static void rollChances() {
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.roleChanceEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null && !passes(entry.getValue())) {
				putRoleMax(id, 0);
			}
		}
		for (Map.Entry<String, Integer> entry : RoleModifierTuningConfig.modifierChanceEntries().entrySet()) {
			Identifier id = parse(entry.getKey());
			if (id != null && !passes(entry.getValue())) {
				putModifierMax(id, 0);
			}
		}
	}

	private static boolean passes(int chance) {
		int clamped = Math.max(RoleModifierTuningConfig.CHANCE_MIN,
			Math.min(RoleModifierTuningConfig.CHANCE_MAX, chance));
		return clamped >= RoleModifierTuningConfig.CHANCE_MAX || RANDOM.nextInt(100) < clamped;
	}

	private static void putRoleMax(Identifier id, int max) {
		rememberRoleMax(id);
		Harpymodloader.ROLE_MAX.put(id, Math.max(RoleModifierTuningConfig.MAX_MIN,
			Math.min(RoleModifierTuningConfig.MAX_MAX, max)));
	}

	private static void putModifierMax(Identifier id, int max) {
		rememberModifierMax(id);
		Harpymodloader.MODIFIER_MAX.put(id, Math.max(RoleModifierTuningConfig.MAX_MIN,
			Math.min(RoleModifierTuningConfig.MAX_MAX, max)));
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

	private static boolean isFixedPairLovers(Identifier id) {
		return "stupid_express".equals(id.getNamespace()) && "lovers".equals(id.getPath());
	}
}
