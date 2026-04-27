package dev.mapselect.permissions;

import dev.mapselect.host.HostComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.UUID;

public final class GexpressPermissions {
	public static final String DEV_USERNAME = "greyifer";
	public static final UUID DEV_UUID = UUID.fromString("4eaa64ee-f4a5-4fb5-868e-2580327543fd");
	public static final int DEV_COLOR = 0xCBFF2E;

	private GexpressPermissions() {}

	public static boolean isDev(PlayerEntity player) {
		return player != null && (isDevUuid(player.getUuid()) || isDevName(player.getGameProfile().getName()));
	}

	public static boolean isDevName(String name) {
		return name != null && name.toLowerCase(Locale.ROOT).equals(DEV_USERNAME);
	}

	public static boolean isDevUuid(UUID uuid) {
		return DEV_UUID.equals(uuid);
	}

	public static boolean isDevUuidString(String uuid) {
		if (uuid == null || uuid.isBlank()) return false;
		try {
			return isDevUuid(UUID.fromString(uuid));
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	public static boolean isHostOrDev(PlayerEntity player) {
		return isDev(player) || HostComponent.isHost(player);
	}

	public static boolean canUseAdminCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isDev(source.getPlayer());
	}

	public static boolean canUseHostCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isHostOrDev(source.getPlayer());
	}

	public static boolean canEditGameOptions(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(2)) {
			return true;
		}
		return player != null && (player.hasPermissionLevel(2) || isHostOrDev(player));
	}

	public static boolean bypassesSupporterGates(PlayerEntity player) {
		return canEditGameOptions(player);
	}

	public static boolean isOperatorLike(MinecraftServer server, UUID uuid) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		if (player != null) {
			return canEditGameOptions(player);
		}
		var cache = server.getUserCache();
		var profile = cache == null ? null : cache.getByUuid(uuid).orElse(null);
		return profile != null
			&& (isDevName(profile.getName()) || server.getPlayerManager().isOperator(profile));
	}

	public static boolean hasBadge(PlayerEntity player) {
		return isDev(player) || HostComponent.isHost(player);
	}

	public static MutableText displayName(PlayerEntity player) {
		return Text.empty()
			.append(isDev(player) ? devBadge() : hostBadge())
			.append(Text.literal(" "))
			.append(Text.literal(player.getGameProfile().getName()));
	}

	public static Text hostBadge() {
		return Text.literal("Host").formatted(Formatting.BLUE);
	}

	public static Text devBadge() {
		return Text.literal("Dev").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(DEV_COLOR)));
	}
}
