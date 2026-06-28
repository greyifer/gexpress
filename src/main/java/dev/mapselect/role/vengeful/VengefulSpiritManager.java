package dev.mapselect.role.vengeful;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.role.vengeful.VengefulSpiritStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.copycat.CopycatManager;
import dev.mapselect.role.pelican.PelicanManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VengefulSpiritManager {
	private static final String VENGEFUL_KNIFE_KEY = "gexpress_vengeful_spirit_knife";
	private static final Map<UUID, PendingRevive> PENDING = new HashMap<>();
	private static final Map<UUID, ActiveRevenge> ACTIVE = new HashMap<>();
	private static final Set<UUID> SPENT = new HashSet<>();
	private static final Set<UUID> FORCED_RETURN_DEATHS = new HashSet<>();

	private VengefulSpiritManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(VengefulSpiritStatePayload.ID, VengefulSpiritStatePayload.CODEC);
		AllowPlayerDeath.EVENT.register(VengefulSpiritManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(VengefulSpiritManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (killer instanceof ServerPlayerEntity serverKiller) {
			ActiveRevenge revenge = ACTIVE.get(serverKiller.getUuid());
			if (revenge != null && victim != null && victim.getUuid().equals(revenge.killerId())) {
				ACTIVE.remove(serverKiller.getUuid());
				removeIssuedKnife(serverKiller);
				sendState(serverKiller, VengefulSpiritStatePayload.clear());
				serverKiller.sendMessage(Text.literal("Your vengeance is complete.").formatted(Formatting.AQUA), true);
				return true;
			}
		}

		if (!(victim instanceof ServerPlayerEntity spirit)) return true;
		if (FORCED_RETURN_DEATHS.contains(spirit.getUuid())) return true;
		if (ACTIVE.remove(spirit.getUuid()) != null) {
			removeIssuedKnife(spirit);
			sendState(spirit, VengefulSpiritStatePayload.clear());
			SPENT.add(spirit.getUuid());
			return true;
		}
		return true;
	}

	public static void afterKillAttempt(PlayerEntity victim, PlayerEntity killer) {
		if (!(victim instanceof ServerPlayerEntity spirit)
				|| !(killer instanceof ServerPlayerEntity serverKiller) || spirit == serverKiller
				|| GameFunctions.isPlayerAliveAndSurvival(spirit)
				|| SPENT.contains(spirit.getUuid()) || !isVengeful(spirit) || PelicanManager.isStashed(spirit)) {
			return;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(spirit.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;

		long now = spirit.getServerWorld().getTime();
		long delay = GexpressConfig.getVengefulSpiritReviveDelaySeconds() * 20L;
		PENDING.put(spirit.getUuid(), new PendingRevive(
			serverKiller.getUuid(),
			serverKiller.getName().getString(),
			spirit.getPos(),
			spirit.getYaw(),
			now + delay
		));
		SPENT.add(spirit.getUuid());
		spirit.sendMessage(Text.literal("Your spirit will return in " + (delay / 20L) + " seconds if "
			+ serverKiller.getName().getString() + " remains alive.").formatted(Formatting.AQUA), true);
		sendState(spirit, new VengefulSpiritStatePayload(VengefulSpiritStatePayload.PENDING,
			serverKiller.getUuid(), serverKiller.getName().getString(), delay, delay));
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
			clear(world);
			return;
		}
		long now = world.getTime();
		tickPending(world, now);
		tickActive(world, now);
	}

	private static void tickPending(ServerWorld world, long now) {
		Iterator<Map.Entry<UUID, PendingRevive>> iterator = PENDING.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PendingRevive> entry = iterator.next();
			if (now < entry.getValue().reviveAt()) continue;
			iterator.remove();
			ServerPlayerEntity spirit = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (spirit == null || GameFunctions.isPlayerAliveAndSurvival(spirit)) continue;
			ServerPlayerEntity killer = world.getServer().getPlayerManager().getPlayer(entry.getValue().killerId());
			if (killer == null || !DeadPlayerStatus.isLivingRoundParticipant(killer)) {
				spirit.sendMessage(Text.literal("Your spirit could not return because "
					+ entry.getValue().killerName() + " is no longer alive.").formatted(Formatting.RED), true);
				sendState(spirit, VengefulSpiritStatePayload.clear());
				continue;
			}
			revive(world, spirit, killer, entry.getValue());
		}
	}

	private static void tickActive(ServerWorld world, long now) {
		Iterator<Map.Entry<UUID, ActiveRevenge>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveRevenge> entry = iterator.next();
			ServerPlayerEntity killer = world.getServer().getPlayerManager().getPlayer(entry.getValue().killerId());
			if (killer == null || !DeadPlayerStatus.isLivingRoundParticipant(killer)) {
				iterator.remove();
				ServerPlayerEntity spirit = world.getServer().getPlayerManager().getPlayer(entry.getKey());
				if (spirit != null) {
					removeIssuedKnife(spirit);
					sendState(spirit, VengefulSpiritStatePayload.clear());
					spirit.sendMessage(Text.literal("Your killer is gone; your spirit can no longer take revenge.")
						.formatted(Formatting.RED), true);
					if (DeadPlayerStatus.isLivingRoundParticipant(spirit)) {
						FORCED_RETURN_DEATHS.add(spirit.getUuid());
						try {
							GameFunctions.killPlayer(spirit, true, null, GameConstants.DeathReasons.GENERIC);
							TrainVoicePlugin.addPlayer(spirit.getUuid());
						} finally {
							FORCED_RETURN_DEATHS.remove(spirit.getUuid());
						}
					}
				}
				continue;
			}
			if (now < entry.getValue().expiresAt()) continue;
			iterator.remove();
			ServerPlayerEntity spirit = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (spirit == null) continue;
			removeIssuedKnife(spirit);
			sendState(spirit, VengefulSpiritStatePayload.clear());
			if (!DeadPlayerStatus.isLivingRoundParticipant(spirit)) continue;
			FORCED_RETURN_DEATHS.add(spirit.getUuid());
			try {
				GameFunctions.killPlayer(spirit, true, null, GameConstants.DeathReasons.GENERIC);
				TrainVoicePlugin.addPlayer(spirit.getUuid());
			} finally {
				FORCED_RETURN_DEATHS.remove(spirit.getUuid());
			}
		}
	}

	private static void revive(ServerWorld world, ServerPlayerEntity spirit, ServerPlayerEntity killer,
			PendingRevive pending) {
		discardBody(world, spirit.getUuid());
		spirit.changeGameMode(GameMode.ADVENTURE);
		Vec3d revivePos = findSafePosition(world, spirit, pending.deathPos(), killer.getPos());
		spirit.teleport(world, revivePos.x, revivePos.y, revivePos.z, pending.yaw(), 0.0F);
		spirit.setHealth(spirit.getMaxHealth());
		spirit.setFireTicks(0);
		spirit.setVelocity(Vec3d.ZERO);
		spirit.velocityModified = true;
		spirit.networkHandler.sendPacket(new SetCameraEntityS2CPacket(spirit));
		TrainVoicePlugin.resetPlayer(spirit.getUuid());
		ensureIssuedKnife(spirit);
		long expiresAt = world.getTime() + GexpressConfig.getVengefulSpiritRevengeSeconds() * 20L;
		ACTIVE.put(spirit.getUuid(), new ActiveRevenge(pending.killerId(), expiresAt));
		long duration = GexpressConfig.getVengefulSpiritRevengeSeconds() * 20L;
		spirit.sendMessage(Text.literal("Kill " + pending.killerName() + " before your spirit fades.")
			.formatted(Formatting.AQUA), true);
		sendState(spirit, new VengefulSpiritStatePayload(VengefulSpiritStatePayload.ACTIVE,
			pending.killerId(), pending.killerName(), duration, duration));
	}

	private static Vec3d findSafePosition(ServerWorld world, ServerPlayerEntity player, Vec3d deathPos,
			Vec3d killerPos) {
		Vec3d safe = resolveDeathPosition(world, player, deathPos);
		if (safe != null) return safe;
		safe = findSafeNear(world, player, deathPos);
		if (safe != null) return safe;
		safe = findSafeNear(world, player, killerPos);
		if (safe != null) return safe;
		safe = findSafeNear(world, player, Vec3d.ofBottomCenter(world.getSpawnPos().up()));
		return safe == null ? Vec3d.ofBottomCenter(world.getSpawnPos().up()) : safe;
	}

	private static Vec3d resolveDeathPosition(ServerWorld world, ServerPlayerEntity player, Vec3d deathPos) {
		if (deathPos == null) return null;
		BlockPos deathFeet = BlockPos.ofFloored(deathPos);
		if (hasSafeSupport(world, deathFeet.down())) {
			if (isSafe(world, player, deathFeet, deathPos)) return deathPos;
			Vec3d centered = new Vec3d(deathPos.x, deathFeet.getY(), deathPos.z);
			if (isSafe(world, player, deathFeet, centered)) return centered;
		}

		for (BlockPos support = deathFeet.down(); support.getY() >= world.getBottomY(); support = support.down()) {
			if (!hasSafeSupport(world, support)) continue;
			BlockPos landingFeet = support.up();
			Vec3d sameColumn = new Vec3d(deathPos.x, landingFeet.getY(), deathPos.z);
			if (isSafe(world, player, landingFeet, sameColumn)) return sameColumn;
			Vec3d centered = Vec3d.ofBottomCenter(landingFeet);
			if (isSafe(world, player, landingFeet, centered)) return centered;
		}
		return null;
	}

	private static boolean hasSafeSupport(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return !state.getCollisionShape(world, pos).isEmpty() && !isHazard(state);
	}

	private static Vec3d findSafeNear(ServerWorld world, ServerPlayerEntity player, Vec3d anchor) {
		if (anchor == null) return null;
		BlockPos origin = BlockPos.ofFloored(anchor);
		int[] verticalOffsets = {0, -1, 1, -2, 2, -3, 3, 4, 5, 6};
		for (int radius = 0; radius <= 8; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					for (int dy : verticalOffsets) {
						BlockPos feet = origin.add(dx, dy, dz);
						Vec3d candidate = Vec3d.ofBottomCenter(feet);
						if (isSafe(world, player, feet, candidate)) return candidate;
					}
				}
			}
		}
		return null;
	}

	private static boolean isSafe(ServerWorld world, ServerPlayerEntity player, BlockPos feet, Vec3d candidate) {
		if (!world.getWorldBorder().contains(feet)) return false;
		BlockState below = world.getBlockState(feet.down());
		BlockState atFeet = world.getBlockState(feet);
		BlockState atHead = world.getBlockState(feet.up());
		if (below.getCollisionShape(world, feet.down()).isEmpty() || isHazard(below)
				|| isHazard(atFeet) || isHazard(atHead)) return false;
		if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()) return false;
		Box moved = player.getBoundingBox().offset(candidate.subtract(player.getPos())).contract(1.0E-4D);
		return world.isSpaceEmpty(player, moved);
	}

	private static boolean isHazard(BlockState state) {
		return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
			|| state.isOf(Blocks.CACTUS) || state.isOf(Blocks.MAGMA_BLOCK)
			|| state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
			|| state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW);
	}

	private static void sendState(ServerPlayerEntity player, VengefulSpiritStatePayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, VengefulSpiritStatePayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void discardBody(ServerWorld world, UUID playerId) {
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			if (playerId.equals(body.getPlayerUuid())) body.discard();
		}
	}

	private static boolean isVengeful(ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.VENGEFUL_SPIRIT_ID.equals(role.identifier())
			|| CopycatManager.isCopyingRole(player, MapSelectRoles.VENGEFUL_SPIRIT_ID));
	}

	private static void ensureIssuedKnife(ServerPlayerEntity spirit) {
		if (hasIssuedKnife(spirit)) return;
		ItemStack knife = WatheItems.KNIFE.getDefaultStack();
		NbtCompound tag = new NbtCompound();
		tag.putBoolean(VENGEFUL_KNIFE_KEY, true);
		knife.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
		spirit.getInventory().insertStack(knife);
		spirit.playerScreenHandler.syncState();
	}

	private static boolean hasIssuedKnife(ServerPlayerEntity spirit) {
		for (int slot = 0; slot < spirit.getInventory().size(); slot++) {
			if (isIssuedKnife(spirit.getInventory().getStack(slot))) return true;
		}
		return false;
	}

	private static void removeIssuedKnife(ServerPlayerEntity spirit) {
		if (spirit == null) return;
		boolean changed = false;
		for (int slot = 0; slot < spirit.getInventory().size(); slot++) {
			if (!isIssuedKnife(spirit.getInventory().getStack(slot))) continue;
			spirit.getInventory().setStack(slot, ItemStack.EMPTY);
			changed = true;
		}
		if (changed) spirit.playerScreenHandler.syncState();
	}

	private static boolean isIssuedKnife(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isOf(WatheItems.KNIFE)) return false;
		NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return customData.copyNbt().getBoolean(VENGEFUL_KNIFE_KEY);
	}

	private static void clear(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				removeIssuedKnife(player);
				sendState(player, VengefulSpiritStatePayload.clear());
			}
		}
		PENDING.clear();
		ACTIVE.clear();
		SPENT.clear();
		FORCED_RETURN_DEATHS.clear();
	}

	private record PendingRevive(UUID killerId, String killerName, Vec3d deathPos, float yaw, long reviveAt) {}
	private record ActiveRevenge(UUID killerId, long expiresAt) {}
}
