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
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.pelican.PelicanManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
				serverKiller.sendMessage(Text.literal("Your vengeance is complete.").formatted(Formatting.AQUA), true);
				return true;
			}
		}

		if (!(victim instanceof ServerPlayerEntity spirit)) return true;
		if (FORCED_RETURN_DEATHS.contains(spirit.getUuid())) return true;
		if (ACTIVE.remove(spirit.getUuid()) != null) {
			removeIssuedKnife(spirit);
			SPENT.add(spirit.getUuid());
			return true;
		}
		if (!(killer instanceof ServerPlayerEntity serverKiller) || spirit == serverKiller
				|| SPENT.contains(spirit.getUuid()) || !isVengeful(spirit) || PelicanManager.isStashed(spirit)) {
			return true;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(spirit.getWorld());
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return true;

		long now = spirit.getServerWorld().getTime();
		PENDING.put(spirit.getUuid(), new PendingRevive(
			serverKiller.getUuid(),
			spirit.getPos(),
			spirit.getYaw(),
			now + GexpressConfig.getVengefulSpiritReviveDelaySeconds() * 20L
		));
		SPENT.add(spirit.getUuid());
		spirit.sendMessage(Text.literal("Your spirit clings to your killer.").formatted(Formatting.AQUA), true);
		return true;
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
			if (killer == null || !DeadPlayerStatus.isLivingRoundParticipant(killer)) continue;
			revive(world, spirit, entry.getValue());
		}
	}

	private static void tickActive(ServerWorld world, long now) {
		Iterator<Map.Entry<UUID, ActiveRevenge>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveRevenge> entry = iterator.next();
			if (now < entry.getValue().expiresAt()) continue;
			iterator.remove();
			ServerPlayerEntity spirit = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (spirit == null) continue;
			removeIssuedKnife(spirit);
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

	private static void revive(ServerWorld world, ServerPlayerEntity spirit, PendingRevive pending) {
		discardBody(world, spirit.getUuid());
		spirit.changeGameMode(GameMode.ADVENTURE);
		spirit.teleport(world, pending.deathPos().x, pending.deathPos().y, pending.deathPos().z, pending.yaw(), 0.0F);
		spirit.setHealth(spirit.getMaxHealth());
		spirit.setFireTicks(0);
		spirit.setVelocity(Vec3d.ZERO);
		spirit.velocityModified = true;
		spirit.networkHandler.sendPacket(new SetCameraEntityS2CPacket(spirit));
		TrainVoicePlugin.resetPlayer(spirit.getUuid());
		ensureIssuedKnife(spirit);
		long expiresAt = world.getTime() + GexpressConfig.getVengefulSpiritRevengeSeconds() * 20L;
		ACTIVE.put(spirit.getUuid(), new ActiveRevenge(pending.killerId(), expiresAt));
		spirit.sendMessage(Text.literal("Kill your killer before your spirit fades.").formatted(Formatting.AQUA), true);
	}

	private static void discardBody(ServerWorld world, UUID playerId) {
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			if (playerId.equals(body.getPlayerUuid())) body.discard();
		}
	}

	private static boolean isVengeful(ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.VENGEFUL_SPIRIT_ID.equals(role.identifier());
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
			for (ServerPlayerEntity player : serverWorld.getPlayers()) removeIssuedKnife(player);
		}
		PENDING.clear();
		ACTIVE.clear();
		SPENT.clear();
		FORCED_RETURN_DEATHS.clear();
	}

	private record PendingRevive(UUID killerId, Vec3d deathPos, float yaw, long reviveAt) {}
	private record ActiveRevenge(UUID killerId, long expiresAt) {}
}
