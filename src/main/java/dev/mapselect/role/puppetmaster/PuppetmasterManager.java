package dev.mapselect.role.puppetmaster;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import dev.doctor4t.wathe.util.ShootMuzzleS2CPayload;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.PuppetmasterHotbarPayload;
import dev.mapselect.network.PuppetmasterInputPayload;
import dev.mapselect.network.PuppetmasterSelectPayload;
import dev.mapselect.network.PuppetmasterStatePayload;
import dev.mapselect.network.PuppetmasterTargetsPayload;
import dev.mapselect.network.PuppetmasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class PuppetmasterManager {
	private static final Map<UUID, ControlSession> sessionsByController = new HashMap<>();
	private static final Map<UUID, UUID> controllerByTarget = new HashMap<>();
	private static final Map<UUID, Long> cooldownUntilByController = new HashMap<>();
	private static final Map<UUID, Integer> usesRemainingByController = new HashMap<>();
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
		ServerTickEvents.START_WORLD_TICK.register(PuppetmasterManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> endForDisconnectedPlayer(handler.player, server)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {
				ControlSession controllerSession = sessionsByController.get(player.getUuid());
				if (controllerSession != null) {
					killControlledTargetForControllerHit(player, controllerSession);
				} else {
					endForPlayer(player.getUuid(), player.getServer());
				}
			}
		});
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> resetRoundLimits());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> resetRoundLimits());
	}

	private static void onUse(ServerPlayerEntity puppetmaster) {
		if (!canUse(puppetmaster)) return;
		if (sessionsByController.containsKey(puppetmaster.getUuid())) {
			endControl(puppetmaster.getUuid(), puppetmaster.getServer(), true);
			return;
		}
		long cooldown = remainingCooldownTicks(puppetmaster);
		if (cooldown > 0L) {
			AbilityCooldownSync.send(puppetmaster, AbilityCooldownPayload.PUPPETMASTER_CONTROL, cooldown,
				(long) GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L, false);
			puppetmaster.sendMessage(Text.literal("Puppetmaster cooldown: " + Math.ceil(cooldown / 20.0D) + "s."), true);
			return;
		}
		if (remainingUses(puppetmaster) <= 0) {
			puppetmaster.sendMessage(Text.literal("No controls left."), true);
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
		if (!consumeUse(puppetmaster)) {
			puppetmaster.sendMessage(Text.literal("No controls left."), true);
			return;
		}

		endControl(puppetmaster.getUuid(), server, false);
		UUID oldController = controllerByTarget.get(target.getUuid());
		if (oldController != null) endControl(oldController, server, true);

		int durationTicks = GexpressConfig.getPuppetmasterControlDurationSeconds() * 20;
		ControlSession session = new ControlSession(puppetmaster, target,
			puppetmaster.getWorld().getTime(),
			puppetmaster.getWorld().getTime() + durationTicks);
		sessionsByController.put(puppetmaster.getUuid(), session);
		controllerByTarget.put(target.getUuid(), puppetmaster.getUuid());
		applyModifierSwap(puppetmaster.getServerWorld(), puppetmaster, target, session);

		copyHotbar(target, puppetmaster);
		session.temporaryKnifeSlot = grantTemporaryKnife(puppetmaster);
		copyHotbar(session.controllerHotbar, target);
		puppetmaster.getInventory().selectedSlot = session.targetSelectedSlot;
		target.getInventory().selectedSlot = session.controllerSelectedSlot;
		PuppetmasterStatePayload state = new PuppetmasterStatePayload(true,
			puppetmaster.getUuid(), target.getUuid(), target.getId());
		broadcastState(puppetmaster.getServerWorld(), state);
		puppetmaster.setInvisible(true);
		target.setSneaking(false);
		target.setSprinting(false);
		target.setVelocity(Vec3d.ZERO);
		puppetmaster.teleport(puppetmaster.getServerWorld(),
			session.targetStartX, session.targetStartY, session.targetStartZ, session.targetStartYaw, session.targetStartPitch);
		target.teleport(target.getServerWorld(),
			session.controllerHomeX, session.controllerHomeY, session.controllerHomeZ,
			session.controllerHomeYaw, session.controllerHomePitch);
		puppetmaster.playerScreenHandler.syncState();
		target.playerScreenHandler.syncState();

		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(puppetmaster));
		puppetmaster.getWorld().playSound(null, puppetmaster.getBlockPos(),
			SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.75F, 0.55F);
		AbilityCooldownSync.send(puppetmaster, AbilityCooldownPayload.PUPPETMASTER_CONTROL, durationTicks, durationTicks, true);
		puppetmaster.sendMessage(Text.literal("Pulling " + target.getName().getString() + "'s strings."), true);
		puppetmaster.sendMessage(Text.literal("Controls left: " + remainingUses(puppetmaster) + "/"
			+ GexpressConfig.getPuppetmasterMaxUses()), false);
		target.sendMessage(Text.literal("You are being controlled."), true);
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
		session.lastInputTick = puppetmaster.getWorld().getTime();
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
					|| !canUse(controller) || !isPlayable(target, controller)) {
				stale.add(session.controllerId);
				continue;
			}
			if (world.getTime() >= session.endTick) {
				stale.add(session.controllerId);
				continue;
			}
			driveTarget(world, target, session);
		}
		for (UUID controllerId : stale) endControl(controllerId, world.getServer(), true);
	}

	private static void driveTarget(ServerWorld world, ServerPlayerEntity target, ControlSession session) {
		target.setVelocity(Vec3d.ZERO);
		target.setYaw(session.controllerHomeYaw);
		target.setPitch(session.controllerHomePitch);
		target.setHeadYaw(session.controllerHomeYaw);
		target.setBodyYaw(session.controllerHomeYaw);
		target.setSneaking(false);
		target.setSprinting(false);
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
		UUID controllerId = controllerByTarget.get(player.getUuid());
		if (controllerId != null) {
			ServerPlayerEntity controller = player.getServer().getPlayerManager().getPlayer(controllerId);
			return controller == null ? null : Text.literal(controller.getName().getString());
		}
		return null;
	}

	public static UUID replacementFor(UUID playerId) {
		if (playerId == null) return null;
		ControlSession controllerSession = sessionsByController.get(playerId);
		if (controllerSession != null) return controllerSession.targetId;
		return controllerByTarget.get(playerId);
	}

	private static int grantTemporaryKnife(ServerPlayerEntity target) {
		if (target.getInventory().count(WatheItems.KNIFE) > 0) return -1;

		int slot = firstEmptyHotbarSlot(target);
		if (slot < 0) slot = MathHelper.clamp(target.getInventory().selectedSlot, 0, 8);
		ItemStack knife = WatheItems.KNIFE.getDefaultStack();
		knife.set(WatheDataComponentTypes.OWNER, target.getUuidAsString());
		target.getInventory().setStack(slot, knife);
		return slot;
	}

	private static int firstEmptyHotbarSlot(ServerPlayerEntity target) {
		for (int slot = 0; slot < 9; slot++) {
			if (target.getInventory().getStack(slot).isEmpty()) return slot;
		}
		return -1;
	}

	private static ItemStack[] copyHotbar(ServerPlayerEntity player) {
		ItemStack[] hotbar = new ItemStack[9];
		for (int slot = 0; slot < 9; slot++) {
			hotbar[slot] = player.getInventory().getStack(slot).copy();
		}
		return hotbar;
	}

	private static void copyHotbar(ServerPlayerEntity from, ServerPlayerEntity to) {
		copyHotbar(copyHotbar(from), to);
	}

	private static void copyHotbar(ItemStack[] hotbar, ServerPlayerEntity to) {
		if (hotbar == null || to == null) return;
		for (int slot = 0; slot < 9 && slot < hotbar.length; slot++) {
			to.getInventory().setStack(slot, hotbar[slot].copy());
		}
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
		usesRemainingByController.clear();
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(Map.copyOf(cooldownUntilByController), Map.copyOf(usesRemainingByController));
	}

	public static void restoreForTimeRewind(TimeState state) {
		cooldownUntilByController.clear();
		usesRemainingByController.clear();
		if (state == null) return;
		cooldownUntilByController.putAll(state.cooldownUntilByController());
		usesRemainingByController.putAll(state.usesRemainingByController());
	}

	private static boolean allowDamage(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (controllerSession != null) {
			if (damageAmount >= player.getHealth()) {
				killControlledTargetForControllerHit(player, controllerSession);
			}
			return false;
		}
		return true;
	}

	private static boolean allowDeath(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {
		ControlSession controllerSession = sessionsByController.get(player.getUuid());
		if (controllerSession != null) {
			killControlledTargetForControllerHit(player, controllerSession);
			return false;
		}
		UUID controllerId = controllerByTarget.get(player.getUuid());
		if (controllerId == null) return true;
		MinecraftServer server = player.getServer();
		endControl(controllerId, server, true);
		return true;
	}

	private static void killControlledTargetForControllerHit(ServerPlayerEntity controller, ControlSession session) {
		if (controller == null || session == null) return;
		MinecraftServer server = controller.getServer();
		ServerPlayerEntity target = server == null ? null : server.getPlayerManager().getPlayer(session.targetId);
		endControl(session.controllerId, server, true);
		controller.setHealth(Math.max(1.0F, controller.getHealth()));
		if (target != null && GameFunctions.isPlayerAliveAndSurvival(target)) {
			GameFunctions.killPlayer(target, true, controller, GameConstants.DeathReasons.GENERIC);
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
				&& target.getWorld() instanceof ServerWorld targetWorld) {
			restoreModifierSwap(controllerWorld, controller, target, session);
			ItemStack[] controlledHotbar = copyHotbar(controller);
			if (session.temporaryKnifeSlot >= 0 && session.temporaryKnifeSlot < controlledHotbar.length
					&& controlledHotbar[session.temporaryKnifeSlot].isOf(WatheItems.KNIFE)) {
				controlledHotbar[session.temporaryKnifeSlot] = session.targetHotbar[session.temporaryKnifeSlot].copy();
			}
			double controlledX = controller.getX();
			double controlledY = controller.getY();
			double controlledZ = controller.getZ();
			float controlledYaw = controller.getYaw();
			float controlledPitch = controller.getPitch();
			target.teleport(targetWorld, controlledX, controlledY, controlledZ, controlledYaw, controlledPitch);
			controller.teleport(controllerWorld, session.controllerHomeX, session.controllerHomeY, session.controllerHomeZ,
				session.controllerHomeYaw, session.controllerHomePitch);
			copyHotbar(session.controllerHotbar, controller);
			copyHotbar(controlledHotbar, target);
			controller.getInventory().selectedSlot = session.controllerSelectedSlot;
			target.getInventory().selectedSlot = session.targetSelectedSlot;
			controller.playerScreenHandler.syncState();
			target.playerScreenHandler.syncState();
		}
		if (controller == null || target == null) {
			restoreModifierSwap(server, session);
		}
		if (controller != null) {
			controller.setInvisible(session.controllerWasInvisible);
			if (applyCooldown) {
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
		if (VultureManager.isStashed(player)) return false;
		if (!canUseHere(player.getWorld(), player) || !isPuppetmaster(player)) return false;
		return isPlayable(player, player);
	}

	private static boolean isPuppetmaster(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.PUPPETMASTER_ID.equals(role.identifier());
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static long remainingCooldownTicks(ServerPlayerEntity player) {
		Long until = cooldownUntilByController.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldownUntilByController.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	private static int remainingUses(ServerPlayerEntity player) {
		if (player == null) return 0;
		int maxUses = GexpressConfig.getPuppetmasterMaxUses();
		Integer current = usesRemainingByController.get(player.getUuid());
		if (current == null || current > maxUses) {
			current = maxUses;
			usesRemainingByController.put(player.getUuid(), current);
		}
		return current;
	}

	private static boolean consumeUse(ServerPlayerEntity player) {
		if (player == null) return false;
		int remaining = remainingUses(player);
		if (remaining <= 0) return false;
		usesRemainingByController.put(player.getUuid(), remaining - 1);
		return true;
	}

	private static void resetRoundLimits() {
		usesRemainingByController.clear();
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity puppetmaster) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(puppetmaster);
	}

	private static boolean withinControlRange(ServerPlayerEntity puppetmaster, ServerPlayerEntity target) {
		if (puppetmaster == null || target == null || puppetmaster.getWorld() != target.getWorld()) return false;
		double range = GexpressConfig.getPuppetmasterControlRange();
		return puppetmaster.squaredDistanceTo(target) <= range * range;
	}

	private static ArrayList<Modifier> copyModifiers(World world, UUID playerId) {
		WorldModifierComponent modifiers = WorldModifierComponent.KEY.getNullable(world);
		if (modifiers == null || playerId == null) return new ArrayList<>();
		return new ArrayList<>(modifiers.getModifiers(playerId));
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
		setModifiers(modifiers, controller, modifiers.getModifiers(controller), session.controllerModifiers);
		setModifiers(modifiers, target, modifiers.getModifiers(target), session.targetModifiers);
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
		if (controller != null) setModifiers(modifiers, controller, modifiers.getModifiers(controller), session.controllerModifiers);
		if (target != null) setModifiers(modifiers, target, modifiers.getModifiers(target), session.targetModifiers);
		modifiers.sync();
	}

	private static void setModifiers(WorldModifierComponent component, ServerPlayerEntity player,
			List<Modifier> before, List<Modifier> after) {
		if (component == null || player == null) return;
		ArrayList<Modifier> current = component.getModifiers(player.getUuid());
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
		private final double targetStartX;
		private final double targetStartY;
		private final double targetStartZ;
		private final float targetStartYaw;
		private final float targetStartPitch;
		private final ItemStack[] controllerHotbar;
		private final ItemStack[] targetHotbar;
		private final ArrayList<Modifier> controllerModifiers;
		private final ArrayList<Modifier> targetModifiers;
		private final int controllerSelectedSlot;
		private final int targetSelectedSlot;
		private final boolean controllerWasInvisible;
		private int temporaryKnifeSlot = -1;
		private PuppetInput input = new PuppetInput(0.0F, 0.0F, false, false, false, false, 0.0F, 0.0F, 0);
		private long lastInputTick;

		private ControlSession(ServerPlayerEntity controller, ServerPlayerEntity target, long now, long endTick) {
			this.controllerId = controller.getUuid();
			this.targetId = target.getUuid();
			this.endTick = endTick;
			this.controllerHomeX = controller.getX();
			this.controllerHomeY = controller.getY();
			this.controllerHomeZ = controller.getZ();
			this.controllerHomeYaw = controller.getYaw();
			this.controllerHomePitch = controller.getPitch();
			this.targetStartX = target.getX();
			this.targetStartY = target.getY();
			this.targetStartZ = target.getZ();
			this.targetStartYaw = target.getYaw();
			this.targetStartPitch = target.getPitch();
			this.controllerHotbar = copyHotbar(controller);
			this.targetHotbar = copyHotbar(target);
			this.controllerModifiers = copyModifiers(controller.getWorld(), controller.getUuid());
			this.targetModifiers = copyModifiers(target.getWorld(), target.getUuid());
			this.controllerSelectedSlot = MathHelper.clamp(controller.getInventory().selectedSlot, 0, 8);
			this.targetSelectedSlot = MathHelper.clamp(target.getInventory().selectedSlot, 0, 8);
			this.controllerWasInvisible = controller.isInvisible();
			this.input = new PuppetInput(0.0F, 0.0F, false, false, false, false,
				target.getYaw(), target.getPitch(), this.targetSelectedSlot);
			this.lastInputTick = now;
		}
	}

	private record PuppetInput(float sideways, float forward, boolean jumping, boolean sneaking, boolean sprinting,
			boolean using, float yaw, float pitch, int selectedSlot) {}

	public record TimeState(Map<UUID, Long> cooldownUntilByController,
			Map<UUID, Integer> usesRemainingByController) {}

}
