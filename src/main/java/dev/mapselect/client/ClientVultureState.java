package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.VultureEatPayload;
import dev.mapselect.network.VultureReleasePayload;
import dev.mapselect.network.VultureStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;

import java.util.UUID;

public final class ClientVultureState {
	private static volatile UUID vultureId;
	private static volatile int vultureEntityId = -1;
	private static boolean wasEatDown;
	private static boolean wasReleaseDown;

	private ClientVultureState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VultureStatePayload.ID, (payload, context) ->
			context.client().execute(() -> applyState(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientVultureState::tick);
		HudRenderCallback.EVENT.register(ClientVultureState::renderHud);
	}

	private static void applyState(MinecraftClient client, VultureStatePayload payload) {
		if (client == null) return;
		if (!payload.stashed()) {
			vultureId = null;
			vultureEntityId = -1;
			if (client.player != null) client.setCameraEntity(client.player);
			return;
		}
		vultureId = payload.vultureId();
		vultureEntityId = payload.vultureEntityId();
		Entity vulture = client.world == null ? null : client.world.getEntityById(vultureEntityId);
		if (vulture != null) client.setCameraEntity(vulture);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			clearLocal();
			wasEatDown = false;
			wasReleaseDown = false;
			return;
		}

		if (isLocalStashed(client)) {
			Entity vulture = client.world.getEntityById(vultureEntityId);
			if (vulture != null && client.getCameraEntity() != vulture) {
				client.setCameraEntity(vulture);
			}
		}

		if (!isLocalVulture(client) || client.currentScreen != null) {
			wasEatDown = false;
			wasReleaseDown = false;
			return;
		}

		KeyBinding eat = resolveAbilityBinding();
		boolean eatDown = eat != null && ClientAbilityKeys.isDown(client, eat);
		if (eatDown && !wasEatDown && ClientPlayNetworking.canSend(VultureEatPayload.ID)) {
			ClientPlayNetworking.send(new VultureEatPayload());
		}
		wasEatDown = eatDown;

		KeyBinding release = resolveReleaseBinding();
		boolean releaseDown = release != null && ClientAbilityKeys.isDown(client, release);
		if (releaseDown && !wasReleaseDown && ClientPlayNetworking.canSend(VultureReleasePayload.ID)) {
			ClientPlayNetworking.send(new VultureReleasePayload());
		}
		wasReleaseDown = releaseDown;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || !isLocalStashed(client)) return;
		int alpha = 96;
		int color = (alpha << 24) | 0x303030;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
	}

	private static boolean isLocalStashed(MinecraftClient client) {
		return client != null && client.player != null && vultureId != null;
	}

	private static boolean isLocalVulture(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.VULTURE_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void clearLocal() {
		vultureId = null;
		vultureEntityId = -1;
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}

	private static KeyBinding resolveReleaseBinding() {
		return ClientAbilityKeys.secondaryBinding();
	}
}
