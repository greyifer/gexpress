package dev.mapselect.command.game;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mapselect.command.GexpressFeedback;
import dev.mapselect.game.AutomaticGameManager;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Predicate;

public final class AutoGameCommand {
	private static final Predicate<ServerCommandSource> CAN_START_GAMES = GexpressPermissions::canStartGames;
	private static final ZoneId ZONE = ZoneId.systemDefault();

	private AutoGameCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("auto")
			.requires(CAN_START_GAMES)
			.executes(AutoGameCommand::status)
			.then(CommandManager.literal("end")
				.executes(ctx -> AutomaticGameManager.end(ctx.getSource())))
			.then(CommandManager.argument("window", StringArgumentType.greedyString())
				.requires(src -> !AutomaticGameManager.isActive())
				.executes(AutoGameCommand::start));
	}

	private static int start(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		Instant now = Instant.now();
		try {
			String[] window = splitWindow(StringArgumentType.getString(ctx, "window"));
			Instant start = parseStart(window[0], now);
			Instant end = parseEnd(window[1], start);
			return AutomaticGameManager.start(source, start, end);
		} catch (IllegalArgumentException e) {
			source.sendError(Text.literal(e.getMessage()));
			return 0;
		}
	}

	private static int status(CommandContext<ServerCommandSource> ctx) {
		if (AutomaticGameManager.isActive()) {
			GexpressFeedback.send(ctx.getSource(), Text.literal("An automatic game is active. Use /g game auto end to stop it.")
				.formatted(Formatting.YELLOW));
		} else {
			GexpressFeedback.send(ctx.getSource(), Text.literal("Usage: /g game auto <start> <end>. Example: /g game auto 20:00 22:00")
				.formatted(Formatting.GRAY));
		}
		return 1;
	}

	private static Instant parseStart(String raw, Instant now) {
		ParsedTime parsed = parse(raw, LocalDate.now(ZONE));
		Instant instant = parsed.instant();
		if (parsed.timeOnly() && !instant.isAfter(now)) {
			instant = instant.plusSeconds(24L * 60L * 60L);
		}
		return instant;
	}

	private static Instant parseEnd(String raw, Instant start) {
		LocalDate baseDate = LocalDateTime.ofInstant(start, ZONE).toLocalDate();
		ParsedTime parsed = parse(raw, baseDate);
		Instant instant = parsed.instant();
		if (parsed.timeOnly() && !instant.isAfter(start)) {
			instant = instant.plusSeconds(24L * 60L * 60L);
		}
		if (!instant.isAfter(start)) {
			throw new IllegalArgumentException("End time must be after start time.");
		}
		return instant;
	}

	private static String[] splitWindow(String raw) {
		String value = raw == null ? "" : raw.trim();
		String[] parts = value.split("\\s+", 3);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("Usage: /g game auto <start> <end>. Example: /g game auto 20:00 22:00");
		}
		return parts;
	}

	private static ParsedTime parse(String raw, LocalDate defaultDate) {
		String value = raw == null ? "" : raw.trim();
		if (value.isBlank()) throw invalidTime(raw);
		if (value.matches("\\d{10,}")) {
			long epoch = Long.parseLong(value);
			if (value.length() > 10) epoch /= 1000L;
			return new ParsedTime(Instant.ofEpochSecond(epoch), false);
		}
		try {
			return new ParsedTime(Instant.parse(value), false);
		} catch (DateTimeParseException ignored) {
		}
		try {
			return new ParsedTime(OffsetDateTime.parse(value).toInstant(), false);
		} catch (DateTimeParseException ignored) {
		}
		try {
			return new ParsedTime(ZonedDateTime.parse(value).toInstant(), false);
		} catch (DateTimeParseException ignored) {
		}

		String normalized = value.replace('_', 'T');
		for (DateTimeFormatter formatter : localDateTimeFormats()) {
			try {
				return new ParsedTime(LocalDateTime.parse(normalized, formatter).atZone(ZONE).toInstant(), false);
			} catch (DateTimeParseException ignored) {
			}
		}
		for (DateTimeFormatter formatter : localTimeFormats()) {
			try {
				LocalTime time = LocalTime.parse(value.toUpperCase(Locale.ROOT), formatter);
				return new ParsedTime(LocalDateTime.of(defaultDate, time).atZone(ZONE).toInstant(), true);
			} catch (DateTimeParseException ignored) {
			}
		}
		throw invalidTime(raw);
	}

	private static DateTimeFormatter[] localDateTimeFormats() {
		return new DateTimeFormatter[] {
			DateTimeFormatter.ISO_LOCAL_DATE_TIME,
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:mm:ss")
		};
	}

	private static DateTimeFormatter[] localTimeFormats() {
		return new DateTimeFormatter[] {
			DateTimeFormatter.ISO_LOCAL_TIME,
			DateTimeFormatter.ofPattern("H:mm"),
			DateTimeFormatter.ofPattern("H:mm:ss"),
			DateTimeFormatter.ofPattern("ha", Locale.ROOT),
			DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)
		};
	}

	private static IllegalArgumentException invalidTime(String raw) {
		return new IllegalArgumentException("Invalid time '" + raw
			+ "'. Use HH:mm, yyyy-MM-ddTHH:mm, or an epoch timestamp.");
	}

	private record ParsedTime(Instant instant, boolean timeOnly) {}
}
