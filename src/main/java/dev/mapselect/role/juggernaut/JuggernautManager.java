package dev.mapselect.role.juggernaut;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JuggernautManager {
	private static final Map<UUID, Integer> killCounts = new HashMap<>();
	private static final Map<UUID, Integer> pendingCooldowns = new HashMap<>();
	private static final Set<UUID> equipped = new HashSet<>();
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static int ticksUntilNextCheck = 0;

	private JuggernautManager() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register(JuggernautManager::onFinishInitialize);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			removeAllLoadouts(world);
			clearRoundState();
		});
		AllowPlayerDeath.EVENT.register(JuggernautManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(JuggernautManager::tick);
	}

	private static void onFinishInitialize(World world, GameWorldComponent game) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (serverWorld.getRegistryKey() != World.OVERWORLD) return;

		clearRoundState();
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (isJuggernaut(player)) {
				grantLoadout(player);
			}
		}
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim == null || victim.getWorld().isClient) return true;
		if (isJuggernaut(victim)) {
			removeLoadout(victim);
			equipped.remove(victim.getUuid());
		}
		if (!(killer instanceof ServerPlayerEntity juggernaut)) return true;
		if (victim == juggernaut || !isJuggernaut(juggernaut)) return true;
		if (!canUseJuggernautHere(juggernaut.getWorld(), juggernaut)) return true;

		UUID id = juggernaut.getUuid();
		int kills = killCounts.merge(id, 1, Integer::sum);
		int cooldownTicks = cooldownTicksAfterKills(kills);
		pendingCooldowns.put(id, cooldownTicks);
		juggernaut.sendMessage(Texts.cooldown(cooldownTicks), true);
		return true;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		applyPendingCooldowns(world);

		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean activeGame = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!activeGame && !GexpressTestState.hasRoleTesters()) {
			removeAllLoadouts(world);
			clearRoundState();
			ticksUntilNextCheck = 0;
			return;
		}

		if (ticksUntilNextCheck > 0) {
			ticksUntilNextCheck--;
			return;
		}
		ticksUntilNextCheck = CHECK_INTERVAL_TICKS - 1;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!isJuggernaut(player)) continue;
			if (!GameFunctions.isPlayerAliveAndSurvival(player) && !GexpressTestState.isRoleTester(player)) continue;
			if (!equipped.contains(player.getUuid())) {
				grantLoadout(player);
			}
		}
	}

	private static void applyPendingCooldowns(ServerWorld world) {
		if (pendingCooldowns.isEmpty()) return;

		for (Map.Entry<UUID, Integer> entry : List.copyOf(pendingCooldowns.entrySet())) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (player == null || player.getWorld() != world) continue;
			applyWeaponCooldown(player, entry.getValue());
			pendingCooldowns.remove(entry.getKey());
		}
	}

	private static void grantLoadout(ServerPlayerEntity player) {
		giveIfMissing(player, WatheItems.KNIFE);
		giveIfMissing(player, WatheItems.REVOLVER);
		killCounts.putIfAbsent(player.getUuid(), 0);
		equipped.add(player.getUuid());
		applyWeaponCooldown(player, cooldownTicksAfterKills(killCounts.getOrDefault(player.getUuid(), 0)));
		MapSelect.LOGGER.debug("Equipped Juggernaut {}", player.getName().getString());
	}

	private static void giveIfMissing(ServerPlayerEntity player, Item item) {
		if (player.getInventory().count(item) > 0) return;
		ItemStack stack = item.getDefaultStack();
		player.getInventory().insertStack(stack);
	}

	private static void applyWeaponCooldown(ServerPlayerEntity player, int cooldownTicks) {
		int ticks = Math.max(0, cooldownTicks);
		player.getItemCooldownManager().set(WatheItems.KNIFE, ticks);
		player.getItemCooldownManager().set(WatheItems.REVOLVER, ticks);
		if (ticks > 0) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.JUGGERNAUT_WEAPONS, ticks, ticks, false);
		} else {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_WEAPONS);
		}
	}

	private static int cooldownTicksAfterKills(int kills) {
		int initial = GexpressConfig.getJuggernautInitialCooldownSeconds();
		int reduction = GexpressConfig.getJuggernautCooldownReductionSeconds();
		int minimum = Math.min(initial, GexpressConfig.getJuggernautMinimumCooldownSeconds());
		int seconds = Math.max(minimum, initial - Math.max(0, kills) * reduction);
		return seconds * 20;
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null) return false;
		if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;

		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		List<ServerPlayerEntity> aliveJuggernauts = alive.stream()
			.filter(JuggernautManager::isJuggernaut)
			.toList();
		if (aliveJuggernauts.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == 1 && alive.getFirst() == aliveJuggernauts.getFirst()) {
			ServerPlayerEntity winner = alive.getFirst();
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.juggernaut",
				MapSelectRoles.JUGGERNAUT == null ? 0x8F1F1F : MapSelectRoles.JUGGERNAUT.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	public static boolean isJuggernaut(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.JUGGERNAUT_ID.equals(role.identifier());
	}

	private static boolean canUseJuggernautHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static void clearRoundState() {
		killCounts.clear();
		pendingCooldowns.clear();
		equipped.clear();
	}

	public static boolean shouldBlockLoadoutDrop(PlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) return false;
		if (!isJuggernaut(player)) return false;
		if (!canUseJuggernautHere(player.getWorld(), player)) return false;
		return isLoadoutItem(stack);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(killCounts), new HashMap<>(pendingCooldowns), new HashSet<>(equipped));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clearRoundState();
		if (state == null) return;
		killCounts.putAll(state.killCounts());
		pendingCooldowns.putAll(state.pendingCooldowns());
		equipped.addAll(state.equipped());
	}

	private static boolean isLoadoutItem(ItemStack stack) {
		return stack.isOf(WatheItems.KNIFE) || stack.isOf(WatheItems.REVOLVER);
	}

	private static void removeAllLoadouts(World world) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		Set<UUID> equippedPlayers = Set.copyOf(equipped);
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (equippedPlayers.contains(player.getUuid()) || isJuggernaut(player)) {
				removeLoadout(player);
			}
		}
	}

	private static void removeLoadout(PlayerEntity player) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (isLoadoutItem(stack)) {
				player.getInventory().setStack(slot, ItemStack.EMPTY);
			}
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_WEAPONS);
			serverPlayer.playerScreenHandler.syncState();
		}
	}

	private static final class Texts {
		private static net.minecraft.text.Text cooldown(int cooldownTicks) {
			return net.minecraft.text.Text.literal("Juggernaut cooldown: " + Math.max(0, cooldownTicks / 20) + "s.");
		}
	}

	public record TimeState(Map<UUID, Integer> killCounts, Map<UUID, Integer> pendingCooldowns, Set<UUID> equipped) {}
}
