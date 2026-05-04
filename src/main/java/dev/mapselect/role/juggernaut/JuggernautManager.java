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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
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
	private static final Map<UUID, Long> shieldRechargeUntil = new HashMap<>();
	private static final Set<UUID> equipped = new HashSet<>();
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final int MAX_STAGE = 5;
	private static final int SHIELD_STAGE = 2;
	private static final int GUN_SHIELD_STAGE = 4;
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
			if (victim instanceof ServerPlayerEntity juggernaut
					&& canUseJuggernautHere(juggernaut.getWorld(), juggernaut)
					&& tryBlockWithShield(juggernaut, reason)) {
				return false;
			}
			removeLoadout(victim);
			equipped.remove(victim.getUuid());
			shieldRechargeUntil.remove(victim.getUuid());
		}
		if (!(killer instanceof ServerPlayerEntity juggernaut)) return true;
		if (victim == juggernaut || !isJuggernaut(juggernaut)) return true;
		if (!canUseJuggernautHere(juggernaut.getWorld(), juggernaut)) return true;

		UUID id = juggernaut.getUuid();
		int kills = killCounts.merge(id, 1, Integer::sum);
		int stage = stageForKills(kills);
		int cooldownTicks = cooldownTicksAtStage(stage);
		pendingCooldowns.put(id, cooldownTicks);
		if (stage >= SHIELD_STAGE && !shieldRechargeUntil.containsKey(id)) {
			AbilityCooldownSync.clear(juggernaut, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
		}
		juggernaut.sendMessage(Texts.stage(stage, cooldownTicks), true);
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
			syncShieldCooldown(player);
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
		applyWeaponCooldown(player, cooldownTicksAtStage(stageForKills(killCounts.getOrDefault(player.getUuid(), 0))));
		syncShieldCooldown(player);
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

	private static int cooldownTicksAtStage(int stage) {
		int initial = GexpressConfig.getJuggernautInitialCooldownSeconds();
		int reduction = GexpressConfig.getJuggernautCooldownReductionSeconds();
		int minimum = Math.min(initial, GexpressConfig.getJuggernautMinimumCooldownSeconds());
		int seconds = Math.max(minimum, initial - cooldownReductionSteps(stage) * reduction);
		return seconds * 20;
	}

	private static int stageForKills(int kills) {
		return Math.min(MAX_STAGE, Math.max(0, kills));
	}

	private static int cooldownReductionSteps(int stage) {
		return Math.min(3, (Math.min(MAX_STAGE, Math.max(0, stage)) + 1) / 2);
	}

	private static boolean tryBlockWithShield(ServerPlayerEntity juggernaut, Identifier reason) {
		boolean knife = dev.doctor4t.wathe.game.GameConstants.DeathReasons.KNIFE.equals(reason);
		boolean gun = dev.doctor4t.wathe.game.GameConstants.DeathReasons.GUN.equals(reason);
		if (!knife && !gun) return false;

		int stage = stageForKills(killCounts.getOrDefault(juggernaut.getUuid(), 0));
		if (stage < SHIELD_STAGE) return false;
		if (gun && stage < GUN_SHIELD_STAGE) return false;

		long remaining = shieldRechargeRemainingTicks(juggernaut);
		if (remaining > 0L) {
			syncShieldCooldown(juggernaut, remaining);
			return false;
		}

		long rechargeTicks = (long) GexpressConfig.getJuggernautShieldRechargeSeconds() * 20L;
		shieldRechargeUntil.put(juggernaut.getUuid(), juggernaut.getWorld().getTime() + rechargeTicks);
		syncShieldCooldown(juggernaut, rechargeTicks);
		juggernaut.getWorld().playSound(null, juggernaut.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK,
			SoundCategory.PLAYERS, 0.9F, 0.85F);
		juggernaut.sendMessage(Text.literal("Juggernaut shield blocked the hit."), true);
		return true;
	}

	private static long shieldRechargeRemainingTicks(ServerPlayerEntity player) {
		Long until = shieldRechargeUntil.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			shieldRechargeUntil.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	private static void syncShieldCooldown(ServerPlayerEntity player) {
		int stage = stageForKills(killCounts.getOrDefault(player.getUuid(), 0));
		if (stage < SHIELD_STAGE) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
			return;
		}
		syncShieldCooldown(player, shieldRechargeRemainingTicks(player));
	}

	private static void syncShieldCooldown(ServerPlayerEntity player, long remainingTicks) {
		if (remainingTicks <= 0L) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
			return;
		}
		AbilityCooldownSync.send(player, AbilityCooldownPayload.JUGGERNAUT_SHIELD, remainingTicks,
			(long) GexpressConfig.getJuggernautShieldRechargeSeconds() * 20L, false);
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
		shieldRechargeUntil.clear();
		equipped.clear();
	}

	public static boolean shouldBlockLoadoutDrop(PlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) return false;
		if (!isJuggernaut(player)) return false;
		if (!canUseJuggernautHere(player.getWorld(), player)) return false;
		return isLoadoutItem(stack);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(killCounts), new HashMap<>(pendingCooldowns),
			new HashMap<>(shieldRechargeUntil), new HashSet<>(equipped));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clearRoundState();
		if (state == null) return;
		killCounts.putAll(state.killCounts());
		pendingCooldowns.putAll(state.pendingCooldowns());
		shieldRechargeUntil.putAll(state.shieldRechargeUntil());
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
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
			serverPlayer.playerScreenHandler.syncState();
		}
	}

	private static final class Texts {
		private static Text stage(int stage, int cooldownTicks) {
			return Text.literal("Juggernaut stage " + stage + "/" + MAX_STAGE
				+ " - weapons: " + Math.max(0, cooldownTicks / 20) + "s.");
		}
	}

	public record TimeState(Map<UUID, Integer> killCounts, Map<UUID, Integer> pendingCooldowns,
			Map<UUID, Long> shieldRechargeUntil, Set<UUID> equipped) {}
}
