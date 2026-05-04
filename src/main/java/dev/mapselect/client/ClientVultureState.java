package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.VultureEatPayload;
import dev.mapselect.network.VultureProgressPayload;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public final class ClientVultureState {
	private static volatile UUID vultureId;
	private static volatile int vultureEntityId = -1;
	private static int eaten;
	private static int required = 1;
	private static boolean showProgress;
	private static float progressAlpha;
	private static boolean wasEatDown;
	private static boolean wasReleaseDown;

	private ClientVultureState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VultureStatePayload.ID, (payload, context) ->
			context.client().execute(() -> applyState(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(VultureProgressPayload.ID, (payload, context) ->
			context.client().execute(() -> applyProgress(payload)));
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

	private static void applyProgress(VultureProgressPayload payload) {
		showProgress = payload.show();
		eaten = Math.max(0, payload.eaten());
		required = Math.max(1, payload.required());
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			clearLocal();
			wasEatDown = false;
			wasReleaseDown = false;
			return;
		}
		progressAlpha = MathHelper.lerp(0.22F, progressAlpha,
			showProgress && isLocalVulture(client) && ClientRoleRevealState.canShowRoleHud(client) ? 1.0F : 0.0F);

		if (isLocalStashed(client)) {
			Entity vulture = client.world.getEntityById(vultureEntityId);
			if (vulture != null && client.getCameraEntity() != vulture) {
				client.setCameraEntity(vulture);
			}
		}

		if (isLocalStashed(client) || !isLocalVulture(client) || client.currentScreen != null
				|| !ClientRoleRevealState.canUseRoleAbility(client)) {
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
		if (client == null || client.player == null) return;
		if (progressAlpha > 0.02F && client.textRenderer != null && !client.options.hudHidden
				&& ClientRoleRevealState.canShowRoleHud(client)) {
			int alpha = Math.max(0, Math.min(255, (int) (progressAlpha * 255.0F)));
			Text text = Text.literal("Pelican " + Math.min(eaten, required) + "/" + required);
			int x = context.getScaledWindowWidth() - client.textRenderer.getWidth(text) - 8;
			context.drawTextWithShadow(client.textRenderer, text, x, 26, 0x00C5DF5C | (alpha << 24));
		}
	}

	public static boolean isLocalStashed(MinecraftClient client) {
		return client != null && client.player != null && vultureId != null;
	}

	public static boolean shouldHideBellyEntity(MinecraftClient client, Entity entity) {
		if (!isLocalStashed(client) || !(entity instanceof PlayerEntity player)) return false;
		if (vultureId != null && vultureId.equals(player.getUuid())) return false;
		Entity vulture = client.world == null ? null : client.world.getEntityById(vultureEntityId);
		return vulture != null && player.isSpectator() && player.squaredDistanceTo(vulture) <= 9.0D;
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
		showProgress = false;
		progressAlpha = 0.0F;
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}

	private static KeyBinding resolveReleaseBinding() {
		return ClientAbilityKeys.secondaryBinding();
	}
}
