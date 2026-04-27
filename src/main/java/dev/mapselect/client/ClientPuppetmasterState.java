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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
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
	private static boolean hasControllerBodyPose;
	private static double controllerBodyX;
	private static double controllerBodyY;
	private static double controllerBodyZ;
	private static float controllerBodyYaw;
	private static float controllerBodyPitch;
	private static float controllerBodyHeadYaw;
	private static float controllerBodyBodyYaw;
	private static float puppetYaw;
	private static float puppetPitch;
	private static boolean hasPuppetLook;
	private static boolean wasAbilityDown;

	private ClientPuppetmasterState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PuppetmasterTargetsPayload.ID, (payload, context) ->
			context.client().execute(() ->
				context.client().setScreen(new PuppetmasterTargetScreen(payload.targets()))));
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
			hasPuppetLook = false;
			if (client.player != null) client.setCameraEntity(client.player);
			return;
		}
		controllerId = payload.controllerId();
		targetId = payload.targetId();
		targetEntityId = payload.targetEntityId();
		if (isLocalController(client)) {
			backupSelectedSlot(client);
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
		if (abilityDown && !wasAbilityDown && ClientPlayNetworking.canSend(PuppetmasterUsePayload.ID)
				&& !ClientVultureState.isLocalStashed(client)
				&& (activeController || (client.currentScreen == null && isLocalPuppetmaster(client)))) {
			ClientPlayNetworking.send(new PuppetmasterUsePayload());
		}
		wasAbilityDown = abilityDown;

		if (activeController) {
			if (client.getCameraEntity() != client.player) client.setCameraEntity(client.player);
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
		ClientPlayNetworking.send(new PuppetmasterInputPayload(sideways, forward, jump, sneak, sprint, use, puppetYaw, puppetPitch, selectedSlot));
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) return;
		if (isLocalTarget(client)) {
			int alpha = 88;
			int color = (alpha << 24) | 0xB00018;
			context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
		}
	}

	public static void syncLookFromLocal(MinecraftClient client) {
		if (client == null || client.player == null || !isLocalController(client)) return;
		initializePuppetLook(client);
		puppetYaw = MathHelper.wrapDegrees(client.player.getYaw());
		puppetPitch = MathHelper.clamp(client.player.getPitch(), -90.0F, 90.0F);
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
		if (playerId == null || controllerId == null || targetId == null) return null;
		if (playerId.equals(controllerId)) return targetId;
		if (playerId.equals(targetId)) return controllerId;
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

	private static void renderControllerBody(MinecraftClient client, net.minecraft.client.util.math.MatrixStack matrices,
			Camera camera, float tickDelta, net.minecraft.client.render.VertexConsumerProvider consumers) {
		if (client == null || client.player == null || !isLocalController(client)) return;
		if (camera == null || camera.getFocusedEntity() == client.player) return;

		double x = (hasControllerBodyPose ? controllerBodyX : client.player.getX()) - camera.getPos().x;
		double y = (hasControllerBodyPose ? controllerBodyY : client.player.getY()) - camera.getPos().y;
		double z = (hasControllerBodyPose ? controllerBodyZ : client.player.getZ()) - camera.getPos().z;
		float yaw = net.minecraft.util.math.MathHelper.lerp(tickDelta, client.player.prevYaw, client.player.getYaw());
		float renderYaw = hasControllerBodyPose ? controllerBodyBodyYaw : yaw;
		float oldYaw = client.player.getYaw();
		float oldPitch = client.player.getPitch();
		float oldHeadYaw = client.player.headYaw;
		float oldBodyYaw = client.player.bodyYaw;
		matrices.push();
		try {
			if (hasControllerBodyPose) {
				client.player.setYaw(controllerBodyYaw);
				client.player.setPitch(controllerBodyPitch);
				client.player.setHeadYaw(controllerBodyHeadYaw);
				client.player.setBodyYaw(controllerBodyBodyYaw);
			}
			client.getEntityRenderDispatcher().render(client.player, x, y, z, renderYaw, tickDelta, matrices, consumers,
				client.getEntityRenderDispatcher().getLight(client.player, tickDelta));
		} finally {
			client.player.setYaw(oldYaw);
			client.player.setPitch(oldPitch);
			client.player.setHeadYaw(oldHeadYaw);
			client.player.setBodyYaw(oldBodyYaw);
			matrices.pop();
		}
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
		hasControllerBodyPose = false;
		hasPuppetLook = false;
	}

	private static void backupSelectedSlot(MinecraftClient client) {
		if (client == null || client.player == null || backedUpSelectedSlot >= 0) return;
		backedUpSelectedSlot = client.player.getInventory().selectedSlot;
	}

	private static void captureControllerBodyPose(MinecraftClient client) {
		if (client == null || client.player == null) return;
		controllerBodyX = client.player.getX();
		controllerBodyY = client.player.getY();
		controllerBodyZ = client.player.getZ();
		controllerBodyYaw = client.player.getYaw();
		controllerBodyPitch = client.player.getPitch();
		controllerBodyHeadYaw = client.player.headYaw;
		controllerBodyBodyYaw = client.player.bodyYaw;
		hasControllerBodyPose = true;
	}

	private static void initializePuppetLook(MinecraftClient client) {
		if (hasPuppetLook || client == null || client.player == null) return;
		Entity target = client.world == null ? null : client.world.getEntityById(targetEntityId);
		puppetYaw = target != null ? target.getYaw() : client.player.getYaw();
		puppetPitch = target != null ? target.getPitch() : client.player.getPitch();
		hasPuppetLook = true;
	}

	private static void moveDriverToPuppet(MinecraftClient client, Entity target) {
		if (client == null || client.player == null || target == null) return;
		client.player.updatePositionAndAngles(target.getX(), target.getY(), target.getZ(), puppetYaw, puppetPitch);
		client.player.setVelocity(target.getVelocity());
		client.player.lastRenderX = target.lastRenderX;
		client.player.lastRenderY = target.lastRenderY;
		client.player.lastRenderZ = target.lastRenderZ;
	}

	private static void applyPuppetLookToLocalPlayer(MinecraftClient client) {
		if (client == null || client.player == null || !hasPuppetLook) return;
		client.player.setYaw(puppetYaw);
		client.player.prevYaw = puppetYaw;
		client.player.setPitch(puppetPitch);
		client.player.prevPitch = puppetPitch;
		client.player.setHeadYaw(puppetYaw);
		client.player.prevHeadYaw = puppetYaw;
		client.player.setBodyYaw(puppetYaw);
		client.player.prevBodyYaw = puppetYaw;
	}

	private static void restoreControllerBodyTransform(MinecraftClient client) {
		if (client == null || client.player == null || !hasControllerBodyPose) return;
		client.player.updatePositionAndAngles(controllerBodyX, controllerBodyY, controllerBodyZ,
			controllerBodyYaw, controllerBodyPitch);
		client.player.setHeadYaw(controllerBodyHeadYaw);
		client.player.prevHeadYaw = controllerBodyHeadYaw;
		client.player.setBodyYaw(controllerBodyBodyYaw);
		client.player.prevBodyYaw = controllerBodyBodyYaw;
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
			return role != null && MapSelectRoles.PUPPETMASTER_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}
}
