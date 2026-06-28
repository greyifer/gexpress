package dev.mapselect.bugreport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.bugreport.BugReportActionPayload;
import dev.mapselect.network.bugreport.BugReportListPayload;
import dev.mapselect.network.bugreport.BugReportListRequestPayload;
import dev.mapselect.network.bugreport.BugReportSubmitPayload;
import dev.mapselect.permissions.GexpressPermissions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DiscordBugReportBridge {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	private DiscordBugReportBridge() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(BugReportSubmitPayload.ID, BugReportSubmitPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BugReportListRequestPayload.ID, BugReportListRequestPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BugReportActionPayload.ID, BugReportActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BugReportListPayload.ID, BugReportListPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BugReportSubmitPayload.ID,
			(payload, context) -> context.server().execute(() -> submit(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(BugReportListRequestPayload.ID,
			(payload, context) -> context.server().execute(() -> listReports(context.player(), payload.status())));
		ServerPlayNetworking.registerGlobalReceiver(BugReportActionPayload.ID,
			(payload, context) -> context.server().execute(() -> updateReport(context.player(), payload)));
	}

	private static void submit(ServerPlayerEntity player, BugReportSubmitPayload payload) {
		if (player == null || payload == null || payload.message().isBlank()) return;
		String endpoint = GexpressConfig.getBugReportDiscordEndpoint();
		if (endpoint.isBlank()) {
			player.sendMessage(Text.literal("Bug report saved locally, but the Discord bot endpoint is not configured on this server.")
				.formatted(Formatting.GOLD), true);
			return;
		}

		URI uri;
		try {
			uri = endpointUri(endpoint);
		} catch (IllegalArgumentException e) {
			player.sendMessage(Text.literal("Discord bug report endpoint is invalid. Ask staff to check the server config.")
				.formatted(Formatting.RED), true);
			MapSelect.LOGGER.warn("Invalid Discord bug report endpoint: {}", endpoint, e);
			return;
		}

		String body = GSON.toJson(body(player, payload));
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.header("User-Agent", "GExpress-Mod")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		String secret = GexpressConfig.getBugReportDiscordSecret();
		if (!secret.isBlank()) builder.header("X-GExpress-Secret", secret);

		CompletableFuture.runAsync(() -> {
			try {
				HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
				boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
				player.getServer().execute(() -> player.sendMessage(Text.literal(ok
					? "Bug report sent to Discord."
					: "Discord bug report failed: HTTP " + response.statusCode())
					.formatted(ok ? Formatting.GREEN : Formatting.RED), true));
			} catch (Throwable t) {
				MapSelect.LOGGER.warn("Failed to send Discord bug report.", t);
				player.getServer().execute(() -> player.sendMessage(Text.literal("Discord bug report failed. Staff can still read the local copy.")
					.formatted(Formatting.RED), true));
			}
		});
	}

	private static void listReports(ServerPlayerEntity player, String rawStatus) {
		if (player == null) return;
		String status = normalizeStatus(rawStatus);
		String endpoint = GexpressConfig.getBugReportDiscordEndpoint();
		if (endpoint.isBlank()) {
			sendReports(player, status, "Bot endpoint is not configured on this server.", List.of());
			return;
		}

		URI uri;
		try {
			uri = listUri(endpoint, status);
		} catch (IllegalArgumentException e) {
			sendReports(player, status, "Bot endpoint is invalid. Ask staff to check the server config.", List.of());
			MapSelect.LOGGER.warn("Invalid Discord bug report endpoint: {}", endpoint, e);
			return;
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Accept", "application/json")
			.header("User-Agent", "GExpress-Mod")
			.GET();
		addSecret(builder);

		CompletableFuture.runAsync(() -> {
			try {
				HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				List<BugReportListPayload.Entry> reports = parseReports(response.body());
				sendReports(player, status, "Loaded " + reports.size() + " Discord report(s).", reports);
			} catch (Throwable t) {
				MapSelect.LOGGER.warn("Failed to fetch Discord bug reports.", t);
				sendReports(player, status, "Could not load Discord reports: " + cleanMessage(t.getMessage()), List.of());
			}
		});
	}

	private static void updateReport(ServerPlayerEntity player, BugReportActionPayload payload) {
		if (player == null || payload == null || payload.id().isBlank()) return;
		if (!GexpressPermissions.canModerateBugReports(player)) {
			player.sendMessage(Text.literal("You do not have permission to moderate bug reports.")
				.formatted(Formatting.RED), true);
			return;
		}
		String endpoint = GexpressConfig.getBugReportDiscordEndpoint();
		if (endpoint.isBlank()) {
			sendReports(player, normalizeStatus(payload.statusFilter()), "Bot endpoint is not configured on this server.", List.of());
			return;
		}

		String nextStatus = switch (payload.action()) {
			case "fixed" -> "fixed";
			case "open", "reopen" -> "open";
			case "duplicate" -> "duplicate";
			case "delete", "deleted" -> "deleted";
			default -> "";
		};
		if (nextStatus.isBlank()) {
			player.sendMessage(Text.literal("Unknown bug report action: " + payload.action()).formatted(Formatting.RED), true);
			return;
		}

		URI uri;
		try {
			uri = reportUri(endpoint, payload.id());
		} catch (IllegalArgumentException e) {
			player.sendMessage(Text.literal("Discord bug report endpoint is invalid. Ask staff to check the server config.")
				.formatted(Formatting.RED), true);
			MapSelect.LOGGER.warn("Invalid Discord bug report endpoint: {}", endpoint, e);
			return;
		}

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Accept", "application/json")
			.header("User-Agent", "GExpress-Mod");
		if ("deleted".equals(nextStatus)) {
			builder.DELETE();
		} else {
			JsonObject body = new JsonObject();
			body.addProperty("status", nextStatus);
			builder.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
		}
		addSecret(builder);

		String statusFilter = normalizeStatus(payload.statusFilter());
		CompletableFuture.runAsync(() -> {
			try {
				HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
				boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
				player.getServer().execute(() -> player.sendMessage(Text.literal(ok
					? "Bug report updated."
					: "Bug report update failed: HTTP " + response.statusCode())
					.formatted(ok ? Formatting.GREEN : Formatting.RED), true));
				listReports(player, statusFilter);
			} catch (Throwable t) {
				MapSelect.LOGGER.warn("Failed to update Discord bug report {}.", payload.id(), t);
				player.getServer().execute(() -> player.sendMessage(Text.literal("Bug report update failed: " + cleanMessage(t.getMessage()))
					.formatted(Formatting.RED), true));
				listReports(player, statusFilter);
			}
		});
	}

	private static JsonObject body(ServerPlayerEntity player, BugReportSubmitPayload payload) {
		JsonObject json = new JsonObject();
		json.addProperty("category", payload.category());
		json.addProperty("title", title(payload));
		json.addProperty("description", payload.message());
		json.addProperty("reporterName", player.getGameProfile().getName());
		json.addProperty("reporterUuid", player.getUuidAsString());
		json.addProperty("serverName", player.getServer().getName());
		json.addProperty("modVersion", modVersion());
		json.addProperty("minecraftVersion", SharedConstants.getGameVersion().getName());
		json.addProperty("createdAt", Instant.now().toString());
		json.addProperty("modId", MapSelect.MOD_ID);
		return json;
	}

	private static URI endpointUri(String endpoint) {
		String value = (endpoint == null ? "" : endpoint.trim()).replaceAll("/+$", "");
		if (!value.endsWith("/api/bug-reports") && !value.endsWith("/bug-report")) {
			value = value + "/api/bug-reports";
		}
		URI uri = URI.create(value);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("Endpoint must be an http(s) URL");
		}
		return uri;
	}

	private static URI collectionUri(String endpoint) {
		String value = (endpoint == null ? "" : endpoint.trim()).replaceAll("/+$", "");
		if (value.endsWith("/bug-report")) {
			value = value.substring(0, value.length() - "/bug-report".length()) + "/api/bug-reports";
		} else if (!value.endsWith("/api/bug-reports")) {
			value = value + "/api/bug-reports";
		}
		URI uri = URI.create(value);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("Endpoint must be an http(s) URL");
		}
		return uri;
	}

	private static URI listUri(String endpoint, String status) {
		return URI.create(collectionUri(endpoint) + "?status=" + enc(normalizeStatus(status)));
	}

	private static URI reportUri(String endpoint, String id) {
		return URI.create(collectionUri(endpoint) + "/" + enc(id).replace("+", "%20"));
	}

	private static void addSecret(HttpRequest.Builder builder) {
		String secret = GexpressConfig.getBugReportDiscordSecret();
		if (!secret.isBlank()) builder.header("X-GExpress-Secret", secret);
	}

	private static void sendReports(ServerPlayerEntity player, String status, String message, List<BugReportListPayload.Entry> reports) {
		if (player == null || player.getServer() == null) return;
		player.getServer().execute(() -> {
			if (ServerPlayNetworking.canSend(player, BugReportListPayload.ID)) {
				ServerPlayNetworking.send(player, new BugReportListPayload(status, message,
					GexpressPermissions.canModerateBugReports(player), reports));
			}
		});
	}

	private static List<BugReportListPayload.Entry> parseReports(String body) throws IOException {
		JsonElement root = JsonParser.parseString(body == null ? "" : body);
		JsonArray reports;
		if (root.isJsonArray()) {
			reports = root.getAsJsonArray();
		} else if (root.isJsonObject() && root.getAsJsonObject().has("reports")) {
			reports = root.getAsJsonObject().getAsJsonArray("reports");
		} else {
			throw new IOException("Unexpected report response");
		}

		List<BugReportListPayload.Entry> out = new ArrayList<>();
		for (JsonElement element : reports) {
			if (!element.isJsonObject()) continue;
			JsonObject report = element.getAsJsonObject();
			out.add(new BugReportListPayload.Entry(
				string(report, "id"),
				string(report, "status"),
				string(report, "category"),
				string(report, "title"),
				string(report, "description"),
				string(report, "reporterName"),
				string(report, "serverName"),
				string(report, "modVersion"),
				string(report, "minecraftVersion"),
				string(report, "createdAt"),
				string(report, "updatedAt"),
				firstString(report, "discordUrl", "url")
			));
		}
		return out;
	}

	private static String firstString(JsonObject obj, String first, String second) {
		String value = string(obj, first);
		return value.isBlank() ? string(obj, second) : value;
	}

	private static String string(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
		return obj.get(key).getAsString();
	}

	private static String normalizeStatus(String raw) {
		String status = raw == null ? "" : raw.trim().toLowerCase();
		return switch (status) {
			case "open", "fixed", "duplicate", "deleted", "all" -> status;
			default -> "all";
		};
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static String cleanMessage(String value) {
		String clean = value == null ? "unknown error" : value.replaceAll("\\s+", " ").trim();
		if (clean.isBlank()) clean = "unknown error";
		return clean.length() <= 120 ? clean : clean.substring(0, 117) + "...";
	}

	private static String title(BugReportSubmitPayload payload) {
		String message = payload.message() == null ? "" : payload.message().replaceAll("\\s+", " ").trim();
		if (message.isEmpty()) return "In-game bug report";
		return message.length() <= 80 ? message : message.substring(0, 77) + "...";
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MapSelect.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}
}
