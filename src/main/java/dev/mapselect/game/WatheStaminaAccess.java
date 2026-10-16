package dev.mapselect.game;

import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class WatheStaminaAccess {
	private static final Map<Class<?>, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

	private WatheStaminaAccess() {}

	public static void set(PlayerEntity player, float staminaTicks) {
		if (player == null || staminaTicks < 0.0F) return;
		Field field = field(player.getClass());
		if (field == null) return;
		try {
			field.setFloat(player, staminaTicks);
		} catch (IllegalAccessException ignored) {
		}
	}

	public static float get(PlayerEntity player) {
		if (player == null) return -1.0F;
		Field field = field(player.getClass());
		if (field == null) return -1.0F;
		try {
			return field.getFloat(player);
		} catch (IllegalAccessException ignored) {
			return -1.0F;
		}
	}

	private static Field field(Class<?> playerClass) {
		return FIELDS.computeIfAbsent(playerClass, WatheStaminaAccess::findField).orElse(null);
	}

	private static Optional<Field> findField(Class<?> playerClass) {
		Class<?> type = playerClass;
		while (type != null) {
			try {
				Field exact = type.getDeclaredField("sprintingTicks");
				if (exact.getType() == float.class && exact.trySetAccessible()) return Optional.of(exact);
			} catch (NoSuchFieldException ignored) {
			}
			type = type.getSuperclass();
		}
		return Optional.empty();
	}
}
