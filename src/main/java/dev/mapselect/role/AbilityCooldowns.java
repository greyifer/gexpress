package dev.mapselect.role;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;

public final class AbilityCooldowns {
	private AbilityCooldowns() {}

	public static long remaining(Map<UUID, Long> cooldowns, ServerPlayerEntity player) {
		if (player == null) return 0L;
		return remaining(cooldowns, player.getUuid(), player.getWorld().getTime());
	}

	public static long remaining(Map<UUID, Long> cooldowns, UUID playerId, long now) {
		if (cooldowns == null || playerId == null) return 0L;
		Long until = cooldowns.get(playerId);
		if (until == null) return 0L;
		long remaining = until - now;
		if (remaining <= 0L) {
			cooldowns.remove(playerId);
			return 0L;
		}
		return remaining;
	}

	public static void start(Map<UUID, Long> cooldowns, ServerPlayerEntity player, long ticks) {
		if (cooldowns == null || player == null) return;
		start(cooldowns, player.getUuid(), player.getWorld().getTime(), ticks);
	}

	public static void start(Map<UUID, Long> cooldowns, UUID playerId, long now, long ticks) {
		if (cooldowns == null || playerId == null) return;
		if (ticks <= 0L) cooldowns.remove(playerId);
		else cooldowns.put(playerId, now + ticks);
	}

	public static long reduce(Map<UUID, Long> cooldowns, ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return remaining(cooldowns, player);
		long remaining = remaining(cooldowns, player);
		if (remaining <= 0L) return 0L;
		long next = Math.max(0L, remaining - ticks);
		if (next <= 0L) cooldowns.remove(player.getUuid());
		else cooldowns.put(player.getUuid(), player.getWorld().getTime() + next);
		return next;
	}

	public static long reduce(Map<UUID, Long> cooldowns, UUID playerId, long now, long ticks) {
		if (playerId == null || ticks <= 0L) return remaining(cooldowns, playerId, now);
		long remaining = remaining(cooldowns, playerId, now);
		if (remaining <= 0L) return 0L;
		long next = Math.max(0L, remaining - ticks);
		if (next <= 0L) cooldowns.remove(playerId);
		else cooldowns.put(playerId, now + next);
		return next;
	}

	public static long secondsCeil(long ticks) {
		return Math.max(0L, (ticks + 19L) / 20L);
	}
}
