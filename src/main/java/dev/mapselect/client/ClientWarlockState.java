package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
import dev.mapselect.network.WarlockKillPayload;
import dev.mapselect.network.WarlockMarkPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.role.warlock.WarlockManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class ClientWarlockState {
	private static final Identifier ICON_IDLE = Identifier.of(MapSelect.MOD_ID, "hud/warlock_knife_idle");
	private static final Identifier ICON_READY = Identifier.of(MapSelect.MOD_ID, "hud/warlock_knife_ready");
	private static final double KILL_RANGE_SQUARED = WarlockManager.KILL_RANGE * WarlockManager.KILL_RANGE;

	private static boolean wasMarkDown;
	private static boolean wasKillDown;

	private ClientWarlockState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientWarlockState::tick);
		HudRenderCallback.EVENT.register(ClientWarlockState::renderHud);
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !shouldHandleWarlock(client)) {
			wasMarkDown = false;
			wasKillDown = false;
			return;
		}
		if (client.currentScreen != null) {
			wasMarkDown = false;
			wasKillDown = false;
			return;
		}

		KeyBinding markBinding = resolveAbilityBinding();
		boolean markDown = markBinding != null && ClientAbilityKeys.isDown(client, markBinding);
		if (isLocalWarlock(client) && markDown && !wasMarkDown && ClientPlayNetworking.canSend(WarlockMarkPayload.ID)) {
			ClientPlayNetworking.send(new WarlockMarkPayload());
		}
		wasMarkDown = markDown;

		KeyBinding killBinding = resolveSecondaryBinding();
		boolean killDown = killBinding != null && ClientAbilityKeys.isDown(client, killBinding);
		if (killDown && !wasKillDown && ClientPlayNetworking.canSend(WarlockKillPayload.ID)) {
			ClientPlayNetworking.send(new WarlockKillPayload());
		}
		wasKillDown = killDown;
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null
				|| ClientVultureState.isLocalStashed(client) || !ClientRoleRevealState.canUseRoleAbility(client)
				|| !shouldHandleWarlock(client)) return;
		if (!hasLocalMark(client)) return;

		boolean ready = isMarkedPlayerInKillRange(client);
		int x = context.getScaledWindowWidth() / 2 - 8;
		int y = context.getScaledWindowHeight() - 64;
		context.drawGuiTexture(ready ? ICON_READY : ICON_IDLE, x, y, 16, 16);
	}

	private static boolean isMarkedPlayerInKillRange(MinecraftClient client) {
		WarlockComponent comp = WarlockComponent.KEY.getNullable(client.world);
		if (comp == null || comp.killCooldownRemainingTicks(client.player.getUuid()) > 0L) return false;

		UUID markId = comp.getMarkedTarget(client.player.getUuid());
		if (markId == null) return false;

		AbstractClientPlayerEntity marked = findPlayer(client, markId);
		if (marked == null || !isVisibleCandidate(marked)) return false;

		for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
			if (candidate == client.player || candidate == marked) continue;
			if (!isVisibleCandidate(candidate)) continue;
			if (candidate.squaredDistanceTo(marked) <= KILL_RANGE_SQUARED) {
				return true;
			}
		}
		return false;
	}

	private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.getUuid().equals(uuid)) return player;
		}
		return null;
	}

	private static boolean isVisibleCandidate(AbstractClientPlayerEntity player) {
		return player != null && player.isAlive() && !player.isSpectator();
	}

	private static boolean isLocalWarlock(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			if (game == null) return false;
			Role role = game.getRole(client.player);
			return role != null && MapSelectRoles.WARLOCK_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean shouldHandleWarlock(MinecraftClient client) {
		return isLocalWarlock(client) || hasLocalMark(client);
	}

	private static boolean hasLocalMark(MinecraftClient client) {
		try {
			WarlockComponent comp = WarlockComponent.KEY.getNullable(client.world);
			return comp != null && comp.getMarkedTarget(client.player.getUuid()) != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static KeyBinding resolveAbilityBinding() {
		return ClientAbilityKeys.primaryBinding();
	}

	private static KeyBinding resolveSecondaryBinding() {
		return ClientAbilityKeys.secondaryBinding();
	}
}
