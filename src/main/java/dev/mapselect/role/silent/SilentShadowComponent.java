package dev.mapselect.role.silent;

import dev.mapselect.MapSelect;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

public class SilentShadowComponent implements AutoSyncedComponent {
	public static final ComponentKey<SilentShadowComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "silent_shadow"),
		SilentShadowComponent.class
	);

	private final World world;
	private final Map<UUID, ShadowState> active = new LinkedHashMap<>();
	private final Map<UUID, Long> cooldownUntil = new LinkedHashMap<>();

	public SilentShadowComponent(World world) {
		this.world = world;
	}

	public boolean isActive(UUID uuid) {
		return uuid != null && active.containsKey(uuid);
	}

	public ShadowState getState(UUID uuid) {
		return uuid == null ? null : active.get(uuid);
	}

	public List<UUID> getActivePlayerIds() {
		return new ArrayList<>(active.keySet());
	}

	public long activeRemainingTicks(UUID uuid) {
		ShadowState state = getState(uuid);
		if (state == null) return 0L;
		return Math.max(0L, state.activeUntil() - world.getTime());
	}

	public long cooldownRemainingTicks(UUID uuid) {
		if (uuid == null) return 0L;
		Long until = cooldownUntil.get(uuid);
		if (until == null) return 0L;
		return Math.max(0L, until - world.getTime());
	}

	public boolean activate(ServerPlayerEntity player, int durationTicks, int cooldownTicks) {
		if (player == null || isActive(player.getUuid())) return false;
		long now = world.getTime();
		long activeUntil = now + Math.max(1L, durationTicks);
		active.put(player.getUuid(), new ShadowState(
			player.getX(),
			player.getY(),
			player.getZ(),
			player.getYaw(),
			player.getPitch(),
			activeUntil
		));
		cooldownUntil.put(player.getUuid(), activeUntil + Math.max(0L, cooldownTicks));
		KEY.sync(world);
		return true;
	}

	public boolean end(ServerPlayerEntity player, boolean teleport) {
		if (player == null) return false;
		ShadowState state = active.remove(player.getUuid());
		if (state == null) return false;
		if (teleport && player.getWorld() instanceof ServerWorld targetWorld) {
			player.teleport(targetWorld, state.x(), state.y(), state.z(), state.yaw(), state.pitch());
		}
		KEY.sync(world);
		return true;
	}

	public boolean remove(UUID uuid) {
		if (uuid == null || active.remove(uuid) == null) return false;
		KEY.sync(world);
		return true;
	}

	public boolean clearAll() {
		if (active.isEmpty() && cooldownUntil.isEmpty()) return false;
		active.clear();
		cooldownUntil.clear();
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		active.clear();
		cooldownUntil.clear();

		NbtList activeList = tag.getList("active", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < activeList.size(); i++) {
			NbtCompound entry = activeList.getCompound(i);
			UUID uuid = parseUuid(entry.getString("uuid"));
			if (uuid == null) continue;
			active.put(uuid, new ShadowState(
				entry.getDouble("x"),
				entry.getDouble("y"),
				entry.getDouble("z"),
				entry.getFloat("yaw"),
				entry.getFloat("pitch"),
				entry.getLong("active_until")
			));
		}

		NbtList cooldowns = tag.getList("cooldowns", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cooldowns.size(); i++) {
			NbtCompound entry = cooldowns.getCompound(i);
			UUID uuid = parseUuid(entry.getString("uuid"));
			if (uuid != null) {
				cooldownUntil.put(uuid, entry.getLong("until"));
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList activeList = new NbtList();
		for (Map.Entry<UUID, ShadowState> entry : active.entrySet()) {
			ShadowState state = entry.getValue();
			NbtCompound nbt = new NbtCompound();
			nbt.putString("uuid", entry.getKey().toString());
			nbt.putDouble("x", state.x());
			nbt.putDouble("y", state.y());
			nbt.putDouble("z", state.z());
			nbt.putFloat("yaw", state.yaw());
			nbt.putFloat("pitch", state.pitch());
			nbt.putLong("active_until", state.activeUntil());
			activeList.add(nbt);
		}
		tag.put("active", activeList);

		NbtList cooldowns = new NbtList();
		for (Map.Entry<UUID, Long> entry : cooldownUntil.entrySet()) {
			NbtCompound nbt = new NbtCompound();
			nbt.putString("uuid", entry.getKey().toString());
			nbt.putLong("until", entry.getValue());
			cooldowns.add(nbt);
		}
		tag.put("cooldowns", cooldowns);
	}

	public static boolean isShadowed(Entity entity) {
		if (entity == null || entity.getWorld() == null) return false;
		SilentShadowComponent comp = KEY.getNullable(entity.getWorld());
		return comp != null && comp.isActive(entity.getUuid());
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isEmpty()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public record ShadowState(double x, double y, double z, float yaw, float pitch, long activeUntil) {}
}
