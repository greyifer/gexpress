package dev.mapselect.role.puppetmaster;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.puppetmaster.PuppetmasterHotbarPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterInputPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterSelectPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterStatePayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterTargetsPayload;
import dev.mapselect.network.role.puppetmaster.PuppetmasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.ExternalRoleCompat;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class PuppetmasterManager {
	private static final Map<UUID, ControlSession> sessionsByController = new HashMap<>();
	private static final Map<UUID, UUID> controllerByTarget = new HashMap<>();
	private static final Map<UUID, Long> cooldownUntilByController = new HashMap<>();
	private static final Random RANDOM = new Random();

	private PuppetmasterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(PuppetmasterUsePayload.ID, PuppetmasterUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PuppetmasterSelectPayload.ID, PuppetmasterSelectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PuppetmasterInputPayload.ID, PuppetmasterInputPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PuppetmasterTargetsPayload.ID, PuppetmasterTargetsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PuppetmasterStatePayload.ID, PuppetmasterStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PuppetmasterHotbarPayload.ID, PuppetmasterHotbarPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(PuppetmasterUsePayload.ID,
			(payload, context) -> context.server().execute(() -> onUse(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(PuppetmasterSelectPayload.ID,
			(payload, context) -> context.server().execute(() -> startControl(context.player(), payload.targetId())));
		ServerPlayNetworking.registerGlobalReceiver(PuppetmasterInputPayload.ID,
			(payload, context) -> context.server().execute(() -> updateInput(context.player(), payload)));
		AllowPlayerDeath.EVENT.register(PuppetmasterManager::allowWatheDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				return allowDamage(player, damageSource, damageAmount);
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				return allowDeath(player, damageSource, damageAmount);
			}
			return true;
		});
		ServerTickEvents.END_WORLD_TICK.register(PuppetmasterManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> endForDisconnectedPlayer(handler.player, server)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {
				ControlSession controllerSession = sessionsByController.get(player.getUuid());
				if (controllerSession != null) {
					killControlledTargetForControllerHit(player, controllerSession, killerFrom(damageSource),
						GameConstants.DeathReasons.GENERIC);
				} else {
					endForPlayer(player.getUuid(), player.getServer());
				}
			}
		});
	}

	private static void onUse(ServerPlayerEntity puppetmaster) {
		if (!canUse(puppetmaster)) return;
		if (sessionsByController.containsKey(puppetmaster.getUuid())) {
			endControl(puppetmaster.getUuid(), puppetmaster.getServer(), true);
			return;
		}
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(puppetmaster);
		long cooldown = creativeBypass ? 0L : remainingCooldownTicks(puppetmaster);
		if (!creativeBypass && cooldown > 0L) {
			AbilityCooldownSync.send(puppetmaster, AbilityCooldownPayload.PUPPETMASTER_CONTROL, cooldown,
				(long) GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L, false);
			puppetmaster.sendMessage(Text.literal("Puppetmaster cooldown: " + Math.ceil(cooldown / 20.0D) + "s."), true);
			return;
		}
		if (GexpressConfig.isPuppetmasterRandomTarget()) {
			startRandomControl(puppetmaster);
		} else {
			openTargets(puppetmaster);
		}
	}

	private static void openTargets(ServerPlayerEntity puppetmaster) {
		List<PuppetmasterTargetsPayload.Entry> targets = eligibleTargets(puppetmaster).stream()
			.map(player -> new PuppetmasterTargetsPayload.Entry(player.getUuid(), player.getName().getString()))
			.toList();
		ServerPlayNetworking.send(puppetmaster, new PuppetmasterTargetsPayload(targets));
	}

	private static void startRandomControl(ServerPlayerEntity puppetmaster) {
		List<ServerPlayerEntity> targets = eligibleTargets(puppetmaster);
		if (targets.isEmpty()) {
			puppetmaster.sendMessage(Text.literal("No living puppets are available."), true);
			return;
		}
		ServerPlayerEntity target = targets.get(RANDOM.nextInt(targets.size()));
		startControl(puppetmaster, target.getUuid());
	}

	private static List<ServerPlayerEntity> eligibleTargets(ServerPlayerEntity puppetmaster) {
		List<ServerPlayerEntity> targets = new ArrayList<>();
		for (ServerPlayerEntity player : puppetmaster.getServerWorld().getPlayers()) {
			if (player == puppetmaster) continue;
			if (!isPlayable(player, puppetmaster)) continue;
			if (!withinControlRange(puppetmaster, player)) continue;
			targets.add(player);
		}
		return targets;
	}

	private static void startControl(ServerPlayerEntity puppetmaster, UUID targetId) {
		if (!canUse(puppetmaster) || targetId == null) return;
		MinecraftServer server = puppetmaster.getServer();
		ServerPlayerEntity target = server == null ? null : server.getPlayerManager().getPlayer(targetId);
		if (target == null || target == puppetmaster || target.getWorld() != puppetmaster.getWorld()
				|| !isPlayable(target, puppetmaster) || !withinControlRange(puppetmaster, target)) {
			puppetmaster.sendMessage(Text.literal("That puppet is no longer available."), true);
			return;
		}

		endControl(puppetmaster.getUuid(), server, false);
		UUID oldController = controllerByTarget.get(target.getUuid());
		if (oldController != null) endControl(oldController, server, true);

		int durationTicks = GexpressConfig.getPuppetmasterControlDurationSeconds() * 20;
		ControlSession session = new ControlSession(puppetmaster, target,
			puppetmaster.getWorld().getTime() + durationTicks);
		grantTemporaryKnife(target, session);
		sessionsByController.put(puppetmaster.getUuid(), session);
		controllerByTarget.put(target.getUuid(), puppetmaster.getUuid());
		applyModifierSwap(puppetmaster.getServerWorld(), puppetmaster, target, session);
		PuppetmasterStatePayload state = new PuppetmasterStatePayload(true,
			puppetmaster.getUuid(), target.getUuid(), target.getId());
		broadcastState(puppetmaster.getServerWorld(), state);
		target.setSneaking(false);
		target.setSprinting(false);
		target.setVelocity(Vec3d.ZERO);
		puppetmaster.setInvisible(false);
		target.setInvisible(session.targetWasInvisible);
		puppetmaster.playerScreenHandler.syncState();
		target.playerScreenHandler.syncState();
		syncPuppetHotbar(puppetmaster, target);

		puppetmaster.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
		AbilitySounds.playTo(List.of(puppetmaster, target),
			SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.75F, 0.55F);
		AbilityCooldownSync.send(puppetmaster, AbilityCooldownPayload.PUPPETMASTER_CONTROL, durationTicks, durationTicks, true);
		puppetmaster.sendMessage(Text.literal("Pulling " + target.getName().getString() + "'s strings."), true);
		target.sendMessage(Text.literal("You are being controlled."), true);
		SpyManager.recordInteraction(puppetmaster, target);
	}

	private static void updateInput(ServerPlayerEntity puppetmaster, PuppetmasterInputPayload payload) {
		ControlSession session = sessionsByController.get(puppetmaster.getUuid());
		if (session == null || !canUse(puppetmaster)) return;
		session.input = new PuppetInput(
			clampInput(payload.sideways()),
			clampInput(payload.forward()),
			payload.jumping(),
			payload.sneaking(),
			payload.sprinting(),
			payload.using(),
			MathHelper.wrapDegrees(payload.yaw()),
			MathHelper.clamp(payload.pitch(), -90.0F, 90.0F),
			MathHelper.clamp(payload.selectedSlot(), 0, 8)
		);
	}

	private static float clampInput(float value) {
		if (!Float.isFinite(value)) return 0.0F;
		return MathHelper.clamp(value, -1.0F, 1.0F);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (sessionsByController.isEmpty()) return;
		List<UUID> stale = new ArrayList<>();
		for (ControlSession session : List.copyOf(sessionsByController.values())) {
			ServerPlayerEntity controller = world.getServer().getPlayerManager().getPlayer(session.controllerId);
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(session.targetId);
			if (controller == null || target == null || target.getWorld() != world || controller.getWorld() != world
					|| !canUse(controller) || !isControlledTargetValid(target, controller)) {
				stale.add(session.controllerId);
				continue;
			}
			if (world.getTime() >= session.endTick) {
				stale.add(session.controllerId);
				continue;
			}
			freezeController(controller, session);
			driveTarget(target, session);
			syncPuppetHotbar(controller, target);
		}
		for (UUID controllerId : stale) endControl(controllerId, world.getServer(), true);
	}

	private static void freezeController(ServerPlayerEntity controller, ControlSession session) {
		controller.setVelocity(Vec3d.ZERO);
		controller.setSneaking(false);
		controller.setSprinting(false);
		controller.setYaw(session.controllerHomeYaw);
		controller.setPitch(session.controllerHomePitch);
		controller.setHeadYaw(session.controllerHomeYaw);
		controller.setBodyYaw(session.controllerHomeYaw);
		if (controller.squaredDistanceTo(session.controllerHomeX, session.controllerHomeY, session.controllerHomeZ) > 0.04D) {
			controller.teleport(controller.getServerWorld(), session.controllerHomeX, session.controllerHomeY,
				session.controllerHomeZ, session.controllerHomeYaw, session.controllerHomePitch);
		}
	}

	private static void driveTarget(ServerPlayerEntity target, ControlSession session) {
		PuppetInput input = session.input;
		float yaw = input.yaw();
		target.setYaw(yaw);
		target.setPitch(input.pitch());
		target.setHeadYaw(yaw);
		target.setBodyYaw(yaw);
		target.setSneaking(input.sneaking());
		target.setSprinting(input.sprinting() && input.forward() > 0.5F && !input.sneaking());
		target.getInventory().selectedSlot = input.selectedSlot();

		float sideways = input.sideways();
		float forward = input.forward();
		float magnitude = MathHelper.sqrt(sideways * sideways + forward * forward);
		if (magnitude > 1.0F) {
			sideways /= magnitude;
			forward /= magnitude;
		}
		double yawRad = yaw * (Math.PI / 180.0D);
		double sin = Math.sin(yawRad);
		double cos = Math.cos(yawRad);
		double speed = input.sneaking() ? 0.10D : input.sprinting() ? 0.30D : 0.20D;
		double velocityX = (sideways * cos - forward * sin) * speed;
		double velocityZ = (forward * cos + sideways * sin) * speed;
		double velocityY = target.getVelocity().y;
		if (input.jumping() && target.isOnGround()) {
			velocityY = 0.42D;
		}
		target.setVelocity(velocityX, velocityY, velocityZ);
		target.velocityModified = true;
		if (input.using() && !session.wasUsing) {
			useSelectedItem(target);
		}
		session.wasUsing = input.using();
	}

	private static boolean isControlledTargetValid(ServerPlayerEntity target, ServerPlayerEntity controller) {
		if (target == null || controller == null || target.isRemoved() || !target.isAlive()) return false;
		if (controllerByTarget.containsKey(target.getUuid())) {
			return GexpressTestState.isRoleTester(controller) || !DeadPlayerStatus.isDeadRoundParticipant(target);
		}
		return isPlayable(target, controller);
	}

	public static boolean isControlled(ServerPlayerEntity player) {
		return player != null && controllerByTarget.containsKey(player.getUuid());
	}

	public static boolean isController(ServerPlayerEntity player) {
		return player != null && sessionsByController.containsKey(player.getUuid());
	}

	public static Text displayNameFor(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return null;
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (controllerSession != null) {
			ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(controllerSession.targetId);
			return target == null ? null : Text.literal(target.getName().getString());
		}
		return null;
	}

	public static UUID replacementFor(UUID playerId) {
		if (playerId == null) return null;
		ControlSession controllerSession = sessionsByController.get(playerId);
		if (controllerSession != null) return controllerSession.targetId;
		return null;
	}

	private static void endForPlayer(UUID playerId, MinecraftServer server) {
		endControl(playerId, server, false);
		UUID controller = controllerByTarget.get(playerId);
		if (controller != null) endControl(controller, server, true);
	}

	public static void clearForTimeRewind(MinecraftServer server) {
		for (UUID controllerId : new ArrayList<>(sessionsByController.keySet())) {
			endControl(controllerId, server, false);
		}
		controllerByTarget.clear();
		cooldownUntilByController.clear();
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(Map.copyOf(cooldownUntilByController));
	}

	public static void restoreForTimeRewind(TimeState state) {
		cooldownUntilByController.clear();
		if (state == null) return;
		cooldownUntilByController.putAll(state.cooldownUntilByController());
	}

	private static boolean allowDamage(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (controllerSession != null) {
			if (damageAmount >= player.getHealth()) {
				killControlledTargetForControllerHit(player, controllerSession, killerFrom(damageSource),
					GameConstants.DeathReasons.GENERIC);
			}
			return false;
		}
		if (controllerByTarget.containsKey(player.getUuid())) {
			return true;
		}
		return true;
	}

	private static boolean allowDeath(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (controllerSession != null) {
			killControlledTargetForControllerHit(player, controllerSession, killerFrom(damageSource),
				GameConstants.DeathReasons.GENERIC);
			return false;
		}
		UUID controllerId = controllerByTarget.get(player.getUuid());
		if (controllerId == null) return true;
		MinecraftServer server = player.getServer();
		endControl(controllerId, server, true);
		return true;
	}

	private static boolean allowWatheDeath(PlayerEntity victim, PlayerEntity killer, net.minecraft.util.Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity player)) return true;
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (ExternalRoleCompat.isVoodooDeath(reason)) {
			if (controllerSession != null) endControl(controllerSession.controllerId, player.getServer(), true);
			return true;
		}
		if (controllerSession != null) {
			killControlledTargetForControllerHit(player, controllerSession, killer, reason);
			return false;
		}
		UUID controllerId = controllerByTarget.get(player.getUuid());
		if (controllerId == null) return true;
		endControl(controllerId, player.getServer(), true);
		return true;
	}

	private static PlayerEntity killerFrom(DamageSource damageSource) {
		if (damageSource == null || !(damageSource.getAttacker() instanceof PlayerEntity killer)) return null;
		return killer;
	}

	private static void killControlledTargetForControllerHit(ServerPlayerEntity controller, ControlSession session,
			PlayerEntity killer, net.minecraft.util.Identifier reason) {
		if (controller == null || session == null) return;
		if (killer != null && killer.getUuid().equals(session.targetId)
				&& !GexpressConfig.canPuppetmasterKillOwnBody()) {
			return;
		}
		MinecraftServer server = controller.getServer();
		ServerPlayerEntity target = server == null ? null : server.getPlayerManager().getPlayer(session.targetId);
		endControl(session.controllerId, server, true);
		controller.setHealth(Math.max(1.0F, controller.getHealth()));
		if (target != null && DeadPlayerStatus.isLivingRoundParticipant(target)) {
			PlayerEntity creditedKiller = killer == null ? controller : killer;
			GameFunctions.killPlayer(target, true, creditedKiller,
				reason == null ? GameConstants.DeathReasons.GENERIC : reason);
		}
	}

	private static void endControl(UUID controllerId, MinecraftServer server, boolean applyCooldown) {
		endControl(controllerId, server, applyCooldown, null);
	}

	private static void endForDisconnectedPlayer(ServerPlayerEntity player, MinecraftServer server) {
		if (player == null) return;
		endControl(player.getUuid(), server, false, player);
		UUID controller = controllerByTarget.get(player.getUuid());
		if (controller != null) endControl(controller, server, true);
	}

	private static void endControl(UUID controllerId, MinecraftServer server, boolean applyCooldown,
			ServerPlayerEntity controllerOverride) {
		ControlSession session = sessionsByController.remove(controllerId);
		if (session == null) return;
		controllerByTarget.remove(session.targetId);

		ServerPlayerEntity controller = controllerOverride != null ? controllerOverride
			: server == null ? null : server.getPlayerManager().getPlayer(session.controllerId);
		ServerPlayerEntity target = server == null ? null : server.getPlayerManager().getPlayer(session.targetId);
		PuppetmasterStatePayload clear = PuppetmasterStatePayload.clear();
		broadcastState(server, clear);
		if (controller != null && target != null && controller.getWorld() instanceof ServerWorld controllerWorld
				&& target.getWorld() instanceof ServerWorld) {
			restoreModifierSwap(controllerWorld, controller, target, session);
			removeTemporaryKnife(target, session);
			target.changeGameMode(session.targetGameMode);
			if (controller.squaredDistanceTo(session.controllerHomeX, session.controllerHomeY, session.controllerHomeZ) > 0.25D) {
				controller.teleport(controllerWorld, session.controllerHomeX, session.controllerHomeY, session.controllerHomeZ,
					session.controllerHomeYaw, session.controllerHomePitch);
			}
			controller.getInventory().selectedSlot = session.controllerSelectedSlot;
			target.getInventory().selectedSlot = session.targetSelectedSlot;
			target.setInvisible(session.targetWasInvisible);
			controller.playerScreenHandler.syncState();
			target.playerScreenHandler.syncState();
		}
		if (controller == null || target == null) {
			restoreModifierSwap(server, session);
		}
		if (controller != null) {
			controller.setInvisible(session.controllerWasInvisible);
			if (applyCooldown && !GexpressTestState.hasCreativeAbilityBypass(controller)) {
				long cooldownTicks = (long) GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L;
				cooldownUntilByController.put(controller.getUuid(), controller.getWorld().getTime() + cooldownTicks);
				AbilityCooldownSync.send(controller, AbilityCooldownPayload.PUPPETMASTER_CONTROL, cooldownTicks, cooldownTicks, false);
			} else {
				AbilityCooldownSync.clear(controller, AbilityCooldownPayload.PUPPETMASTER_CONTROL);
			}
			controller.networkHandler.sendPacket(new SetCameraEntityS2CPacket(controller));
			controller.playerScreenHandler.syncState();
			controller.sendMessage(Text.literal("Released puppet."), true);
		}
		if (target != null) {
			removeTemporaryKnife(target, session);
			target.changeGameMode(session.targetGameMode);
			target.setInvisible(session.targetWasInvisible);
			target.stopUsingItem();
			target.setSneaking(false);
			target.setSprinting(false);
			target.playerScreenHandler.syncState();
			target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
			target.sendMessage(Text.literal("You are free."), true);
		}
	}

	private static void broadcastState(ServerWorld world, PuppetmasterStatePayload payload) {
		if (world == null || payload == null) return;
		for (ServerPlayerEntity player : PlayerLookup.world(world)) {
			if (ServerPlayNetworking.canSend(player, PuppetmasterStatePayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void broadcastState(MinecraftServer server, PuppetmasterStatePayload payload) {
		if (server == null || payload == null) return;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(player, PuppetmasterStatePayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static boolean canUse(ServerPlayerEntity player) {
		if (player == null || player.getWorld().isClient) return false;
		if (PelicanManager.isStashed(player)) return false;
		if (!canUseHere(player.getWorld(), player) || !isPuppetmaster(player)) return false;
		return isPlayable(player, player);
	}

	private static boolean isPuppetmaster(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && (MapSelectRoles.PUPPETMASTER_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.PUPPETMASTER_ID));
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static long remainingCooldownTicks(ServerPlayerEntity player) {
		if (GexpressTestState.hasCreativeAbilityBypass(player)) return 0L;
		Long until = cooldownUntilByController.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldownUntilByController.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	public static long reduceCooldown(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return remainingCooldownTicks(player);
		long remaining = remainingCooldownTicks(player);
		if (remaining <= 0L) return 0L;
		long next = Math.max(0L, remaining - ticks);
		if (next <= 0L) {
			cooldownUntilByController.remove(player.getUuid());
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.PUPPETMASTER_CONTROL);
		} else {
			cooldownUntilByController.put(player.getUuid(), player.getWorld().getTime() + next);
			AbilityCooldownSync.send(player, AbilityCooldownPayload.PUPPETMASTER_CONTROL, next,
				(long) GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L, false);
		}
		return next;
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity puppetmaster) {
		if (GexpressTestState.isRoleTester(puppetmaster)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static boolean withinControlRange(ServerPlayerEntity puppetmaster, ServerPlayerEntity target) {
		if (puppetmaster == null || target == null || puppetmaster.getWorld() != target.getWorld()) return false;
		double range = GexpressConfig.getPuppetmasterControlRange();
		return puppetmaster.squaredDistanceTo(target) <= range * range;
	}

	private static void grantTemporaryKnife(ServerPlayerEntity target, ControlSession session) {
		if (target == null || session == null || target.getInventory().count(WatheItems.KNIFE) > 0) return;
		int slot = firstEmptyHotbarSlot(target);
		if (slot < 0) return;
		target.getInventory().setStack(slot, WatheItems.KNIFE.getDefaultStack());
		target.getInventory().selectedSlot = slot;
		session.temporaryKnifeSlot = slot;
		session.input = new PuppetInput(0.0F, 0.0F, false, false, false, false,
			session.targetStartYaw, session.targetStartPitch, slot);
	}

	private static int firstEmptyHotbarSlot(ServerPlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).isEmpty()) return slot;
		}
		return -1;
	}

	private static void removeTemporaryKnife(ServerPlayerEntity target, ControlSession session) {
		if (target == null || session == null || session.temporaryKnifeSlot < 0) return;
		ItemStack stack = target.getInventory().getStack(session.temporaryKnifeSlot);
		if (stack.isOf(WatheItems.KNIFE)) {
			target.getInventory().setStack(session.temporaryKnifeSlot, ItemStack.EMPTY);
			target.playerScreenHandler.syncState();
		}
		session.temporaryKnifeSlot = -1;
	}

	private static void syncPuppetHotbar(ServerPlayerEntity controller, ServerPlayerEntity target) {
		if (controller == null || target == null || !ServerPlayNetworking.canSend(controller, PuppetmasterHotbarPayload.ID)) {
			return;
		}
		List<ItemStack> hotbar = new ArrayList<>(9);
		for (int slot = 0; slot < 9; slot++) {
			hotbar.add(target.getInventory().getStack(slot).copy());
		}
		ServerPlayNetworking.send(controller, new PuppetmasterHotbarPayload(hotbar,
			MathHelper.clamp(target.getInventory().selectedSlot, 0, 8)));
	}

	private static void useSelectedItem(ServerPlayerEntity target) {
		ItemStack stack = target.getStackInHand(Hand.MAIN_HAND);
		if (stack.isEmpty()) return;
		target.interactionManager.interactItem(target, target.getWorld(), stack, Hand.MAIN_HAND);
	}

	private static ArrayList<Modifier> copyModifiers(World world, UUID playerId) {
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null || playerId == null) return new ArrayList<>();
		ArrayList<Modifier> current = modifiers.getModifiers(playerId);
		return new ArrayList<>(current == null ? List.of() : current);
	}

	private static void applyModifierSwap(ServerWorld world, ServerPlayerEntity controller,
			ServerPlayerEntity target, ControlSession session) {
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null) return;
		setModifiers(modifiers, controller, session.controllerModifiers, session.targetModifiers);
		setModifiers(modifiers, target, session.targetModifiers, session.controllerModifiers);
		modifiers.sync();
	}

	private static void restoreModifierSwap(ServerWorld world, ServerPlayerEntity controller,
			ServerPlayerEntity target, ControlSession session) {
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null) return;
		setModifiers(modifiers, controller, safeModifiers(modifiers, controller), session.controllerModifiers);
		setModifiers(modifiers, target, safeModifiers(modifiers, target), session.targetModifiers);
		modifiers.sync();
	}

	private static void restoreModifierSwap(MinecraftServer server, ControlSession session) {
		if (server == null || session == null) return;
		ServerPlayerEntity controller = server.getPlayerManager().getPlayer(session.controllerId);
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(session.targetId);
		if (controller == null && target == null) return;
		ServerWorld world = controller != null ? controller.getServerWorld() : target.getServerWorld();
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null) return;
		if (controller != null) setModifiers(modifiers, controller, safeModifiers(modifiers, controller), session.controllerModifiers);
		if (target != null) setModifiers(modifiers, target, safeModifiers(modifiers, target), session.targetModifiers);
		modifiers.sync();
	}

	private static void setModifiers(WorldModifierComponent component, ServerPlayerEntity player,
			List<Modifier> before, List<Modifier> after) {
		if (component == null || player == null) return;
		ArrayList<Modifier> current = safeModifiers(component, player);
		List<Modifier> oldValues = before == null ? List.of() : before;
		List<Modifier> newValues = after == null ? List.of() : after;
		for (Modifier modifier : oldValues) {
			if (!containsModifier(newValues, modifier)) safeRemoveModifier(player, modifier);
		}
		current.clear();
		current.addAll(newValues);
		for (Modifier modifier : newValues) {
			if (!containsModifier(oldValues, modifier)) safeAssignModifier(player, modifier);
		}
	}

	private static boolean containsModifier(List<Modifier> modifiers, Modifier needle) {
		if (modifiers == null || needle == null) return false;
		for (Modifier modifier : modifiers) {
			if (modifier == needle || (modifier != null && modifier.identifier().equals(needle.identifier()))) return true;
		}
		return false;
	}

	private static ArrayList<Modifier> safeModifiers(WorldModifierComponent component, ServerPlayerEntity player) {
		if (component == null || player == null) return new ArrayList<>();
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
		return current == null ? new ArrayList<>() : current;
	}

	private static void safeAssignModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierAssigned listener failed while Puppetmaster swapped {} onto {}.",
				modifier == null ? "(none)" : modifier.identifier(), player.getName().getString(), t);
		}
	}

	private static void safeRemoveModifier(ServerPlayerEntity player, Modifier modifier) {
		try {
			ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("ModifierRemoved listener failed while Puppetmaster removed {} from {}.",
				modifier == null ? "(none)" : modifier.identifier(), player.getName().getString(), t);
		}
	}

	private static final class ControlSession {
		private final UUID controllerId;
		private final UUID targetId;
		private final long endTick;
		private final double controllerHomeX;
		private final double controllerHomeY;
		private final double controllerHomeZ;
		private final float controllerHomeYaw;
		private final float controllerHomePitch;
		private final float targetStartYaw;
		private final float targetStartPitch;
		private final ArrayList<Modifier> controllerModifiers;
		private final ArrayList<Modifier> targetModifiers;
		private final int controllerSelectedSlot;
		private final int targetSelectedSlot;
		private final boolean controllerWasInvisible;
		private final boolean targetWasInvisible;
		private final GameMode targetGameMode;
		private int temporaryKnifeSlot = -1;
		private boolean wasUsing;
		private PuppetInput input = new PuppetInput(0.0F, 0.0F, false, false, false, false, 0.0F, 0.0F, 0);

		private ControlSession(ServerPlayerEntity controller, ServerPlayerEntity target, long endTick) {
			this.controllerId = controller.getUuid();
			this.targetId = target.getUuid();
			this.endTick = endTick;
			this.controllerHomeX = controller.getX();
			this.controllerHomeY = controller.getY();
			this.controllerHomeZ = controller.getZ();
			this.controllerHomeYaw = controller.getYaw();
			this.controllerHomePitch = controller.getPitch();
			this.targetStartYaw = target.getYaw();
			this.targetStartPitch = target.getPitch();
			this.controllerModifiers = copyModifiers(controller.getWorld(), controller.getUuid());
			this.targetModifiers = copyModifiers(target.getWorld(), target.getUuid());
			this.controllerSelectedSlot = MathHelper.clamp(controller.getInventory().selectedSlot, 0, 8);
			this.targetSelectedSlot = MathHelper.clamp(target.getInventory().selectedSlot, 0, 8);
			this.controllerWasInvisible = controller.isInvisible();
			this.targetWasInvisible = target.isInvisible();
			this.targetGameMode = target.interactionManager.getGameMode();
			this.input = new PuppetInput(0.0F, 0.0F, false, false, false, false,
				target.getYaw(), target.getPitch(), this.targetSelectedSlot);
		}
	}

	private record PuppetInput(float sideways, float forward, boolean jumping, boolean sneaking, boolean sprinting,
			boolean using, float yaw, float pitch, int selectedSlot) {}

	public record TimeState(Map<UUID, Long> cooldownUntilByController) {}

}
