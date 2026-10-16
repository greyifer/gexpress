package dev.mapselect.client.game;

import dev.mapselect.client.screen.DraftRoleSelectionScreen;
import dev.mapselect.network.game.DraftStatePayload;
import dev.mapselect.network.game.DraftWelcomePayload;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

public final class ClientDraftState {
	private static DraftStatePayload current = closed();

	private ClientDraftState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DraftStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(DraftWelcomePayload.ID, (payload, context) ->
			context.client().execute(() -> showWelcome(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> current = closed());
		ClientTickEvents.END_CLIENT_TICK.register(ClientDraftState::tick);
	}

	private static void showWelcome(DraftWelcomePayload payload) {
		if (payload == null || payload.roleId() == null) return;
		RoundTextRenderer.startWelcome(new RoleAnnouncementTexts.RoleAnnouncementText(
			announcementName(payload.roleId()), payload.color()), payload.killers(), payload.targets());
	}

	private static String announcementName(Identifier roleId) {
		if (roleId == null) return "civilian";
		if ("wathe".equalsIgnoreCase(roleId.getNamespace())) return roleId.getPath();
		String namespaced = roleId.getNamespace() + "." + roleId.getPath();
		Language language = Language.getInstance();
		if (!language.hasTranslation("announcement.role." + namespaced)
				&& language.hasTranslation("announcement.role." + roleId.getPath())) {
			return roleId.getPath();
		}
		return namespaced;
	}

	private static void apply(MinecraftClient client, DraftStatePayload payload) {
		current = payload == null ? closed() : payload;
		if (current.phase() == DraftStatePayload.Phase.CLOSED) {
			if (client.currentScreen instanceof DraftRoleSelectionScreen) client.setScreen(null);
			return;
		}
		if (client.currentScreen instanceof DraftRoleSelectionScreen screen) {
			screen.update(current);
		} else {
			client.setScreen(new DraftRoleSelectionScreen(current));
		}
	}

	private static void tick(MinecraftClient client) {
		if (current.phase() == DraftStatePayload.Phase.CLOSED || client.player == null || client.world == null) return;
		if (!(client.currentScreen instanceof DraftRoleSelectionScreen)) {
			client.setScreen(new DraftRoleSelectionScreen(current));
		}
	}

	private static DraftStatePayload closed() {
		return new DraftStatePayload(DraftStatePayload.Phase.CLOSED, 0, -1, -1, 0,
			java.util.List.of(), java.util.List.of());
	}
}
