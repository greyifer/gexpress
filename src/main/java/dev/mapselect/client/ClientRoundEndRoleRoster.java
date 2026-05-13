package dev.mapselect.client;

import dev.mapselect.network.RoundEndRoleRosterPayload;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientRoundEndRoleRoster {
	private static final Map<UUID, String> ROLE_IDS = new ConcurrentHashMap<>();

	private ClientRoundEndRoleRoster() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RoundEndRoleRosterPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				ROLE_IDS.clear();
				ROLE_IDS.putAll(payload.roleIds());
			}));
	}

	public static Text roleText(UUID playerId, Text fallback) {
		String raw = playerId == null ? null : ROLE_IDS.get(playerId);
		Identifier id = raw == null || raw.isBlank() ? null : Identifier.tryParse(raw);
		if (id == null) return fallback == null ? Text.empty() : fallback;
		Text translated = Text.translatable("announcement.role." + id.getNamespace() + "." + id.getPath());
		String key = "announcement.role." + id.getNamespace() + "." + id.getPath();
		return key.equals(translated.getString()) ? Text.literal(titleCase(id.getPath())) : translated;
	}

	public static int roleColor(UUID playerId, int fallback) {
		String raw = playerId == null ? null : ROLE_IDS.get(playerId);
		Identifier id = raw == null || raw.isBlank() ? null : Identifier.tryParse(raw);
		if (id == null) return fallback;
		for (Role role : WatheRoles.ROLES) {
			if (role != null && id.equals(role.identifier())) {
				return 0xFF000000 | role.color();
			}
		}
		return fallback;
	}

	private static String titleCase(String raw) {
		if (raw == null || raw.isBlank()) return "";
		String[] parts = raw.replace('-', '_').split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.toString();
	}
}
