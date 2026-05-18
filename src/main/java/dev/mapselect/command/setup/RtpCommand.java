package dev.mapselect.command.setup;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import cat.rezelyn.watheextended.game.TeleportationSlot;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.MapSelect;
import dev.mapselect.network.GexpressPresetsSyncHandler;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public class RtpCommand {

	private static final Predicate<ServerCommandSource> OP = GexpressPermissions::canUseSetupCommands;

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("rtp")
			.requires(OP)
			.then(CommandManager.literal("add")
				.executes(RtpCommand::runAddHere)
				.then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
					.then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
						.then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
							.executes(ctx -> runAddExplicit(ctx,
								DoubleArgumentType.getDouble(ctx, "x"),
								DoubleArgumentType.getDouble(ctx, "y"),
								DoubleArgumentType.getDouble(ctx, "z")))))))
			.then(CommandManager.literal("remove")
				.then(CommandManager.argument("id", IntegerArgumentType.integer(0))
					.executes(ctx -> runRemove(ctx, IntegerArgumentType.getInteger(ctx, "id")))))
			.then(CommandManager.literal("removenearest")
				.executes(RtpCommand::runRemoveNearest))
			.then(CommandManager.literal("list")
				.executes(RtpCommand::runList))
			.then(CommandManager.literal("clear")
				.executes(RtpCommand::runClear))
			.then(CommandManager.literal("enable")
				.executes(ctx -> runSetEnabled(ctx, true)))
			.then(CommandManager.literal("disable")
				.executes(ctx -> runSetEnabled(ctx, false)));
	}

	private static WatheExtendedWorldComponent comp(ServerCommandSource src) {
		return WatheExtendedWorldComponent.KEY.get(src.getWorld());
	}

	private static int runAddHere(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		ServerPlayerEntity p = src.getPlayer();
		if (p == null) {
			src.sendError(Text.literal("'add' without coords requires a player. Use /g setup rtp add <x> <y> <z>."));
			return 0;
		}
		int id = comp(src).addTeleportationSlot(new TeleportationSlot(p.getX(), p.getY(), p.getZ(), snapYaw90(p.getYaw()), 0.0F));
		persistActiveMapRandomSpawns(src);
		src.sendFeedback(() -> Text.literal("Added RTP slot #" + id + " at your position.").formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runAddExplicit(CommandContext<ServerCommandSource> ctx, double x, double y, double z) {
		ServerCommandSource src = ctx.getSource();
		ServerPlayerEntity p = src.getPlayer();
		float yaw = p != null ? snapYaw90(p.getYaw()) : 0f;
		float pitch = 0f;
		int id = comp(src).addTeleportationSlot(new TeleportationSlot(x, y, z, yaw, pitch));
		persistActiveMapRandomSpawns(src);
		src.sendFeedback(() -> Text.literal("Added RTP slot #" + id + " at (" + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ").").formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int runRemove(CommandContext<ServerCommandSource> ctx, int id) {
		ServerCommandSource src = ctx.getSource();
		boolean removed = comp(src).removeTeleportationSlot(id);
		if (!removed) {
			src.sendError(Text.literal("No RTP slot with id " + id + "."));
			return 0;
		}
		src.sendFeedback(() -> Text.literal("Removed RTP slot #" + id + ".").formatted(Formatting.YELLOW), true);
		persistActiveMapRandomSpawns(src);
		return 1;
	}

	private static int runRemoveNearest(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		ServerPlayerEntity p = src.getPlayer();
		if (p == null) {
			src.sendError(Text.literal("'removenearest' requires a player."));
			return 0;
		}
		WatheExtendedWorldComponent c = comp(src);
		Map<Integer, TeleportationSlot> slots = c.getTeleportationSlots();
		if (slots == null || slots.isEmpty()) {
			src.sendError(Text.literal("No RTP slots exist."));
			return 0;
		}
		double px = p.getX(), py = p.getY(), pz = p.getZ();
		int bestId = -1;
		double bestDist = Double.POSITIVE_INFINITY;
		for (Map.Entry<Integer, TeleportationSlot> e : slots.entrySet()) {
			TeleportationSlot s = e.getValue();
			double dx = s.x - px, dy = s.y - py, dz = s.z - pz;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestDist) {
				bestDist = d;
				bestId = e.getKey();
			}
		}
		final int id = bestId;
		final double dist = Math.sqrt(bestDist);
		c.removeTeleportationSlot(id);
		src.sendFeedback(() -> Text.literal("Removed nearest RTP slot #" + id + " (" + fmt(dist) + " blocks away).").formatted(Formatting.YELLOW), true);
		persistActiveMapRandomSpawns(src);
		return 1;
	}

	private static int runList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		WatheExtendedWorldComponent c = comp(src);
		Map<Integer, TeleportationSlot> slots = c.getTeleportationSlots();
		if (slots == null || slots.isEmpty()) {
			src.sendFeedback(() -> Text.literal("No RTP slots. Use /g setup rtp add.").formatted(Formatting.GRAY), false);
			return 1;
		}
		boolean enabled = c.isRtpEnabled();
		Text header = Text.literal("─── RTP Slots (" + slots.size() + ", "
			+ (enabled ? "enabled" : "disabled") + ") ───").formatted(Formatting.GOLD, Formatting.BOLD);
		src.sendFeedback(() -> header, false);
		for (Map.Entry<Integer, TeleportationSlot> e : slots.entrySet()) {
			int id = e.getKey();
			TeleportationSlot s = e.getValue();
			String cmd = "/tp @s " + fmt(s.x) + " " + fmt(s.y) + " " + fmt(s.z) + " " + fmt(s.yaw) + " " + fmt(s.pitch);
			Text line = Text.literal("  #" + id + "  ").formatted(Formatting.GRAY)
				.append(Text.literal("[TP]").formatted(Formatting.AQUA)
					.styled(st -> st
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Teleport to slot " + id)))))
				.append(Text.literal("  (" + fmt(s.x) + ", " + fmt(s.y) + ", " + fmt(s.z) + ")").formatted(Formatting.WHITE));
			src.sendFeedback(() -> line, false);
		}
		return 1;
	}

	private static int runClear(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		WatheExtendedWorldComponent c = comp(src);
		int n = c.getTeleportationSlots() == null ? 0 : c.getTeleportationSlots().size();
		c.setTeleportationSlots(new LinkedHashMap<>());
		src.sendFeedback(() -> Text.literal("Cleared " + n + " RTP slot(s).").formatted(Formatting.YELLOW), true);
		persistActiveMapRandomSpawns(src);
		return 1;
	}

	private static int runSetEnabled(CommandContext<ServerCommandSource> ctx, boolean enabled) {
		ServerCommandSource src = ctx.getSource();
		comp(src).setRtpEnabled(enabled);
		src.sendFeedback(() -> Text.literal("RTP " + (enabled ? "enabled" : "disabled") + ".").formatted(Formatting.GREEN), true);
		return 1;
	}

	private static String fmt(double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) d);
		return String.format(java.util.Locale.ROOT, "%.2f", d);
	}

	private static String fmt(float f) {
		if (f == Math.floor(f) && !Float.isInfinite(f)) return Integer.toString((int) f);
		return String.format(java.util.Locale.ROOT, "%.1f", f);
	}

	private static float snapYaw90(float yaw) {
		float snapped = Math.round(yaw / 90.0F) * 90.0F;
		snapped = ((snapped + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
		return snapped == -180.0F ? 180.0F : snapped;
	}

	private static void persistActiveMapRandomSpawns(ServerCommandSource src) {
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
		String currentMap = weather == null ? null : weather.getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) return;
		try {
			MapPreset preset = PresetStorage.load(src.getServer(), currentMap);
			if (preset == null) return;
			preset.randomSpawnPositions = MapPreset.randomSpawnsFrom(src.getWorld());
			PresetStorage.save(src.getServer(), currentMap, preset);
			GexpressPresetsSyncHandler.broadcastPresets(src.getServer());
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to persist RTP slots into active map '{}': {}", currentMap, e.toString());
		}
	}
}
