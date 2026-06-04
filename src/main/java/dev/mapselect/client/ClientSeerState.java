package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.SeerDeathPayload;
import dev.mapselect.network.SeerCompareUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public final class ClientSeerState {
	private static int flashTicks;
	private static final int FLASH_DURATION = 26;
	private static boolean wasCompareDown;

	private ClientSeerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SeerDeathPayload.ID, (payload, context) ->
			context.client().execute(() -> flashTicks = FLASH_DURATION));
		ClientTickEvents.END_CLIENT_TICK.register(ClientSeerState::tick);
		HudRenderCallback.EVENT.register(ClientSeerState::renderHud);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !isLocalSeer(client)) {
			wasCompareDown = false;
			return;
		}
		KeyBinding binding = ClientAbilityKeys.primaryBinding();
		boolean down = binding != null && ClientAbilityKeys.isDown(client, binding);
		if (down && !wasCompareDown && ClientPlayNetworking.canSend(SeerCompareUsePayload.ID)) {
			ClientPlayNetworking.send(new SeerCompareUsePayload());
		}
		wasCompareDown = down;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null || flashTicks <= 0) return;
		float progress = flashTicks / (float) FLASH_DURATION;
		int alpha = Math.max(0, Math.min(145, Math.round(145.0F * progress)));
		int color = (alpha << 24) | 0xB00018;
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), color);
		flashTicks--;
	}

	private static boolean isLocalSeer(MinecraftClient client) {
		return MapSelectRoles.SEER_ID.equals(ClientCopycatState.effectiveRoleId(client, localRoleId(client)));
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
