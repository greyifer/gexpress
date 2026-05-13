package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.CovenantBatPayload;
import dev.mapselect.network.CovenantBitePayload;
import dev.mapselect.network.CovenantStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ClientCovenantState {
	private static boolean active;
	private static boolean dracula;
	private static int bloodTicks;
	private static int maxBloodTicks = 1;
	private static int batTicks;
	private static int maxBatTicks = 1;
	private static boolean batForm;
	private static boolean wasBiteDown;
	private static boolean wasBatDown;

	private ClientCovenantState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CovenantStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientCovenantState::tick);
		HudRenderCallback.EVENT.register(ClientCovenantState::render);
	}

	public static int batRemainingTicks() {
		return batTicks;
	}

	public static int batMaxTicks() {
		return maxBatTicks;
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

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalCovenant(client)
				|| client.currentScreen != null) {
			wasBiteDown = false;
			wasBatDown = false;
			return;
		}

		KeyBinding bite = ClientAbilityKeys.primaryBinding();
		boolean biteDown = bite != null && ClientAbilityKeys.isDown(client, bite);
		if (biteDown && !wasBiteDown && ClientPlayNetworking.canSend(CovenantBitePayload.ID)) {
			ClientPlayNetworking.send(new CovenantBitePayload());
		}
		wasBiteDown = biteDown;

		KeyBinding bat = ClientAbilityKeys.secondaryBinding();
		boolean batDown = bat != null && ClientAbilityKeys.isDown(client, bat);
		if (isLocalDracula(client) && batDown && !wasBatDown && ClientPlayNetworking.canSend(CovenantBatPayload.ID)) {
			ClientPlayNetworking.send(new CovenantBatPayload());
		}
		wasBatDown = batDown;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.options.hudHidden) return;
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

		if (dracula) {
			int batY = y + height + 16;
			drawHorizontalBar(context, x - 46, batY, 54, 5, batTicks / (float) Math.max(1, maxBatTicks),
				batForm ? 0xFF6E1230 : 0xFF332134, 0xFF895CC7, 0xFFD6B8FF);
			String text = Math.max(0, (batTicks + 19) / 20) + "s";
			context.drawTextWithShadow(client.textRenderer, text, x - 22, batY + 8, 0xFFD6B8FF);
		}
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

	private static void drawHorizontalBar(DrawContext context, int x, int y, int width, int height,
			float progress, int frame, int fill, int shine) {
		float clamped = Math.max(0.0F, Math.min(1.0F, progress));
		context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF100306);
		context.fill(x, y, x + width, y + height, frame);
		int filled = Math.round((width - 2) * clamped);
		if (filled > 0) {
			context.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fill);
			context.fill(x + 1, y + 1, x + 1 + filled, y + 2, shine);
		}
	}

	private static boolean isLocalCovenant(MinecraftClient client) {
		Identifier id = localRoleId(client);
		return MapSelectRoles.DRACULA_ID.equals(id) || MapSelectRoles.VAMPIRE_ID.equals(id);
	}

	private static boolean isLocalDracula(MinecraftClient client) {
		return MapSelectRoles.DRACULA_ID.equals(localRoleId(client));
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
