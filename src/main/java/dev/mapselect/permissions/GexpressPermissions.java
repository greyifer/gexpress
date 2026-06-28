package dev.mapselect.permissions;

import dev.mapselect.host.HostComponent;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.config.GexpressConfig;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

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
	public static final String PERMISSION_GAME_START = "commands_game_start";
	public static final String PERMISSION_SETUP_COMMANDS = "commands_setup";
	public static final String PERMISSION_MAP_COMMANDS = "commands_map";
	public static final String PERMISSION_TRAIN_COMMANDS = "commands_train";
	public static final String PERMISSION_RTP_COMMANDS = "commands_rtp";
	public static final String PERMISSION_ROLE_COMMANDS = "commands_roles";
	public static final String PERMISSION_MODIFIER_COMMANDS = "commands_modifiers";
	public static final String PERMISSION_ADMIN_COMMANDS = "commands_admin";
	public static final String PERMISSION_SERVER_ACCESS_COMMANDS = "commands_server_access";
	public static final String PERMISSION_VOICE_COMMANDS = "commands_voice";
	public static final String PERMISSION_TAGS_EDIT = "tags_edit";
	public static final String PERMISSION_BUG_REPORTS_MODERATE = "bug_reports_moderate";
	public static final String PERMISSION_CONFIG_EDIT = "config_edit";
	public static final String PERMISSION_DEV_MENUS = "dev_menus";
	public static final String PERMISSION_MAP_TOOLS = "map_tools";
	public static final String PERMISSION_SKINS_MANAGE = "skins_manage";
	public static final String PERMISSION_ECONOMY_MANAGE = "economy_manage";
	public static final String PERMISSION_PROGRESSION_MANAGE = "progression_manage";
	public static final String PERMISSION_DIAGNOSTICS = "diagnostics";
	public static final String PERMISSION_PLAYERS_TAB = "tab_players";
	public static final String PERMISSION_GAME_TAB = "tab_game";
	public static final String PERMISSION_GAME_TAB_EDIT = "tab_game_edit";
	public static final String PERMISSION_MAPS_TAB = "tab_maps";
	public static final String PERMISSION_MAPS_TAB_EDIT = "tab_maps_edit";
	public static final String PERMISSION_TRAIN_CARTS_TAB = "tab_train_carts";
	public static final String PERMISSION_TRAIN_CARTS_TAB_EDIT = "tab_train_carts_edit";
	public static final String PERMISSION_DEV_TAB = "tab_dev";
	public static final String PERMISSION_DEV_TAB_EDIT = "tab_dev_edit";
	public static final String PERMISSION_TRUSTED = "trusted";
	public static final String PERMISSION_STAFF = "staff";
	public static final String PERMISSION_OWNER = "owner";
	private static final String COMMAND_PERMISSION_PREFIX = "cmd_";
	private static final List<PermissionEntry> PERMISSION_ENTRIES = buildPermissionEntries();
	private static final Map<String, String> PERMISSION_DESCRIPTIONS = buildPermissionDescriptions();

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
			|| hasAnyTagPermission(player, PERMISSION_ADMIN_COMMANDS, "admin")
			|| hasCommandPathPermission(player, "admin");
	}

	private static boolean hasBroadAdmin(PlayerEntity player) {
		return player != null && (player.hasPermissionLevel(2) || isDev(player) || isOwner(player)
			|| hasAnyTagPermission(player, PERMISSION_ADMIN_COMMANDS, "admin"));
	}

	public static boolean canEditTags(ServerCommandSource source) {
		if (source.hasPermissionLevel(2)) return true;
		return source.getEntity() instanceof PlayerEntity player
			&& (isDev(player) || hasAnyTagPermission(player, PERMISSION_TAGS_EDIT, PERMISSION_ADMIN_COMMANDS, "admin")
				|| hasCommandPathPermission(player, "admin", "tag"));
	}

	public static boolean canEditTags(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player) || hasAnyTagPermission(player, PERMISSION_TAGS_EDIT)
			|| hasCommandPathPermission(player, "admin", "tag"));
	}

	public static boolean canUseHostCommands(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || isHostOrDev(source.getPlayer());
	}

	public static boolean canManageServerAccess(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canUseHostCommands(source)
			|| hasAnyTagPermission(player, PERMISSION_SERVER_ACCESS_COMMANDS)
			|| hasCommandPathPermission(player, "open") || hasCommandPathPermission(player, "close");
	}

	public static boolean canStartGames(PlayerEntity player) {
		return player != null && (player.hasPermissionLevel(2) || isHostOrDev(player)
			|| hasAnyTagPermission(player, PERMISSION_GAME_START, "game_start", "start_game")
			|| hasCommandPathPermission(player, "game", "start"));
	}

	public static boolean canStartGames(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || canStartGames(source.getPlayer());
	}

	public static boolean canUseSetupCommands(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || canUseMapTools(player)
			|| hasCommandPathPermission(player, "setup");
	}

	public static boolean canUseMapCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canUseSetupCommands(source)
			|| hasAnyTagPermission(player, PERMISSION_MAP_COMMANDS)
			|| hasCommandPathPermission(player, "setup", "map");
	}

	public static boolean canUseTrainCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canUseSetupCommands(source)
			|| hasAnyTagPermission(player, PERMISSION_TRAIN_COMMANDS)
			|| hasCommandPathPermission(player, "setup", "train");
	}

	public static boolean canUseRtpCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canUseSetupCommands(source)
			|| hasAnyTagPermission(player, PERMISSION_RTP_COMMANDS)
			|| hasCommandPathPermission(player, "setup", "rtp");
	}

	public static boolean canUseRoleCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || canUseAdminCommands(source)
			|| hasTagPermission(player, PERMISSION_ROLE_COMMANDS)
			|| hasCommandPathPermission(player, "roles");
	}

	public static boolean canUseModifierCommands(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return canUseHostCommands(source) || canUseAdminCommands(source)
			|| hasTagPermission(player, PERMISSION_MODIFIER_COMMANDS)
			|| hasCommandPathPermission(player, "modifiers");
	}

	public static boolean canEditGameOptions(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(2)) {
			return true;
		}
		return player != null && (player.hasPermissionLevel(2) || isOwner(player) || isHostOrDev(player));
	}

	public static boolean canEditConfigOptions(PlayerEntity player) {
		return canEditGameTab(player);
	}

	public static boolean canEditSetupOptions(PlayerEntity player) {
		return canEditMapsTab(player) || canEditTrainCartsTab(player);
	}

	public static boolean canUseMapTools(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player) || isHostOrDev(player) || isBuilder(player)
			|| hasAnyTagPermission(player, PERMISSION_MAP_TOOLS, PERMISSION_SETUP_COMMANDS, "builder", "setup"));
	}

	public static boolean canUseDevMenus(PlayerEntity player) {
		return canViewDevTab(player);
	}

	public static boolean canViewPlayersTab(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player) || isHostOrDev(player)
			|| hasAnyTagPermission(player, PERMISSION_PLAYERS_TAB, PERMISSION_TAGS_EDIT,
				PERMISSION_PROGRESSION_MANAGE, PERMISSION_ECONOMY_MANAGE));
	}

	public static boolean canViewGameTab(PlayerEntity player) {
		return canEditGameTab(player) || hasAnyTagPermission(player, PERMISSION_GAME_TAB);
	}

	public static boolean canEditGameTab(PlayerEntity player) {
		return player != null && (canEditGameOptions(player)
			|| hasAnyTagPermission(player, PERMISSION_GAME_TAB_EDIT, PERMISSION_CONFIG_EDIT));
	}

	public static boolean canViewMapsTab(PlayerEntity player) {
		return canEditMapsTab(player) || hasAnyTagPermission(player, PERMISSION_MAPS_TAB);
	}

	public static boolean canEditMapsTab(PlayerEntity player) {
		return player != null && (canEditGameOptions(player) || canUseMapTools(player)
			|| hasAnyTagPermission(player, PERMISSION_MAPS_TAB_EDIT, PERMISSION_MAP_COMMANDS));
	}

	public static boolean canViewTrainCartsTab(PlayerEntity player) {
		return canEditTrainCartsTab(player) || hasAnyTagPermission(player, PERMISSION_TRAIN_CARTS_TAB);
	}

	public static boolean canEditTrainCartsTab(PlayerEntity player) {
		return player != null && (canEditGameOptions(player) || canUseMapTools(player)
			|| hasAnyTagPermission(player, PERMISSION_TRAIN_CARTS_TAB_EDIT, PERMISSION_TRAIN_COMMANDS));
	}

	public static boolean canViewDevTab(PlayerEntity player) {
		return player != null && (canEditDevTab(player)
			|| hasAnyTagPermission(player, PERMISSION_DEV_TAB, PERMISSION_TAGS_EDIT,
				PERMISSION_SKINS_MANAGE, PERMISSION_PROGRESSION_MANAGE, PERMISSION_ECONOMY_MANAGE));
	}

	public static boolean canEditDevTab(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player)
			|| hasAnyTagPermission(player, PERMISSION_DEV_TAB_EDIT, PERMISSION_DEV_MENUS));
	}

	public static boolean canManageSkins(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player) || hasAnyTagPermission(player, PERMISSION_SKINS_MANAGE)
			|| hasCommandPathPermission(player, "skins") || hasCommandPathPermission(player, "admin", "skins"));
	}

	public static boolean canManageSkins(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || canManageSkins(source.getPlayer());
	}

	public static boolean canManageEconomy(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canManageEconomy(player);
	}

	public static boolean canManageEconomy(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player) || hasAnyTagPermission(player, PERMISSION_ECONOMY_MANAGE)
			|| hasCommandPathPermission(player, "admin", "gcoin"));
	}

	public static boolean canManageProgression(ServerCommandSource source) {
		return source.hasPermissionLevel(2) || canManageProgression(source.getPlayer());
	}

	public static boolean canManageProgression(PlayerEntity player) {
		return player != null && (hasBroadAdmin(player)
			|| hasAnyTagPermission(player, PERMISSION_PROGRESSION_MANAGE)
			|| hasCommandPathPermission(player, "admin", "level"));
	}

	public static boolean canManageVoice(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || canUseHostCommands(source)
			|| hasAnyTagPermission(player, PERMISSION_VOICE_COMMANDS)
			|| hasCommandPathPermission(player, "admin", "voice");
	}

	public static boolean canUseDiagnostics(ServerCommandSource source) {
		PlayerEntity player = source.getPlayer();
		return source.hasPermissionLevel(2) || hasBroadAdmin(player)
			|| hasAnyTagPermission(player, PERMISSION_DIAGNOSTICS)
			|| hasCommandPathPermission(player, "admin", "diagnostics")
			|| hasCommandPathPermission(player, "admin", "debug");
	}

	public static boolean canModerateBugReports(PlayerEntity player) {
		return player != null && (player.hasPermissionLevel(2) || isDev(player) || isStaff(player)
			|| hasAnyTagPermission(player, PERMISSION_BUG_REPORTS_MODERATE, PERMISSION_ADMIN_COMMANDS));
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
		GexpressConfig.LevelTagEntry levelTag = GexpressConfig.getLevelTagForLevel(LevelComponent.level(player));
		if (levelTag != null) {
			if (!out.getString().isEmpty()) out.append(Text.literal(" "));
			out.append(Text.literal(levelTag.displayName())
				.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(levelTag.color()))));
		}
		if (!out.getString().isEmpty()) out.append(Text.literal(" "));
		out.append(Text.literal(player.getGameProfile().getName()));
		out.append(Text.literal(" [LvL " + LevelComponent.level(player) + "]").formatted(Formatting.GRAY));
		return out;
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

	public static boolean canUseCommandPath(ServerCommandSource source, String... path) {
		if (source.hasPermissionLevel(2)) return true;
		PlayerEntity player = source.getPlayer();
		return player != null && (hasBroadAdmin(player) || hasCommandPathPermission(player, path));
	}

	public static boolean canUseCommandBranch(ServerCommandSource source, String... path) {
		if (source.hasPermissionLevel(2)) return true;
		PlayerEntity player = source.getPlayer();
		return player != null && (hasBroadAdmin(player) || hasCommandPathPermission(player, path)
			|| hasCommandDescendantPermission(player, path));
	}

	private static boolean hasCommandPathPermission(PlayerEntity player, String... path) {
		List<String> parts = normalizedCommandPath(path);
		if (parts.isEmpty()) return false;
		for (int length = parts.size(); length >= 1; length--) {
			if (hasTagPermission(player, commandPermission(parts, length))) return true;
		}
		return false;
	}

	private static boolean hasCommandDescendantPermission(PlayerEntity player, String... path) {
		List<String> parts = normalizedCommandPath(path);
		if (parts.isEmpty()) return false;
		String exact = commandPermission(parts, parts.size());
		String prefix = exact + "_";
		return hasAnyTagPermissionMatching(player, permission ->
			permission != null && (permission.equals(exact) || permission.startsWith(prefix)));
	}

	private static boolean hasAnyTagPermissionMatching(PlayerEntity player, Predicate<String> predicate) {
		if (player == null || player.getWorld() == null) return false;
		PlayerTagComponent tags = PlayerTagComponent.KEY.getNullable(player.getWorld());
		if (tags == null) return false;
		for (PlayerTag tag : effectiveTags(player)) {
			if (containsMatchingPermission(tags.permissions(tag), predicate)) return true;
		}
		for (String tagId : tags.getPlayerCustomTags(player.getUuid())) {
			PlayerTagComponent.CustomTag tag = tags.getCustomTag(tagId);
			if (tag != null && containsMatchingPermission(tag.permissions(), predicate)) return true;
		}
		return false;
	}

	private static boolean containsMatchingPermission(Set<String> permissions, Predicate<String> predicate) {
		if (permissions == null || predicate == null) return false;
		for (String permission : permissions) {
			String key = canonicalPermission(permission);
			if (predicate.test(key)) return true;
		}
		return false;
	}

	public static String commandPermission(String... path) {
		List<String> parts = normalizedCommandPath(path);
		return parts.isEmpty() ? null : commandPermission(parts, parts.size());
	}

	private static String commandPermission(List<String> parts, int length) {
		if (parts == null || parts.isEmpty() || length <= 0) return null;
		StringBuilder out = new StringBuilder(COMMAND_PERMISSION_PREFIX);
		for (int i = 0; i < Math.min(length, parts.size()); i++) {
			if (i > 0) out.append('_');
			out.append(parts.get(i));
		}
		return out.toString();
	}

	private static List<String> normalizedCommandPath(String... path) {
		if (path == null) return List.of();
		List<String> parts = new ArrayList<>();
		for (String raw : path) {
			if (raw == null) continue;
			for (String part : raw.split("[/\\s]+")) {
				String cleaned = normalizePermissionToken(part);
				if (cleaned == null || cleaned.isBlank() || cleaned.equals("g") || cleaned.equals("gexpress")) {
					continue;
				}
				parts.add(cleaned);
			}
		}
		return parts;
	}

	private static String normalizePermissionKey(String raw) {
		String key = normalizePermissionToken(raw);
		if (key == null) return null;
		if (key.startsWith("command_")) return COMMAND_PERMISSION_PREFIX + key.substring("command_".length());
		if (key.startsWith("gexpress_")) return COMMAND_PERMISSION_PREFIX + key.substring("gexpress_".length());
		if (key.startsWith("g_")) return COMMAND_PERMISSION_PREFIX + key.substring("g_".length());
		return key;
	}

	private static String normalizePermissionToken(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT)
			.replaceAll("^/+", "")
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");
		if (cleaned.isBlank() || cleaned.length() > 96) return null;
		return cleaned;
	}

	public static String canonicalPermission(String raw) {
		String key = normalizePermissionKey(raw);
		if (key == null) return null;
		if (key.startsWith(COMMAND_PERMISSION_PREFIX)) return key;
		return switch (key) {
			case "admin" -> PERMISSION_ADMIN_COMMANDS;
			case "host" -> PERMISSION_GAME_COMMANDS;
			case "game_start", "start_game" -> PERMISSION_GAME_START;
			case "setup", "builder" -> PERMISSION_SETUP_COMMANDS;
			case "map_command", "map_commands" -> PERMISSION_MAP_COMMANDS;
			case "train", "train_command", "train_commands" -> PERMISSION_TRAIN_COMMANDS;
			case "rtp", "randomtp" -> PERMISSION_RTP_COMMANDS;
			case "server", "server_access", "join_access" -> PERMISSION_SERVER_ACCESS_COMMANDS;
			case "voice", "voicechat", "voice_chat" -> PERMISSION_VOICE_COMMANDS;
			case "config", "options", "game_config", PERMISSION_CONFIG_EDIT -> PERMISSION_GAME_TAB_EDIT;
			case "dev", "devtab", "dev_tab", PERMISSION_DEV_MENUS -> PERMISSION_DEV_TAB;
			case "map", "maps", "maptools" -> PERMISSION_MAP_TOOLS;
			case "skin", "skins" -> PERMISSION_SKINS_MANAGE;
			case "economy", "gcoin", "coins" -> PERMISSION_ECONOMY_MANAGE;
			case "progression", "level", "levels", "xp" -> PERMISSION_PROGRESSION_MANAGE;
			case "debug", "diag" -> PERMISSION_DIAGNOSTICS;
			case "players", "players_tab" -> PERMISSION_PLAYERS_TAB;
			case "game_tab" -> PERMISSION_GAME_TAB;
			case "game_edit", "game_tab_edit" -> PERMISSION_GAME_TAB_EDIT;
			case "maps_tab" -> PERMISSION_MAPS_TAB;
			case "maps_edit", "maps_tab_edit" -> PERMISSION_MAPS_TAB_EDIT;
			case "train_carts", "train_carts_tab" -> PERMISSION_TRAIN_CARTS_TAB;
			case "train_carts_edit", "train_carts_tab_edit" -> PERMISSION_TRAIN_CARTS_TAB_EDIT;
			case "dev_tools", "dev_tools_tab" -> PERMISSION_DEV_TAB;
			case "dev_edit", "dev_tab_edit" -> PERMISSION_DEV_TAB_EDIT;
			default -> key;
		};
	}

	public static Map<String, String> permissionDescriptions() {
		return PERMISSION_DESCRIPTIONS;
	}

	private static List<PermissionEntry> buildPermissionEntries() {
		List<PermissionEntry> out = new ArrayList<>();
		add(out, "Command Groups", PERMISSION_GAME_COMMANDS, "Game commands", "Use /g game start/end commands.");
		add(out, "Command Groups", PERMISSION_GAME_START, "Start game only", "Start games with /g game start without granting end-game controls.");
		add(out, "Command Groups", PERMISSION_SETUP_COMMANDS, "Setup bundle", "Use the full /g setup command group.");
		add(out, "Command Groups", PERMISSION_MAP_COMMANDS, "Map commands", "Use /g setup map commands.");
		add(out, "Command Groups", PERMISSION_TRAIN_COMMANDS, "Train commands", "Use /g setup train commands.");
		add(out, "Command Groups", PERMISSION_RTP_COMMANDS, "RTP commands", "Use /g setup rtp commands.");
		add(out, "Command Groups", PERMISSION_ROLE_COMMANDS, "Role commands", "Use /g roles tuning and role utility commands.");
		add(out, "Command Groups", PERMISSION_MODIFIER_COMMANDS, "Modifier commands", "Use /g modifiers tuning commands.");
		add(out, "Command Groups", PERMISSION_ADMIN_COMMANDS, "Admin bundle", "Use broad /g admin management, testing, and dev commands.");
		add(out, "Command Groups", PERMISSION_SERVER_ACCESS_COMMANDS, "Server access", "Use /g open and /g close.");
		add(out, "Command Groups", PERMISSION_VOICE_COMMANDS, "Voice controls", "Use /g admin voice mute, dead groups, and voice admin tools.");
		add(out, "Command Groups", PERMISSION_DIAGNOSTICS, "Diagnostics", "Use diagnostics and player-state debug commands.");
		addCommand(out, "Command Paths", "/g game", "Use the whole /g game command group.", "game");
		addCommand(out, "Command Paths", "/g setup", "Use the whole /g setup command group.", "setup");
		addCommand(out, "Command Paths", "/g setup map", "Use every /g setup map command.", "setup", "map");
		addCommand(out, "Command Paths", "/g setup train", "Use every /g setup train command.", "setup", "train");
		addCommand(out, "Command Paths", "/g setup rtp", "Use every /g setup rtp command.", "setup", "rtp");
		addCommand(out, "Command Paths", "/g roles", "Use every /g roles command.", "roles");
		addCommand(out, "Command Paths", "/g modifiers", "Use every /g modifiers command.", "modifiers");
		addCommand(out, "Command Paths", "/g skins", "Use the whole /g skins command group.", "skins");
		addCommand(out, "Command Paths", "/g admin", "Use the whole /g admin command group.", "admin");
		addCommand(out, "Command Paths", "/g admin tag", "Use every /g admin tag command.", "admin", "tag");
		addCommand(out, "Command Paths", "/g admin gcoin", "Use every /g admin gcoin command.", "admin", "gcoin");
		addCommand(out, "Command Paths", "/g admin voice", "Use every /g admin voice command.", "admin", "voice");
		addCommand(out, "Command Paths", "/g admin level", "Use every /g admin level command.", "admin", "level");
		addCommand(out, "Command Paths", "/g admin skins", "Use every /g admin skins command.", "admin", "skins");
		addCommand(out, "Command Paths", "/g admin host", "Use every /g admin host command.", "admin", "host");
		addCommand(out, "Command Paths", "/g admin trusted", "Use every /g admin trusted command.", "admin", "trusted");
		addCommand(out, "Command Paths", "/g admin diagnostics", "Use /g admin diagnostics commands.", "admin", "diagnostics");
		addCommand(out, "Command Paths", "/g admin painterdoor", "Use Painter doorway dev controls.", "admin", "painterdoor");
		addCommand(out, "Command Paths", "/g open", "Open the server.", "open");
		addCommand(out, "Command Paths", "/g close", "Close the server and kick lower-access players.", "close");
		addCommand(out, "Individual Commands", "/g game start", "Start games.", "game", "start");
		addCommand(out, "Individual Commands", "/g game end", "End games.", "game", "end");
		addCommand(out, "Individual Commands", "/g setup map list", "List map presets.", "setup", "map", "list");
		addCommand(out, "Individual Commands", "/g setup map show", "Show map preset information.", "setup", "map", "show");
		addCommand(out, "Individual Commands", "/g setup map set", "Set the active map preset.", "setup", "map", "set");
		addCommand(out, "Individual Commands", "/g setup map validate", "Validate map presets.", "setup", "map", "validate");
		addCommand(out, "Individual Commands", "/g setup map create", "Create map presets.", "setup", "map", "create");
		addCommand(out, "Individual Commands", "/g setup map edit", "Edit map presets.", "setup", "map", "edit");
		addCommand(out, "Individual Commands", "/g setup map delete", "Delete map presets.", "setup", "map", "delete");
		addCommand(out, "Individual Commands", "/g setup train preset list", "List train presets.", "setup", "train", "preset", "list");
		addCommand(out, "Individual Commands", "/g setup train preset show", "Show train presets.", "setup", "train", "preset", "show");
		addCommand(out, "Individual Commands", "/g setup train preset save", "Save train presets.", "setup", "train", "preset", "save");
		addCommand(out, "Individual Commands", "/g setup train preset delete", "Delete train presets.", "setup", "train", "preset", "delete");
		addCommand(out, "Individual Commands", "/g setup rtp list", "List RTP slots.", "setup", "rtp", "list");
		addCommand(out, "Individual Commands", "/g setup rtp add", "Add RTP slots.", "setup", "rtp", "add");
		addCommand(out, "Individual Commands", "/g setup rtp remove", "Remove RTP slots.", "setup", "rtp", "remove");
		addCommand(out, "Individual Commands", "/g setup rtp clear", "Clear RTP slots.", "setup", "rtp", "clear");
		addCommand(out, "Individual Commands", "/g roles tuning", "Edit role tuning.", "roles", "tuning");
		addCommand(out, "Individual Commands", "/g modifiers tuning", "Edit modifier tuning.", "modifiers", "tuning");
		addCommand(out, "Individual Commands", "/g admin tag add", "Add player tags.", "admin", "tag", "add");
		addCommand(out, "Individual Commands", "/g admin tag set", "Set player tags.", "admin", "tag", "set");
		addCommand(out, "Individual Commands", "/g admin tag remove", "Remove player tags.", "admin", "tag", "remove");
		addCommand(out, "Individual Commands", "/g admin tag settings", "Edit built-in tag settings.", "admin", "tag", "settings");
		addCommand(out, "Individual Commands", "/g admin tag custom", "Create or edit custom tags.", "admin", "tag", "custom");
		addCommand(out, "Individual Commands", "/g admin tag list", "List player tags.", "admin", "tag", "list");
		addCommand(out, "Individual Commands", "/g admin tag permissions", "List available tag permissions.", "admin", "tag", "permissions");
		addCommand(out, "Individual Commands", "/g admin gcoin set", "Set G'Coin balances.", "admin", "gcoin", "set");
		addCommand(out, "Individual Commands", "/g admin gcoin give", "Give G'Coin.", "admin", "gcoin", "give");
		addCommand(out, "Individual Commands", "/g admin gcoin remove", "Remove G'Coin.", "admin", "gcoin", "remove");
		addCommand(out, "Individual Commands", "/g admin level level", "Set player levels.", "admin", "level", "level");
		addCommand(out, "Individual Commands", "/g admin level xp", "Set in-level XP progress.", "admin", "level", "xp");
		addCommand(out, "Individual Commands", "/g admin level reset", "Reset player level and XP progress.", "admin", "level", "reset");
		addCommand(out, "Individual Commands", "/g admin level rewards", "Reset XP Roadmap reward claims.", "admin", "level", "rewards");
		addCommand(out, "Individual Commands", "/g admin voice mute", "Voice mute players.", "admin", "voice", "mute");
		addCommand(out, "Individual Commands", "/g admin voice unmute", "Voice unmute players.", "admin", "voice", "unmute");
		addCommand(out, "Individual Commands", "/g admin voice dead", "Manage dead voice groups.", "admin", "voice", "dead");
		addCommand(out, "Individual Commands", "/g admin voice list", "List voice mutes.", "admin", "voice", "list");
		addCommand(out, "Individual Commands", "/g admin skins give", "Give weapon skins.", "admin", "skins", "give");
		addCommand(out, "Individual Commands", "/g admin skins remove", "Remove weapon skins.", "admin", "skins", "remove");
		addCommand(out, "Individual Commands", "/g admin skins case give", "Give skin cases.", "admin", "skins", "case", "give");
		addCommand(out, "Individual Commands", "/g admin skins case remove", "Remove skin cases.", "admin", "skins", "case", "remove");
		addCommand(out, "Individual Commands", "/g admin host add", "Add hosts.", "admin", "host", "add");
		addCommand(out, "Individual Commands", "/g admin host remove", "Remove hosts.", "admin", "host", "remove");
		addCommand(out, "Individual Commands", "/g admin host list", "List hosts.", "admin", "host", "list");
		addCommand(out, "Individual Commands", "/g admin trusted add", "Add trusted players.", "admin", "trusted", "add");
		addCommand(out, "Individual Commands", "/g admin trusted remove", "Remove trusted players.", "admin", "trusted", "remove");
		addCommand(out, "Individual Commands", "/g admin trusted list", "List trusted players.", "admin", "trusted", "list");
		addCommand(out, "Individual Commands", "/g admin painterdoor toggle", "Toggle Painter doorway use for the looked-at door.", "admin", "painterdoor", "toggle");
		addCommand(out, "Individual Commands", "/g admin painterdoor disable", "Disable Painter doorway use for the looked-at door.", "admin", "painterdoor", "disable");
		addCommand(out, "Individual Commands", "/g admin painterdoor enable", "Enable Painter doorway use for the looked-at door.", "admin", "painterdoor", "enable");
		addCommand(out, "Individual Commands", "/g admin painterdoor clear", "Clear disabled Painter doorway doors.", "admin", "painterdoor", "clear");
		add(out, "Tabs", PERMISSION_PLAYERS_TAB, "Players tab", "Open the Players tab; editing still needs tag, progression, or economy permissions.");
		add(out, "Tabs", PERMISSION_GAME_TAB, "Game tab", "Open the gameplay tuning tab.");
		add(out, "Tabs", PERMISSION_MAPS_TAB, "Maps tab", "Open the map preset tab.");
		add(out, "Tabs", PERMISSION_TRAIN_CARTS_TAB, "Train Carts tab", "Open the train cart preset tab.");
		add(out, "Tabs", PERMISSION_DEV_TAB, "Dev tools tab", "Open the dev tools tab.");
		add(out, "Tab Editing", PERMISSION_GAME_TAB_EDIT, "Edit Game tab", "Save gameplay tuning and role/task config changes.");
		add(out, "Tab Editing", PERMISSION_MAPS_TAB_EDIT, "Edit Maps tab", "Create, edit, delete, and save map presets.");
		add(out, "Tab Editing", PERMISSION_TRAIN_CARTS_TAB_EDIT, "Edit Train Carts tab", "Create, edit, delete, and save train cart presets.");
		add(out, "Tab Editing", PERMISSION_DEV_TAB_EDIT, "Edit Dev tab", "Save dev tuning, level roadmap, model, case, and visual config changes.");
		add(out, "Management", PERMISSION_TAGS_EDIT, "Tags", "Create tags and edit tag assignments, colors, priorities, and permissions.");
		add(out, "Management", PERMISSION_PROGRESSION_MANAGE, "Progression", "Set player levels, XP, and XP Roadmap reward state.");
		add(out, "Management", PERMISSION_SKINS_MANAGE, "Skins", "Manage weapon skins, skin cases, and server skin imports.");
		add(out, "Management", PERMISSION_ECONOMY_MANAGE, "Economy", "Grant, remove, and set G'Coin balances.");
		add(out, "Management", PERMISSION_BUG_REPORTS_MODERATE, "Bug reports", "Mark bug reports open, fixed, or duplicate, and delete reports.");
		add(out, "Management", PERMISSION_MAP_TOOLS, "Setup tools bundle", "Create, edit, validate, and apply G'Express map/train setup presets.");
		add(out, "Status Flags", PERMISSION_TRUSTED, "Trusted", "Counts as Trusted for trusted-only bypasses and display.");
		add(out, "Status Flags", PERMISSION_STAFF, "Staff", "Counts as Staff for display and staff checks.");
		add(out, "Status Flags", PERMISSION_OWNER, "Owner", "Owner display tag; default Owner receives broad command and edit permissions.");
		return Collections.unmodifiableList(out);
	}

	private static void add(List<PermissionEntry> entries, String group, String key, String label, String description) {
		entries.add(new PermissionEntry(key, label, description, group));
	}

	private static void addCommand(List<PermissionEntry> entries, String group, String label, String description,
			String... path) {
		String key = commandPermission(path);
		if (key != null) add(entries, group, key, label, description);
	}

	private static Map<String, String> buildPermissionDescriptions() {
		LinkedHashMap<String, String> out = new LinkedHashMap<>();
		for (PermissionEntry entry : PERMISSION_ENTRIES) out.put(entry.key(), entry.description());
		return Collections.unmodifiableMap(out);
	}

	public static List<PermissionEntry> permissionEntries() {
		return PERMISSION_ENTRIES;
	}

	public static List<String> permissionKeys() {
		return List.copyOf(PERMISSION_DESCRIPTIONS.keySet());
	}

	public static String permissionDescription(String permission) {
		String key = canonicalPermission(permission);
		if (key == null) return null;
		String description = PERMISSION_DESCRIPTIONS.get(key);
		if (description != null) return description;
		if (key.startsWith(COMMAND_PERMISSION_PREFIX)) {
			return "Use /g " + key.substring(COMMAND_PERMISSION_PREFIX.length()).replace('_', ' ') + ".";
		}
		return null;
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

	public record PermissionEntry(String key, String label, String description, String group) {
	}
}
