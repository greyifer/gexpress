package dev.mapselect.skin;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerSkinComponent implements AutoSyncedComponent {
	public static final ComponentKey<PlayerSkinComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "player_skins"),
		PlayerSkinComponent.class
	);

	private final World world;
	private final Map<UUID, EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>>> unlockedByPlayer = new java.util.LinkedHashMap<>();
	private final Map<UUID, EnumMap<WeaponSkinType, WeaponSkin>> equippedByPlayer = new java.util.LinkedHashMap<>();
	private final Map<UUID, Map<String, Integer>> casesByPlayer = new java.util.LinkedHashMap<>();

	public PlayerSkinComponent(World world) {
		this.world = world;
	}

	public Set<WeaponSkin> unlocked(UUID playerId, WeaponSkinType type) {
		if (playerId == null || type == null) return Set.of(WeaponSkin.DEFAULT);
		LinkedHashSet<WeaponSkin> out = new LinkedHashSet<>();
		if (WeaponSkin.DEFAULT.supports(type)) out.add(WeaponSkin.DEFAULT);
		EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>> byType = unlockedByPlayer.get(playerId);
		if (byType != null) {
			Set<WeaponSkin> stored = byType.get(type);
			if (stored != null) {
				for (WeaponSkin skin : stored) {
					if (skin != null && skin.supports(type)) out.add(skin.logical(type));
				}
			}
		}
		return Collections.unmodifiableSet(out);
	}

	public boolean hasAnySkin(UUID playerId) {
		if (playerId == null) return false;
		EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>> unlocked = unlockedByPlayer.get(playerId);
		if (unlocked != null) {
			for (Set<WeaponSkin> skins : unlocked.values()) {
				for (WeaponSkin skin : skins) {
					if (skin != null && skin != WeaponSkin.DEFAULT) return true;
				}
			}
		}
		EnumMap<WeaponSkinType, WeaponSkin> equipped = equippedByPlayer.get(playerId);
		if (equipped != null) {
			for (WeaponSkin skin : equipped.values()) {
				if (skin != null && skin != WeaponSkin.DEFAULT) return true;
			}
		}
		return false;
	}

	public WeaponSkin equipped(UUID playerId, WeaponSkinType type) {
		if (playerId == null || type == null) return WeaponSkin.DEFAULT;
		EnumMap<WeaponSkinType, WeaponSkin> byType = equippedByPlayer.get(playerId);
		WeaponSkin skin = byType == null ? null : byType.get(type);
		skin = skin == null ? WeaponSkin.DEFAULT : skin.logical(type);
		return isUnlocked(playerId, type, skin) ? skin : WeaponSkin.DEFAULT;
	}

	public boolean hasEquipped(UUID playerId, WeaponSkinType type) {
		EnumMap<WeaponSkinType, WeaponSkin> byType = playerId == null ? null : equippedByPlayer.get(playerId);
		return byType != null && byType.containsKey(type);
	}

	public boolean isUnlocked(UUID playerId, WeaponSkinType type, WeaponSkin skin) {
		if (playerId == null || type == null || skin == null) return false;
		if (!skin.supports(type)) return false;
		skin = skin.logical(type);
		if (skin.unlockedByDefault()) return true;
		EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>> byType = unlockedByPlayer.get(playerId);
		Set<WeaponSkin> unlocked = byType == null ? null : byType.get(type);
		return unlocked != null && (unlocked.contains(skin)
			|| (type == WeaponSkinType.GUN && ((skin == WeaponSkin.GOLD && unlocked.contains(WeaponSkin.HOST))
				|| (skin == WeaponSkin.COLA && unlocked.contains(WeaponSkin.TRUSTED)))));
	}

	public boolean give(UUID playerId, WeaponSkinType type, WeaponSkin skin) {
		if (playerId == null || type == null || skin == null || skin == WeaponSkin.DEFAULT || !skin.supports(type)) return false;
		skin = skin.logical(type);
		if (skin == WeaponSkin.DEFAULT) return false;
		boolean changed = unlockedByPlayer
			.computeIfAbsent(playerId, id -> new EnumMap<>(WeaponSkinType.class))
			.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
			.add(skin);
		if (changed) KEY.sync(world);
		return changed;
	}

	public boolean remove(UUID playerId, WeaponSkinType type, WeaponSkin skin) {
		if (playerId == null || type == null || skin == null || skin == WeaponSkin.DEFAULT || !skin.supports(type)) return false;
		WeaponSkin logical = skin.logical(type);
		EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>> byType = unlockedByPlayer.get(playerId);
		Set<WeaponSkin> unlocked = byType == null ? null : byType.get(type);
		if (unlocked == null) return false;
		boolean changed = unlocked.remove(logical);
		if (type == WeaponSkinType.GUN && logical == WeaponSkin.GOLD) changed |= unlocked.remove(WeaponSkin.HOST);
		if (type == WeaponSkinType.GUN && logical == WeaponSkin.COLA) changed |= unlocked.remove(WeaponSkin.TRUSTED);
		if (!changed) return false;
		if (unlocked.isEmpty()) byType.remove(type);
		if (byType.isEmpty()) unlockedByPlayer.remove(playerId);
		if (equipped(playerId, type) == logical) equip(playerId, type, WeaponSkin.DEFAULT);
		KEY.sync(world);
		return true;
	}

	public boolean equip(UUID playerId, WeaponSkinType type, WeaponSkin skin) {
		if (playerId == null || type == null || skin == null || !isUnlocked(playerId, type, skin)) return false;
		skin = skin.logical(type);
		EnumMap<WeaponSkinType, WeaponSkin> byType = equippedByPlayer.computeIfAbsent(playerId,
			id -> new EnumMap<>(WeaponSkinType.class));
		WeaponSkin previous = byType.put(type, skin);
		if (previous == skin) return false;
		KEY.sync(world);
		return true;
	}

	public Map<String, Integer> caseCounts(UUID playerId) {
		if (playerId == null) return Map.of();
		Map<String, Integer> cases = casesByPlayer.get(playerId);
		return cases == null ? Map.of() : Collections.unmodifiableMap(cases);
	}

	public int caseCount(UUID playerId, String caseId) {
		if (playerId == null || caseId == null) return 0;
		Map<String, Integer> cases = casesByPlayer.get(playerId);
		return cases == null ? 0 : Math.max(0, cases.getOrDefault(normalizeCaseId(caseId), 0));
	}

	public boolean giveCase(UUID playerId, String caseId, int amount) {
		if (playerId == null || caseId == null || amount <= 0) return false;
		return setCaseCount(playerId, caseId, caseCount(playerId, caseId) + amount);
	}

	public boolean removeCase(UUID playerId, String caseId, int amount) {
		if (playerId == null || caseId == null || amount <= 0) return false;
		return setCaseCount(playerId, caseId, Math.max(0, caseCount(playerId, caseId) - amount));
	}

	public boolean setCaseCount(UUID playerId, String caseId, int amount) {
		if (playerId == null || caseId == null) return false;
		String cleaned = normalizeCaseId(caseId);
		if (cleaned.isEmpty()) return false;
		Map<String, Integer> cases = casesByPlayer.computeIfAbsent(playerId, id -> new java.util.LinkedHashMap<>());
		int next = Math.max(0, amount);
		Integer previous = next <= 0 ? cases.remove(cleaned) : cases.put(cleaned, next);
		if (cases.isEmpty()) casesByPlayer.remove(playerId);
		boolean changed = previous == null ? next > 0 : previous != next;
		if (changed) KEY.sync(world);
		return changed;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		unlockedByPlayer.clear();
		equippedByPlayer.clear();
		casesByPlayer.clear();
		NbtList unlocked = tag.getList("unlocked", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < unlocked.size(); i++) {
			NbtCompound entry = unlocked.getCompound(i);
			UUID playerId = parseUuid(entry.getString("player"));
			WeaponSkinType type = WeaponSkinType.byId(entry.getString("type"));
			WeaponSkin skin = WeaponSkin.byId(entry.getString("skin"));
			if (playerId != null && type != null && skin != null && skin != WeaponSkin.DEFAULT && skin.supports(type)) {
				unlockedByPlayer.computeIfAbsent(playerId, id -> new EnumMap<>(WeaponSkinType.class))
					.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
					.add(skin.logical(type));
			}
		}
		NbtList equipped = tag.getList("equipped", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < equipped.size(); i++) {
			NbtCompound entry = equipped.getCompound(i);
			UUID playerId = parseUuid(entry.getString("player"));
			WeaponSkinType type = WeaponSkinType.byId(entry.getString("type"));
			WeaponSkin skin = WeaponSkin.byId(entry.getString("skin"));
			if (playerId != null && type != null && isUnlocked(playerId, type, skin)) {
				equippedByPlayer.computeIfAbsent(playerId, id -> new EnumMap<>(WeaponSkinType.class))
					.put(type, skin.logical(type));
			}
		}
		NbtList cases = tag.getList("cases", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cases.size(); i++) {
			NbtCompound entry = cases.getCompound(i);
			UUID playerId = parseUuid(entry.getString("player"));
			String caseId = normalizeCaseId(entry.getString("case"));
			int count = Math.max(0, entry.getInt("count"));
			if (playerId != null && !caseId.isEmpty() && count > 0) {
				casesByPlayer.computeIfAbsent(playerId, id -> new java.util.LinkedHashMap<>()).put(caseId, count);
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList unlocked = new NbtList();
		for (Map.Entry<UUID, EnumMap<WeaponSkinType, LinkedHashSet<WeaponSkin>>> playerEntry : unlockedByPlayer.entrySet()) {
			for (Map.Entry<WeaponSkinType, LinkedHashSet<WeaponSkin>> typeEntry : playerEntry.getValue().entrySet()) {
				for (WeaponSkin skin : typeEntry.getValue()) {
					NbtCompound out = new NbtCompound();
					out.putString("player", playerEntry.getKey().toString());
					out.putString("type", typeEntry.getKey().id());
					out.putString("skin", skin.id());
					unlocked.add(out);
				}
			}
		}
		tag.put("unlocked", unlocked);

		NbtList equipped = new NbtList();
		for (Map.Entry<UUID, EnumMap<WeaponSkinType, WeaponSkin>> playerEntry : equippedByPlayer.entrySet()) {
			for (Map.Entry<WeaponSkinType, WeaponSkin> typeEntry : playerEntry.getValue().entrySet()) {
				NbtCompound out = new NbtCompound();
				out.putString("player", playerEntry.getKey().toString());
				out.putString("type", typeEntry.getKey().id());
				out.putString("skin", typeEntry.getValue().id());
				equipped.add(out);
			}
		}
		tag.put("equipped", equipped);

		NbtList cases = new NbtList();
		for (Map.Entry<UUID, Map<String, Integer>> playerEntry : casesByPlayer.entrySet()) {
			for (Map.Entry<String, Integer> caseEntry : playerEntry.getValue().entrySet()) {
				int count = Math.max(0, caseEntry.getValue());
				if (count <= 0) continue;
				NbtCompound out = new NbtCompound();
				out.putString("player", playerEntry.getKey().toString());
				out.putString("case", caseEntry.getKey());
				out.putInt("count", count);
				cases.add(out);
			}
		}
		tag.put("cases", cases);
	}

	private static UUID parseUuid(String raw) {
		try {
			return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String normalizeCaseId(String raw) {
		if (raw == null) return "";
		return raw.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
	}
}
