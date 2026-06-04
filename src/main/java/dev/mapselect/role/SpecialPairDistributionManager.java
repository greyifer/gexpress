package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps Stupid Express fixed-pair specials from leaking past their intended pair size.
 */
public final class SpecialPairDistributionManager {
	private static final int PAIR_SIZE = 2;

	private SpecialPairDistributionManager() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register(SpecialPairDistributionManager::normalize);
	}

	private static void normalize(World world, GameWorldComponent game) {
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		try {
			normalizePairRoles(serverWorld, game);
			normalizePairModifiers(serverWorld);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to normalize Stupid Express pair distribution.", t);
		}
	}

	private static void normalizePairRoles(ServerWorld world, GameWorldComponent game) {
		boolean changed = false;
		for (Role role : WatheRoles.ROLES) {
			if (role == null || role.identifier() == null
				|| !RoleModifierTuningBridge.isStupidExpressPairRole(role.identifier())) continue;

			List<UUID> assigned = new ArrayList<>(safeRoleAssignments(game, role));
			if (assigned.size() > PAIR_SIZE) {
				Set<UUID> keep = selectRoleKeepers(world, game, assigned);
				for (UUID playerId : assigned) {
					if (keep.contains(playerId)) continue;
					assignRole(world, game, playerId, WatheRoles.CIVILIAN);
					changed = true;
				}
				assigned = new ArrayList<>(keep);
			}

			if (assigned.size() == 1) {
				ServerPlayerEntity partner = findRolePartner(world, game, assigned);
				if (partner != null) {
					assignRole(world, game, partner.getUuid(), role);
					changed = true;
				}
			}
		}
		if (changed) game.sync();
	}

	private static Collection<UUID> safeRoleAssignments(GameWorldComponent game, Role role) {
		try {
			Collection<UUID> assigned = game.getAllWithRole(role);
			return assigned == null ? List.of() : assigned;
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Could not read assignments for {}.", id(role), t);
			return List.of();
		}
	}

	private static Set<UUID> selectRoleKeepers(ServerWorld world, GameWorldComponent game, List<UUID> assigned) {
		Set<UUID> keep = new HashSet<>();
		for (UUID playerId : assigned) {
			if (keep.size() >= PAIR_SIZE) break;
			ServerPlayerEntity player = serverPlayer(world, playerId);
			if (player != null && game.getRole(player) != null) keep.add(playerId);
		}
		for (UUID playerId : assigned) {
			if (keep.size() >= PAIR_SIZE) break;
			keep.add(playerId);
		}
		return keep;
	}

	private static ServerPlayerEntity findRolePartner(ServerWorld world, GameWorldComponent game, Collection<UUID> assigned) {
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity candidate : world.getPlayers()) {
			if (assigned.contains(candidate.getUuid())) continue;
			if (!game.isRole(candidate, WatheRoles.CIVILIAN)) continue;
			candidates.add(candidate);
		}
		return random(candidates);
	}

	private static void assignRole(ServerWorld world, GameWorldComponent game, UUID playerId, Role role) {
		if (playerId == null || role == null) return;
		ServerPlayerEntity player = serverPlayer(world, playerId);
		Role previous = player == null ? game.getRoles().get(playerId) : game.getRole(player);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			safeRemoveRole(player, previous);
		}
		if (player != null) {
			game.addRole(player, role);
		} else {
			game.getRoles().put(playerId, role);
		}
		if (player != null && !Harpymodloader.VANNILA_ROLES.contains(role)) {
			safeAssignRole(player, role);
		}
	}

	private static void normalizePairModifiers(ServerWorld world) {
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(world);
		if (component == null) return;

		boolean changed = false;
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			if (modifier == null || modifier.identifier() == null
				|| !RoleModifierTuningBridge.isStupidExpressPairModifier(modifier.identifier())) continue;

			List<UUID> assigned = new ArrayList<>(safeModifierAssignments(component, modifier));
			if (assigned.size() > PAIR_SIZE) {
				Set<UUID> keep = selectModifierKeepers(world, assigned);
				for (UUID playerId : assigned) {
					if (keep.contains(playerId)) continue;
					changed |= removeModifier(world, component, playerId, modifier);
				}
				assigned = new ArrayList<>(keep);
			}

			if (assigned.size() == 1) {
				ServerPlayerEntity partner = findModifierPartner(world, component, modifier, assigned);
				if (partner != null) {
					addModifier(component, partner, modifier);
					changed = true;
				}
			}
		}
		if (changed) component.sync();
	}

	private static Collection<UUID> safeModifierAssignments(WorldModifierComponent component, Modifier modifier) {
		try {
			Collection<UUID> assigned = component.getAllWithModifier(modifier);
			return assigned == null ? List.of() : assigned;
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Could not read assignments for {}.", id(modifier), t);
			return List.of();
		}
	}

	private static Set<UUID> selectModifierKeepers(ServerWorld world, List<UUID> assigned) {
		Set<UUID> keep = new HashSet<>();
		UUID first = firstOnlineWithModifier(world, assigned);
		if (first != null) {
			keep.add(first);
			UUID opposite = firstOppositeSide(world, assigned, first);
			if (opposite != null) keep.add(opposite);
		}
		for (UUID playerId : assigned) {
			if (keep.size() >= PAIR_SIZE) break;
			keep.add(playerId);
		}
		return keep;
	}

	private static UUID firstOnlineWithModifier(ServerWorld world, List<UUID> assigned) {
		for (UUID playerId : assigned) {
			ServerPlayerEntity player = serverPlayer(world, playerId);
			if (player != null) return playerId;
		}
		return assigned.isEmpty() ? null : assigned.getFirst();
	}

	private static UUID firstOppositeSide(ServerWorld world, List<UUID> assigned, UUID first) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		ServerPlayerEntity firstPlayer = serverPlayer(world, first);
		if (game == null || firstPlayer == null) return null;
		boolean firstInnocent = game.isInnocent(firstPlayer);
		for (UUID playerId : assigned) {
			if (playerId.equals(first)) continue;
			ServerPlayerEntity player = serverPlayer(world, playerId);
			if (player != null && game.isInnocent(player) != firstInnocent) return playerId;
		}
		return null;
	}

	private static ServerPlayerEntity findModifierPartner(ServerWorld world, WorldModifierComponent component,
			Modifier modifier, Collection<UUID> assigned) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		List<ServerPlayerEntity> preferred = new ArrayList<>();
		List<ServerPlayerEntity> fallback = new ArrayList<>();
		for (ServerPlayerEntity candidate : world.getPlayers()) {
			if (assigned.contains(candidate.getUuid()) || component.isModifier(candidate.getUuid(), modifier)) continue;
			if (game != null && game.getRole(candidate) == null) continue;
			fallback.add(candidate);
			if (game != null && game.isInnocent(candidate) && !game.isRole(candidate, WatheRoles.VIGILANTE)) {
				preferred.add(candidate);
			}
		}
		ServerPlayerEntity partner = random(preferred);
		return partner == null ? random(fallback) : partner;
	}

	private static void addModifier(WorldModifierComponent component, ServerPlayerEntity player, Modifier modifier) {
		if (component == null || player == null || modifier == null) return;
		component.addModifier(player.getUuid(), modifier);
		safeAssignModifier(player, modifier);
	}

	private static boolean removeModifier(ServerWorld world, WorldModifierComponent component, UUID playerId,
			Modifier modifier) {
		if (component == null || playerId == null || modifier == null) return false;
		ArrayList<Modifier> current = component.getModifiers(playerId);
		if (current == null || current.isEmpty()) return false;
		Identifier id = modifier.identifier();
		boolean removed = current.removeIf(value -> value == modifier
			|| (value != null && id != null && id.equals(value.identifier())));
		if (!removed) return false;
		ServerPlayerEntity player = serverPlayer(world, playerId);
		if (player != null) safeRemoveModifier(player, modifier);
		return true;
	}

	private static ServerPlayerEntity serverPlayer(ServerWorld world, UUID playerId) {
		if (world == null || playerId == null) return null;
		PlayerEntity player = world.getPlayerByUuid(playerId);
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
	}

	private static ServerPlayerEntity random(List<ServerPlayerEntity> players) {
		if (players == null || players.isEmpty()) return null;
		return players.get((int) (Math.random() * players.size()));
	}

	private static void safeAssignRole(ServerPlayerEntity player, Role role) {
		if (player == null || role == null) return;
		try {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleAssigned listener failed while normalizing {} on {}.",
				id(role), player.getName().getString(), t);
		}
	}

	private static void safeRemoveRole(ServerPlayerEntity player, Role role) {
		if (player == null || role == null) return;
		try {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleRemoved listener failed while normalizing {} on {}.",
				id(role), player.getName().getString(), t);
		}
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null) return;
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed while normalizing {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		if (player == null || modifier == null) return;
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed while normalizing {} on {}.",
				id(modifier), player.getName().getString(), t);
		}
		player.calculateDimensions();
		player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
	}

	private static String id(Role role) {
		return role == null || role.identifier() == null ? "(none)" : role.identifier().toString();
	}

	private static String id(Modifier modifier) {
		return modifier == null || modifier.identifier() == null ? "(none)" : modifier.identifier().toString();
	}
}
