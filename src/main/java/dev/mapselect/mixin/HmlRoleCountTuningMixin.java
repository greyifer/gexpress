package dev.mapselect.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.role.RoleModifierTuningBridge;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = ModdedMurderGameMode.class, remap = false)
public abstract class HmlRoleCountTuningMixin {
	@Unique
	private static final ThreadLocal<Integer> GEXPRESS_PLAYER_COUNT = ThreadLocal.withInitial(() -> 0);

	@Inject(method = "assignVannilaRoles", at = @At("HEAD"))
	private void gexpress$captureVanillaRolePlayers(ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfoReturnable<Integer> cir) {
		int playerCount = players == null ? 0 : players.size();
		GEXPRESS_PLAYER_COUNT.set(playerCount);
		RoleModifierTuningBridge.prepareForGame(playerCount);
	}

	@Inject(method = "assignVannilaRoles", at = @At("RETURN"))
	private void gexpress$clearVanillaRolePlayers(ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfoReturnable<Integer> cir) {
		GEXPRESS_PLAYER_COUNT.remove();
	}

	@Inject(method = "assignCivilianReplacingRoles", at = @At("HEAD"))
	private void gexpress$captureCivilianRolePlayers(int killerCount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		GEXPRESS_PLAYER_COUNT.set(players == null ? 0 : players.size());
	}

	@Inject(method = "assignCivilianReplacingRoles", at = @At("RETURN"))
	private void gexpress$clearCivilianRolePlayers(int killerCount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		gexpress$ensureSpecialFamilyStarters(world, game, players);
		GEXPRESS_PLAYER_COUNT.remove();
	}

	@Inject(method = "assignKillerReplacingRoles", at = @At("HEAD"))
	private void gexpress$prepareKillerReplacingRoles(int killerCount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		GEXPRESS_PLAYER_COUNT.set(players == null ? 0 : players.size());
	}

	@Inject(method = "assignKillerReplacingRoles", at = @At("RETURN"))
	private void gexpress$clearKillerReplacingRolePlayers(int killerCount, ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		GEXPRESS_PLAYER_COUNT.remove();
	}

	@ModifyArg(method = "assignVannilaRoles", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/ScoreboardRoleSelectorComponent;assignKillers(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/cca/GameWorldComponent;Ljava/util/List;I)I"),
		index = 3)
	private int gexpress$customKillerAmount(int original) {
		if (!gexpress$passesRoleChance("wathe:killer")) return 0;
		return gexpress$sideRoleAmount(original, GexpressConfig.getMaxKillerAmount(),
			GexpressConfig.getPlayersPerKiller(), true);
	}

	@ModifyArg(method = "assignVannilaRoles", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/ScoreboardRoleSelectorComponent;assignVigilantes(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/cca/GameWorldComponent;Ljava/util/List;I)V"),
		index = 3)
	private int gexpress$customVigilanteAmount(int original) {
		if (!gexpress$passesRoleChance("wathe:vigilante")) return 0;
		return gexpress$sideRoleAmount(original, GexpressConfig.getMaxVigilanteAmount(),
			GexpressConfig.getPlayersPerVigilante(), false);
	}

	@ModifyVariable(method = "assignCivilianReplacingRoles", at = @At(value = "STORE"), index = 8)
	private int gexpress$customNeutralAmount(int original) {
		return gexpress$sideRoleAmount(original, GexpressConfig.getMaxNeutralAmount(),
			GexpressConfig.getPlayersPerNeutral(), false);
	}

	@Unique
	private static int gexpress$sideRoleAmount(int original, int configuredMax, int playersPerRole, boolean atLeastOne) {
		int players = Math.max(0, GEXPRESS_PLAYER_COUNT.get());
		int automatic = gexpress$automaticRoleAmount(players, playersPerRole, atLeastOne);
		if (!GexpressConfig.useCustomRoleCounts()) return automatic;
		if (configuredMax >= RoleModifierTuningConfig.MAX_MAX) return automatic;
		int cap = Math.max(0, Math.min(players, configuredMax));
		return Math.min(automatic, cap);
	}

	@Unique
	private static int gexpress$automaticRoleAmount(int players, int playersPerRole, boolean atLeastOne) {
		if (players <= 0) return 0;
		int divisor = Math.max(1, playersPerRole);
		int amount = players / divisor;
		if (atLeastOne && players > 1) amount = Math.max(1, amount);
		return Math.max(0, Math.min(players, amount));
	}

	@Unique
	private static boolean gexpress$passesRoleChance(String roleId) {
		int chance = RoleModifierTuningConfig.getRoleChance(roleId);
		int clamped = Math.max(RoleModifierTuningConfig.CHANCE_MIN,
			Math.min(RoleModifierTuningConfig.CHANCE_MAX, chance));
		return clamped >= RoleModifierTuningConfig.CHANCE_MAX
			|| (clamped > 0 && ThreadLocalRandom.current().nextInt(100) < clamped);
	}

