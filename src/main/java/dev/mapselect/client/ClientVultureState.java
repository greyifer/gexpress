package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.VultureEatPayload;
import dev.mapselect.network.VultureProgressPayload;
import dev.mapselect.network.VultureReleasePayload;
import dev.mapselect.network.VultureStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.voice.ClientVoiceActivity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.UUID;

public final class ClientVultureState {
	private static volatile UUID vultureId;
	private static volatile int vultureEntityId = -1;
	private static volatile boolean localStashed;
	private static int eaten;
	private static int required = 1;
	private static List<VultureProgressPayload.BellyEntry> belly = List.of();
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
			localStashed = false;
			vultureId = null;
			vultureEntityId = -1;
			if (client.player != null) client.setCameraEntity(client.player);
			return;
		}
		localStashed = true;
		vultureId = payload.vultureId();
		vultureEntityId = payload.vultureEntityId();
		Entity vulture = vultureEntity(client);
		if (vulture != null && client.getCameraEntity() != vulture) client.setCameraEntity(vulture);
	}

	private static void applyProgress(VultureProgressPayload payload) {
		showProgress = payload.show();
		eaten = Math.max(0, payload.eaten());
		required = Math.max(1, payload.required());
		belly = payload.belly();
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
			Entity vulture = vultureEntity(client);
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
			drawBelly(context, client, alpha);
		}
	}

	private static void drawBelly(DrawContext context, MinecraftClient client, int alpha) {
		if (belly == null || belly.isEmpty()) return;
		int maxNameWidth = 0;
		for (VultureProgressPayload.BellyEntry entry : belly) {
			maxNameWidth = Math.max(maxNameWidth, client.textRenderer.getWidth(entry.name()));
		}
		int rowWidth = 18 + maxNameWidth;
		int x = context.getScaledWindowWidth() - rowWidth - 8;
		int y = 40;
		for (VultureProgressPayload.BellyEntry entry : belly) {
			drawHead(context, client, entry.playerId(), x, y, alpha, isSpeaking(entry.playerId()));
			context.drawTextWithShadow(client.textRenderer, entry.name(), x + 18, y + 4, (alpha << 24) | 0xFFFFFF);
			y += 19;
		}
	}

	private static void drawHead(DrawContext context, MinecraftClient client, UUID playerId, int x, int y,
			int alpha, boolean speaking) {
		if (playerId == null) return;
		Identifier texture = DefaultSkinHelper.getSkinTextures(playerId).texture();
		ClientPlayNetworkHandler network = client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(playerId);
			if (entry != null) texture = entry.getSkinTextures().texture();
		}
		int border = speaking ? 0xFFFFFFFF : ((alpha << 24) | 0x55708430);
		context.fill(x - 1, y - 1, x + 17, y + 17, border);
		context.drawTexture(texture, x, y, 16, 16, 8.0F, 8.0F, 8, 8, 64, 64);
		context.drawTexture(texture, x, y, 16, 16, 40.0F, 8.0F, 8, 8, 64, 64);
	}

	private static boolean isSpeaking(UUID playerId) {
		return ClientVoiceActivity.isSpeaking(playerId);
	}

	public static boolean isLocalStashed(MinecraftClient client) {
		return client != null && client.player != null && localStashed;
	}

	public static boolean shouldHideBellyEntity(MinecraftClient client, Entity entity) {
		if (!isLocalStashed(client) || !(entity instanceof PlayerEntity player)) return false;
		if (client.player != null && client.player.getUuid().equals(player.getUuid())) return false;
		if (vultureId != null && vultureId.equals(player.getUuid())) return false;
		return player.isSpectator();
	}

	private static Entity vultureEntity(MinecraftClient client) {
		if (client == null || client.world == null) return null;
		Entity byId = vultureEntityId < 0 ? null : client.world.getEntityById(vultureEntityId);
		if (byId != null) return byId;
		return vultureId == null ? null : client.world.getPlayerByUuid(vultureId);
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
		localStashed = false;
		vultureId = null;
		vultureEntityId = -1;
		showProgress = false;
		belly = List.of();
		progressAlpha = 0.0F;
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}

	private static KeyBinding resolveReleaseBinding() {
		return ClientAbilityKeys.secondaryBinding();
	}
}
