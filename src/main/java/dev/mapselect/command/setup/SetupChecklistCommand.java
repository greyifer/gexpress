package dev.mapselect.command.setup;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import cat.rezelyn.watheextended.game.TeleportationSlot;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.MapPresetValidator;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.preset.train.TrainPresetValidator;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class SetupChecklistCommand {
	private SetupChecklistCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("checklist")
			.requires(source -> GexpressPermissions.canUseSetupCommands(source)
				|| GexpressPermissions.canUseCommandPath(source, "setup", "checklist"))
			.executes(SetupChecklistCommand::run);
	}

	private static int run(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		try {
			int failures = 0;
			List<String> maps = PresetStorage.list(src.getServer());
			List<String> trains = TrainPresetStorage.list(src.getServer());
			MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
			String activeMapName = weather == null ? null : weather.getCurrentMapName();
			MapPreset activeMap = activeMapName == null || activeMapName.isBlank()
				? null : PresetStorage.load(src.getServer(), activeMapName);

			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal("G'Express setup checklist")
				.formatted(Formatting.GOLD), false);
			failures += row(src, !maps.isEmpty(), "Map presets", maps.size() + " saved");
			failures += row(src, activeMap != null, "Active map", activeMapName == null ? "none" : activeMapName);
			failures += row(src, !trains.isEmpty(), "Train presets", trains.size() + " saved");
			failures += checkDefaultTrain(src, activeMap);
			failures += checkRtp(src);
			failures += checkMapValidation(src, maps);
			failures += checkTrainValidation(src, trains);

			final int totalFailures = failures;
			dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(totalFailures == 0
					? "Setup checklist passed."
					: "Setup checklist has " + totalFailures + " item(s) to fix.")
				.formatted(totalFailures == 0 ? Formatting.GREEN : Formatting.YELLOW), false);
			return totalFailures == 0 ? 1 : 0;
		} catch (IOException e) {
			src.sendError(Text.literal("Failed to run setup checklist: " + e.getMessage()));
			return 0;
		}
	}

	private static int checkDefaultTrain(ServerCommandSource src, MapPreset activeMap) throws IOException {
		if (activeMap == null) return row(src, false, "Default train", "no active map");
		String train = activeMap.defaultTrainPreset;
		boolean ok = train != null && !train.isBlank() && TrainPresetStorage.exists(src.getServer(), train);
		return row(src, ok, "Default train", train == null || train.isBlank() ? "not set" : train);
	}

	private static int checkRtp(ServerCommandSource src) {
		WatheExtendedWorldComponent component = WatheExtendedWorldComponent.KEY.getNullable(src.getWorld());
		Map<Integer, TeleportationSlot> slots = component == null ? Map.of() : component.getTeleportationSlots();
		int count = slots == null ? 0 : slots.size();
		boolean enabled = component != null && component.isRtpEnabled();
		return row(src, enabled && count >= 2, "RTP", count + " slot(s), " + (enabled ? "enabled" : "disabled"));
	}

	private static int checkMapValidation(ServerCommandSource src, List<String> names) throws IOException {
		int errors = 0;
		int warnings = 0;
		for (String name : names) {
			MapPreset preset = PresetStorage.load(src.getServer(), name);
			List<MapPresetValidator.Issue> issues = MapPresetValidator.validate(src.getServer(), name, preset);
			errors += countMapIssues(issues, MapPresetValidator.Severity.ERROR);
			warnings += countMapIssues(issues, MapPresetValidator.Severity.WARNING);
		}
		return row(src, errors == 0, "Map validation", errors + " error(s), " + warnings + " warning(s)");
	}

	private static int checkTrainValidation(ServerCommandSource src, List<String> names) throws IOException {
		int errors = 0;
		int warnings = 0;
		for (String name : names) {
			TrainPreset preset = TrainPresetStorage.load(src.getServer(), name);
			List<TrainPresetValidator.Issue> issues = TrainPresetValidator.validate(name, preset);
			errors += countTrainIssues(issues, TrainPresetValidator.Severity.ERROR);
			warnings += countTrainIssues(issues, TrainPresetValidator.Severity.WARNING);
		}
		return row(src, errors == 0, "Train validation", errors + " error(s), " + warnings + " warning(s)");
	}

	private static int row(ServerCommandSource src, boolean ok, String label, String detail) {
		Formatting color = ok ? Formatting.GREEN : Formatting.YELLOW;
		String marker = ok ? "[OK] " : "[TODO] ";
		dev.mapselect.command.GexpressFeedback.send(src, () -> Text.literal(marker + label + ": " + detail)
			.formatted(color), false);
		return ok ? 0 : 1;
	}

	private static int countMapIssues(List<MapPresetValidator.Issue> issues, MapPresetValidator.Severity severity) {
		return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
	}

	private static int countTrainIssues(List<TrainPresetValidator.Issue> issues, TrainPresetValidator.Severity severity) {
		return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
	}
}
