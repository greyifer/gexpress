package dev.mapselect.role.silent;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.game.StandingSafeRandomTeleport;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.pelican.PelicanManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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
	private final Map<UUID, Long> exitRetryAt = new LinkedHashMap<>();

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
		boolean hadCooldown = uuid != null && cooldownUntil.containsKey(uuid);
		long remaining = AbilityCooldowns.remaining(cooldownUntil, uuid, world.getTime());
		if (remaining <= 0L && hadCooldown) KEY.sync(world);
		return remaining;
	}

	public long reduceCooldown(UUID uuid, long ticks) {
		long nextRemaining = AbilityCooldowns.reduce(cooldownUntil, uuid, world.getTime(), ticks);
		KEY.sync(world);
		return nextRemaining;
	}

	public boolean activate(ServerPlayerEntity player, int durationTicks, int cooldownTicks) {
		if (player == null || isActive(player.getUuid())) return false;
		exitRetryAt.remove(player.getUuid());
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
		AbilityCooldowns.start(cooldownUntil, player.getUuid(), now,
			Math.max(1L, durationTicks) + Math.max(0L, cooldownTicks));
		KEY.sync(world);
		return true;
	}

	public boolean end(ServerPlayerEntity player, boolean teleport) {
		return end(player, teleport, true);
	}

	private boolean end(ServerPlayerEntity player, boolean teleport, boolean sync) {
		if (player == null) return false;
		UUID playerId = player.getUuid();
		ShadowState state = active.get(playerId);
		if (state == null || !(player.getWorld() instanceof ServerWorld targetWorld)) return false;

		Vec3d origin = new Vec3d(state.x(), state.y(), state.z());
		Vec3d current = player.getPos();
		if (!teleport && StandingSafeRandomTeleport.isStandingBlockClear(targetWorld, player, current)) {
			active.remove(playerId);
			exitRetryAt.remove(playerId);
			if (sync) KEY.sync(world);
			return true;
		}
		if (world.getTime() < exitRetryAt.getOrDefault(playerId, Long.MIN_VALUE)) return false;

		Vec3d destination;
		if (teleport) {
			// The saved origin was a valid standing position when Shadow March began. If its
			// standing box is still clear, preserve the exact position even when it is mid-air.
			destination = StandingSafeRandomTeleport.isStandingBlockClear(targetWorld, player, origin)
				? origin
				: StandingSafeRandomTeleport.nearestStandingSafeExpanding(targetWorld, player, origin);
			if (destination == null && StandingSafeRandomTeleport.isStandingBlockClear(targetWorld, player, current)) {
				destination = current;
			}
			if (destination == null) {
				destination = StandingSafeRandomTeleport.nearestStandingSafeExpanding(targetWorld, player, current);
			}
		} else {
			// Non-natural cancellation only moves a player whose standing box is actually
			// embedded in blocks. Prefer the closest safe pocket to their current position.
			destination = StandingSafeRandomTeleport.nearestStandingSafeExpanding(targetWorld, player, current);
			if (destination == null && StandingSafeRandomTeleport.isStandingBlockClear(targetWorld, player, origin)) {
				destination = origin;
			}
			if (destination == null) {
				destination = StandingSafeRandomTeleport.nearestStandingSafeExpanding(targetWorld, player, origin);
			}
		}

		// Never strand a player by removing phasing at a blocked location. A later tick or
		// reconnect can retry after the surrounding blocks or map bounds change.
		if (destination == null) {
			// A malformed or temporarily sealed map must not turn the safe-exit retry into
			// an expensive full-radius search every server tick.
			exitRetryAt.put(playerId, world.getTime() + 20L);
			return false;
		}

		player.setPose(EntityPose.STANDING);
		player.teleport(targetWorld, destination.x, destination.y, destination.z,
			teleport ? state.yaw() : player.getYaw(), teleport ? state.pitch() : player.getPitch());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.fallDistance = 0.0F;
		active.remove(playerId);
		exitRetryAt.remove(playerId);
		if (sync) KEY.sync(world);
		return true;
	}

	public boolean remove(UUID uuid) {
		if (uuid == null || active.remove(uuid) == null) return false;
		exitRetryAt.remove(uuid);
		KEY.sync(world);
		return true;
	}

	public boolean clearAll() {
		if (active.isEmpty() && cooldownUntil.isEmpty()) return false;
		boolean changed = false;
		if (world instanceof ServerWorld serverWorld) {
			for (UUID playerId : new ArrayList<>(active.keySet())) {
				ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerId);
				// Keep offline entries so reconnecting players retain collision protection until
				// their saved in-block position can be resolved safely.
				if (player == null) continue;
				if (PelicanManager.isStashed(player) || !GameFunctions.isPlayerAliveAndSurvival(player)) {
					changed |= active.remove(playerId) != null;
					exitRetryAt.remove(playerId);
					continue;
				}
				changed |= end(player, false, false);
			}
		}
		if (!cooldownUntil.isEmpty()) {
			cooldownUntil.clear();
			changed = true;
		}
		if (changed) KEY.sync(world);
		return changed;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		active.clear();
		cooldownUntil.clear();
		exitRetryAt.clear();

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
