package dev.mapselect.role.bombspecialist;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class C4BackComponent implements AutoSyncedComponent {
	public static final ComponentKey<C4BackComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "c4_back"),
		C4BackComponent.class
	);

	private final World world;
	/** Carrier UUID → world tick at which their C4 should detonate. Insertion-ordered. */
	private final Map<UUID, Long> carriers = new LinkedHashMap<>();
	private final Map<UUID, Long> readOnlyCarriers = Collections.unmodifiableMap(carriers);
	private final Map<UUID, Long> plantedAt = new LinkedHashMap<>();
	private final Map<UUID, Integer> presetIndexes = new LinkedHashMap<>();

	public C4BackComponent(World world) {
		this.world = world;
	}

	public boolean hasC4(UUID uuid) {
		return uuid != null && carriers.containsKey(uuid);
	}

	public Map<UUID, Long> getCarriers() {
		return readOnlyCarriers;
	}

	/** Attach with the default fuse (from GexpressConfig). Returns false if already attached. */
	public boolean addC4(UUID uuid) {
		return addC4(uuid, GexpressConfig.getC4FuseSeconds() * 20L);
	}

	/**
	 * Attach with a specific fuse length in ticks. Used by the /g roles c4 attach command
	 * when we want to honor live config changes without requiring a server restart.
	 */
	public boolean addC4(UUID uuid, long fuseTicks) {
		if (uuid == null || carriers.containsKey(uuid)) return false;
		long firstBeepDelayTicks = Math.max(0L, (long) GexpressConfig.getC4FirstBeepSeconds() * 20L);
		long detonationAt = world.getTime() + firstBeepDelayTicks + Math.max(1L, fuseTicks);
		carriers.put(uuid, detonationAt);
		plantedAt.put(uuid, world.getTime());
		presetIndexes.put(uuid, choosePlacementPresetIndex());
		KEY.sync(this.world);
		return true;
	}

	public boolean removeC4(UUID uuid) {
		if (uuid == null || carriers.remove(uuid) == null) return false;
		plantedAt.remove(uuid);
		presetIndexes.remove(uuid);
		KEY.sync(this.world);
		return true;
	}

	public boolean clearAll() {
		if (carriers.isEmpty()) return false;
		carriers.clear();
		plantedAt.clear();
		presetIndexes.clear();
		KEY.sync(this.world);
		return true;
	}

	/** Ticks remaining on this player's fuse (for HUD / debugging). -1 if not attached. */
	public long ticksUntilDetonation(UUID uuid) {
		Long t = carriers.get(uuid);
		if (t == null) return -1L;
		return Math.max(0L, t - world.getTime());
	}

	public long ticksSincePlant(UUID uuid) {
		Long t = plantedAt.get(uuid);
		if (t == null) return Long.MAX_VALUE;
		return Math.max(0L, world.getTime() - t);
	}

	public int getPresetIndex(UUID uuid) {
		if (uuid == null) return 0;
		return Math.max(0, presetIndexes.getOrDefault(uuid, 0));
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		carriers.clear();
		plantedAt.clear();
		presetIndexes.clear();
		NbtList list = tag.getList("carriers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			String uuidStr = entry.getString("uuid");
			long detonationAt = entry.getLong("detonation_tick");
			long plantedTick = entry.contains("planted_tick")
				? entry.getLong("planted_tick")
				: detonationAt - ((long) GexpressConfig.getC4FirstBeepSeconds()
					+ (long) GexpressConfig.getC4FuseSeconds()) * 20L;
			int presetIndex = entry.contains("preset_index") ? Math.max(0, entry.getInt("preset_index")) : 0;
			if (uuidStr == null || uuidStr.isEmpty()) continue;
			try {
				UUID uuid = UUID.fromString(uuidStr);
				carriers.put(uuid, detonationAt);
				plantedAt.put(uuid, plantedTick);
				presetIndexes.put(uuid, presetIndex);
			} catch (IllegalArgumentException ignored) {
			}
		}
		// Legacy v1 format: plain list of UUID strings without detonation times.
		NbtList legacy = tag.getList("carriers_legacy", NbtElement.STRING_TYPE);
		if (legacy != null) {
			long fallback = world.getTime() + ((long) GexpressConfig.getC4FirstBeepSeconds()
				+ (long) GexpressConfig.getC4FuseSeconds()) * 20L;
			for (int i = 0; i < legacy.size(); i++) {
				try {
					UUID uuid = UUID.fromString(legacy.getString(i));
					carriers.putIfAbsent(uuid, fallback);
					plantedAt.putIfAbsent(uuid, world.getTime());
					presetIndexes.putIfAbsent(uuid, 0);
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> e : carriers.entrySet()) {
			NbtCompound entry = new NbtCompound();
			entry.putString("uuid", e.getKey().toString());
			entry.putLong("detonation_tick", e.getValue());
			entry.putLong("planted_tick", plantedAt.getOrDefault(e.getKey(), world.getTime()));
			entry.putInt("preset_index", presetIndexes.getOrDefault(e.getKey(), 0));
			list.add(entry);
		}
		tag.put("carriers", list);
	}

	private int choosePlacementPresetIndex() {
		int count = GexpressConfig.getC4PlacementPresetCount();
		if (count <= 1) return 0;
		return this.world.getRandom().nextInt(count);
	}

	public static boolean hasC4(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		C4BackComponent c = KEY.getNullable(player.getWorld());
		return c != null && c.hasC4(player.getUuid());
	}

	public static int getPresetIndex(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return 0;
		C4BackComponent c = KEY.getNullable(player.getWorld());
		return c != null ? c.getPresetIndex(player.getUuid()) : 0;
	}
}
