package dev.mapselect.role.warlock;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WarlockComponent implements AutoSyncedComponent {
	public static final ComponentKey<WarlockComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "warlock"),
		WarlockComponent.class
	);

	private final World world;
	private final Map<UUID, UUID> warlockToMark = new LinkedHashMap<>();
	private final Map<UUID, Long> markCooldownUntil = new LinkedHashMap<>();
	private final Map<UUID, Long> killCooldownUntil = new LinkedHashMap<>();

	public WarlockComponent(World world) {
		this.world = world;
	}

	public UUID getMarkedTarget(UUID warlock) {
		return warlock == null ? null : warlockToMark.get(warlock);
	}

	public List<UUID> getWarlocks() {
		return new ArrayList<>(warlockToMark.keySet());
	}

	public long markCooldownRemainingTicks(UUID warlock) {
		return cooldownRemainingTicks(markCooldownUntil, warlock);
	}

	public long killCooldownRemainingTicks(UUID warlock) {
		return cooldownRemainingTicks(killCooldownUntil, warlock);
	}

	public long reduceMarkCooldown(UUID warlock, long ticks) {
		return reduceCooldown(markCooldownUntil, warlock, ticks);
	}

	public long reduceKillCooldown(UUID warlock, long ticks) {
		return reduceCooldown(killCooldownUntil, warlock, ticks);
	}

	public boolean assignMark(UUID warlock, UUID target) {
		if (warlock == null || target == null || warlock.equals(target)) return false;
		warlockToMark.put(warlock, target);
		markCooldownUntil.put(warlock, world.getTime() + (long) GexpressConfig.getWarlockMarkCooldownSeconds() * 20L);
		KEY.sync(world);
		return true;
	}

	public void setKillCooldown(UUID warlock) {
		if (warlock == null) return;
		killCooldownUntil.put(warlock, world.getTime() + (long) GexpressConfig.getWarlockKillCooldownSeconds() * 20L);
		KEY.sync(world);
	}

	public boolean removeWarlock(UUID warlock) {
		if (warlock == null) return false;
		boolean changed = warlockToMark.remove(warlock) != null;
		changed |= markCooldownUntil.remove(warlock) != null;
		changed |= killCooldownUntil.remove(warlock) != null;
		if (changed) KEY.sync(world);
		return changed;
	}

	public boolean removeMark(UUID warlock) {
		if (warlock == null || warlockToMark.remove(warlock) == null) return false;
		KEY.sync(world);
		return true;
	}

	public boolean clearAll() {
		if (warlockToMark.isEmpty() && markCooldownUntil.isEmpty() && killCooldownUntil.isEmpty()) return false;
		warlockToMark.clear();
		markCooldownUntil.clear();
		killCooldownUntil.clear();
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		warlockToMark.clear();
		markCooldownUntil.clear();
		killCooldownUntil.clear();

		NbtList marks = tag.getList("marks", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < marks.size(); i++) {
			NbtCompound entry = marks.getCompound(i);
			UUID warlock = parseUuid(entry.getString("warlock"));
			UUID target = parseUuid(entry.getString("target"));
			if (warlock == null || target == null || warlock.equals(target)) continue;
			warlockToMark.put(warlock, target);
		}

		readCooldowns(tag.getList("mark_cooldowns", NbtElement.COMPOUND_TYPE), markCooldownUntil);
		readCooldowns(tag.getList("kill_cooldowns", NbtElement.COMPOUND_TYPE), killCooldownUntil);
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList marks = new NbtList();
		for (Map.Entry<UUID, UUID> entry : warlockToMark.entrySet()) {
			NbtCompound mark = new NbtCompound();
			mark.putString("warlock", entry.getKey().toString());
			mark.putString("target", entry.getValue().toString());
			marks.add(mark);
		}
		tag.put("marks", marks);
		tag.put("mark_cooldowns", writeCooldowns(markCooldownUntil));
		tag.put("kill_cooldowns", writeCooldowns(killCooldownUntil));
	}

	private long cooldownRemainingTicks(Map<UUID, Long> cooldowns, UUID warlock) {
		if (warlock == null) return 0L;
		Long until = cooldowns.get(warlock);
		if (until == null) return 0L;
		return Math.max(0L, until - world.getTime());
	}

	private long reduceCooldown(Map<UUID, Long> cooldowns, UUID warlock, long ticks) {
		if (warlock == null || ticks <= 0L) return cooldownRemainingTicks(cooldowns, warlock);
		Long until = cooldowns.get(warlock);
		if (until == null) return 0L;
		long remaining = until - world.getTime();
		if (remaining <= 0L) {
			cooldowns.remove(warlock);
			KEY.sync(world);
			return 0L;
		}
		long nextRemaining = Math.max(0L, remaining - ticks);
		if (nextRemaining <= 0L) cooldowns.remove(warlock);
		else cooldowns.put(warlock, world.getTime() + nextRemaining);
		KEY.sync(world);
		return nextRemaining;
	}

	private static void readCooldowns(NbtList list, Map<UUID, Long> out) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			UUID warlock = parseUuid(entry.getString("warlock"));
			if (warlock != null) {
				out.put(warlock, entry.getLong("until"));
			}
		}
	}

	private static NbtList writeCooldowns(Map<UUID, Long> cooldowns) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
			NbtCompound cooldown = new NbtCompound();
			cooldown.putString("warlock", entry.getKey().toString());
			cooldown.putLong("until", entry.getValue());
			list.add(cooldown);
		}
		return list;
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
