package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.PuppetmasterInputPayload;
import dev.mapselect.network.PuppetmasterHotbarPayload;
import dev.mapselect.network.PuppetmasterStatePayload;
import dev.mapselect.network.PuppetmasterTargetsPayload;
import dev.mapselect.network.PuppetmasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientPuppetmasterState {
	private static volatile UUID controllerId;
	private static volatile UUID targetId;
	private static volatile int targetEntityId = -1;
	private static List<ItemStack> puppetHotbar = List.of();
	private static int puppetSelectedSlot = 0;
	private static int backedUpSelectedSlot = -1;
	private static boolean hotbarInitialized;
	private static float puppetYaw;
	private static float puppetPitch;
	private static boolean hasPuppetLook;
	private static boolean hasControllerLookAnchor;
	private static float controllerLookAnchorYaw;
	private static float controllerLookAnchorPitch;
	private static boolean wasAbilityDown;
	private static int targetRefreshTicks;

	private ClientPuppetmasterState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterTargetsPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				if (context.client().currentScreen instanceof PuppetmasterTargetScreen screen) {
					screen.updateTargets(payload.targets());
				} else {
					context.client().setScreen(new PuppetmasterTargetScreen(payload.targets()));
				}
			}));
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterStatePayload.ID, (payload, context) ->
			context.client().execute(() -> applyState(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterHotbarPayload.ID, (payload, context) ->
			context.client().execute(() -> applyHotbar(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientPuppetmasterState::tick);
		HudRenderCallback.EVENT.register(ClientPuppetmasterState::renderHud);
	}

	private static void applyState(MinecraftClient client, PuppetmasterStatePayload payload) {
		if (client == null) return;
		if (!payload.active()) {
			restoreSelectedSlot(client);
			controllerId = null;
			targetId = null;
			targetEntityId = -1;
			puppetHotbar = List.of();
			puppetSelectedSlot = 0;
			hotbarInitialized = false;
			hasPuppetLook = false;
			hasControllerLookAnchor = false;
			if (client.player != null) client.setCameraEntity(client.player);
			return;
		}
		controllerId = payload.controllerId();
		targetId = payload.targetId();
		targetEntityId = payload.targetEntityId();
		if (isLocalController(client)) {
			backupSelectedSlot(client);
			captureControllerLookAnchor(client);
		}
	}

	private static void applyHotbar(MinecraftClient client, PuppetmasterHotbarPayload payload) {
		if (client == null || client.player == null || !isLocalController(client)) return;
		List<ItemStack> copy = new ArrayList<>(9);
		for (ItemStack stack : payload.hotbar()) {
			copy.add(stack.copy());
		}
		puppetHotbar = copy;
		int selectedSlot = Math.max(0, Math.min(8, payload.selectedSlot()));
		if (!hotbarInitialized) {
			puppetSelectedSlot = selectedSlot;
			client.player.getInventory().selectedSlot = selectedSlot;
			hotbarInitialized = true;
		}
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			wasAbilityDown = false;
			clearLocal();
			return;
		}

		KeyBinding ability = resolveAbilityBinding();
		boolean abilityDown = ability != null && ClientAbilityKeys.isDown(client, ability);
		boolean activeController = isLocalController(client);
		if (!ClientRoleRevealState.canUseRoleAbility(client)) {
			wasAbilityDown = false;
			return;
		}
		if (abilityDown && !wasAbilityDown && client.currentScreen instanceof PuppetmasterTargetScreen) {
			client.setScreen(null);
			wasAbilityDown = true;
			return;
		}
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(PuppetmasterUsePayload.ID)
				&& !ClientVultureState.isLocalStashed(client)
				&& (activeController || (client.currentScreen == null && isLocalPuppetmaster(client)))) {
			ClientPlayNetworking.send(new PuppetmasterUsePayload());
		}
		wasAbilityDown = abilityDown;
		if (client.currentScreen instanceof PuppetmasterTargetScreen && ClientPlayNetworking.canSend(PuppetmasterUsePayload.ID)) {
			if (targetRefreshTicks-- <= 0) {
				targetRefreshTicks = 10;
				ClientPlayNetworking.send(new PuppetmasterUsePayload());
			}
		} else {
			targetRefreshTicks = 0;
		}

		if (activeController) {
			AbstractClientPlayerEntity target = getTargetPlayer(client);
			if (target != null && client.getCameraEntity() != target) client.setCameraEntity(target);
			syncLookFromLocal(client);
			sendInput(client);
		}
	}

	public static void sendImmediateInput(MinecraftClient client) {
		if (client == null || !isLocalController(client)) return;
		syncLookFromLocal(client);
		sendInput(client);
	}

	private static void sendInput(MinecraftClient client) {
		if (!ClientPlayNetworking.canSend(PuppetmasterInputPayload.ID)) return;
		initializePuppetLook(client);
		float forward = 0.0F;
		float sideways = 0.0F;
		if (ClientAbilityKeys.isDown(client, client.options.forwardKey)) forward += 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.backKey)) forward -= 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.leftKey)) sideways += 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.rightKey)) sideways -= 1.0F;
		boolean jump = ClientAbilityKeys.isDown(client, client.options.jumpKey);
		boolean sneak = ClientAbilityKeys.isDown(client, client.options.sneakKey);
		boolean sprint = ClientAbilityKeys.isDown(client, client.options.sprintKey);
		boolean use = ClientAbilityKeys.isDown(client, client.options.useKey);
		int selectedSlot = client.player.getInventory().selectedSlot;
		if (hasSyncedHotbar()) puppetSelectedSlot = selectedSlot;
		applyLocalPrediction(client, sideways, forward, jump, sneak, sprint);
		ClientPlayNetworking.send(new PuppetmasterInputPayload(sideways, forward, jump, sneak, sprint, use, puppetYaw, puppetPitch, selectedSlot));
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null) return;
		if (ClientRoleRevealState.canUseRoleAbility(client) && isLocalTarget(client)) {
			int alpha = 88;
			int color = (alpha << 24) | 0xB00018;
			context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
		}
	}

	public static void syncLookFromLocal(MinecraftClient client) {
		if (client == null || client.player == null || !isLocalController(client)) return;
		initializePuppetLook(client);
		captureControllerLookAnchor(client);
		float deltaYaw = MathHelper.wrapDegrees(client.player.getYaw() - controllerLookAnchorYaw);
		float deltaPitch = client.player.getPitch() - controllerLookAnchorPitch;
		if (Math.abs(deltaYaw) > 0.001F || Math.abs(deltaPitch) > 0.001F) {
			puppetYaw = MathHelper.wrapDegrees(puppetYaw + deltaYaw);
			puppetPitch = MathHelper.clamp(puppetPitch + deltaPitch, -90.0F, 90.0F);
			restoreControllerLook(client);
		}
		applyLocalLook(client);
	}

	public static void applyMouseLook(MinecraftClient client, double deltaX, double deltaY) {
		if (client == null || client.player == null || !isLocalController(client)) return;
		initializePuppetLook(client);
		captureControllerLookAnchor(client);
		puppetYaw = MathHelper.wrapDegrees(puppetYaw + (float) deltaX * 0.15F);
		puppetPitch = MathHelper.clamp(puppetPitch + (float) deltaY * 0.15F, -90.0F, 90.0F);
		restoreControllerLook(client);
		applyLocalLook(client);
	}

	public static boolean isLocalController(MinecraftClient client) {
		return client != null && client.player != null && controllerId != null
			&& controllerId.equals(client.player.getUuid());
	}

	public static AbstractClientPlayerEntity getTargetPlayer(MinecraftClient client) {
		if (client == null || client.world == null || !isLocalController(client)) return null;
		Entity target = client.world.getEntityById(targetEntityId);
		return target instanceof AbstractClientPlayerEntity player ? player : null;
	}

	public static UUID replacementFor(UUID playerId) {
		return null;
	}

	public static LivingEntity getRenderEntityFor(LivingEntity entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == null || client == null || entity != client.player) return entity;
		AbstractClientPlayerEntity target = getTargetPlayer(client);
		return target == null ? entity : target;
	}

	public static ItemStack getHotbarStack(int slot) {
		if (slot < 0 || slot >= puppetHotbar.size()) return ItemStack.EMPTY;
		return puppetHotbar.get(slot);
	}

	public static ItemStack getSelectedStack() {
		return getHotbarStack(puppetSelectedSlot);
	}

	public static float getPuppetYaw(MinecraftClient client) {
		initializePuppetLook(client);
		return puppetYaw;
	}

	public static float getPuppetPitch(MinecraftClient client) {
		initializePuppetLook(client);
		return puppetPitch;
	}

	public static boolean hasSyncedHotbar() {
		return !puppetHotbar.isEmpty();
	}

	public static boolean isLocalTarget(MinecraftClient client) {
		return client != null && client.player != null && targetId != null
			&& targetId.equals(client.player.getUuid());
	}

	private static void clearLocal() {
		restoreSelectedSlot(MinecraftClient.getInstance());
		controllerId = null;
		targetId = null;
		targetEntityId = -1;
		puppetHotbar = List.of();
		puppetSelectedSlot = 0;
		hotbarInitialized = false;
		hasPuppetLook = false;
		hasControllerLookAnchor = false;
	}

	private static void backupSelectedSlot(MinecraftClient client) {
		if (client == null || client.player == null || backedUpSelectedSlot >= 0) return;
		backedUpSelectedSlot = client.player.getInventory().selectedSlot;
	}

	private static void initializePuppetLook(MinecraftClient client) {
		if (hasPuppetLook || client == null || client.player == null) return;
		Entity target = client.world == null ? null : client.world.getEntityById(targetEntityId);
		puppetYaw = target != null ? target.getYaw() : client.player.getYaw();
		puppetPitch = target != null ? target.getPitch() : client.player.getPitch();
		hasPuppetLook = true;
	}

	private static void captureControllerLookAnchor(MinecraftClient client) {
		if (hasControllerLookAnchor || client == null || client.player == null) return;
		controllerLookAnchorYaw = client.player.getYaw();
		controllerLookAnchorPitch = client.player.getPitch();
		hasControllerLookAnchor = true;
	}

	private static void restoreControllerLook(MinecraftClient client) {
		if (!hasControllerLookAnchor || client == null || client.player == null) return;
		client.player.setYaw(controllerLookAnchorYaw);
		client.player.setPitch(controllerLookAnchorPitch);
		client.player.setHeadYaw(controllerLookAnchorYaw);
		client.player.setBodyYaw(controllerLookAnchorYaw);
	}

	private static void applyLocalLook(MinecraftClient client) {
		AbstractClientPlayerEntity target = getTargetPlayer(client);
		if (target == null) return;
		target.setYaw(puppetYaw);
		target.setPitch(puppetPitch);
		target.setHeadYaw(puppetYaw);
		target.setBodyYaw(puppetYaw);
	}

	private static void applyLocalPrediction(MinecraftClient client, float sideways, float forward, boolean jumping,
			boolean sneaking, boolean sprinting) {
		AbstractClientPlayerEntity target = getTargetPlayer(client);
		if (target == null) return;
		applyLocalLook(client);
		target.setSneaking(sneaking);
		target.setSprinting(sprinting && forward > 0.5F && !sneaking);
		float magnitude = MathHelper.sqrt(sideways * sideways + forward * forward);
		if (magnitude > 1.0F) {
			sideways /= magnitude;
			forward /= magnitude;
		}
		double yawRad = puppetYaw * (Math.PI / 180.0D);
		double sin = Math.sin(yawRad);
		double cos = Math.cos(yawRad);
		double speed = sneaking ? 0.08D : sprinting ? 0.22D : 0.15D;
		double velocityX = (sideways * cos - forward * sin) * speed;
		double velocityZ = (forward * cos + sideways * sin) * speed;
		double velocityY = target.getVelocity().y;
		if (jumping && target.isOnGround()) {
			velocityY = 0.42D;
		}
		target.setVelocity(velocityX, velocityY, velocityZ);
	}

	private static void restoreSelectedSlot(MinecraftClient client) {
		if (client == null || client.player == null || backedUpSelectedSlot < 0) return;
		client.player.getInventory().selectedSlot = backedUpSelectedSlot;
		backedUpSelectedSlot = -1;
	}

	private static boolean isLocalPuppetmaster(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && ClientCopycatState.isEffectiveRole(client, MapSelectRoles.PUPPETMASTER_ID);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}
}
