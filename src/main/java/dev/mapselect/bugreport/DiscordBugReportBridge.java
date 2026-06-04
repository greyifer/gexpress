package dev.mapselect.bugreport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.BugReportSubmitPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public final class DiscordBugReportBridge {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	private DiscordBugReportBridge() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(BugReportSubmitPayload.ID, BugReportSubmitPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BugReportSubmitPayload.ID,
			(payload, context) -> context.server().execute(() -> submit(context.player(), payload)));
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
