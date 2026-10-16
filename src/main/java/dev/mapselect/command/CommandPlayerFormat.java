package dev.mapselect.command;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class CommandPlayerFormat {
	private CommandPlayerFormat() {}

	public static String names(Collection<? extends ServerPlayerEntity> players) {
		if (players == null || players.isEmpty()) return "(none)";
		List<String> names = new ArrayList<>();
		for (ServerPlayerEntity player : players) {
			if (player != null) names.add(player.getName().getString());
		}
		return names.isEmpty() ? "(none)" : String.join(", ", names);
	}

	public static String profiles(Collection<GameProfile> profiles) {
		if (profiles == null || profiles.isEmpty()) return "(none)";
		List<String> names = new ArrayList<>();
		for (GameProfile profile : profiles) {
			if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
				names.add(profile.getName());
			}
		}
		return names.isEmpty() ? "(none)" : String.join(", ", names);
	}

	public static String cachedName(ServerCommandSource source, UUID playerId) {
		if (playerId == null) return "(unknown)";
		ServerPlayerEntity online = online(source, playerId);
		if (online != null) return online.getGameProfile().getName();
		var cache = source.getServer().getUserCache();
		return cache == null ? playerId.toString()
			: cache.getByUuid(playerId).map(GameProfile::getName).orElse(playerId.toString());
	}

	public static String cachedName(ServerCommandSource source, GameProfile profile) {
		if (profile == null) return "(unknown)";
		if (profile.getId() == null) {
			String name = profile.getName();
			return name == null || name.isBlank() ? "(unknown)" : name;
		}
		ServerPlayerEntity online = online(source, profile);
		if (online != null) return online.getGameProfile().getName();
		String name = profile.getName();
		return name == null || name.isBlank() ? cachedName(source, profile.getId()) : name;
	}

	public static String cachedNames(ServerCommandSource source, Collection<UUID> playerIds) {
		if (playerIds == null || playerIds.isEmpty()) return "(none)";
		List<String> names = new ArrayList<>();
		for (UUID playerId : playerIds) names.add(cachedName(source, playerId));
		return names.isEmpty() ? "(none)" : String.join(", ", names);
	}

	public static ServerPlayerEntity online(ServerCommandSource source, GameProfile profile) {
		return profile == null ? null : online(source, profile.getId());
	}

	public static ServerPlayerEntity online(ServerCommandSource source, UUID playerId) {
		if (source == null || source.getServer() == null || playerId == null) return null;
		return source.getServer().getPlayerManager().getPlayer(playerId);
	}

	public static List<GameProfile> validProfiles(Collection<GameProfile> profiles) {
		List<GameProfile> out = new ArrayList<>();
		if (profiles == null) return out;
		for (GameProfile profile : profiles) {
			if (profile != null && profile.getId() != null) out.add(profile);
		}
		return out;
	}
}
