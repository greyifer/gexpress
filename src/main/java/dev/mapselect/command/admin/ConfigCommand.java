package dev.mapselect.command.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.config.GexpressConfigSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public final class ConfigCommand {
	private ConfigCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("config")
			.requires(ConfigCommand::canUseConfigBranch)
			.then(CommandManager.literal("preset")
				.then(CommandManager.literal("save")
					.requires(source -> canUseConfigCommand(source, "export"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.executes(ctx -> runExport(ctx, StringArgumentType.getString(ctx, "name")))
						.then(CommandManager.literal("replace")
							.then(CommandManager.literal("confirm")
								.executes(ctx -> runExportReplace(ctx, StringArgumentType.getString(ctx, "name")))))))
				.then(CommandManager.literal("apply")
					.requires(source -> canUseConfigCommand(source, "import"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(ConfigCommand::suggestExports)
						.executes(ctx -> runImport(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("dryrun")
					.requires(source -> canUseConfigCommand(source, "import"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(ConfigCommand::suggestExports)
						.executes(ctx -> runImportDryRun(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("diff")
					.requires(source -> canUseConfigCommand(source, "diff"))
					.then(CommandManager.argument("name", StringArgumentType.word())
						.suggests(ConfigCommand::suggestExports)
						.executes(ctx -> runDiff(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("list")
					.requires(source -> canUseConfigCommand(source, "list"))
					.executes(ConfigCommand::runList)))
			.then(CommandManager.literal("export")
				.requires(source -> canUseConfigCommand(source, "export"))
				.then(CommandManager.argument("name", StringArgumentType.word())
					.executes(ctx -> runExport(ctx, StringArgumentType.getString(ctx, "name")))
					.then(CommandManager.literal("replace")
						.then(CommandManager.literal("confirm")
							.executes(ctx -> runExportReplace(ctx, StringArgumentType.getString(ctx, "name")))))))
			.then(CommandManager.literal("import")
				.requires(source -> canUseConfigCommand(source, "import"))
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(ConfigCommand::suggestExports)
					.executes(ctx -> runImport(ctx, StringArgumentType.getString(ctx, "name")))
					.then(CommandManager.literal("dryrun")
						.executes(ctx -> runImportDryRun(ctx, StringArgumentType.getString(ctx, "name"))))))
			.then(CommandManager.literal("diff")
				.requires(source -> canUseConfigCommand(source, "diff"))
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests(ConfigCommand::suggestExports)
					.executes(ctx -> runDiff(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(CommandManager.literal("list")
				.requires(source -> canUseConfigCommand(source, "list"))
				.executes(ConfigCommand::runList));
	}

	private static boolean canUseConfigBranch(ServerCommandSource source) {
		return source.hasPermissionLevel(2)
			|| GexpressPermissions.canEditGameTab(source.getPlayer())
			|| GexpressPermissions.canUseCommandBranch(source, "admin", "config");
	}

	private static boolean canUseConfigCommand(ServerCommandSource source, String subcommand) {
		return source.hasPermissionLevel(2)
			|| GexpressPermissions.canEditGameTab(source.getPlayer())
			|| GexpressPermissions.canUseCommandPath(source, "admin", "config", subcommand);
	}

	private static CompletableFuture<Suggestions> suggestExports(CommandContext<ServerCommandSource> ctx,
			SuggestionsBuilder builder) {
		try {
			return CommandSource.suggestMatching(GexpressConfig.listConfigExports(), builder);
		} catch (IOException ignored) {
			return builder.buildFuture();
		}
	}

	private static int runExport(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!GexpressConfig.isValidExportName(name)) {
			src.sendError(Text.literal("Invalid config export name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		try {
			Path path = GexpressConfig.exportConfig(name);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Exported G'Express config to "
				+ path.getFileName() + ".").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to export config: " + e.getMessage()));
			return 0;
		}
	}

	private static int runExportReplace(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!GexpressConfig.isValidExportName(name)) {
			src.sendError(Text.literal("Invalid config export name. Use letters, digits, '_' or '-', up to 64 chars."));
			return 0;
		}
		try {
			GexpressConfig.exportConfig(name, true);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Saved G'Express config preset '"
				+ name + "' and backed up the previous copy if one existed.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to save config preset: " + e.getMessage()));
			return 0;
		}
	}

	private static int runImport(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!GexpressConfig.isValidExportName(name)) {
			src.sendError(Text.literal("Invalid config export name."));
			return 0;
		}
		try {
			GexpressConfig.importConfig(name);
			GexpressConfigSyncHandler.broadcastConfig(src.getServer());
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Imported G'Express config export '"
				+ name + "'.").formatted(Formatting.GREEN), true);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to import config: " + e.getMessage()));
			return 0;
		}
	}

	private static int runImportDryRun(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!GexpressConfig.isValidExportName(name)) {
			src.sendError(Text.literal("Invalid config export name."));
			return 0;
		}
		try {
			GexpressConfig.ConfigImportPreview preview = GexpressConfig.previewImportConfig(name);
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Config export '" + name
				+ "' is readable and migrates from version " + preview.sourceVersion()
				+ " to " + preview.targetVersion() + " without writing files.").formatted(Formatting.GREEN), false);
			return 1;
		} catch (IOException e) {
			src.sendError(Text.literal("Config import dry-run failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			List<String> exports = GexpressConfig.listConfigExports();
			if (exports.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("No config exports found."), false);
				return 1;
			}
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Config exports ("
				+ exports.size() + "): " + String.join(", ", exports)), false);
			return exports.size();
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to list config exports: " + e.getMessage()));
			return 0;
		}
	}

	private static int runDiff(CommandContext<ServerCommandSource> ctx, String name) {
		ServerCommandSource src = ctx.getSource();
		if (!GexpressConfig.isValidExportName(name)) {
			src.sendError(Text.literal("Invalid config export name."));
			return 0;
		}
		try {
			GexpressConfig.save();
			JsonObject current = readObject(GexpressConfig.configPath());
			JsonObject exported = readObject(GexpressConfig.exportFile(name));
			List<String> changes = diffTopLevel(current, exported);
			if (changes.isEmpty()) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Config export '" + name
					+ "' matches the current config.").formatted(Formatting.GREEN), false);
				return 1;
			}
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("Config diff against '" + name
				+ "': " + changes.size() + " changed field(s).").formatted(Formatting.YELLOW), false);
			for (int i = 0; i < Math.min(12, changes.size()); i++) {
				String row = changes.get(i);
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - " + row).formatted(Formatting.GRAY), false);
			}
			if (changes.size() > 12) {
				dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(" - ... "
					+ (changes.size() - 12) + " more").formatted(Formatting.DARK_GRAY), false);
			}
			return changes.size();
		} catch (IOException | IllegalStateException e) {
			src.sendError(Text.literal("Failed to diff config: " + e.getMessage()));
			return 0;
		}
	}

	private static JsonObject readObject(Path path) throws IOException {
		if (!Files.exists(path)) throw new IOException(path.getFileName() + " does not exist.");
		JsonElement element = JsonParser.parseString(Files.readString(path));
		if (!element.isJsonObject()) throw new IOException(path.getFileName() + " is not a JSON object.");
		return element.getAsJsonObject();
	}

	private static List<String> diffTopLevel(JsonObject current, JsonObject exported) {
		Set<String> keys = new TreeSet<>();
		current.keySet().forEach(keys::add);
		exported.keySet().forEach(keys::add);
		List<String> changes = new ArrayList<>();
		for (String key : keys) {
			JsonElement currentValue = current.get(key);
			JsonElement exportedValue = exported.get(key);
			if (Objects.equals(currentValue, exportedValue)) continue;
			changes.add(key + ": current=" + displayValue(key, currentValue)
				+ ", export=" + displayValue(key, exportedValue));
		}
		return changes;
	}

	private static String displayValue(String key, JsonElement value) {
		if (value == null || value.isJsonNull()) return "null";
		if (key != null && key.toLowerCase(java.util.Locale.ROOT).contains("secret")) return "<redacted>";
		String raw = value.toString();
		return raw.length() > 80 ? raw.substring(0, 77) + "..." : raw;
	}
}
