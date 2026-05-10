package dev.mapselect.role.bodyguard;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
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
	private static int tickGate;

	private BodyguardManager() {}

	public static void register() {
		GameEvents.ON_FINISH_INITIALIZE.register(BodyguardManager::initializeRound);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearRound(world));
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
		}
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
		return isIssuedRevolver(stack);
	}

	public static boolean shouldBlockWeaponRemoval(PlayerEntity player, Predicate<ItemStack> predicate, int maxCount) {
		if (player == null || predicate == null || maxCount <= 0 || !isBodyguard(player)) return false;
		try {
			ItemStack probe = WatheItems.REVOLVER.getDefaultStack();
			NbtCompound tag = new NbtCompound();
			tag.putBoolean(BODYGUARD_REVOLVER_KEY, true);
			probe.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
			return predicate.test(probe);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void clearRound(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				removeIssuedRevolver(player);
			}
		}
		targetByBodyguard.clear();
		tickGate = 0;
	}

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
}
