package dev.mapselect.client.role.pelican;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.AnimatedCounterText;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.network.role.pelican.VultureDigestPayload;
import dev.mapselect.network.role.pelican.VultureEatPayload;
import dev.mapselect.network.role.pelican.VultureProgressPayload;
import dev.mapselect.network.role.pelican.VultureReleasePayload;
import dev.mapselect.network.role.pelican.VultureStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.voice.ClientVoiceActivity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
	private static int digestCooldownTicks;
	private static List<VultureProgressPayload.BellyEntry> belly = List.of();
	private static boolean showProgress;
	private static float progressAlpha;
	private static float bellyAlpha;
	private static boolean wasEatDown;
	private static boolean wasReleaseDown;
	private static boolean wasChatDown;
	private static boolean wasCommandDown;
	private static boolean wasPopupInteractDown;
	private static final AnimatedCounterText COUNTER_TEXT = new AnimatedCounterText();

	private ClientVultureState() {}

	public static void register() {
		ClientPelicanChatPopups.register();
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
			ClientPelicanChatPopups.clearAll();
			if (client.player != null) client.setCameraEntity(client.player);
			return;
		}
		localStashed = true;
		suppressInstinct();
		vultureId = payload.vultureId();
		vultureEntityId = payload.vultureEntityId();
		Entity vulture = vultureEntity(client);
		if (vulture != null && client.getCameraEntity() != vulture) client.setCameraEntity(vulture);
	}

	private static void applyProgress(VultureProgressPayload payload) {
		showProgress = payload.show();
		eaten = Math.max(0, payload.eaten());
		required = Math.max(1, payload.required());
		digestCooldownTicks = Math.max(0, payload.digestCooldownTicks());
		belly = payload.belly();
		COUNTER_TEXT.setTarget(Math.min(eaten, required), required);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			clearLocal();
			wasEatDown = false;
			wasReleaseDown = false;
			wasChatDown = false;
			wasCommandDown = false;
			wasPopupInteractDown = false;
			return;
		}
		progressAlpha = MathHelper.lerp(0.22F, progressAlpha,
			showProgress && isLocalVulture(client) && ClientRoleRevealState.canShowRoleHud(client) ? 1.0F : 0.0F);
		boolean showBelly = belly != null && !belly.isEmpty()
			&& (isLocalStashed(client)
				|| (showProgress && isLocalVulture(client) && ClientRoleRevealState.canShowRoleHud(client)));
		bellyAlpha = MathHelper.lerp(0.22F, bellyAlpha, showBelly ? 1.0F : 0.0F);
		COUNTER_TEXT.tick();
		if (digestCooldownTicks > 0) digestCooldownTicks--;

		if (isLocalStashed(client)) {
			suppressInstinct();
			Entity vulture = vultureEntity(client);
			if (vulture != null && client.getCameraEntity() != vulture) {
				client.setCameraEntity(vulture);
			}
		}

		handlePopupInteractionKey(client);
		handlePelicanChatKeys(client);

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

	private static void handlePopupInteractionKey(MinecraftClient client) {
		boolean pelican = canOpenPelicanChat(client)
			&& MapSelectRoles.VULTURE_ID.equals(localRoleId(client));
		boolean popupDown = pelican
			&& ClientAbilityKeys.isDown(client, ClientAbilityKeys.tertiaryBinding());
		if (!pelican && client.currentScreen instanceof PelicanPopupInteractionScreen screen) {
			screen.close();
		} else if (popupDown && !wasPopupInteractDown) {
			if (client.currentScreen instanceof PelicanPopupInteractionScreen screen) {
				screen.close();
			} else if (client.currentScreen == null) {
				client.setScreen(new PelicanPopupInteractionScreen());
			}
		}
		wasPopupInteractDown = popupDown;
	}

	private static void handlePelicanChatKeys(MinecraftClient client) {
		boolean pelican = canOpenPelicanChat(client);
		boolean chatDown = pelican && ClientAbilityKeys.isDown(client, client.options.chatKey);
		boolean commandDown = pelican && ClientAbilityKeys.isDown(client, client.options.commandKey);
		if (client.currentScreen == null) {
			if (commandDown && !wasCommandDown) {
				client.setScreen(new ChatScreen("/"));
			} else if (chatDown && !wasChatDown) {
				client.setScreen(new ChatScreen(""));
			}
		}
		wasChatDown = chatDown;
		wasCommandDown = commandDown;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null) return;
		if (client.textRenderer == null) return;
		if (progressAlpha > 0.02F && ClientRoleRevealState.canShowRoleHud(client)) {
			int alpha = Math.max(0, Math.min(255, (int) (progressAlpha * 255.0F)));
			int x = context.getScaledWindowWidth() - COUNTER_TEXT.width(client.textRenderer, "Pelican ") - 8;
			COUNTER_TEXT.render(context, client.textRenderer, "Pelican ", x, 26, 0xC5DF5C, alpha,
				tickCounter.getTickDelta(true));
		}
		if (bellyAlpha > 0.02F) {
			int alpha = Math.max(0, Math.min(255, (int) (bellyAlpha * 255.0F)));
			drawBelly(context, client, alpha, progressAlpha > 0.10F ? 40 : 26);
		}
	}

	private static void drawBelly(DrawContext context, MinecraftClient client, int alpha, int startY) {
		if (belly == null || belly.isEmpty()) return;
		int maxNameWidth = 0;
		for (VultureProgressPayload.BellyEntry entry : belly) {
			maxNameWidth = Math.max(maxNameWidth, client.textRenderer.getWidth(entry.name()));
		}
		int rowWidth = 18 + maxNameWidth;
		int x = context.getScaledWindowWidth() - rowWidth - 8;
		int y = startY;
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

	public static boolean shouldShowInventoryMenu(MinecraftClient client) {
		return client != null && client.player != null && client.world != null && showProgress
			&& MapSelectRoles.VULTURE_ID.equals(localRoleId(client))
			&& !isLocalStashed(client) && ClientRoleRevealState.canUseRoleAbility(client);
	}

	public static List<VultureProgressPayload.BellyEntry> bellyEntries() {
		return belly == null ? List.of() : List.copyOf(belly);
	}

	public static int digestCooldownTicks() {
		return Math.max(0, digestCooldownTicks);
	}

	public static void requestDigest(UUID targetId) {
		if (targetId != null && ClientPlayNetworking.canSend(VultureDigestPayload.ID)) {
			ClientPlayNetworking.send(new VultureDigestPayload(targetId));
		}
	}

	/**
	 * Uses the server-synced Pelican progress bit as a fallback while client role data catches up.
	 * This is deliberately independent of operator status.
	 */
	public static boolean canOpenPelicanChat(MinecraftClient client) {
		return client != null && client.player != null && client.world != null && !isLocalStashed(client)
			&& dev.doctor4t.wathe.game.GameFunctions.isPlayerAliveAndSurvival(client.player)
			&& (isLocalVulture(client) || showProgress);
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
			Role role = localRole(client);
			return role != null && ClientCopycatState.isEffectiveRole(client, MapSelectRoles.VULTURE_ID);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Identifier localRoleId(MinecraftClient client) {
		Role role = localRole(client);
		return role == null ? null : role.identifier();
	}

	private static Role localRole(MinecraftClient client) {
		try {
			if (client == null || client.world == null || client.player == null) return null;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game == null ? null : game.getRole(client.player);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void clearLocal() {
		localStashed = false;
		vultureId = null;
		vultureEntityId = -1;
		showProgress = false;
		eaten = 0;
		required = 1;
		digestCooldownTicks = 0;
		belly = List.of();
		progressAlpha = 0.0F;
		bellyAlpha = 0.0F;
		COUNTER_TEXT.reset();
		ClientPelicanChatPopups.clearAll();
	}

	private static void suppressInstinct() {
		if (WatheClient.instinctKeybind != null) WatheClient.instinctKeybind.setPressed(false);
		WatheClient.prevInstinctLightLevel = -0.04F;
		WatheClient.instinctLightLevel = -0.04F;
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}

	private static KeyBinding resolveReleaseBinding() {
		return ClientAbilityKeys.secondaryBinding();
	}
}
