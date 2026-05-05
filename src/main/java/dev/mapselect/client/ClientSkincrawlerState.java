package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.SkincrawlerSkinPayload;
import dev.mapselect.network.SkincrawlerUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.SkinTextures;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientSkincrawlerState {
	private static final Map<UUID, UUID> swaps = new ConcurrentHashMap<>();
	private static boolean wasAbilityDown;

	private ClientSkincrawlerState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SkincrawlerSkinPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				swaps.clear();
				swaps.putAll(payload.swaps());
			}));
		ClientTickEvents.END_CLIENT_TICK.register(ClientSkincrawlerState::tick);
	}

	public static UUID replacementFor(UUID playerId) {
		return playerId == null ? null : swaps.get(playerId);
	}

	public static SkinTextures replacementTextures(UUID replacementId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getNetworkHandler() == null || replacementId == null) return null;
		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(replacementId);
		return entry == null ? null : entry.getSkinTextures();
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalSkincrawler(client)) {
			wasAbilityDown = false;
			return;
		}
		KeyBinding ability = ClientAbilityKeys.primaryBinding();
		boolean down = ability != null && ClientAbilityKeys.isDown(client, ability);
		if (down && !wasAbilityDown && ClientPlayNetworking.canSend(SkincrawlerUsePayload.ID)) {
			ClientPlayNetworking.send(new SkincrawlerUsePayload());
		}
		wasAbilityDown = down;
	}

	private static boolean isLocalSkincrawler(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role != null && MapSelectRoles.SKINCRAWLER_ID.equals(role.identifier());
		} catch (Throwable ignored) {
			return false;
		}
	}
}
