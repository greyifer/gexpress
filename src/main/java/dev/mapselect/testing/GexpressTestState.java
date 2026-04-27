package dev.mapselect.testing;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GexpressTestState {
	private static final Set<UUID> ROLE_TESTERS = ConcurrentHashMap.newKeySet();
	private static final Set<ModifierKey> MODIFIER_TESTERS = ConcurrentHashMap.newKeySet();

	private GexpressTestState() {}

	public static void markRole(UUID playerId) {
		if (playerId != null) ROLE_TESTERS.add(playerId);
	}

	public static void unmarkRole(UUID playerId) {
		if (playerId != null) ROLE_TESTERS.remove(playerId);
	}

	public static boolean isRoleTester(PlayerEntity player) {
		return player != null && ROLE_TESTERS.contains(player.getUuid());
	}

	public static boolean hasRoleTesters() {
		return !ROLE_TESTERS.isEmpty();
	}

	public static void markModifier(UUID playerId, Modifier modifier) {
		if (playerId != null && modifier != null) {
			MODIFIER_TESTERS.add(new ModifierKey(playerId, modifier.identifier()));
		}
	}

	public static void unmarkModifier(UUID playerId, Modifier modifier) {
		if (playerId != null && modifier != null) {
			MODIFIER_TESTERS.remove(new ModifierKey(playerId, modifier.identifier()));
		}
	}

	public static void clearModifiers(UUID playerId) {
		if (playerId != null) {
			MODIFIER_TESTERS.removeIf(key -> key.playerId().equals(playerId));
		}
	}

	public static boolean isModifierTester(PlayerEntity player, Modifier modifier) {
		return player != null
			&& modifier != null
			&& MODIFIER_TESTERS.contains(new ModifierKey(player.getUuid(), modifier.identifier()));
	}

	public static boolean hasModifierTesters() {
		return !MODIFIER_TESTERS.isEmpty();
	}

	private record ModifierKey(UUID playerId, Identifier modifierId) {}
}
