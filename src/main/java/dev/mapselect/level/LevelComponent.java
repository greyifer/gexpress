package dev.mapselect.level;

import dev.mapselect.MapSelect;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class LevelComponent implements AutoSyncedComponent {
	public static final ComponentKey<LevelComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "levels"),
		LevelComponent.class
	);

	private final World world;
	private final Map<UUID, Integer> xpByPlayer = new LinkedHashMap<>();

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

	public boolean addXp(UUID playerId, int amount) {
		if (playerId == null || amount <= 0) return false;
		int next = Math.max(0, xp(playerId) + amount);
		xpByPlayer.put(playerId, next);
		KEY.sync(world);
		return true;
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
		return Math.max(1, level) * 100;
	}

	public static int level(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return 1;
		LevelComponent levels = KEY.getNullable(player.getWorld());
		return levels == null ? 1 : levels.level(player.getUuid());
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		xpByPlayer.clear();
		NbtList entries = tag.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			UUID playerId = parseUuid(entry.getString("uuid"));
			if (playerId == null) continue;
			xpByPlayer.put(playerId, Math.max(0, entry.getInt("xp")));
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList entries = new NbtList();
		for (Map.Entry<UUID, Integer> entry : xpByPlayer.entrySet()) {
			NbtCompound out = new NbtCompound();
			out.putString("uuid", entry.getKey().toString());
			out.putInt("xp", Math.max(0, entry.getValue()));
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
