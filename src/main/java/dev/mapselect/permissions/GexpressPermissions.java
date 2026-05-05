package dev.mapselect.permissions;

import dev.mapselect.host.HostComponent;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.host.TrustedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.UUID;

public final class GexpressPermissions {
	public static final String DEV_USERNAME = "greyifer";
	public static final UUID DEV_UUID = UUID.fromString("4eaa64ee-f4a5-4fb5-868e-2580327543fd");
	public static final int DEV_COLOR = 0xCBFF2E;
	public static final int TRUSTED_COLOR = 0xF2C94C;
	public static final int PASSENGER_COLOR = 0x3C8AC9;
	public static final int DESIGNER_COLOR = 0x781419;
	public static final int BUILDER_COLOR = 0xFC552B;

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

	public static boolean isTrusted(PlayerEntity player) {
		return TrustedComponent.isTrusted(player);
	}

	public static boolean isBuilder(PlayerEntity player) {
		return effectiveTag(player) == PlayerTag.BUILDER;
	}

	public static boolean canUseAdminCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isDev(source.getPlayer());
	}

	public static boolean canUseHostCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isHostOrDev(source.getPlayer());
	}

	public static boolean canUseSetupCommands(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || isBuilder(player);
	}

	public static boolean canEditGameOptions(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(2)) {
			return true;
		}
		return player != null && (player.hasPermissionLevel(2) || isHostOrDev(player));
	}

	public static boolean canEditSetupOptions(PlayerEntity player) {
		return canEditGameOptions(player) || isBuilder(player);
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
		PlayerTag tag = effectiveTag(player);
		return tag != null && tag != PlayerTag.PASSENGER;
	}

	public static MutableText displayName(PlayerEntity player) {
		return Text.empty()
			.append(badgeFor(player))
			.append(Text.literal(" "))
			.append(Text.literal(player.getGameProfile().getName()));
	}

	private static Text badgeFor(PlayerEntity player) {
		return tagBadge(effectiveTag(player));
	}

	public static PlayerTag effectiveTag(PlayerEntity player) {
		if (player == null) return PlayerTag.PASSENGER;
		return effectiveTag(player.getWorld(), player.getUuid(), player.getGameProfile().getName());
	}

	public static PlayerTag effectiveTag(World world, UUID uuid, String name) {
		if (isDevUuid(uuid) || isDevName(name)) return PlayerTag.DEV;
		if (world != null) {
			HostComponent hosts = HostComponent.KEY.getNullable(world);
			if (hosts != null && hosts.isHost(uuid)) return PlayerTag.HOST;
			TrustedComponent trusted = TrustedComponent.KEY.getNullable(world);
			if (trusted != null && trusted.isTrusted(uuid)) return PlayerTag.TRUSTED;
			PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(world);
			PlayerTag explicit = tags == null ? null : tags.getTag(uuid);
			if (explicit != null) return explicit;
		}
		return PlayerTag.PASSENGER;
	}

	public static Text tagBadge(PlayerTag tag) {
		if (tag == null) return passengerBadge();
		return tag.text();
	}

	public static Text hostBadge() {
		return PlayerTag.HOST.text();
	}

	public static Text devBadge() {
		return PlayerTag.DEV.text();
	}

	public static Text trustedBadge() {
		return PlayerTag.TRUSTED.text();
	}

	public static Text passengerBadge() {
		return PlayerTag.PASSENGER.text();
	}

	public static Text designerBadge() {
		return PlayerTag.DESIGNER.text();
	}

	public static Text builderBadge() {
		return PlayerTag.BUILDER.text();
	}
}
