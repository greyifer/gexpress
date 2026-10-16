package dev.mapselect.client.game;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.network.game.NeutralWinPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.UUID;

public final class ClientNeutralWinState {
	private static UUID winnerId;
	private static String translationKey;
	private static int color;
	private static String winnerName = "";

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
		if ("announcement.win.gexpress.last_survivor".equals(translationKey)) {
			var client = MinecraftClient.getInstance();
			var entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(winnerId);
			String name = !winnerName.isBlank() ? winnerName : entry == null ? "Last survivor" : entry.getProfile().getName();
			return Text.translatable(translationKey, name).withColor(color);
		}
		return Text.translatable(translationKey).withColor(color);
	}

	private static void apply(NeutralWinPayload payload) {
		winnerId = payload.winnerId();
		translationKey = payload.translationKey();
		color = payload.color();
		winnerName = payload.winnerName();
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.STARTING) {
			reset();
		}
	}

	private static void reset() {
		winnerId = null;
		translationKey = null;
		color = 0xFFFFFF;
		winnerName = "";
	}
}
