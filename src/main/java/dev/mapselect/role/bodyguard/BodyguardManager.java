package dev.mapselect.role.bodyguard;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.BodyguardFeedPayload;
import dev.mapselect.network.BodyguardStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class BodyguardManager {
	private static final double PROTECTION_RANGE_SQUARED = 25.0D;
	private static final String BODYGUARD_REVOLVER_KEY = "gexpress_bodyguard_revolver";
	private static final Map<UUID, UUID> targetByBodyguard = new ConcurrentHashMap<>();
	private static final Map<String, Long> nextLineTick = new ConcurrentHashMap<>();
	private static final Map<UUID, MoodRestore> pendingMoodRestores = new ConcurrentHashMap<>();
	private static int tickGate;

	private BodyguardManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(BodyguardStatePayload.ID, BodyguardStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BodyguardFeedPayload.ID, BodyguardFeedPayload.CODEC);
		GameEvents.ON_FINISH_INITIALIZE.register(BodyguardManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearRound(world));
		AllowPlayerDeath.EVENT.register(BodyguardManager::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(BodyguardManager::afterDeath);
		ServerTickEvents.END_WORLD_TICK.register(BodyguardManager::tick);
	}

	private static void initializeRound(World world, GameWorldComponent game) {
		clearRound(world);
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		for (ServerPlayerEntity bodyguard : serverWorld.getPlayers()) {
			if (!isBodyguard(bodyguard)) continue;
			ServerPlayerEntity target = chooseTarget(serverWorld, game, bodyguard);
			if (target == null) {
				bodyguard.sendMessage(Text.literal("Bodyguard could not find a protection target.")
					.formatted(Formatting.GRAY), true);
				continue;
			}
			targetByBodyguard.put(bodyguard.getUuid(), target.getUuid());
			bodyguard.sendMessage(Text.literal("Protect " + target.getName().getString() + ".")
				.formatted(Formatting.BLUE), false);
			sendState(bodyguard, target);
		}
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (++tickGate < 5) return;
		tickGate = 0;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearRound(world);
			return;
		}

		for (ServerPlayerEntity bodyguard : world.getPlayers()) {
			if (!isBodyguard(bodyguard) || VultureManager.isStashed(bodyguard)
					|| !isPlayable(bodyguard, bodyguard)) {
				removeIssuedRevolver(bodyguard);
				sendClear(bodyguard);
				continue;
			}
			UUID targetId = targetByBodyguard.get(bodyguard.getUuid());
			ServerPlayerEntity target = targetId == null ? null : world.getServer().getPlayerManager().getPlayer(targetId);
			boolean inRange = target != null
				&& target.getWorld() == world
				&& !VultureManager.isStashed(target)
				&& isPlayable(target, bodyguard)
				&& bodyguard.squaredDistanceTo(target) <= PROTECTION_RANGE_SQUARED;
			if (inRange) ensureRevolver(bodyguard);
			else removeIssuedRevolver(bodyguard);
			if (target != null && target.getWorld() == world && isPlayable(target, bodyguard)) sendState(bodyguard, target);
			else sendClear(bodyguard);
		}
		nextLineTick.entrySet().removeIf(entry -> entry.getValue() <= world.getTime() - 20L * 10L);
	}

	private static ServerPlayerEntity chooseTarget(ServerWorld world, GameWorldComponent game, ServerPlayerEntity bodyguard) {
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player == bodyguard || VultureManager.isStashed(player) || !isPlayable(player, bodyguard)) continue;
			if (GexpressConfig.isBodyguardProtectOnlyCivilians() && !isCivilianSide(game, player)) continue;
			candidates.add(player);
		}
		if (candidates.isEmpty()) return null;
		return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
	}

	private static boolean isCivilianSide(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game == null ? null : game.getRole(player);
		return role != null && role.isInnocent() && !game.canUseKillerFeatures(player);
	}

	private static void ensureRevolver(ServerPlayerEntity bodyguard) {
		if (hasIssuedRevolver(bodyguard)) return;
		ItemStack revolver = WatheItems.REVOLVER.getDefaultStack();
		revolver.set(WatheDataComponentTypes.OWNER, bodyguard.getUuidAsString());
		NbtCompound tag = new NbtCompound();
		tag.putBoolean(BODYGUARD_REVOLVER_KEY, true);
		revolver.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
		bodyguard.getInventory().insertStack(revolver);
		bodyguard.playerScreenHandler.syncState();
	}

	private static boolean hasIssuedRevolver(ServerPlayerEntity bodyguard) {
		for (int slot = 0; slot < bodyguard.getInventory().size(); slot++) {
			if (isIssuedRevolver(bodyguard.getInventory().getStack(slot))) return true;
		}
		return false;
	}

	private static void removeIssuedRevolver(ServerPlayerEntity bodyguard) {
		if (bodyguard == null) return;
		boolean changed = false;
		for (int slot = 0; slot < bodyguard.getInventory().size(); slot++) {
			if (!isIssuedRevolver(bodyguard.getInventory().getStack(slot))) continue;
			bodyguard.getInventory().setStack(slot, ItemStack.EMPTY);
			changed = true;
		}
		if (changed) bodyguard.playerScreenHandler.syncState();
	}

	private static boolean isIssuedRevolver(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isOf(WatheItems.REVOLVER)) return false;
		NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return customData.copyNbt().getBoolean(BODYGUARD_REVOLVER_KEY);
	}

	public static boolean shouldBlockWeaponDrop(PlayerEntity player, ItemStack stack) {
		if (isIssuedRevolver(stack)) return true;
		return player != null && isBodyguard(player) && stack != null && stack.isOf(WatheItems.REVOLVER);
	}

	public static boolean shouldBlockWeaponRemoval(PlayerEntity player, Predicate<ItemStack> predicate, int maxCount) {
		if (player == null || predicate == null || maxCount <= 0 || !isBodyguard(player)) return false;
		try {
			ItemStack probe = WatheItems.REVOLVER.getDefaultStack();
			probe.set(WatheDataComponentTypes.OWNER, player.getUuidAsString());
			NbtCompound tag = new NbtCompound();
			tag.putBoolean(BODYGUARD_REVOLVER_KEY, true);
			probe.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
			return predicate.test(probe);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static void recordInteraction(ServerPlayerEntity actor, ServerPlayerEntity other) {
		if (actor == null || other == null || actor == other) return;
		if (!isPlayable(actor, actor) || !isPlayable(other, actor)) return;
		for (Map.Entry<UUID, UUID> entry : targetByBodyguard.entrySet()) {
			ServerPlayerEntity bodyguard = actor.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (bodyguard == null || bodyguard == actor || bodyguard == other || !isBodyguard(bodyguard)
					|| !isPlayable(bodyguard, bodyguard)) continue;
			if (actor.getUuid().equals(entry.getValue())) {
				sendFeed(bodyguard, actor.getName().getString() + " interacted with " + other.getName().getString() + ".");
			} else if (other.getUuid().equals(entry.getValue())) {
				sendFeed(bodyguard, actor.getName().getString() + " interacted with " + other.getName().getString() + ".");
			}
		}
	}

	public static void recordTask(PlayerEntity player, PlayerMoodComponent.TrainTask task) {
		if (!(player instanceof ServerPlayerEntity target) || task == null) return;
		String action = switch (task.getType()) {
			case SLEEP -> "slept";
			case OUTSIDE -> "got fresh air";
			case EAT -> "ate";
			case DRINK -> "drank";
		};
		for (Map.Entry<UUID, UUID> entry : targetByBodyguard.entrySet()) {
			if (!target.getUuid().equals(entry.getValue())) continue;
			ServerPlayerEntity bodyguard = target.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (bodyguard != null && isBodyguard(bodyguard) && isPlayable(bodyguard, bodyguard)) {
				sendFeed(bodyguard, target.getName().getString() + " " + action + ".");
			}
		}
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, net.minecraft.util.Identifier reason) {
		if (victim instanceof ServerPlayerEntity target && killer instanceof ServerPlayerEntity bodyguard
				&& isBodyguard(bodyguard) && hasIssuedRevolver(bodyguard)) {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(target.getWorld());
			if (game != null && isCivilianSide(game, target)) {
				PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(bodyguard);
				if (mood != null) pendingMoodRestores.put(target.getUuid(),
					new MoodRestore(bodyguard.getUuid(), mood.getMood()));
			}
		}
		return true;
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (!(entity instanceof ServerPlayerEntity target)) return;
		restoreBodyguardMood(target);
		handleProtectedTargetDeath(target);
	}

	private static void restoreBodyguardMood(ServerPlayerEntity victim) {
		MoodRestore restore = pendingMoodRestores.remove(victim.getUuid());
		if (restore == null || victim.getServer() == null) return;
		ServerPlayerEntity bodyguard = victim.getServer().getPlayerManager().getPlayer(restore.bodyguardId());
		if (bodyguard == null) return;
		PlayerMoodComponent mood = PlayerMoodComponent.KEY.getNullable(bodyguard);
		if (mood != null) {
			mood.setMood(restore.mood());
			PlayerMoodComponent.KEY.sync(bodyguard);
		}
	}

	private static void handleProtectedTargetDeath(ServerPlayerEntity target) {
		if (!(target.getWorld() instanceof ServerWorld world)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		for (Map.Entry<UUID, UUID> entry : new ArrayList<>(targetByBodyguard.entrySet())) {
			if (!target.getUuid().equals(entry.getValue())) continue;
			ServerPlayerEntity bodyguard = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			targetByBodyguard.remove(entry.getKey());
			if (bodyguard == null || bodyguard.getWorld() != world) continue;
			removeIssuedRevolver(bodyguard);
			sendClear(bodyguard);
			if (!isBodyguard(bodyguard) || !isPlayable(bodyguard, bodyguard)) continue;
			if (bodyguard.squaredDistanceTo(target) <= PROTECTION_RANGE_SQUARED) {
				sendFeed(bodyguard, target.getName().getString() + " died while you were protecting them.");
				GameFunctions.killPlayer(bodyguard, true, target, GameConstants.DeathReasons.GENERIC);
			} else if (game != null) {
				game.addRole(bodyguard, WatheRoles.CIVILIAN);
				game.sync();
				bodyguard.sendMessage(Text.literal(target.getName().getString()
					+ " died outside your protection range. You are now a Civilian.").formatted(Formatting.GRAY),
					false);
			}
		}
	}

	private static void sendState(ServerPlayerEntity bodyguard, ServerPlayerEntity target) {
		if (bodyguard == null || target == null || !ServerPlayNetworking.canSend(bodyguard, BodyguardStatePayload.ID)) {
			return;
		}
		ServerPlayNetworking.send(bodyguard,
			new BodyguardStatePayload(true, target.getUuid(), target.getName().getString()));
	}

	private static void sendClear(ServerPlayerEntity bodyguard) {
		if (bodyguard != null && ServerPlayNetworking.canSend(bodyguard, BodyguardStatePayload.ID)) {
			ServerPlayNetworking.send(bodyguard, BodyguardStatePayload.clear());
		}
	}

	private static void sendFeed(ServerPlayerEntity bodyguard, String line) {
		if (bodyguard == null || line == null || line.isBlank()
				|| !ServerPlayNetworking.canSend(bodyguard, BodyguardFeedPayload.ID)) return;
		long now = bodyguard.getWorld().getTime();
		String throttleKey = bodyguard.getUuid() + ":" + line;
		if (now < nextLineTick.getOrDefault(throttleKey, 0L)) return;
		nextLineTick.put(throttleKey, now + 20L);
		ServerPlayNetworking.send(bodyguard, new BodyguardFeedPayload(line));
	}

	private static void clearRound(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				removeIssuedRevolver(player);
				sendClear(player);
			}
		}
		targetByBodyguard.clear();
		nextLineTick.clear();
		pendingMoodRestores.clear();
		tickGate = 0;
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(Map.copyOf(targetByBodyguard));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		targetByBodyguard.clear();
		if (state != null) targetByBodyguard.putAll(state.targetByBodyguard());
		if (world == null) return;
		for (ServerPlayerEntity bodyguard : world.getPlayers()) {
			UUID targetId = targetByBodyguard.get(bodyguard.getUuid());
			ServerPlayerEntity target = targetId == null ? null : world.getServer().getPlayerManager().getPlayer(targetId);
			if (target != null && target.getWorld() == world) sendState(bodyguard, target);
			else sendClear(bodyguard);
		}
	}

	public record TimeState(Map<UUID, UUID> targetByBodyguard) {}

	private static boolean isBodyguard(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.BODYGUARD_ID.equals(role.identifier());
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity bodyguard) {
		if (GexpressTestState.isRoleTester(bodyguard)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private record MoodRestore(UUID bodyguardId, float mood) {}
}