	@Unique
	private static void gexpress$ensureSpecialFamilyStarters(ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> players) {
		if (world == null || game == null || players == null || players.isEmpty()) return;
		if (players.size() < GexpressConfig.getMafiaMinimumPlayers()) return;

		GexpressConfig.SpecialRoleOccurrence occurrence = GexpressConfig.getSpecialRoleOccurrence();
		Set<ServerPlayerEntity> reserved = new HashSet<>();
		boolean changed = false;
		if (occurrence.mafiaEnabled() && MapSelectRoles.GODFATHER != null
				&& !gexpress$hasRole(game, players, MapSelectRoles.GODFATHER)) {
			ServerPlayerEntity candidate = gexpress$specialStarterCandidate(game, players, reserved);
			if (candidate != null) {
				gexpress$assignSpecialStarter(game, candidate, MapSelectRoles.GODFATHER);
				reserved.add(candidate);
				changed = true;
			}
		}
		if (occurrence.covenantEnabled() && MapSelectRoles.DRACULA != null
				&& !gexpress$hasRole(game, players, MapSelectRoles.DRACULA)) {
			ServerPlayerEntity candidate = gexpress$specialStarterCandidate(game, players, reserved);
			if (candidate != null) {
				gexpress$assignSpecialStarter(game, candidate, MapSelectRoles.DRACULA);
				reserved.add(candidate);
				changed = true;
			}
		}
		if (changed) game.sync();
	}

	@Unique
	private static boolean gexpress$hasRole(GameWorldComponent game, List<ServerPlayerEntity> players, Role role) {
		if (game == null || players == null || role == null) return false;
		for (ServerPlayerEntity player : players) {
			if (player != null && game.isRole(player, role)) return true;
		}
		return false;
	}

	@Unique
	private static ServerPlayerEntity gexpress$specialStarterCandidate(GameWorldComponent game,
			List<ServerPlayerEntity> players, Set<ServerPlayerEntity> reserved) {
		List<ServerPlayerEntity> civilians = new ArrayList<>();
		List<ServerPlayerEntity> innocents = new ArrayList<>();
		List<ServerPlayerEntity> fallback = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			if (player == null || reserved.contains(player)) continue;
			Role role = game.getRole(player);
			if (role == null || gexpress$isFamilyRole(role)) continue;
			if (role.canUseKiller() || game.canUseKillerFeatures(player)) continue;
			if (game.isRole(player, WatheRoles.CIVILIAN)) {
				civilians.add(player);
			} else if (role.isInnocent() && !game.isRole(player, WatheRoles.VIGILANTE)) {
				innocents.add(player);
			} else {
				fallback.add(player);
			}
		}
		ServerPlayerEntity candidate = gexpress$random(civilians);
		if (candidate != null) return candidate;
		candidate = gexpress$random(innocents);
		return candidate == null ? gexpress$random(fallback) : candidate;
	}

	@Unique
	private static boolean gexpress$isFamilyRole(Role role) {
		if (role == null || role.identifier() == null) return false;
		return MapSelectRoles.GODFATHER_ID.equals(role.identifier())
			|| MapSelectRoles.MAFIOSO_ID.equals(role.identifier())
			|| MapSelectRoles.JANITOR_ID.equals(role.identifier())
			|| MapSelectRoles.PICKPOCKET_ID.equals(role.identifier())
			|| MapSelectRoles.BURGLAR_ID.equals(role.identifier())
			|| MapSelectRoles.DRACULA_ID.equals(role.identifier())
			|| MapSelectRoles.VAMPIRE_ID.equals(role.identifier());
	}

	@Unique
	private static ServerPlayerEntity gexpress$random(List<ServerPlayerEntity> players) {
		if (players == null || players.isEmpty()) return null;
		return players.get(ThreadLocalRandom.current().nextInt(players.size()));
	}

	@Unique
	private static void gexpress$assignSpecialStarter(GameWorldComponent game, ServerPlayerEntity player, Role role) {
		if (game == null || player == null || role == null) return;
		Role previous = game.getRole(player);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			gexpress$safeRemoveRole(player, previous);
		}
		game.addRole(player, role);
		if (!Harpymodloader.VANNILA_ROLES.contains(role)) {
			gexpress$safeAssignRole(player, role);
		}
	}

	@Unique
	private static void gexpress$safeAssignRole(ServerPlayerEntity player, Role role) {
		try {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleAssigned listener failed while reserving {} for {}.",
				role.identifier(), player.getName().getString(), t);
		}
	}

	@Unique
	private static void gexpress$safeRemoveRole(ServerPlayerEntity player, Role role) {
		try {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, role);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModdedRoleRemoved listener failed while reserving {} for {}.",
				role.identifier(), player.getName().getString(), t);
		}
	}
}
