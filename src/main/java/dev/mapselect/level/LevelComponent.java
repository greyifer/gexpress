package dev.mapselect.level;

import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LevelComponent implements AutoSyncedComponent {
	public static final ComponentKey<LevelComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "levels"),
		LevelComponent.class
	);

	private final World world;
	private final Map<UUID, Integer> xpByPlayer = new LinkedHashMap<>();
	private final Map<UUID, Set<Integer>> claimedRewardsByPlayer = new LinkedHashMap<>();

	public LevelComponent(World world) {
		this.world = world;
	}

	public Map<UUID, Integer> entries() {
		return Collections.unmodifiableMap(xpByPlayer);
	}

	public int xp(UUID playerId) {
		return playerId == null ? 0 : Math.max(0, xpByPlayer.getOrDefault(playerId, 0));
	}

	public int level(UUID playerId) {
		return levelForXp(xp(playerId));
	}

	public int xpIntoLevel(UUID playerId) {
		int remaining = xp(playerId);
		int level = 1;
		while (remaining >= xpNeededForLevel(level)) {
			remaining -= xpNeededForLevel(level);
			level++;
		}
		return remaining;
	}

	public int xpNeededForNextLevel(UUID playerId) {
		return xpNeededForLevel(level(playerId));
	}

	public boolean hasClaimedReward(UUID playerId, int level) {
		if (playerId == null || level <= 0) return false;
		Set<Integer> claimed = claimedRewardsByPlayer.get(playerId);
		return claimed != null && claimed.contains(level);
	}

	public Set<Integer> claimedRewards(UUID playerId) {
		Set<Integer> claimed = playerId == null ? null : claimedRewardsByPlayer.get(playerId);
		return claimed == null ? Set.of() : Collections.unmodifiableSet(claimed);
	}

	public boolean markRewardClaimed(UUID playerId, int level) {
		if (playerId == null || level <= 0 || hasClaimedReward(playerId, level)) return false;
		claimedRewardsByPlayer.computeIfAbsent(playerId, id -> new HashSet<>()).add(level);
		KEY.sync(world);
		return true;
	}

	public boolean addXp(UUID playerId, int amount) {
		if (playerId == null || amount <= 0) return false;
		int next = Math.max(0, xp(playerId) + amount);
		return setXp(playerId, next);
	}

	public boolean setXp(UUID playerId, int xp) {
		if (playerId == null) return false;
		int next = Math.max(0, xp);
		xpByPlayer.put(playerId, next);
		KEY.sync(world);
		return true;
	}

	public boolean setLevel(UUID playerId, int level) {
		return setXp(playerId, totalXpForLevel(level));
	}

	public static int levelForXp(int xp) {
		int remaining = Math.max(0, xp);
		int level = 1;
		while (remaining >= xpNeededForLevel(level)) {
			remaining -= xpNeededForLevel(level);
			level++;
		}
		return level;
	}

	public static int xpNeededForLevel(int level) {
		return GexpressConfig.getXpNeededForLevel(level);
	}

	public static int totalXpForLevel(int level) {
		int target = Math.max(1, level);
		int total = 0;
		for (int current = 1; current < target; current++) {
			total += xpNeededForLevel(current);
		}
		return total;
	}

	public static int level(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return 1;
		LevelComponent levels = KEY.getNullable(player.getWorld());
		return levels == null ? 1 : levels.level(player.getUuid());
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		xpByPlayer.clear();
		claimedRewardsByPlayer.clear();
		NbtList entries = tag.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			UUID playerId = parseUuid(entry.getString("uuid"));
			if (playerId == null) continue;
			xpByPlayer.put(playerId, Math.max(0, entry.getInt("xp")));
			NbtList claimed = entry.getList("claimedRewards", NbtElement.INT_TYPE);
			if (!claimed.isEmpty()) {
				Set<Integer> levels = new HashSet<>();
				for (int j = 0; j < claimed.size(); j++) {
					int level = claimed.getInt(j);
					if (level > 0) levels.add(level);
				}
				if (!levels.isEmpty()) claimedRewardsByPlayer.put(playerId, levels);
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList entries = new NbtList();
		for (Map.Entry<UUID, Integer> entry : xpByPlayer.entrySet()) {
			NbtCompound out = new NbtCompound();
			out.putString("uuid", entry.getKey().toString());
			out.putInt("xp", Math.max(0, entry.getValue()));
			Set<Integer> claimed = claimedRewardsByPlayer.get(entry.getKey());
			if (claimed != null && !claimed.isEmpty()) {
				NbtList claimedList = new NbtList();
				claimed.stream().filter(level -> level > 0).sorted().forEach(level -> claimedList.add(net.minecraft.nbt.NbtInt.of(level)));
				out.put("claimedRewards", claimedList);
			}
			entries.add(out);
		}
		tag.put("players", entries);
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
