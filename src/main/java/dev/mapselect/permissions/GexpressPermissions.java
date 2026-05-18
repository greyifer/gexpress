package dev.mapselect.permissions;

import dev.mapselect.host.HostComponent;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.level.LevelComponent;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GexpressPermissions {
	public static final String DEV_USERNAME = "greyifer";
	public static final UUID DEV_UUID = UUID.fromString("4eaa64ee-f4a5-4fb5-868e-2580327543fd");
	public static final int OWNER_COLOR = 0x196266;
	public static final int DEV_COLOR = 0xCBFF2E;
	public static final int TRUSTED_COLOR = 0xF2C94C;
	public static final int STAFF_COLOR = 0x79B9A9;
	public static final int CREATOR_COLOR = 0xD36BFF;
	public static final int PASSENGER_COLOR = 0x3C8AC9;
	public static final String PERMISSION_GAME_COMMANDS = "commands_game";
	public static final String PERMISSION_SETUP_COMMANDS = "commands_setup";
	public static final String PERMISSION_ROLE_COMMANDS = "commands_roles";
	public static final String PERMISSION_MODIFIER_COMMANDS = "commands_modifiers";
	public static final String PERMISSION_ADMIN_COMMANDS = "commands_admin";
	public static final String PERMISSION_TAGS_EDIT = "tags_edit";
	public static final String PERMISSION_TRUSTED = "trusted";
	public static final String PERMISSION_STAFF = "staff";
	public static final String PERMISSION_OWNER = "owner";
	private static final Map<String, String> PERMISSION_DESCRIPTIONS = permissionDescriptions();

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
		return isDev(player) || HostComponent.isHost(player)
			|| hasAnyTagPermission(player, PERMISSION_GAME_COMMANDS, "host");
	}

	public static boolean isTrusted(PlayerEntity player) {
		return TrustedComponent.isTrusted(player) || effectiveTags(player).contains(PlayerTag.TRUSTED)
			|| hasTagPermission(player, PERMISSION_TRUSTED);
	}

	public static boolean isBuilder(PlayerEntity player) {
		return hasAnyTagPermission(player, PERMISSION_SETUP_COMMANDS, "builder", "setup");
	}

	public static boolean isStaff(PlayerEntity player) {
		return hasTagPermission(player, PERMISSION_STAFF);
	}

	public static boolean isOwner(PlayerEntity player) {
		return hasTagPermission(player, PERMISSION_OWNER);
	}

	public static boolean canUseAdminCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || isDev(player)
			|| hasAnyTagPermission(player, PERMISSION_ADMIN_COMMANDS, "admin");
	}

	public static boolean canEditTags(ServerCommandSource source) {
		if (source.hasPermissionLevel(2)) return true;
		return source.getEntity() instanceof PlayerEntity player
			&& (isDev(player) || hasAnyTagPermission(player, PERMISSION_TAGS_EDIT, PERMISSION_ADMIN_COMMANDS, "admin"));
	}

	public static boolean canUseHostCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isHostOrDev(source.getPlayer());
	}

	public static boolean canUseSetupCommands(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || isBuilder(player);
	}

	public static boolean canUseRoleCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || canUseAdminCommands(source)
			|| hasTagPermission(player, PERMISSION_ROLE_COMMANDS);
	}

	public static boolean canUseModifierCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || canUseAdminCommands(source)
			|| hasTagPermission(player, PERMISSION_MODIFIER_COMMANDS);
	}

	public static boolean canEditGameOptions(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(2)) {
			return true;
		}
		return player != null && (player.hasPermissionLevel(2) || isOwner(player) || isHostOrDev(player));
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
		if (profile == null) return false;
		if (isDevName(profile.getName()) || server.getPlayerManager().isOperator(profile)) return true;
		World world = server.getWorld(World.OVERWORLD);
		PlayerTagComponent tags = world == null ? null : PlayerTagComponent.KEY.getNullable(world);
		return tags != null && (tags.getTag(uuid) == PlayerTag.OWNER
			|| tags.hasPermission(uuid, tags.getPlayerTags(uuid), PERMISSION_OWNER)
			|| tags.hasPermission(uuid, tags.getPlayerTags(uuid), PERMISSION_ADMIN_COMMANDS));
	}

	public static boolean hasBadge(PlayerEntity player) {
		return topDisplayTagInfos(player).stream().anyMatch(tag -> !PlayerTag.PASSENGER.id().equals(tag.id()));
	}

	public static MutableText displayName(PlayerEntity player) {
		MutableText out = Text.empty();
		for (TagInfo tag : topDisplayTagInfos(player)) {
			if (!out.getString().isEmpty()) out.append(Text.literal(" "));
			out.append(tagBadge(tag));
		}
		if (!out.getString().isEmpty()) out.append(Text.literal(" "));
		out.append(Text.literal(player.getGameProfile().getName()));
		out.append(Text.literal(" [Lv " + LevelComponent.level(player) + "]").formatted(Formatting.GRAY));
		return out;
	}

	private static Text badgeFor(PlayerEntity player) {
		return tagBadge(topDisplayTagInfos(player).getFirst());
	}

	public static List<PlayerTag> topDisplayTags(PlayerEntity player) {
		List<PlayerTag> tags = effectiveTags(player);
		if (tags.isEmpty()) return List.of(PlayerTag.PASSENGER);
		return tags.stream().limit(2).toList();
	}

	public static List<TagInfo> topDisplayTagInfos(PlayerEntity player) {
		List<TagInfo> tags = effectiveTagInfos(player);
		if (tags.isEmpty()) return List.of(TagInfo.from(PlayerTag.PASSENGER, player == null || player.getWorld() == null
			? null : PlayerTagComponent.KEY.getNullable(player.getWorld())));
		return tags.stream().limit(2).toList();
	}

	public static PlayerTag effectiveTag(PlayerEntity player) {
		if (player == null) return PlayerTag.PASSENGER;
		List<PlayerTag> tags = effectiveTags(player);
		return tags.isEmpty() ? PlayerTag.PASSENGER : tags.getFirst();
	}

	public static PlayerTag effectiveTag(World world, UUID uuid, String name) {
		List<PlayerTag> tags = effectiveTags(world, uuid, name);
		return tags.isEmpty() ? PlayerTag.PASSENGER : tags.getFirst();
	}

	public static List<PlayerTag> effectiveTags(PlayerEntity player) {
		if (player == null) return List.of(PlayerTag.PASSENGER);
		return effectiveTags(player.getWorld(), player.getUuid(), player.getGameProfile().getName());
	}

	public static List<TagInfo> effectiveTagInfos(PlayerEntity player) {
		if (player == null) return List.of(TagInfo.from(PlayerTag.PASSENGER));
		return effectiveTagInfos(player.getWorld(), player.getUuid(), player.getGameProfile().getName());
	}

	public static List<TagInfo> effectiveTagInfos(World world, UUID uuid, String name) {
		List<TagInfo> out = new ArrayList<>();
		PlayerTagComponent tagComponent = world == null ? null : PlayerTagComponent.KEY.getNullable(world);
		if (world != null) {
			if (tagComponent != null) {
				for (PlayerTag tag : tagComponent.getPlayerTags(uuid)) out.add(TagInfo.from(tag, tagComponent));
				for (String customId : tagComponent.getPlayerCustomTags(uuid)) {
					PlayerTagComponent.CustomTag customTag = tagComponent.getCustomTag(customId);
					if (customTag != null) out.add(TagInfo.from(customTag));
				}
			}
			HostComponent hosts = HostComponent.KEY.getNullable(world);
			if (hosts != null && hosts.isHost(uuid)) out.add(TagInfo.from(PlayerTag.HOST, tagComponent));
			TrustedComponent trusted = TrustedComponent.KEY.getNullable(world);
			if (trusted != null && trusted.isTrusted(uuid)) out.add(TagInfo.from(PlayerTag.TRUSTED, tagComponent));
		}
		if (isDevUuid(uuid) || isDevName(name)) out.add(TagInfo.from(PlayerTag.DEV, tagComponent));
		if (out.isEmpty()) out.add(TagInfo.from(PlayerTag.PASSENGER, tagComponent));
		return out.stream()
			.distinct()
			.sorted(Comparator.comparingInt(TagInfo::priority).reversed())
			.toList();
	}

	public static List<PlayerTag> effectiveTags(World world, UUID uuid, String name) {
		List<PlayerTag> out = new ArrayList<>();
		if (world != null) {
			PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(world);
			if (tags != null) out.addAll(tags.getPlayerTags(uuid));
			if (isDevUuid(uuid) || isDevName(name)) out.add(PlayerTag.DEV);
			HostComponent hosts = HostComponent.KEY.getNullable(world);
			if (hosts != null && hosts.isHost(uuid)) out.add(PlayerTag.HOST);
			TrustedComponent trusted = TrustedComponent.KEY.getNullable(world);
			if (trusted != null && trusted.isTrusted(uuid)) out.add(PlayerTag.TRUSTED);
		}
		if (isDevUuid(uuid) || isDevName(name)) out.add(PlayerTag.DEV);
		if (out.isEmpty()) out.add(PlayerTag.PASSENGER);
		return out.stream()
			.distinct()
			.sorted(Comparator.comparingInt(PlayerTag::priority).reversed())
			.toList();
	}

	private static boolean hasTagPermission(PlayerEntity player, String permission) {
		if (player == null || player.getWorld() == null) return false;
		PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(player.getWorld());
		return tags != null && tags.hasPermission(player.getUuid(), effectiveTags(player), permission);
	}

	private static boolean hasAnyTagPermission(PlayerEntity player, String... permissions) {
		if (permissions == null) return false;
		for (String permission : permissions) {
			if (hasTagPermission(player, permission)) return true;
		}
		return false;
	}

	public static String canonicalPermission(String raw) {
		String key = PlayerTagComponent.normalizeCustomId(raw);
		if (key == null) return null;
		return switch (key) {
			case "admin" -> PERMISSION_ADMIN_COMMANDS;
			case "host" -> PERMISSION_GAME_COMMANDS;
			case "setup", "builder" -> PERMISSION_SETUP_COMMANDS;
			default -> key;
		};
	}

	public static Map<String, String> permissionDescriptions() {
		LinkedHashMap<String, String> out = new LinkedHashMap<>();
		out.put(PERMISSION_GAME_COMMANDS, "Use /g game start/end commands.");
		out.put(PERMISSION_SETUP_COMMANDS, "Use /g setup map, train, and RTP editing commands.");
		out.put(PERMISSION_ROLE_COMMANDS, "Use /g roles tuning and role utility commands.");
		out.put(PERMISSION_MODIFIER_COMMANDS, "Use /g modifiers tuning commands.");
		out.put(PERMISSION_ADMIN_COMMANDS, "Use /g admin management, testing, and dev commands.");
		out.put(PERMISSION_TAGS_EDIT, "Create tags and edit tag assignments, colors, priorities, and permissions.");
		out.put(PERMISSION_TRUSTED, "Counts as Trusted for trusted-only bypasses and display.");
		out.put(PERMISSION_STAFF, "Counts as Staff for display and staff checks.");
		out.put(PERMISSION_OWNER, "Owner display tag; default Owner also receives every command permission.");
		return Collections.unmodifiableMap(out);
	}

	public static List<String> permissionKeys() {
		return List.copyOf(PERMISSION_DESCRIPTIONS.keySet());
	}

	public static String permissionDescription(String permission) {
		String key = canonicalPermission(permission);
		return key == null ? null : PERMISSION_DESCRIPTIONS.get(key);
	}

	public static Text tagBadge(PlayerTag tag) {
		if (tag == null) return passengerBadge();
		return tag.text();
	}

	public static Text tagBadge(TagInfo tag) {
		if (tag == null) return passengerBadge();
		return Text.literal(tag.displayName()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(tag.color())));
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

	public static Text staffBadge() {
		return PlayerTag.STAFF.text();
	}

	public static Text passengerBadge() {
		return PlayerTag.PASSENGER.text();
	}

	public static Text ownerBadge() {
		return PlayerTag.OWNER.text();
	}

	public record TagInfo(String id, String displayName, int color, int priority) {
		public static TagInfo from(PlayerTag tag) {
			return new TagInfo(tag.id(), tag.displayName(), tag.color(), tag.priority());
		}

		public static TagInfo from(PlayerTag tag, PlayerTagComponent component) {
			if (component == null) return from(tag);
			return new TagInfo(tag.id(), tag.displayName(), component.color(tag), component.priority(tag));
		}

		public static TagInfo from(PlayerTagComponent.CustomTag tag) {
			return new TagInfo(tag.id(), tag.displayName(), tag.color(), tag.priority());
		}
	}
}
