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
	private final Map<UUID, Long> freezeCooldownUntil = new LinkedHashMap<>();
	private final Map<UUID, Integer> usesRemaining = new LinkedHashMap<>();
	private final Map<UUID, Integer> freezeUsesRemaining = new LinkedHashMap<>();

	public TimeMasterComponent(World world) {
		this.world = world;
	}

	public long cooldownRemainingTicks(UUID playerId) {
		return cooldownRemainingTicks(cooldownUntil, playerId);
	}

	public long freezeCooldownRemainingTicks(UUID playerId) {
		return cooldownRemainingTicks(freezeCooldownUntil, playerId);
	}

	private long cooldownRemainingTicks(Map<UUID, Long> cooldowns, UUID playerId) {
		if (playerId == null) return 0L;
		Long until = cooldowns.get(playerId);
		if (until == null) return 0L;
		long remaining = until - world.getTime();
		if (remaining <= 0L) {
			cooldowns.remove(playerId);
			KEY.sync(world);
			return 0L;
		}
		return remaining;
	}

	public int usesRemaining(UUID playerId) {
		if (playerId == null) return 0;
		return usesRemaining.getOrDefault(playerId, GexpressConfig.getTimeMasterMaxUses());
	}

	public int freezeUsesRemaining(UUID playerId) {
		if (playerId == null) return 0;
		return freezeUsesRemaining.getOrDefault(playerId, GexpressConfig.getTimeMasterFreezeMaxUses());
	}

	public boolean consume(UUID playerId) {
		if (playerId == null) return false;
		int remaining = usesRemaining(playerId);
		if (remaining <= 0 || cooldownRemainingTicks(playerId) > 0L) return false;

		usesRemaining.put(playerId, remaining - 1);
		setRewindCooldown(playerId);
		return true;
	}

	public void spendRewindAfterRestore(UUID playerId) {
		if (playerId == null) return;
		int remaining = usesRemaining(playerId);
		if (remaining > 0) {
			usesRemaining.put(playerId, remaining - 1);
		}
		setRewindCooldown(playerId);
	}

	private void setRewindCooldown(UUID playerId) {
		long cooldownTicks = (long) GexpressConfig.getTimeMasterCooldownSeconds() * 20L;
		if (cooldownTicks > 0L) {
			cooldownUntil.put(playerId, world.getTime() + cooldownTicks);
		} else {
			cooldownUntil.remove(playerId);
		}
		KEY.sync(world);
	}

	public void setFreezeCooldown(UUID playerId) {
		if (playerId == null) return;
		long cooldownTicks = (long) GexpressConfig.getTimeMasterFreezeCooldownSeconds() * 20L;
		if (cooldownTicks > 0L) {
			freezeCooldownUntil.put(playerId, world.getTime() + cooldownTicks);
		} else {
			freezeCooldownUntil.remove(playerId);
		}
		KEY.sync(world);
	}

	public boolean consumeFreeze(UUID playerId) {
		if (playerId == null) return false;
		int remaining = freezeUsesRemaining(playerId);
		if (remaining <= 0 || freezeCooldownRemainingTicks(playerId) > 0L) return false;

		freezeUsesRemaining.put(playerId, remaining - 1);
		setFreezeCooldown(playerId);
		return true;
	}

	public void ensurePlayer(UUID playerId) {
		if (playerId == null) return;
		boolean changed = false;
		int maxUses = GexpressConfig.getTimeMasterMaxUses();
		Integer current = usesRemaining.get(playerId);
		if (current == null || current > maxUses) {
			usesRemaining.put(playerId, maxUses);
			changed = true;
		}
		int maxFreezeUses = GexpressConfig.getTimeMasterFreezeMaxUses();
		Integer currentFreeze = freezeUsesRemaining.get(playerId);
		if (currentFreeze == null || currentFreeze > maxFreezeUses) {
			freezeUsesRemaining.put(playerId, maxFreezeUses);
			changed = true;
		}
		if (changed) KEY.sync(world);
	}

	public boolean clearAll() {
		if (cooldownUntil.isEmpty() && freezeCooldownUntil.isEmpty()
				&& usesRemaining.isEmpty() && freezeUsesRemaining.isEmpty()) return false;
		cooldownUntil.clear();
		freezeCooldownUntil.clear();
		usesRemaining.clear();
		freezeUsesRemaining.clear();
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		cooldownUntil.clear();
		freezeCooldownUntil.clear();
		usesRemaining.clear();
		freezeUsesRemaining.clear();
		readCooldowns(tag.getList("cooldowns", NbtElement.COMPOUND_TYPE), cooldownUntil);
		readCooldowns(tag.getList("freezeCooldowns", NbtElement.COMPOUND_TYPE), freezeCooldownUntil);
		readUses(tag.getList("uses", NbtElement.COMPOUND_TYPE), usesRemaining);
		readUses(tag.getList("freezeUses", NbtElement.COMPOUND_TYPE), freezeUsesRemaining);
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tag.put("cooldowns", writeCooldowns(cooldownUntil));
		tag.put("freezeCooldowns", writeCooldowns(freezeCooldownUntil));

		tag.put("uses", writeUses(usesRemaining));
		tag.put("freezeUses", writeUses(freezeUsesRemaining));
	}

	private NbtList writeCooldowns(Map<UUID, Long> cooldowns) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
			NbtCompound cooldown = new NbtCompound();
			cooldown.putString("player", entry.getKey().toString());
			cooldown.putLong("until", entry.getValue());
			list.add(cooldown);
		}
		return list;
	}

	private void readCooldowns(NbtList list, Map<UUID, Long> out) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID player = parseUuid(entry.getString("player"));
			if (player != null) out.put(player, entry.getLong("until"));
		}
	}

	private NbtList writeUses(Map<UUID, Integer> usesRemaining) {
		NbtList uses = new NbtList();
		for (Map.Entry<UUID, Integer> entry : usesRemaining.entrySet()) {
			NbtCompound use = new NbtCompound();
			use.putString("player", entry.getKey().toString());
			use.putInt("remaining", entry.getValue());
			uses.add(use);
		}
		return uses;
	}

	private void readUses(NbtList list, Map<UUID, Integer> out) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID player = parseUuid(entry.getString("player"));
			if (player != null) out.put(player, entry.getInt("remaining"));
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
