package dev.mapselect.client.role.rat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.AnimatedCounterText;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.game.WatheStaminaAccess;
import dev.mapselect.network.role.rat.RatInfectPayload;
import dev.mapselect.network.role.rat.RatHuntPayload;
import dev.mapselect.network.role.rat.RatInvisibilityPayload;
import dev.mapselect.network.role.rat.RatPlayDeadPayload;
import dev.mapselect.network.role.rat.RatStatePayload;
import dev.mapselect.network.role.rat.RatVisibilityPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.rat.RatManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientRatState {
	public static final int INFECTED_GLOW_COLOR = 0xB89A6A;
	public static final int HUNT_GLOW_COLOR = 0xFF3B30;
	public static final int JUMP_COOLDOWN_TICKS = 3 * 20;
	private static final Set<UUID> INVISIBLE_RATS = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> INFECTED_PLAYERS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> HUNT_END_TICKS = new ConcurrentHashMap<>();
	private static final AnimatedCounterText COUNTER_TEXT = new AnimatedCounterText();
	private static boolean active;
	private static int invisibilityTicks;
	private static int maxInvisibilityTicks = 1;
	private static boolean invisible;
	private static boolean playingDead;
	private static int infectedPlayers;
	private static int requiredPlayers;
	private static float counterAlpha;
	private static float huntWarningAlpha;
	private static float huntWarningAgeTicks;
	private static boolean wasPrimaryDown;
	private static boolean wasSecondaryDown;
	private static boolean wasTertiaryDown;
	private static long jumpCooldownEndTick;
	private static long allowedJumpTick = Long.MIN_VALUE;
	private static UUID jumpCooldownPlayerId;

	private ClientRatState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RatStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(RatVisibilityPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				INVISIBLE_RATS.clear();
				INVISIBLE_RATS.addAll(payload.invisibleRatIds());
			}));
		ClientPlayNetworking.registerGlobalReceiver(RatHuntPayload.ID, (payload, context) ->
			context.client().execute(() -> applyHunts(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(ClientRatState::tick);
		HudRenderCallback.EVENT.register(ClientRatState::renderHud);
	}

	public static boolean isInvisibleRat(PlayerEntity player) {
		return player != null && INVISIBLE_RATS.contains(player.getUuid());
	}

	public static boolean isHuntedRat(PlayerEntity player) {
		return player != null && HUNT_END_TICKS.containsKey(player.getUuid());
	}

	public static boolean isLocalInvisible() {
		return active && invisible;
	}

	public static boolean isPlayingDead() {
		return active && playingDead;
	}

	public static boolean allowLocalJump(MinecraftClient client, boolean jumpKeyPressed) {
		if (!jumpKeyPressed || client == null || client.player == null || client.world == null) {
			return false;
		}
		UUID playerId = client.player.getUuid();
		long currentTick = client.world.getTime();
		if (!playerId.equals(jumpCooldownPlayerId)) {
			jumpCooldownPlayerId = playerId;
			jumpCooldownEndTick = 0L;
			allowedJumpTick = Long.MIN_VALUE;
		}
		if (currentTick == allowedJumpTick) return true;
		if (!client.player.isOnGround() || currentTick < jumpCooldownEndTick) return false;
		allowedJumpTick = currentTick;
		jumpCooldownEndTick = currentTick + JUMP_COOLDOWN_TICKS;
		return true;
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		return player != null && (HUNT_END_TICKS.containsKey(player.getUuid())
			|| (active && INFECTED_PLAYERS.contains(player.getUuid())));
	}

	public static int glowColor(AbstractClientPlayerEntity player) {
		return player != null && HUNT_END_TICKS.containsKey(player.getUuid())
			? HUNT_GLOW_COLOR : INFECTED_GLOW_COLOR;
	}

	public static float invisibilityCharge() {
		return Math.max(0.0F, Math.min(1.0F,
			invisibilityTicks / (float) Math.max(1, maxInvisibilityTicks)));
	}

	public static long jumpCooldownRemainingTicks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!active || client.world == null || client.player == null || !isLocalRat(client)) return 0L;
		return Math.max(0L, jumpCooldownEndTick - client.world.getTime());
	}

	private static void apply(RatStatePayload payload) {
		if (payload == null) {
			clearLocal();
			return;
		}
		active = payload.active();
		invisibilityTicks = payload.invisibilityTicks();
		maxInvisibilityTicks = payload.maxInvisibilityTicks();
		invisible = payload.invisible();
		playingDead = payload.playingDead();
		infectedPlayers = payload.infectedPlayers();
		requiredPlayers = payload.requiredPlayers();
		INFECTED_PLAYERS.clear();
		if (payload.active()) INFECTED_PLAYERS.addAll(payload.infectedPlayerIds());
		COUNTER_TEXT.setTarget(infectedPlayers, requiredPlayers);
	}

	private static void applyHunts(RatHuntPayload payload) {
		boolean wasActive = !HUNT_END_TICKS.isEmpty();
		HUNT_END_TICKS.clear();
		if (payload == null) return;
		for (RatHuntPayload.Hunt hunt : payload.hunts()) {
			HUNT_END_TICKS.put(hunt.ratId(), hunt.endTick());
		}
		if (!wasActive && !HUNT_END_TICKS.isEmpty()) huntWarningAgeTicks = 0.0F;
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) INVISIBLE_RATS.clear();
		if (client == null || client.world == null) {
			HUNT_END_TICKS.clear();
		} else {
			long now = client.world.getTime();
			HUNT_END_TICKS.entrySet().removeIf(entry -> entry.getValue() <= now);
		}
		huntWarningAlpha = MathHelper.lerp(0.18F, huntWarningAlpha,
			HUNT_END_TICKS.isEmpty() ? 0.0F : 1.0F);
		if (!HUNT_END_TICKS.isEmpty()) {
			huntWarningAgeTicks = Math.min(200.0F, huntWarningAgeTicks + 1.0F);
		}
		if (huntWarningAlpha < 0.01F) huntWarningAlpha = 0.0F;
		if (huntWarningAlpha > 0.99F) huntWarningAlpha = 1.0F;
		counterAlpha = MathHelper.lerp(0.22F, counterAlpha,
			active && isLocalRat(client) && ClientRoleRevealState.canShowRoleHud(client) ? 1.0F : 0.0F);
		if (counterAlpha < 0.01F) counterAlpha = 0.0F;
		if (counterAlpha > 0.99F) counterAlpha = 1.0F;
		COUNTER_TEXT.tick();
		if (active && isLocalRat(client)) {
			regenerateExtraStamina(client);
			if (playingDead) lockLocalInput(client);
		}
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalRat(client)) {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
			wasTertiaryDown = false;
			return;
		}

		if (!playingDead) {
			KeyBinding primary = ClientAbilityKeys.primaryBinding();
			boolean primaryDown = primary != null && ClientAbilityKeys.isDown(client, primary);
			if (primaryDown && !wasPrimaryDown && ClientPlayNetworking.canSend(RatInfectPayload.ID)) {
				ClientPlayNetworking.send(new RatInfectPayload());
			}
			wasPrimaryDown = primaryDown;

			KeyBinding secondary = ClientAbilityKeys.secondaryBinding();
			boolean secondaryDown = secondary != null && ClientAbilityKeys.isDown(client, secondary);
			if (secondaryDown && !wasSecondaryDown && ClientPlayNetworking.canSend(RatInvisibilityPayload.ID)) {
				ClientPlayNetworking.send(new RatInvisibilityPayload());
			}
			wasSecondaryDown = secondaryDown;
		} else {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
		}

		KeyBinding tertiary = ClientAbilityKeys.tertiaryBinding();
		boolean tertiaryDown = tertiary != null && ClientAbilityKeys.isDown(client, tertiary);
		if (tertiaryDown && !wasTertiaryDown && ClientPlayNetworking.canSend(RatPlayDeadPayload.ID)) {
			ClientPlayNetworking.send(new RatPlayDeadPayload());
		}
		wasTertiaryDown = tertiaryDown;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		renderHuntWarning(context, tickCounter, client);
		if (client == null || ClientHudVisibility.shouldHide(client) || client.player == null
				|| client.textRenderer == null || counterAlpha <= 0.02F || !isLocalRat(client)) {
			return;
		}
		int alpha = Math.max(0, Math.min(255, Math.round(counterAlpha * 255.0F)));
		String label = "Infected ";
		int x = context.getScaledWindowWidth() - COUNTER_TEXT.width(client.textRenderer, label) - 8;
		COUNTER_TEXT.render(context, client.textRenderer, label, x, 26, INFECTED_GLOW_COLOR, alpha,
			tickCounter.getTickDelta(true));
	}

	private static void renderHuntWarning(DrawContext context, RenderTickCounter tickCounter,
			MinecraftClient client) {
		if (client == null || client.world == null || client.player == null || client.textRenderer == null
				|| ClientHudVisibility.shouldHide(client) || HUNT_END_TICKS.isEmpty()
				|| huntWarningAlpha <= 0.02F) return;
		long now = client.world.getTime();
		long endTick = HUNT_END_TICKS.values().stream().mapToLong(Long::longValue).min().orElse(now);
		long remainingTicks = Math.max(0L, endTick - now);
		if (remainingTicks <= 0L) return;

		int alpha = Math.max(0, Math.min(255, Math.round(huntWarningAlpha * 255.0F)));
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int panelWidth = Math.min(width - 24, 430);
		int panelHeight = 78;
		int left = (width - panelWidth) / 2;
		float intro = MathHelper.clamp(
			(huntWarningAgeTicks + tickCounter.getTickDelta(true)) / 16.0F, 0.0F, 1.0F);
		float shifted = intro - 1.0F;
		float eased = 1.0F + 2.70158F * shifted * shifted * shifted
			+ 1.70158F * shifted * shifted;
		int targetTop = 24;
		int top = Math.round(MathHelper.lerp(eased, -panelHeight - 12.0F, targetTop));
		int panelAlpha = Math.round(alpha * 0.78F);
		context.fill(left + 4, top + 5, left + panelWidth + 4, top + panelHeight + 5,
			(Math.round(alpha * 0.35F) << 24));
		context.fill(left, top, left + panelWidth, top + panelHeight,
			(panelAlpha << 24) | 0x120607);
		context.fill(left, top, left + panelWidth, top + 4, (alpha << 24) | HUNT_GLOW_COLOR);
		context.drawBorder(left, top, panelWidth, panelHeight,
			(alpha << 24) | 0x8A1F25);

		float pulse = 1.0F + 0.04F * MathHelper.sin(
			(client.world.getTime() + tickCounter.getTickDelta(true)) * 0.22F);
		drawCenteredScaled(context, client, "THE RAT HAS INFECTED EVERYONE",
			width / 2, top + 17, (alpha << 24) | 0xFFDFE0, pulse * 1.65F, panelWidth - 24);
		long seconds = Math.max(1L, (remainingTicks + 19L) / 20L);
		String countdown = "KILL THE RAT  -  " + seconds + " SECONDS REMAIN";
		drawCenteredScaled(context, client, countdown, width / 2, top + 45,
			(alpha << 24) | 0xFF6A62, 1.15F, panelWidth - 24);

		int barLeft = left + 12;
		int barRight = left + panelWidth - 12;
		int barTop = top + panelHeight - 9;
		context.fill(barLeft, barTop, barRight, barTop + 3, (alpha << 24) | 0x351316);
		int fill = Math.round((barRight - barLeft)
			* MathHelper.clamp(remainingTicks / (float) RatManager.HUNT_DURATION_TICKS, 0.0F, 1.0F));
		context.fill(barLeft, barTop, barLeft + fill, barTop + 3,
			(alpha << 24) | HUNT_GLOW_COLOR);
	}

	private static void drawCenteredScaled(DrawContext context, MinecraftClient client, String value,
			int centerX, int y, int color, float maximumScale, int maximumWidth) {
		int textWidth = Math.max(1, client.textRenderer.getWidth(value));
		float scale = Math.min(maximumScale, Math.max(0.55F, maximumWidth / (float) textWidth));
		context.getMatrices().push();
		context.getMatrices().translate(centerX, y, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawTextWithShadow(client.textRenderer, value, -textWidth / 2, 0, color);
		context.getMatrices().pop();
	}

	public static boolean isLocalRat(MinecraftClient client) {
		return client != null && isRat(client.player);
	}

	public static boolean isRat(PlayerEntity player) {
		try {
			if (player == null || player.getWorld() == null) return false;
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
			Role role = game == null ? null : game.getRole(player);
			return role != null && MapSelectRoles.RAT_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void regenerateExtraStamina(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.player.isSprinting() || playingDead) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		Role role = game == null ? null : game.getRole(client.player);
		if (role == null || !MapSelectRoles.RAT_ID.equals(role.identifier())
				|| role.getMaxSprintTime() <= 0) return;
		float stamina = WatheStaminaAccess.get(client.player);
		if (stamina < 0.0F || stamina >= role.getMaxSprintTime()) return;
		WatheStaminaAccess.set(client.player, Math.min(role.getMaxSprintTime(), stamina + 0.25F));
	}

	private static void lockLocalInput(MinecraftClient client) {
		if (client == null || client.player == null) return;
		Input input = client.player.input;
		if (input != null) {
			input.movementSideways = 0.0F;
			input.movementForward = 0.0F;
			input.pressingForward = false;
			input.pressingBack = false;
			input.pressingLeft = false;
			input.pressingRight = false;
			input.jumping = false;
			input.sneaking = false;
		}
		client.player.setSprinting(false);
		client.player.setVelocity(Vec3d.ZERO);
	}

	private static void clear() {
		INVISIBLE_RATS.clear();
		HUNT_END_TICKS.clear();
		huntWarningAlpha = 0.0F;
		huntWarningAgeTicks = 0.0F;
		clearLocal();
	}

	private static void clearLocal() {
		active = false;
		INFECTED_PLAYERS.clear();
		invisibilityTicks = 0;
		maxInvisibilityTicks = 1;
		invisible = false;
		playingDead = false;
		infectedPlayers = 0;
		requiredPlayers = 0;
		counterAlpha = 0.0F;
		COUNTER_TEXT.reset();
		wasPrimaryDown = false;
		wasSecondaryDown = false;
		wasTertiaryDown = false;
		jumpCooldownEndTick = 0L;
		allowedJumpTick = Long.MIN_VALUE;
		jumpCooldownPlayerId = null;
	}
}
