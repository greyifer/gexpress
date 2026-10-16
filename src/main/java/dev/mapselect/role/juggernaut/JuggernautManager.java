package dev.mapselect.role.juggernaut;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheBlocks;
import dev.mapselect.MapSelect;
import dev.mapselect.item.KeycardItem;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.juggernaut.JuggernautStagePayload;
import dev.mapselect.network.role.juggernaut.JuggernautDoorBreakPayload;
import dev.mapselect.network.role.juggernaut.JuggernautDoorDirectionPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JuggernautManager {
	public static final int DOOR_BREAK_COOLDOWN_TICKS = 45 * 20;
	private static final double DOOR_BREAK_RANGE = 5.0D;
	private static final int CHECK_INTERVAL_TICKS = 10;
	private static final int KNIFE_INITIAL_SECONDS = 120;
	private static final int REVOLVER_INITIAL_SECONDS = 120;
	private static final int KNIFE_KILL_REDUCTION_SECONDS = 20;
	private static final int REVOLVER_KILL_REDUCTION_SECONDS = 20;
	private static final int KNIFE_MINIMUM_SECONDS = 20;
	private static final int REVOLVER_MINIMUM_SECONDS = 40;
	private static final Map<UUID, Integer> knifeCooldownSeconds = new HashMap<>();
	private static final Map<UUID, Integer> revolverCooldownSeconds = new HashMap<>();
	private static final Map<UUID, PendingCooldown> pendingCooldowns = new HashMap<>();
	private static final Map<UUID, Long> doorBreakCooldownUntil = new HashMap<>();
	private static final Map<RegistryKey<World>, Map<BlockPos, Direction>> brokenDoorsByWorld = new HashMap<>();
	private static final Map<RegistryKey<World>, Set<BlockPos>> pendingDoorResetsByWorld = new HashMap<>();
	private static final Set<UUID> creditedDeadVictims = new HashSet<>();
	private static final Set<UUID> equipped = new HashSet<>();
	private static int ticksUntilNextCheck;

	private JuggernautManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(JuggernautDoorBreakPayload.ID,
			JuggernautDoorBreakPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(JuggernautStagePayload.ID, JuggernautStagePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(JuggernautDoorDirectionPayload.ID,
			JuggernautDoorDirectionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JuggernautDoorBreakPayload.ID,
			(payload, context) -> context.server().execute(() -> tryBreakDoor(context.player())));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				syncDoorCooldown(handler.player);
				syncBrokenDoorDirections(handler.player);
			}));
		GameEvents.ON_FINISH_INITIALIZE.register(JuggernautManager::onFinishInitialize);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			queueBrokenDoorResets(world);
			removeAllLoadouts(world);
			clearRoundState();
		});
		ServerTickEvents.END_WORLD_TICK.register(JuggernautManager::tick);
	}

	public static void tryBreakDoor(ServerPlayerEntity player) {
		if (player == null || PelicanManager.isStashed(player) || !isJuggernaut(player)
				|| !canUseJuggernautHere(player.getWorld(), player)
				|| (!GameFunctions.isPlayerAliveAndSurvival(player)
					&& !GexpressTestState.isRoleTester(player))) return;

		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(player);
		long remaining = creativeBypass ? 0L : AbilityCooldowns.remaining(doorBreakCooldownUntil, player);
		if (remaining > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK,
				remaining, DOOR_BREAK_COOLDOWN_TICKS, false);
			player.sendMessage(Text.literal("Door Break ready in "
				+ Math.max(1L, AbilityCooldowns.secondsCeil(remaining)) + "s."), true);
			return;
		}

		ServerWorld world = player.getServerWorld();
		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(DOOR_BREAK_RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(start, end,
			RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		DoorBlockEntity door = hit != null && hit.getType() == HitResult.Type.BLOCK
			? KeycardItem.findDoor(world, hit.getBlockPos()) : null;
		if (door == null) {
			player.sendMessage(Text.literal("Look at a nearby door to break it down."), true);
			return;
		}
		if (!door.getCachedState().isOf(WatheBlocks.SMALL_WOOD_DOOR)) {
			player.sendMessage(Text.literal("Door Break only works on small wooden doors."), true);
			return;
		}
		if (door.isBlasted()) {
			player.sendMessage(Text.literal("That door is already broken down."), true);
			return;
		}
		Direction facing = door.getFacing();
		if (facing == null || hit.getSide().getAxis() != facing.getAxis()) {
			player.sendMessage(Text.literal("Aim at the broad face of the door."), true);
			return;
		}
		Direction fallDirection = JuggernautDoorFallDirection.resolve(facing, player.getRotationVec(1.0F));
		door.blast();
		door.sync();
		BlockPos doorPos = door.getPos().toImmutable();
		brokenDoorsByWorld.computeIfAbsent(world.getRegistryKey(), ignored -> new HashMap<>())
			.put(doorPos, fallDirection);
		broadcastDoorDirection(world, JuggernautDoorDirectionPayload.set(doorPos, fallDirection));
		world.playSound(null, door.getPos(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
			SoundCategory.BLOCKS, 1.1F, 0.9F);
		if (creativeBypass) {
			AbilityCooldowns.start(doorBreakCooldownUntil, player, 0L);
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK);
		} else {
			AbilityCooldowns.start(doorBreakCooldownUntil, player, DOOR_BREAK_COOLDOWN_TICKS);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK,
				DOOR_BREAK_COOLDOWN_TICKS, DOOR_BREAK_COOLDOWN_TICKS, false);
		}
	}

	private static void syncDoorCooldown(ServerPlayerEntity player) {
		if (player == null || !isJuggernaut(player)) return;
		long remaining = AbilityCooldowns.remaining(doorBreakCooldownUntil, player);
		if (remaining > 0L) {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK,
				remaining, DOOR_BREAK_COOLDOWN_TICKS, false);
		} else {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK);
		}
	}

	private static void onFinishInitialize(World world, GameWorldComponent game) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(serverWorld)) return;

		// Safety net for a prior round interrupted before finalization completed.
		queueBrokenDoorResets(serverWorld);
		clearRoundState();
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (isJuggernaut(player)) grantLoadout(player);
		}
	}

	public static void onCommittedDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim == null || victim.getWorld().isClient) return;
		if (isJuggernaut(victim)) {
			removeLoadout(victim);
			clearPlayerState(victim.getUuid());
		}
		if (!(killer instanceof ServerPlayerEntity juggernaut)) return;
		if (victim == juggernaut || !isJuggernaut(juggernaut)) return;
		if (!canUseJuggernautHere(juggernaut.getWorld(), juggernaut)) return;

		WeaponKind weapon = weaponKind(reason);
		if (weapon == null) return;
		if (!creditedDeadVictims.add(victim.getUuid())) return;
		reduceWeaponCooldown(juggernaut, weapon);
	}

	private static void tick(ServerWorld world) {
		resetQueuedDoors(world);
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world)) return;

		applyPendingCooldowns(world);
		creditedDeadVictims.removeIf(victimId -> {
			ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(victimId);
			return victim != null && victim.getWorld() == world && GameFunctions.isPlayerAliveAndSurvival(victim);
		});

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
			grantLoadout(player);
		}
	}

	private static void queueBrokenDoorResets(World eventWorld) {
		if (!(eventWorld instanceof ServerWorld world)) return;
		RegistryKey<World> key = world.getRegistryKey();
		Map<BlockPos, Direction> broken = brokenDoorsByWorld.remove(key);
		if (broken != null && !broken.isEmpty()) {
			pendingDoorResetsByWorld.computeIfAbsent(key, ignored -> new HashSet<>()).addAll(broken.keySet());
		}
		resetQueuedDoors(world);
	}

	private static void syncBrokenDoorDirections(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)
				|| !ServerPlayNetworking.canSend(player, JuggernautDoorDirectionPayload.ID)) return;
		Map<BlockPos, Direction> directions = brokenDoorsByWorld.get(world.getRegistryKey());
		if (directions == null) return;
		for (Map.Entry<BlockPos, Direction> entry : directions.entrySet()) {
			ServerPlayNetworking.send(player,
				JuggernautDoorDirectionPayload.set(entry.getKey(), entry.getValue()));
		}
	}

	private static void broadcastDoorDirection(ServerWorld world,
			JuggernautDoorDirectionPayload payload) {
		if (world == null || payload == null) return;
		for (ServerPlayerEntity viewer : PlayerLookup.world(world)) {
			if (ServerPlayNetworking.canSend(viewer, JuggernautDoorDirectionPayload.ID)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private static void resetQueuedDoors(ServerWorld world) {
		if (world == null) return;
		RegistryKey<World> key = world.getRegistryKey();
		Set<BlockPos> pending = pendingDoorResetsByWorld.get(key);
		if (pending == null || pending.isEmpty()) return;
		for (BlockPos pos : Set.copyOf(pending)) {
			if (resetDoor(world, pos)) {
				pending.remove(pos);
				broadcastDoorDirection(world, JuggernautDoorDirectionPayload.clear(pos));
			}
		}
		if (pending.isEmpty()) pendingDoorResetsByWorld.remove(key);
	}

	private static boolean resetDoor(ServerWorld world, BlockPos pos) {
		if (!(world.getBlockEntity(pos) instanceof DoorBlockEntity door)) {
			// The door was removed or restored by the map resetter, so nothing remains to clean.
			return true;
		}
		door.setBlasted(false);
		door.setCloseCountdown(0);
		if (door.isOpen()) door.toggle(true);
		door.setBlasted(false);
		door.setCloseCountdown(0);
		door.sync();
		// A break and round end can occur on the same world tick. DoorBlockEntity deliberately
		// ignores a second toggle that tick, so retain it for the next server-tick retry.
		return !door.isOpen() && !door.isBlasted();
	}

	private static void grantLoadout(ServerPlayerEntity player) {
		giveIfMissing(player, WatheItems.KNIFE);
		giveIfMissing(player, WatheItems.REVOLVER);

		UUID id = player.getUuid();
		knifeCooldownSeconds.putIfAbsent(id, KNIFE_INITIAL_SECONDS);
		revolverCooldownSeconds.putIfAbsent(id, REVOLVER_INITIAL_SECONDS);
		if (equipped.add(id)) {
			applyWeaponCooldown(player, WeaponKind.KNIFE, knifeCooldownSeconds.get(id) * 20);
			applyWeaponCooldown(player, WeaponKind.REVOLVER, revolverCooldownSeconds.get(id) * 20);
			clearStage(player);
			MapSelect.LOGGER.debug("Equipped Juggernaut {}", player.getName().getString());
		}
	}

	private static void giveIfMissing(ServerPlayerEntity player, Item item) {
		if (dev.mapselect.role.RoleItemInventory.count(player, item) > 0) return;
		ItemStack stack = item.getDefaultStack();
		player.getInventory().insertStack(stack);
	}

	private static void reduceWeaponCooldown(ServerPlayerEntity player, WeaponKind weapon) {
		UUID id = player.getUuid();
		Map<UUID, Integer> cooldowns = cooldowns(weapon);
		int fallback = weapon == WeaponKind.KNIFE ? KNIFE_INITIAL_SECONDS : REVOLVER_INITIAL_SECONDS;
		int reduction = weapon == WeaponKind.KNIFE
			? KNIFE_KILL_REDUCTION_SECONDS
			: REVOLVER_KILL_REDUCTION_SECONDS;
		int minimum = weapon == WeaponKind.KNIFE
			? KNIFE_MINIMUM_SECONDS
			: REVOLVER_MINIMUM_SECONDS;
		int previousSeconds = cooldowns.getOrDefault(id, fallback);
		if (previousSeconds <= minimum) return;
		int seconds = Math.max(minimum, previousSeconds - reduction);
		cooldowns.put(id, seconds);
		int ticks = seconds * 20;
		pendingCooldowns.put(id, new PendingCooldown(weapon, ticks));
		sendWeaponCooldown(player, weapon, ticks);
		player.sendMessage(Text.literal(weapon.label() + " cooldown is now " + seconds + "s."), true);
	}

	private static void applyPendingCooldowns(ServerWorld world) {
		if (pendingCooldowns.isEmpty()) return;

		for (Map.Entry<UUID, PendingCooldown> entry : List.copyOf(pendingCooldowns.entrySet())) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (player == null || player.getWorld() != world) continue;
			PendingCooldown pending = entry.getValue();
			applyWeaponCooldown(player, pending.weapon(), pending.ticks());
			pendingCooldowns.remove(entry.getKey());
		}
	}

	private static void applyWeaponCooldown(ServerPlayerEntity player, WeaponKind weapon, int cooldownTicks) {
		int ticks = GexpressTestState.hasCreativeAbilityBypass(player) ? 0 : Math.max(0, cooldownTicks);
		player.getItemCooldownManager().set(weapon.item(), ticks);
		sendWeaponCooldown(player, weapon, ticks);
	}

	public static void applyCurrentWeaponCooldown(ServerPlayerEntity player, WeaponKind weapon) {
		if (player == null || weapon == null || !isJuggernaut(player)) return;
		int fallback = weapon == WeaponKind.KNIFE ? KNIFE_INITIAL_SECONDS : REVOLVER_INITIAL_SECONDS;
		int seconds = cooldowns(weapon).getOrDefault(player.getUuid(), fallback);
		applyWeaponCooldown(player, weapon, seconds * 20);
	}

	private static void sendWeaponCooldown(ServerPlayerEntity player, WeaponKind weapon, int ticks) {
		String key = weapon == WeaponKind.KNIFE
			? AbilityCooldownPayload.JUGGERNAUT_KNIFE
			: AbilityCooldownPayload.JUGGERNAUT_REVOLVER;
		int total = weapon == WeaponKind.KNIFE ? KNIFE_INITIAL_SECONDS * 20 : REVOLVER_INITIAL_SECONDS * 20;
		if (ticks > 0) {
			AbilityCooldownSync.send(player, key, ticks, total, false);
		} else {
			AbilityCooldownSync.clear(player, key);
		}
		AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_WEAPONS);
		AbilityCooldownSync.clear(player, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
	}

	private static Map<UUID, Integer> cooldowns(WeaponKind weapon) {
		return weapon == WeaponKind.KNIFE ? knifeCooldownSeconds : revolverCooldownSeconds;
	}

	private static WeaponKind weaponKind(Identifier reason) {
		if (isKnifeDeath(reason)) return WeaponKind.KNIFE;
		if (isGunDeath(reason)) return WeaponKind.REVOLVER;
		return null;
	}

	private static boolean isKnifeDeath(Identifier reason) {
		if (dev.doctor4t.wathe.game.GameConstants.DeathReasons.KNIFE.equals(reason)) return true;
		String path = reason == null ? "" : reason.getPath().toLowerCase(java.util.Locale.ROOT);
		return path.contains("knife") || path.contains("stab");
	}

	private static boolean isGunDeath(Identifier reason) {
		if (dev.doctor4t.wathe.game.GameConstants.DeathReasons.GUN.equals(reason)) return true;
		String path = reason == null ? "" : reason.getPath().toLowerCase(java.util.Locale.ROOT);
		return path.contains("gun") || path.contains("revolver") || path.contains("shot") || path.contains("shoot")
			|| path.contains("bullet") || path.contains("projectile");
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
		knifeCooldownSeconds.clear();
		revolverCooldownSeconds.clear();
		pendingCooldowns.clear();
		doorBreakCooldownUntil.clear();
		creditedDeadVictims.clear();
		equipped.clear();
		PayloadSync.forgetAll(JuggernautStagePayload.ID);
	}

	private static void clearPlayerState(UUID playerId) {
		knifeCooldownSeconds.remove(playerId);
		revolverCooldownSeconds.remove(playerId);
		pendingCooldowns.remove(playerId);
		doorBreakCooldownUntil.remove(playerId);
		equipped.remove(playerId);
	}

	public static boolean shouldBlockLoadoutDrop(PlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) return false;
		if (!isJuggernaut(player)) return false;
		if (!canUseJuggernautHere(player.getWorld(), player)) return false;
		return isLoadoutItem(stack);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(knifeCooldownSeconds), new HashMap<>(revolverCooldownSeconds),
			new HashMap<>(pendingCooldowns), new HashMap<>(doorBreakCooldownUntil), new HashSet<>(equipped));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clearRoundState();
		if (state == null) return;
		knifeCooldownSeconds.putAll(state.knifeCooldownSeconds());
		revolverCooldownSeconds.putAll(state.revolverCooldownSeconds());
		pendingCooldowns.putAll(state.pendingCooldowns());
		doorBreakCooldownUntil.putAll(state.doorBreakCooldownUntil());
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
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_KNIFE);
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_REVOLVER);
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_SHIELD);
			AbilityCooldownSync.clear(serverPlayer, AbilityCooldownPayload.JUGGERNAUT_DOOR_BREAK);
			clearStage(serverPlayer);
			serverPlayer.playerScreenHandler.syncState();
		}
	}

	private static void clearStage(ServerPlayerEntity player) {
		PayloadSync.sendIfChanged(player, JuggernautStagePayload.ID, JuggernautStagePayload.clear());
	}

	public enum WeaponKind {
		KNIFE,
		REVOLVER;

		private Item item() {
			return this == KNIFE ? WatheItems.KNIFE : WatheItems.REVOLVER;
		}

		private String label() {
			return this == KNIFE ? "Knife" : "Revolver";
		}
	}

	public record PendingCooldown(WeaponKind weapon, int ticks) {}

	public record TimeState(Map<UUID, Integer> knifeCooldownSeconds, Map<UUID, Integer> revolverCooldownSeconds,
			Map<UUID, PendingCooldown> pendingCooldowns, Map<UUID, Long> doorBreakCooldownUntil,
			Set<UUID> equipped) {}
}
