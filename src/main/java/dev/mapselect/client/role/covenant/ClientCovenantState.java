package dev.mapselect.client.role.covenant;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.hud.ClientHudVisibility;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.pelican.ClientVultureState;


import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.role.covenant.CovenantBatPayload;
import dev.mapselect.network.role.covenant.CovenantBatMovementPayload;
import dev.mapselect.network.role.covenant.CovenantBatRenderPayload;
import dev.mapselect.network.role.covenant.CovenantBitePayload;
import dev.mapselect.network.role.covenant.CovenantStatePayload;
import dev.mapselect.network.role.covenant.CovenantTurnPayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ClientCovenantState {
	private static final float BAT_FLY_SPEED = 0.10F;
	private static final double BAT_HORIZONTAL_SPEED = 0.45D;
	private static final double BAT_VERTICAL_SPEED = 0.34D;
	private static final Set<UUID> BAT_FORM_PLAYERS = new HashSet<>();
	private static boolean active;
	private static int bloodTicks;
	private static int maxBloodTicks = 1;
	private static int batTicks;
	private static int maxBatTicks = 1;
	private static boolean batForm;
	private static boolean dracula;
	private static boolean wasBiteDown;
	private static boolean wasBatDown;
	private static boolean wasTurnDown;

	private ClientCovenantState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CovenantStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CovenantBatRenderPayload.ID, (payload, context) ->
			context.client().execute(() -> applyBatRender(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientCovenantState::tick);
		HudRenderCallback.EVENT.register(ClientCovenantState::render);
	}

	public static int batRemainingTicks() {
		return batTicks;
	}

	public static boolean hasActiveState() {
		return active;
	}

	public static int batMaxTicks() {
		return maxBatTicks;
	}

	public static boolean batForm() {
		return batForm;
	}

	public static float turnReadiness() {
		int required = Math.max(1, (int) Math.ceil(maxBloodTicks * 0.75D));
		return Math.max(0.0F, Math.min(1.0F, bloodTicks / (float) required));
	}

	public static boolean isBatForm(PlayerEntity player) {
		if (player == null) return false;
		if (BAT_FORM_PLAYERS.contains(player.getUuid())) return true;
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.player != null
			&& client.player.getUuid().equals(player.getUuid()) && batForm;
	}

	private static void apply(CovenantStatePayload payload) {
		active = payload.active();
		dracula = payload.dracula();
		bloodTicks = payload.bloodTicks();
		maxBloodTicks = payload.maxBloodTicks();
		batTicks = payload.batTicks();
		maxBatTicks = payload.maxBatTicks();
		batForm = payload.batForm();
	}

	private static void applyBatRender(CovenantBatRenderPayload payload) {
		BAT_FORM_PLAYERS.clear();
		if (payload != null) BAT_FORM_PLAYERS.addAll(payload.playerIds());
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) {
			BAT_FORM_PLAYERS.clear();
		}
		if (client == null || client.player == null || client.world == null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalCovenant(client)
				|| client.currentScreen != null) {
			wasBiteDown = false;
			wasBatDown = false;
			wasTurnDown = false;
			return;
		}

		KeyBinding bat = ClientAbilityKeys.secondaryBinding();
		boolean batDown = bat != null && ClientAbilityKeys.isDown(client, bat);
		if (isLocalDracula(client) && batDown && !wasBatDown && ClientPlayNetworking.canSend(CovenantBatPayload.ID)) {
			ClientPlayNetworking.send(new CovenantBatPayload());
		}
		wasBatDown = batDown;

		KeyBinding bite = ClientAbilityKeys.primaryBinding();
		boolean biteDown = bite != null && ClientAbilityKeys.isDown(client, bite);
		if (!batForm && biteDown && !wasBiteDown && ClientPlayNetworking.canSend(CovenantBitePayload.ID)) {
			ClientPlayNetworking.send(new CovenantBitePayload());
		}
		wasBiteDown = biteDown;

		KeyBinding turn = ClientAbilityKeys.tertiaryBinding();
		boolean turnDown = turn != null && ClientAbilityKeys.isDown(client, turn);
		if (!batForm && isLocalDracula(client) && turnDown && !wasTurnDown
				&& ClientPlayNetworking.canSend(CovenantTurnPayload.ID)) {
			ClientPlayNetworking.send(new CovenantTurnPayload());
		}
		wasTurnDown = turnDown;

		if (batForm) maintainBatFlight(client);
	}

	private static void maintainBatFlight(MinecraftClient client) {
		client.player.getAbilities().allowFlying = true;
		client.player.getAbilities().flying = true;
		client.player.getAbilities().setFlySpeed(BAT_FLY_SPEED);
		client.player.fallDistance = 0.0F;
		float forward = 0.0F;
		float sideways = 0.0F;
		if (ClientAbilityKeys.isDown(client, client.options.forwardKey)) forward += 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.backKey)) forward -= 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.leftKey)) sideways += 1.0F;
		if (ClientAbilityKeys.isDown(client, client.options.rightKey)) sideways -= 1.0F;
		boolean ascending = ClientAbilityKeys.isDown(client, client.options.jumpKey);
		boolean descending = ClientAbilityKeys.isDown(client, client.options.sneakKey);
		boolean sprinting = ClientAbilityKeys.isDown(client, client.options.sprintKey);
		applyBatVelocity(client.player, sideways, forward, ascending, descending, sprinting);
		if (ClientPlayNetworking.canSend(CovenantBatMovementPayload.ID)) {
			ClientPlayNetworking.send(new CovenantBatMovementPayload(sideways, forward,
				ascending, descending, sprinting));
		}
	}

	private static void applyBatVelocity(PlayerEntity player, float sideways, float forward,
			boolean ascending, boolean descending, boolean sprinting) {
		if (player == null) return;
		Vec3d velocity = player.getVelocity();
		double targetY = ascending == descending ? 0.0D : ascending ? BAT_VERTICAL_SPEED : -BAT_VERTICAL_SPEED;
		double inputLength = Math.sqrt(sideways * sideways + forward * forward);
		if (inputLength < 0.001D) {
			player.setVelocity(velocity.x * 0.72D,
				MathHelper.lerp(0.58D, velocity.y, targetY), velocity.z * 0.72D);
			return;
		}
		double normalizedSideways = sideways / inputLength;
		double normalizedForward = forward / inputLength;
		double yaw = Math.toRadians(player.getYaw());
		double directionX = normalizedSideways * Math.cos(yaw) - normalizedForward * Math.sin(yaw);
		double directionZ = normalizedForward * Math.cos(yaw) + normalizedSideways * Math.sin(yaw);
		double speed = BAT_HORIZONTAL_SPEED * (sprinting ? 1.18D : 1.0D);
		player.setVelocity(
			MathHelper.lerp(0.48D, velocity.x, directionX * speed),
			MathHelper.lerp(0.58D, velocity.y, targetY),
			MathHelper.lerp(0.48D, velocity.z, directionZ * speed)
		);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null) return;
		if (!active || !isLocalCovenant(client) || !ClientRoleRevealState.canShowRoleHud(client)
				|| ClientVultureState.isLocalStashed(client)) {
			return;
		}

		int height = 72;
		int width = 7;
		int x = context.getScaledWindowWidth() - 18;
		int y = context.getScaledWindowHeight() / 2 - height / 2;
		drawVerticalBar(context, x, y, width, height, bloodTicks / (float) Math.max(1, maxBloodTicks),
			0xFF2C060D, 0xFFB81832, 0xFFFF5B6F);
		context.drawTextWithShadow(client.textRenderer, Text.literal("Blood"), x - 22, y - 12, 0xFFFFE7EA);
	}

	private static void drawVerticalBar(DrawContext context, int x, int y, int width, int height,
			float progress, int frame, int fill, int shine) {
		float clamped = Math.max(0.0F, Math.min(1.0F, progress));
		context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF100306);
		context.fill(x, y, x + width, y + height, frame);
		int filled = Math.round((height - 2) * clamped);
		int top = y + height - 1 - filled;
		if (filled > 0) {
			context.fill(x + 1, top, x + width - 1, y + height - 1, fill);
			context.fill(x + 1, top, x + 2, y + height - 1, shine);
		}
	}

	private static boolean isLocalCovenant(MinecraftClient client) {
		Identifier id = localRoleId(client);
		id = ClientCopycatState.effectiveRoleId(client, id);
		return MapSelectRoles.DRACULA_ID.equals(id) || MapSelectRoles.VAMPIRE_ID.equals(id);
	}

	private static boolean isLocalDracula(MinecraftClient client) {
		return MapSelectRoles.DRACULA_ID.equals(ClientCopycatState.effectiveRoleId(client, localRoleId(client)));
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
