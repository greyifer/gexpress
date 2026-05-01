package dev.mapselect.client;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.NeutralWinPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.UUID;

public final class ClientNeutralWinState {
	private static UUID winnerId;
	private static String translationKey;
	private static int color;

	private ClientNeutralWinState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NeutralWinPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientNeutralWinState::tick);
	}

	public static Text endText(UUID requestedWinner) {
		if (winnerId == null || translationKey == null || requestedWinner == null
				|| !winnerId.equals(requestedWinner)) {
			return null;
		}
		return Text.translatable(translationKey).withColor(color);
	}

	private static void apply(NeutralWinPayload payload) {
		winnerId = payload.winnerId();
		translationKey = payload.translationKey();
		color = payload.color();
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (game != null && (game.getGameStatus() == GameWorldComponent.GameStatus.STARTING
				|| game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)) {
			reset();
		}
	}

	private static void reset() {
		winnerId = null;
		translationKey = null;
		color = 0xFFFFFF;
	}
}
