package dev.mapselect.role.timemaster;

import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TimeMasterComponent implements AutoSyncedComponent {
	public static final ComponentKey<TimeMasterComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "time_master"),
		TimeMasterComponent.class
	);

	private final World world;
	private final Map<UUID, Long> cooldownUntil = new LinkedHashMap<>();
	private final Map<UUID, Integer> usesRemaining = new LinkedHashMap<>();

	public TimeMasterComponent(World world) {
		this.world = world;
	}

	public long cooldownRemainingTicks(UUID playerId) {
		if (playerId == null) return 0L;
		Long until = cooldownUntil.get(playerId);
		if (until == null) return 0L;
		long remaining = until - world.getTime();
		if (remaining <= 0L) {
			cooldownUntil.remove(playerId);
			KEY.sync(world);
			return 0L;
		}
		return remaining;
	}

	public int usesRemaining(UUID playerId) {
		if (playerId == null) return 0;
		return usesRemaining.getOrDefault(playerId, GexpressConfig.getTimeMasterMaxUses());
	}

	public boolean consume(UUID playerId) {
		if (playerId == null) return false;
		int remaining = usesRemaining(playerId);
		if (remaining <= 0 || cooldownRemainingTicks(playerId) > 0L) return false;

		usesRemaining.put(playerId, remaining - 1);
		long cooldownTicks = (long) GexpressConfig.getTimeMasterCooldownSeconds() * 20L;
		if (cooldownTicks > 0L) {
			cooldownUntil.put(playerId, world.getTime() + cooldownTicks);
		} else {
			cooldownUntil.remove(playerId);
		}
		KEY.sync(world);
		return true;
	}

	public void ensurePlayer(UUID playerId) {
		if (playerId == null) return;
		usesRemaining.putIfAbsent(playerId, GexpressConfig.getTimeMasterMaxUses());
		KEY.sync(world);
	}

	public boolean clearAll() {
		if (cooldownUntil.isEmpty() && usesRemaining.isEmpty()) return false;
		cooldownUntil.clear();
		usesRemaining.clear();
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		cooldownUntil.clear();
		usesRemaining.clear();
		readCooldowns(tag.getList("cooldowns", NbtElement.COMPOUND_TYPE));
		readUses(tag.getList("uses", NbtElement.COMPOUND_TYPE));
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList cooldowns = new NbtList();
		for (Map.Entry<UUID, Long> entry : cooldownUntil.entrySet()) {
			NbtCompound cooldown = new NbtCompound();
			cooldown.putString("player", entry.getKey().toString());
			cooldown.putLong("until", entry.getValue());
			cooldowns.add(cooldown);
		}
		tag.put("cooldowns", cooldowns);

		NbtList uses = new NbtList();
		for (Map.Entry<UUID, Integer> entry : usesRemaining.entrySet()) {
			NbtCompound use = new NbtCompound();
			use.putString("player", entry.getKey().toString());
			use.putInt("remaining", entry.getValue());
			uses.add(use);
		}
		tag.put("uses", uses);
	}

	private void readCooldowns(NbtList list) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID player = parseUuid(entry.getString("player"));
			if (player != null) cooldownUntil.put(player, entry.getLong("until"));
		}
	}

	private void readUses(NbtList list) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID player = parseUuid(entry.getString("player"));
			if (player != null) usesRemaining.put(player, entry.getInt("remaining"));
		}
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isEmpty()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
