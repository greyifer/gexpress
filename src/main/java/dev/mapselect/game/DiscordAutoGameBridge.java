package dev.mapselect.game;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public final class DiscordAutoGameBridge {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	private DiscordAutoGameBridge() {}

	public static boolean isConfigured() {
		return !GexpressConfig.getBugReportDiscordEndpoint().isBlank();
	}

	public static CompletableFuture<Result> announce(MinecraftServer server, ServerPlayerEntity creator,
			Instant start, Instant end) {
		URI uri;
		try {
			uri = endpointUri(GexpressConfig.getBugReportDiscordEndpoint());
		} catch (IllegalArgumentException e) {
			return CompletableFuture.failedFuture(e);
		}

		JsonObject body = new JsonObject();
		body.addProperty("startEpochSeconds", start.getEpochSecond());
		body.addProperty("endEpochSeconds", end.getEpochSecond());
		body.addProperty("startedBy", creator == null ? "Server" : creator.getGameProfile().getName());
		body.addProperty("serverName", server == null ? "" : server.getName());
		body.addProperty("minimumPlayers", AutomaticGameManager.MINIMUM_READY_PLAYERS);
		body.addProperty("countdownSeconds", AutomaticGameManager.START_COUNTDOWN_SECONDS);

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.header("User-Agent", "GExpress-Mod")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
		String secret = GexpressConfig.getBugReportDiscordSecret();
		if (!secret.isBlank()) builder.header("X-GExpress-Secret", secret);

		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
				return new Result(response.statusCode() >= 200 && response.statusCode() < 300,
					response.statusCode(), response.body() == null ? "" : response.body());
			} catch (Throwable t) {
				MapSelect.LOGGER.warn("Failed to send automatic game announcement.", t);
				throw new RuntimeException(t);
			}
		});
	}

	private static URI endpointUri(String endpoint) {
		String value = (endpoint == null ? "" : endpoint.trim()).replaceAll("/+$", "");
		if (value.endsWith("/api/bug-reports")) {
			value = value.substring(0, value.length() - "/api/bug-reports".length()) + "/api/auto-game";
		} else if (value.endsWith("/bug-report")) {
			value = value.substring(0, value.length() - "/bug-report".length()) + "/api/auto-game";
		} else if (!value.endsWith("/api/auto-game")) {
			value = value + "/api/auto-game";
		}
		URI uri = URI.create(value);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("Endpoint must be an http(s) URL");
		}
		return uri;
	}

	public record Result(boolean ok, int statusCode, String body) {}
}
