package dev.mapselect.command.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.KinsWatheSafePreparation;
import dev.mapselect.game.RoundParticipantComponent;
import dev.mapselect.game.RoundParticipantTracker;
import dev.mapselect.game.ServerPerformanceMonitor;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.MapPresetValidator;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.preset.train.TrainPresetValidator;
import dev.mapselect.role.GexpressRoleShop;
import dev.mapselect.voice.DeadVoiceGroupManager;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class DiagnosticsCommand {
	private DiagnosticsCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("diagnostics")
			.requires(GexpressPermissions::canUseDiagnostics)
			.executes(DiagnosticsCommand::runSummary)
			.then(CommandManager.literal("config").executes(DiagnosticsCommand::runConfig))
			.then(CommandManager.literal("compat").executes(DiagnosticsCommand::runCompat))
			.then(CommandManager.literal("presets").executes(DiagnosticsCommand::runPresets))
			.then(CommandManager.literal("round").executes(DiagnosticsCommand::runRound))
			.then(CommandManager.literal("localization").executes(DiagnosticsCommand::runLocalization))
			.then(CommandManager.literal("player")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(ctx -> runPlayers(ctx, GameProfileArgumentType.getProfileArgument(ctx, "players")))));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildDebugTree() {
		return CommandManager.literal("debugplayer")
			.requires(GexpressPermissions::canUseDiagnostics)
			.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
				.executes(ctx -> runPlayers(ctx, GameProfileArgumentType.getProfileArgument(ctx, "players"))));
	}

	private static int runSummary(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
		String status = game == null ? "unknown" : game.getGameStatus().name();
		String mode = game == null || game.getGameMode() == null ? "unknown" : game.getGameMode().toString();
		String map = weather == null || weather.getCurrentMapName() == null ? "none" : weather.getCurrentMapName();
		int online = src.getServer().getPlayerManager().getCurrentPlayerCount();
		int pairs = LoversManager.pairViews(src.getWorld()).size();

		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express diagnostics").formatted(Formatting.GOLD), false);
		send(src, "Game", "status=" + status + ", mode=" + mode + ", safePrep="
			+ KinsWatheSafePreparation.isActive(src.getWorld()) + ", currentMap=" + map);
		send(src, "Players", "online=" + online + ", loversPairs=" + pairs);
		send(src, "Performance", "tickAvg=" + fmt(ServerPerformanceMonitor.rollingTickMs())
			+ "ms, approxTps=" + fmt(ServerPerformanceMonitor.rollingTps())
			+ ", spikes=" + ServerPerformanceMonitor.spikeCount());
		send(src, "Config", "version=" + GexpressConfig.loadedConfigVersion() + "/"
			+ GexpressConfig.CURRENT_CONFIG_VERSION + ", customRoleCounts="
			+ GexpressConfig.useCustomRoleCounts() + ", maxKillers=" + GexpressConfig.getMaxKillerAmount()
			+ ", maxModifiers=" + GexpressConfig.getMaxModifiersPerPlayer());
		for (LoversManager.PairView pair : LoversManager.pairViews(src.getWorld())) {
			send(src, "Lovers", pair.firstName() + " <-> " + pair.secondName());
		}
		return 1;
	}

	private static int runConfig(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express config summary").formatted(Formatting.GOLD), false);
		send(src, "Version", GexpressConfig.loadedConfigVersion() + "/" + GexpressConfig.CURRENT_CONFIG_VERSION);
		send(src, "Role counts", "custom=" + GexpressConfig.useCustomRoleCounts()
			+ ", killers=" + GexpressConfig.getMaxKillerAmount()
			+ ", vigilantes=" + GexpressConfig.getMaxVigilanteAmount()
			+ ", neutrals=" + GexpressConfig.getMaxNeutralAmount()
			+ ", modifiersPerPlayer=" + GexpressConfig.getMaxModifiersPerPlayer());
		send(src, "Scaled role counts", "playersPerKiller=" + GexpressConfig.getPlayersPerKiller()
			+ ", playersPerVigilante=" + GexpressConfig.getPlayersPerVigilante()
			+ ", playersPerNeutral=" + GexpressConfig.getPlayersPerNeutral());
		send(src, "Economy", "passive[killer=" + GexpressConfig.getPassiveIncomeKiller()
			+ ", civilian=" + GexpressConfig.getPassiveIncomeCivilian()
			+ ", neutral=" + GexpressConfig.getPassiveIncomeNeutral()
			+ ", vigilante=" + GexpressConfig.getPassiveIncomeVigilante()
			+ ", mafia=" + GexpressConfig.getPassiveIncomeMafia() + "]");
		send(src, "C4", "price=" + GexpressConfig.getC4Price()
			+ ", fuse=" + GexpressConfig.getC4FuseSeconds()
			+ "s, firstBeep=" + GexpressConfig.getC4FirstBeepSeconds()
			+ "s, wrongWire=" + GexpressConfig.getWrongWirePercent() + "%");
		send(src, "Snitch", "tasks=" + GexpressConfig.getSnitchTasksRequired()
			+ ", warningRemaining=" + GexpressConfig.getSnitchWarningTasksRemaining());
		send(src, "Time Master", "rewind=" + GexpressConfig.getTimeMasterRewindSeconds()
			+ "s, uses=" + GexpressConfig.getTimeMasterMaxUses()
			+ ", freeze=" + GexpressConfig.getTimeMasterFreezeDurationSeconds()
			+ "s/" + GexpressConfig.getTimeMasterFreezeMaxUses() + " use(s)");
		send(src, "HUD", "abilityScale=" + GexpressConfig.getAbilityHudScalePercent()
			+ "%, offset=(" + GexpressConfig.getAbilityHudOffsetX()
			+ ", " + GexpressConfig.getAbilityHudOffsetY() + ")");
		return 1;
	}

	private static int runCompat(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express optional compatibility").formatted(Formatting.GOLD), false);
		int loaded = 0;
		for (CompatRow row : compatRows()) {
			boolean present = isAnyLoaded(row.modIds());
			if (present) loaded++;
			send(src, row.label(), present ? "loaded" : "not loaded - " + row.fallback());
		}
		return loaded;
	}

	private static int runPresets(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> mapNames = PresetStorage.list(src.getServer());
			List<String> trainNames = TrainPresetStorage.list(src.getServer());
			int mapErrors = 0;
			int mapWarnings = 0;
			int mapBackups = 0;
			for (String name : mapNames) {
				MapPreset preset = PresetStorage.load(src.getServer(), name);
				List<MapPresetValidator.Issue> issues = MapPresetValidator.validate(src.getServer(), name, preset);
				mapErrors += countMapIssues(issues, MapPresetValidator.Severity.ERROR);
				mapWarnings += countMapIssues(issues, MapPresetValidator.Severity.WARNING);
				mapBackups += PresetStorage.listBackups(src.getServer(), name).size();
			}
			int trainErrors = 0;
			int trainWarnings = 0;
			int trainBackups = 0;
			for (String name : trainNames) {
				TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
				List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate(name, preset);
				trainErrors += countTrainIssues(issues, TrainPresetValidator.Severity.ERROR);
				trainWarnings += countTrainIssues(issues, TrainPresetValidator.Severity.WARNING);
				trainBackups += TrainPresetStorage.listBackups(src.getServer(), name).size();
			}
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express preset diagnostics").formatted(Formatting.GOLD), false);
			send(src, "Map presets", "count=" + mapNames.size()
				+ ", errors=" + mapErrors + ", warnings=" + mapWarnings + ", backups=" + mapBackups);
			send(src, "Train presets", "count=" + trainNames.size()
				+ ", errors=" + trainErrors + ", warnings=" + trainWarnings + ", backups=" + trainBackups);
			return mapErrors + trainErrors == 0 ? 1 : 0;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to inspect presets: " + e.getMessage()));
			return 0;
		}
	}

	private static int runRound(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
		RoundParticipantComponent participants = RoundParticipantComponent.KEY.getNullable(src.getWorld());
		String status = game == null ? "unknown" : game.getGameStatus().name();
		String mode = game == null || game.getGameMode() == null ? "unknown" : game.getGameMode().toString();
		String map = weather == null || weather.getCurrentMapName() == null ? "none" : weather.getCurrentMapName();
		int roleAssignments = game == null || game.getRoles() == null ? 0 : game.getRoles().size();
		int trackedParticipants = participants == null ? 0 : participants.participants().size();
		int onlineParticipants = 0;
		int onlineSpectators = 0;
		int living = 0;
		int dead = 0;
		for (ServerPlayerEntity player : src.getServer().getPlayerManager().getPlayerList()) {
			if (RoundParticipantTracker.isRoundParticipant(src.getWorld(), game, player.getUuid())) onlineParticipants++;
			if (RoundParticipantTracker.isRoundSpectator(src.getWorld(), game, player.getUuid())) onlineSpectators++;
			if (DeadPlayerStatus.isLivingRoundParticipant(player)) living++;
			else if (DeadPlayerStatus.isDeadRoundParticipant(player)) dead++;
		}
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express round diagnostics").formatted(Formatting.GOLD), false);
		send(src, "Round", "status=" + status + ", mode=" + mode + ", currentMap=" + map
			+ ", safePrep=" + KinsWatheSafePreparation.isActive(src.getWorld()));
		send(src, "Assignments", "roles=" + roleAssignments + ", trackedParticipants=" + trackedParticipants);
		send(src, "Online", "participants=" + onlineParticipants + ", spectators=" + onlineSpectators
			+ ", living=" + living + ", dead=" + dead);
		return game == null ? 0 : 1;
	}

	private static int runLocalization(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			JsonObject lang = readLanguage();
			List<String> missing = new ArrayList<>();
			int blank = 0;
			for (String key : expectedLocalizationKeys()) {
				JsonElement value = lang.get(key);
				if (value == null) {
					missing.add(key);
				} else if (!value.isJsonPrimitive() || value.getAsString().isBlank()) {
					blank++;
				}
			}
			for (String key : lang.keySet()) {
				JsonElement value = lang.get(key);
				if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) blank++;
			}
			final int langKeys = lang.size();
			final int missingCount = missing.size();
			final int blankCount = blank;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Localization: "
				+ langKeys + " key(s), " + missingCount + " missing expected key(s), "
				+ blankCount + " blank/non-string value(s).")
				.formatted(missingCount == 0 && blankCount == 0 ? Formatting.GREEN : Formatting.YELLOW), false);
			for (int i = 0; i < Math.min(10, missing.size()); i++) {
				String key = missing.get(i);
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - missing " + key)
					.formatted(Formatting.GRAY), false);
			}
			if (missing.size() > 10) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - ... "
					+ (missing.size() - 10) + " more").formatted(Formatting.DARK_GRAY), false);
			}
			return missingCount == 0 && blankCount == 0 ? 1 : 0;
		} catch (IOException | IllegalStateException e) {
			src.sendError(Text.literal("Failed to inspect localization: " + e.getMessage()));
			return 0;
		}
	}

	private static int runPlayers(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles) {
		ServerCommandSource src = ctx.getSource();
		int count = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(profile.getId());
			if (player == null) {
				send(src, profile.getName(), "offline");
				continue;
			}
			dumpPlayer(src, player);
			count++;
		}
		return count;
	}

	private static void dumpPlayer(ServerCommandSource src, ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		PlayerShopComponent shop = PlayerShopComponent.KEY.getNullable(player);
		List<ShopEntry> resolvedShop = GexpressRoleShop.resolve(player);
		UUID partner = LoversManager.partnerId(player.getUuid());
		UUID playerId = player.getUuid();

		send(src, player.getName().getString(), "role=" + roleId(role)
			+ ", modifiers=" + modifierIds(player)
			+ ", dead=" + DeadPlayerStatus.isDeadRoundParticipant(player)
			+ ", safePrep=" + KinsWatheSafePreparation.isActive(player.getWorld()));
		send(src, "  state", "uuid=" + playerId
			+ ", world=" + player.getWorld().getRegistryKey().getValue()
			+ ", mode=" + player.interactionManager.getGameMode()
			+ ", pos=" + blockPos(player)
			+ ", hp=" + fmt(player.getHealth()));
		send(src, "  round", "participant=" + RoundParticipantTracker.isRoundParticipant(player.getWorld(), game, playerId)
			+ ", spectator=" + RoundParticipantTracker.isRoundSpectator(player.getWorld(), game, playerId)
			+ ", living=" + DeadPlayerStatus.isLivingRoundParticipant(player)
			+ ", voice=" + DeadVoiceGroupManager.status(player));
		send(src, "  economy", "watheGold=" + (shop == null ? "unknown" : shop.balance)
			+ ", customShop=" + GexpressRoleShop.hasCustomShop(player)
			+ ", shopEntries=" + resolvedShop.size()
			+ ", moneyHud=" + GexpressRoleShop.showsMoneyHud(player));
		send(src, "  lovers", partner == null ? "none" : partner.toString());
		send(src, "  tags", GexpressPermissions.effectiveTagInfos(player).stream()
			.map(GexpressPermissions.TagInfo::id).toList().toString());
	}

	private static String roleId(Role role) {
		return role == null || role.identifier() == null ? "none" : role.identifier().toString();
	}

	private static List<String> modifierIds(ServerPlayerEntity player) {
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return List.of();
		List<Modifier> modifiers = component.getModifiers(player.getUuid());
		if (modifiers == null || modifiers.isEmpty()) return List.of();
		return modifiers.stream()
			.filter(modifier -> modifier != null && modifier.identifier() != null)
			.map(modifier -> modifier.identifier().toString())
			.toList();
	}

	private static void send(ServerCommandSource src, String label, String value) {
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(label + ": ").formatted(Formatting.GRAY)
			.append(Text.literal(value).formatted(Formatting.WHITE)), false);
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static String blockPos(ServerPlayerEntity player) {
		return (int) Math.floor(player.getX()) + ","
			+ (int) Math.floor(player.getY()) + ","
			+ (int) Math.floor(player.getZ());
	}

	private static boolean isAnyLoaded(List<String> modIds) {
		FabricLoader loader = FabricLoader.getInstance();
		for (String modId : modIds) {
			if (loader.isModLoaded(modId)) return true;
		}
		return false;
	}

	private static List<CompatRow> compatRows() {
		return List.of(
			new CompatRow("Simple Voice Chat", List.of("voicechat"), "voice groups/mutes use text-only diagnostics"),
			new CompatRow("Axiom", List.of("axiom"), "live editor refresh hooks stay disabled"),
			new CompatRow("Kins Wathe", List.of("kins_wathe", "kinswathe"), "Kins-specific safe-prep hooks stay inactive"),
			new CompatRow("Noelle's Roles", List.of("noellesroles"), "Painter skips Noelle-specific disguise support"),
			new CompatRow("Star Express", List.of("starexpress"), "Star Express role metadata stays generic"),
			new CompatRow("Stupid Express", List.of("stupid_express"), "Stupid Express lover pairing support stays inactive"),
			new CompatRow("Iris/Sodium", List.of("iris", "sodium"), "shader clipping compatibility uses vanilla paths")
		);
	}

	private record CompatRow(String label, List<String> modIds, String fallback) {}

	private static int countMapIssues(List<MapPresetValidator.Issue> issues, MapPresetValidator.Severity severity) {
		return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
	}

	private static int countTrainIssues(List<TrainPresetValidator.Issue> issues, TrainPresetValidator.Severity severity) {
		return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
	}

	private static JsonObject readLanguage() throws IOException {
		try (InputStream stream = DiagnosticsCommand.class.getClassLoader()
				.getResourceAsStream("assets/gexpress/lang/en_us.json")) {
			if (stream == null) throw new IOException("assets/gexpress/lang/en_us.json is missing.");
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!parsed.isJsonObject()) throw new IOException("en_us.json is not a JSON object.");
				return parsed.getAsJsonObject();
			}
		}
	}

	private static Set<String> expectedLocalizationKeys() {
		Harpymodloader.refreshRoles();
		Set<String> keys = new TreeSet<>();
		for (Role role : WatheRoles.ROLES) {
			Identifier id = role == null ? null : role.identifier();
			if (id == null || !"gexpress".equals(id.getNamespace())) continue;
			keys.add("announcement.role." + id.getNamespace() + "." + id.getPath());
			keys.add("gui.watheextended.guidebook.role.desc." + id.getNamespace() + "." + id.getPath());
		}
		for (Modifier modifier : HMLModifiers.MODIFIERS) {
			Identifier id = modifier == null ? null : modifier.identifier();
			if (id == null || !"gexpress".equals(id.getNamespace())) continue;
			keys.add("gui.watheextended.guidebook.modifier.desc." + id.getNamespace() + "." + id.getPath());
		}
		return keys;
	}
}
