package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.TrainWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.AnnounceWelcomePayload;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.mafia.TakeoverManager;
import dev.mapselect.role.snitch.SnitchManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GexpressRoleAssignment {
	private static final Random RANDOM = new Random();
	private static int assignmentPlayerCount;

	private GexpressRoleAssignment() {}

	public static boolean initializeGame(ServerWorld world, GameWorldComponent game, List<ServerPlayerEntity> players) {
		try {
			Harpymodloader.refreshRoles();
			HarpyModLoaderConfig.HANDLER.load();
			RoleModifierTuningConfig.load();
			assignmentPlayerCount = players.size();
			setNight(world);

			WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(world);
			modifiers.getModifiers().clear();

			game.clearRoleMap();
			for (ServerPlayerEntity player : players) {
				ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
				game.addRole(player, WatheRoles.CIVILIAN);
			}

			List<ServerPlayerEntity> available = new ArrayList<>(players);
			Map<Role, Integer> assignedCounts = new HashMap<>();
			if (GexpressGameModes.isTakeover(game)) {
				assignTakeoverRoles(game, available, assignedCounts);
			} else {
				TakeoverManager.clear();
				assignForcedRoles(game, available, assignedCounts);
				assignConfiguredRoles(game, available, assignedCounts);
			}
			grantVanillaRoleLoadouts(game, players);
			resetStartingBalances(game, players);
			game.sync();
			SnitchManager.syncAll(world, game);

			sendRoleAnnouncements(game, players);
			assignForcedModifiers(game, modifiers, players);
			assignConfiguredModifiers(game, modifiers, players);
			modifiers.sync();
			sendModifierAnnouncements(modifiers, players);

			Harpymodloader.FORCED_MODDED_ROLE.clear();
			Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
			Harpymodloader.FORCED_MODDED_MODIFIER.clear();
			return true;
		} catch (Throwable t) {
			MapSelect.LOGGER.error("G'Express role assignment failed; falling back to Harpy's assigner.", t);
			return false;
		} finally {
			assignmentPlayerCount = 0;
		}
	}

	private static void assignTakeoverRoles(GameWorldComponent game, List<ServerPlayerEntity> available,
			Map<Role, Integer> assignedCounts) {
		if (MapSelectRoles.GODFATHER == null || available.isEmpty()) {
			TakeoverManager.assignSides(List.of());
			return;
		}
		Collections.shuffle(available, RANDOM);
		int count = Math.min(2, available.size());
		List<ServerPlayerEntity> godfathers = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ServerPlayerEntity player = available.removeFirst();
			assignRole(game, player, MapSelectRoles.GODFATHER);
			assignedCounts.merge(MapSelectRoles.GODFATHER, 1, Integer::sum);
			godfathers.add(player);
		}
		TakeoverManager.assignSides(godfathers);
	}

	private static void setNight(ServerWorld world) {
		TrainWorldComponent.KEY.get(world).setTimeOfDay(TrainWorldComponent.TimeOfDay.NIGHT);
	}

	private static void assignForcedRoles(GameWorldComponent game, List<ServerPlayerEntity> available,
			Map<Role, Integer> assignedCounts) {
		for (Map.Entry<Role, List<UUID>> entry : Harpymodloader.FORCED_MODDED_ROLE.entrySet()) {
			Role role = entry.getKey();
			if (role == null || isRetiredExternalVulture(role) || entry.getValue() == null) continue;
			for (UUID playerId : entry.getValue()) {
				ServerPlayerEntity player = takePlayer(available, playerId);
				if (player == null) continue;
				assignRole(game, player, role);
				assignedCounts.merge(role, 1, Integer::sum);
			}
		}

		for (Map.Entry<UUID, Role> entry : Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet()) {
			Role role = entry.getValue();
			ServerPlayerEntity player = takePlayer(available, entry.getKey());
			if (player == null || role == null || isRetiredExternalVulture(role)) continue;
			assignRole(game, player, role);
			assignedCounts.merge(role, 1, Integer::sum);
		}
	}

	private static void assignConfiguredRoles(GameWorldComponent game, List<ServerPlayerEntity> available,
			Map<Role, Integer> assignedCounts) {
		int killerTarget = GexpressConfig.useCustomRoleCounts()
			? GexpressConfig.getMaxKillerAmount()
			: scaledKillerCount(available, assignedCounts);
		int vigilanteTarget = GexpressConfig.useCustomRoleCounts()
			? GexpressConfig.getMaxVigilanteAmount()
			: scaledVigilanteCount(available, assignedCounts);
		int neutralTarget = GexpressConfig.useCustomRoleCounts()
			? GexpressConfig.getMaxNeutralAmount()
			: scaledNeutralCount(available, assignedCounts);
		int killerSlots = Math.max(0, Math.min(killerTarget, available.size())
			- countAssignedKillers(assignedCounts));
		int assignedKillers = assignRolePool(game, available, assignedCounts, rolePool(RolePool.KILLER), killerSlots);
		assignFallbackKillers(game, available, assignedCounts, killerSlots - assignedKillers);
		int vigilanteSlots = Math.max(0, Math.min(vigilanteTarget, available.size())
			- assignedCounts.getOrDefault(WatheRoles.VIGILANTE, 0));
		assignRolePool(game, available, assignedCounts, vigilantePool(), vigilanteSlots);
		int neutralSlots = Math.max(0, Math.min(neutralTarget, available.size())
			- countAssignedNeutrals(assignedCounts));
		assignRolePool(game, available, assignedCounts, rolePool(RolePool.NEUTRAL), neutralSlots);
		assignRolePool(game, available, assignedCounts, rolePool(RolePool.CIVILIAN), available.size());
	}

	private static int scaledKillerCount(List<ServerPlayerEntity> available, Map<Role, Integer> assignedCounts) {
		int players = totalPlayerCount(available, assignedCounts);
		if (players <= 1) return 0;
		return Math.max(1, players / GexpressConfig.getPlayersPerKiller());
	}

	private static int scaledVigilanteCount(List<ServerPlayerEntity> available, Map<Role, Integer> assignedCounts) {
		int players = totalPlayerCount(available, assignedCounts);
		return Math.max(0, players / GexpressConfig.getPlayersPerVigilante());
	}

	private static int scaledNeutralCount(List<ServerPlayerEntity> available, Map<Role, Integer> assignedCounts) {
		int players = totalPlayerCount(available, assignedCounts);
		return Math.max(0, players / GexpressConfig.getPlayersPerNeutral());
	}

	private static int totalPlayerCount(List<ServerPlayerEntity> available, Map<Role, Integer> assignedCounts) {
		int total = available.size();
		for (int count : assignedCounts.values()) total += Math.max(0, count);
		return total;
	}

	private static int assignRolePool(GameWorldComponent game, List<ServerPlayerEntity> available,
			Map<Role, Integer> assignedCounts, List<Role> pool, int maxAssignments) {
		if (available.isEmpty() || maxAssignments <= 0 || pool.isEmpty()) return 0;
		List<Role> tickets = roleTickets(pool, assignedCounts);
		Collections.shuffle(tickets, RANDOM);
		int assignments = Math.min(maxAssignments, tickets.size());
		for (int i = 0; i < assignments && !available.isEmpty(); i++) {
			Role role = tickets.get(i);
			ServerPlayerEntity player = available.remove(RANDOM.nextInt(available.size()));
			assignRole(game, player, role);
			assignedCounts.merge(role, 1, Integer::sum);
		}
		return assignments;
	}

	private static void assignFallbackKillers(GameWorldComponent game, List<ServerPlayerEntity> available,
			Map<Role, Integer> assignedCounts, int count) {
		if (available.isEmpty() || count <= 0) return;
		Role fallback = fallbackKillerRole();
		if (fallback == null) return;
		for (int i = 0; i < count && !available.isEmpty(); i++) {
			ServerPlayerEntity player = available.remove(RANDOM.nextInt(available.size()));
			assignRole(game, player, fallback);
			assignedCounts.merge(fallback, 1, Integer::sum);
		}
	}

	private static Role fallbackKillerRole() {
		if (WatheRoles.KILLER != null && isAssignableRole(WatheRoles.KILLER) && !isRoleDisabled(WatheRoles.KILLER)) {
			return WatheRoles.KILLER;
		}
		List<Role> pool = rolePool(RolePool.KILLER);
		if (pool.isEmpty()) return null;
		return pool.get(RANDOM.nextInt(pool.size()));
	}

	private static List<Role> roleTickets(List<Role> pool, Map<Role, Integer> assignedCounts) {
		List<Role> tickets = new ArrayList<>();
		for (Role role : pool) {
			if (role == null || role.identifier() == null) continue;
			if (!passes(RoleModifierTuningConfig.getRoleChance(role.identifier().toString()))) continue;
			int configured = RoleModifierTuningConfig.getRoleMax(role.identifier().toString());
			int alreadyAssigned = assignedCounts.getOrDefault(role, 0);
			int remaining = Math.max(0, configured - alreadyAssigned);
			for (int i = 0; i < remaining; i++) tickets.add(role);
		}
		return tickets;
	}

	private static List<Role> rolePool(RolePool pool) {
		List<Role> out = new ArrayList<>();
		for (Role role : shuffledRoles()) {
			if (!isAssignableRole(role) || isRoleDisabled(role)) continue;
			if (role == WatheRoles.VIGILANTE && pool != RolePool.VIGILANTE) continue;
			if (pool.matches(role)) out.add(role);
		}
		return out;
	}

	private static List<Role> vigilantePool() {
		if (WatheRoles.VIGILANTE == null || !isAssignableRole(WatheRoles.VIGILANTE)
				|| isRoleDisabled(WatheRoles.VIGILANTE)) {
			return List.of();
		}
		return List.of(WatheRoles.VIGILANTE);
	}

	private static int countAssignedKillers(Map<Role, Integer> assignedCounts) {
		int total = 0;
		for (Map.Entry<Role, Integer> entry : assignedCounts.entrySet()) {
			Role role = entry.getKey();
			if (role != null && role.canUseKiller()) {
				total += Math.max(0, entry.getValue());
			}
		}
		return total;
	}

	private static int countAssignedNeutrals(Map<Role, Integer> assignedCounts) {
		int total = 0;
		for (Map.Entry<Role, Integer> entry : assignedCounts.entrySet()) {
			Role role = entry.getKey();
			if (role != null && !role.canUseKiller() && !role.isInnocent()) {
				total += Math.max(0, entry.getValue());
			}
		}
		return total;
	}

	private static List<Role> shuffledRoles() {
		List<Role> roles = new ArrayList<>(WatheRoles.ROLES);
		Collections.shuffle(roles, RANDOM);
		return roles;
	}

	private static boolean isAssignableRole(Role role) {
		if (role == null
				|| role == WatheRoles.CIVILIAN
				|| role == WatheRoles.DISCOVERY_CIVILIAN
				|| role == WatheRoles.LOOSE_END
				|| MapSelectRoles.MAFIOSO == role
				|| MapSelectRoles.JANITOR == role
				|| isMafiaDisabledForLobby(role)
				|| isRetiredExternalVulture(role)) {
			return false;
		}
		return !Harpymodloader.NON_MURDER_ROLES.contains(role);
	}

	private static boolean isMafiaDisabledForLobby(Role role) {
		return role == MapSelectRoles.GODFATHER
			&& assignmentPlayerCount > 0
			&& assignmentPlayerCount < GexpressConfig.getMafiaMinimumPlayers();
	}

	private static boolean isRetiredExternalVulture(Role role) {
		if (role == null || role.identifier() == null) return false;
		Identifier id = role.identifier();
		return "vulture".equals(id.getPath());
	}

	private static boolean isRoleDisabled(Role role) {
		HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
		return config.disabled != null && config.disabled.contains(role.identifier().toString());
	}

	private static void assignRole(GameWorldComponent game, ServerPlayerEntity player, Role role) {
		Role previous = game.getRole(player);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, previous);
		}
		game.addRole(player, role);
		if (!Harpymodloader.VANNILA_ROLES.contains(role)) {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
		}
	}

	private static ServerPlayerEntity takePlayer(List<ServerPlayerEntity> available, UUID playerId) {
		if (playerId == null) return null;
		for (int i = 0; i < available.size(); i++) {
			ServerPlayerEntity player = available.get(i);
			if (player.getUuid().equals(playerId)) {
				return available.remove(i);
			}
		}
		return null;
	}

	private static void sendRoleAnnouncements(GameWorldComponent game, List<ServerPlayerEntity> players) {
		int killerTeamCount = game.getAllKillerTeamPlayers().size();
		int nonKillerCount = Math.max(0, players.size() - killerTeamCount);
		for (ServerPlayerEntity player : players) {
			Role role = game.getRole(player);
			int roleIndex = roleAnnouncementIndex(role);
			ServerPlayNetworking.send(player, new AnnounceWelcomePayload(roleIndex, killerTeamCount, nonKillerCount));
		}
	}

	private static int roleAnnouncementIndex(Role role) {
		RoleAnnouncementTexts.RoleAnnouncementText text;
		if (role == WatheRoles.KILLER) {
			text = RoleAnnouncementTexts.KILLER;
		} else if (role == WatheRoles.VIGILANTE) {
			text = RoleAnnouncementTexts.VIGILANTE;
		} else if (role == WatheRoles.LOOSE_END) {
			text = RoleAnnouncementTexts.LOOSE_END;
		} else if (role == WatheRoles.CIVILIAN || role == null) {
			text = RoleAnnouncementTexts.CIVILIAN;
		} else {
			text = Harpymodloader.autogeneratedAnnouncements.get(role);
			if (text == null) text = RoleAnnouncementTexts.CIVILIAN;
		}
		int index = RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(text);
		if (index >= 0) return index;
		return RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(RoleAnnouncementTexts.CIVILIAN);
	}

	private static void assignForcedModifiers(GameWorldComponent game, WorldModifierComponent modifiers,
			List<ServerPlayerEntity> players) {
		Map<UUID, ServerPlayerEntity> byId = new HashMap<>();
		for (ServerPlayerEntity player : players) byId.put(player.getUuid(), player);

		for (Map.Entry<Modifier, List<UUID>> entry : Harpymodloader.FORCED_MODDED_MODIFIER.entrySet()) {
			Modifier modifier = entry.getKey();
			if (modifier == null || entry.getValue() == null) continue;
			for (UUID playerId : entry.getValue()) {
				ServerPlayerEntity player = byId.get(playerId);
				if (player == null || hasModifier(modifiers, player, modifier)) continue;
				if (isLoversModifier(modifier) && modifiers.getAllWithModifier(modifier).size() >= 2) continue;
				if (!canApplyModifier(game, modifiers, player, modifier)) continue;
				addModifier(modifiers, player, modifier);
			}
		}
	}

	private static void assignConfiguredModifiers(GameWorldComponent game, WorldModifierComponent modifiers,
			List<ServerPlayerEntity> players) {
		List<Modifier> modifierOrder = new ArrayList<>(HMLModifiers.MODIFIERS);
		Collections.shuffle(modifierOrder, RANDOM);
		for (Modifier modifier : modifierOrder) {
			if (modifier == null || isModifierDisabled(modifier)) continue;
			if (!passes(RoleModifierTuningConfig.getModifierChance(modifier.identifier().toString()))) continue;

			if (isLoversModifier(modifier)) {
				assignLoversModifier(game, modifiers, players, modifier);
				continue;
			}

			int amount = RoleModifierTuningConfig.getModifierMax(modifier.identifier().toString());
			int alreadyAssigned = modifiers.getAllWithModifier(modifier).size();
			int remaining = Math.max(0, amount - alreadyAssigned);
			for (int i = 0; i < remaining; i++) {
				ServerPlayerEntity player = randomModifierCandidate(game, modifiers, players, modifier);
				if (player == null) break;
				addModifier(modifiers, player, modifier);
			}
		}
	}

	private static void assignLoversModifier(GameWorldComponent game, WorldModifierComponent modifiers,
			List<ServerPlayerEntity> players, Modifier modifier) {
		int alreadyAssigned = modifiers.getAllWithModifier(modifier).size();
		if (alreadyAssigned >= 2) return;
		int needed = 2 - alreadyAssigned;
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			if (hasModifier(modifiers, player, modifier)) continue;
			if (!canApplyModifier(game, modifiers, player, modifier)) continue;
			candidates.add(player);
		}
		if (candidates.size() < needed) return;
		Collections.shuffle(candidates, RANDOM);
		for (int i = 0; i < needed; i++) {
			addModifier(modifiers, candidates.get(i), modifier);
		}
	}

	private static boolean isModifierDisabled(Modifier modifier) {
		HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
		return config.disabledModifiers != null
			&& config.disabledModifiers.contains(modifier.identifier().toString());
	}

	private static boolean isLoversModifier(Modifier modifier) {
		if (modifier == null || modifier.identifier() == null) return false;
		String namespace = modifier.identifier().getNamespace().toLowerCase(java.util.Locale.ROOT);
		String path = modifier.identifier().getPath().toLowerCase(java.util.Locale.ROOT);
		return namespace.contains("stupid") && path.contains("lover");
	}

	private static ServerPlayerEntity randomModifierCandidate(GameWorldComponent game, WorldModifierComponent modifiers,
			List<ServerPlayerEntity> players, Modifier modifier) {
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			if (hasModifier(modifiers, player, modifier)) continue;
			if (!canApplyModifier(game, modifiers, player, modifier)) continue;
			candidates.add(player);
		}
		if (candidates.isEmpty()) return null;
		return candidates.get(RANDOM.nextInt(candidates.size()));
	}

	private static boolean canApplyModifier(GameWorldComponent game, WorldModifierComponent modifiers,
			ServerPlayerEntity player, Modifier modifier) {
		int maxModifiers = GexpressConfig.getMaxModifiersPerPlayer();
		if (maxModifiers <= 0 || modifierCount(modifiers, player) >= maxModifiers) {
			return false;
		}
		Role role = game.getRole(player);
		if (isCivilianOnlyGexpressModifier(modifier) && (role == null || !role.isInnocent()
				|| game.canUseKillerFeatures(player))) {
			return false;
		}
		if (modifier.canOnlyBeAppliedTo != null
				&& !modifier.canOnlyBeAppliedTo.isEmpty()
				&& !modifier.canOnlyBeAppliedTo.contains(role)) {
			return false;
		}
		if (modifier.cannotBeAppliedTo != null && modifier.cannotBeAppliedTo.contains(role)) {
			return false;
		}
		if (modifier.killerOnly && !game.canUseKillerFeatures(player)) {
			return false;
		}
		return !modifier.civilianOnly || !game.canUseKillerFeatures(player);
	}

	private static int modifierCount(WorldModifierComponent modifiers, ServerPlayerEntity player) {
		List<Modifier> current = modifiers.getModifiers(player);
		return current == null ? 0 : current.size();
	}

	private static boolean isCivilianOnlyGexpressModifier(Modifier modifier) {
		if (modifier == null || modifier.identifier() == null) return false;
		Identifier id = modifier.identifier();
		return MapSelectModifiers.HUNGRY_ID.equals(id)
			|| MapSelectModifiers.THIRSTY_ID.equals(id)
			|| MapSelectModifiers.PARANOID_ID.equals(id);
	}

	private static boolean hasModifier(WorldModifierComponent modifiers, ServerPlayerEntity player, Modifier modifier) {
		List<Modifier> current = modifiers.getModifiers(player);
		return current != null && current.contains(modifier);
	}

	private static void addModifier(WorldModifierComponent modifiers, ServerPlayerEntity player, Modifier modifier) {
		modifiers.addModifier(player.getUuid(), modifier);
		ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
	}

	private static void grantVanillaRoleLoadouts(GameWorldComponent game, List<ServerPlayerEntity> players) {
		for (ServerPlayerEntity player : players) {
			if (game.getRole(player) == WatheRoles.VIGILANTE
					&& player.getInventory().count(WatheItems.REVOLVER) <= 0) {
				player.giveItemStack(WatheItems.REVOLVER.getDefaultStack());
			}
			Role role = game.getRole(player);
			if (role != null && MapSelectRoles.BOMB_SPECIALIST_ID.equals(role.identifier())) {
				if (player.getInventory().count(MapSelectItems.C4) <= 0) {
					player.giveItemStack(MapSelectItems.C4.getDefaultStack());
				}
				if (player.getInventory().count(MapSelectItems.C4_DETONATOR) <= 0) {
					player.giveItemStack(MapSelectItems.C4_DETONATOR.getDefaultStack());
				}
			}
		}
	}

	private static void resetStartingBalances(GameWorldComponent game, List<ServerPlayerEntity> players) {
		for (ServerPlayerEntity player : players) {
			Role role = game == null ? null : game.getRole(player);
			int balance = startingBalance(game, player, role);
			PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
			shop.setBalance(balance);
			PlayerShopComponent.KEY.sync(player);
		}
	}

	private static int startingBalance(GameWorldComponent game, ServerPlayerEntity player, Role role) {
		if (role == null || role.identifier() == null) {
			return game != null && game.canUseKillerFeatures(player) ? GameConstants.MONEY_START : 0;
		}
		Identifier id = role.identifier();
		if (MapSelectRoles.GODFATHER_ID.equals(id)) return GexpressConfig.getGodfatherStartingGold();
		if (MapSelectRoles.MAFIOSO_ID.equals(id)) return GexpressConfig.getMafiosoStartingGold();
		if (MapSelectRoles.JANITOR_ID.equals(id)) return GexpressConfig.getJanitorStartingGold();
		if (GexpressRoleShop.showsMoneyHud(player)) return GameConstants.MONEY_START;
		return game != null && game.canUseKillerFeatures(player) ? GameConstants.MONEY_START : 0;
	}

	private static boolean isMafiaRole(Role role) {
		if (role == null || role.identifier() == null) return false;
		Identifier id = role.identifier();
		return MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id);
	}

	private static void sendModifierAnnouncements(WorldModifierComponent modifiers, List<ServerPlayerEntity> players) {
		for (ServerPlayerEntity player : players) {
			List<Modifier> current = modifiers.getModifiers(player);
			if (current == null || current.isEmpty()) {
				if (!HMLModifiers.MODIFIERS.isEmpty()) {
					player.sendMessage(Text.translatable("announcement.no_modifiers").formatted(Formatting.GRAY), true);
				}
				continue;
			}

			MutableText message = Text.translatable("announcement.modifier").formatted(Formatting.YELLOW);
			message.append(Text.literal(" "));
			for (int i = 0; i < current.size(); i++) {
				if (i > 0) message.append(Text.literal(", ").formatted(Formatting.GRAY));
				Modifier modifier = current.get(i);
				message.append(modifier.getName(false).copy()
					.styled(style -> style.withColor(TextColor.fromRgb(modifier.color()))));
			}
			player.sendMessage(message, true);
		}
	}

	private static boolean passes(int chance) {
		int clamped = Math.max(RoleModifierTuningConfig.CHANCE_MIN,
			Math.min(RoleModifierTuningConfig.CHANCE_MAX, chance));
		return clamped >= RoleModifierTuningConfig.CHANCE_MAX || RANDOM.nextInt(100) < clamped;
	}

	private enum RolePool {
		KILLER {
			@Override
			boolean matches(Role role) {
				return role.canUseKiller();
			}
		},
		NEUTRAL {
			@Override
			boolean matches(Role role) {
				return !role.canUseKiller() && !role.isInnocent();
			}
		},
		CIVILIAN {
			@Override
			boolean matches(Role role) {
				return !role.canUseKiller() && role.isInnocent();
			}
		},
		VIGILANTE {
			@Override
			boolean matches(Role role) {
				return role == WatheRoles.VIGILANTE;
			}
		};

		abstract boolean matches(Role role);
	}
}
