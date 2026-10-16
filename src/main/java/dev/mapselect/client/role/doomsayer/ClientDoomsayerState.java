package dev.mapselect.client.role.doomsayer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.AnimatedCounterText;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.network.role.doomsayer.DoomsayerGuessPayload;
import dev.mapselect.network.role.doomsayer.DoomsayerStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.doomsayer.DoomsayerManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ClientDoomsayerState {
	private static final AnimatedCounterText COUNTER_TEXT = new AnimatedCounterText();
	private static boolean active;
	private static int correctGuesses;
	private static int requiredGuesses = 1;
	private static List<DoomsayerStatePayload.Target> targets = List.of();
	private static Object stateWorld;

	private ClientDoomsayerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DoomsayerStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientDoomsayerState::tick);
		HudRenderCallback.EVENT.register(ClientDoomsayerState::renderHud);
	}

	private static void apply(DoomsayerStatePayload payload) {
		stateWorld = MinecraftClient.getInstance().world;
		if (payload == null || !payload.active()) {
			clear();
			return;
		}
		active = payload.active();
		requiredGuesses = Math.max(1, payload.requiredGuesses());
		correctGuesses = Math.max(0, Math.min(requiredGuesses, payload.correctGuesses()));
		targets = payload.targets();
		COUNTER_TEXT.setTarget(correctGuesses, requiredGuesses);
	}

	private static void tick(MinecraftClient client) {
		COUNTER_TEXT.tick();
		Object currentWorld = client == null ? null : client.world;
		if (currentWorld != stateWorld) {
			clear();
			stateWorld = currentWorld;
		}
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active || client == null || client.player == null || client.textRenderer == null
				|| ClientHudVisibility.shouldHide(client) || !isLocalDoomsayer(client)
				|| !ClientRoleRevealState.canShowRoleHud(client)) return;
		String label = "Doomsayer ";
		int x = context.getScaledWindowWidth() - COUNTER_TEXT.width(client.textRenderer, label) - 8;
		COUNTER_TEXT.render(context, client.textRenderer, label, x, 26, 0x39F29B, 255,
			tickCounter.getTickDelta(true));
	}

	public static boolean shouldShowInventoryMenu(MinecraftClient client) {
		return active && client != null && client.player != null && client.world != null
			&& isLocalDoomsayer(client) && ClientRoleRevealState.canUseRoleAbility(client);
	}

	public static List<DoomsayerStatePayload.Target> targets() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return targets;
		return targets.stream().filter(target -> {
			if (target == null || target.playerId() == null) return false;
			var player = client.world.getPlayerByUuid(target.playerId());
			return player != null && GameFunctions.isPlayerAliveAndSurvival(player);
		}).toList();
	}

	public static DoomsayerStatePayload.Target target(UUID playerId) {
		if (playerId == null) return null;
		for (DoomsayerStatePayload.Target target : targets()) {
			if (playerId.equals(target.playerId())) return target;
		}
		return null;
	}

	public static List<Role> guessableRoles() {
		return WatheRoles.ROLES.stream()
			.filter(DoomsayerManager::isGuessableRole)
			.sorted(Comparator.comparing(role -> role.identifier().getPath(), String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	public static void submitGuess(UUID targetId, String rolePath) {
		if (targetId == null || rolePath == null || rolePath.isBlank()
				|| !ClientPlayNetworking.canSend(DoomsayerGuessPayload.ID)) return;
		ClientPlayNetworking.send(new DoomsayerGuessPayload(targetId, rolePath.strip()));
	}

	public static boolean isLocalDoomsayer(MinecraftClient client) {
		return MapSelectRoles.DOOMSAYER_ID.equals(localRoleId(client));
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			if (client == null || client.world == null || client.player == null) return null;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void clear() {
		active = false;
		correctGuesses = 0;
		requiredGuesses = 1;
		targets = List.of();
		COUNTER_TEXT.reset();
	}
}
