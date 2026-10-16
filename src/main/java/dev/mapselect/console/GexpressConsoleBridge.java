package dev.mapselect.console;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.mapselect.MapSelect;
import dev.mapselect.command.admin.TagCommand;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.config.GexpressConfigSyncHandler;
import dev.mapselect.role.RoleModifierTuningBridge;
import dev.mapselect.skin.SkinShopConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Token-authenticated owner bridge used by the local G'Network Admin Console.
 * It binds to loopback by default, so remote use must be deliberately exposed or tunnelled.
 */
public final class GexpressConsoleBridge {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
		.resolve("gexpress-console.json");
	private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
	private static final int MAX_RECENT_NONCES = 4096;
	private static final long NONCE_RETENTION_SECONDS = 5 * 60L;
	private static final Object NONCE_LOCK = new Object();
	private static final LinkedHashMap<String, Long> RECENT_NONCES = new LinkedHashMap<>();
	private static HttpServer httpServer;
	private static ExecutorService executor;
	private static BridgeConfig config;
	private static MinecraftServer minecraftServer;

	private GexpressConsoleBridge() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(GexpressConsoleBridge::start);
		ServerLifecycleEvents.SERVER_STOPPED.register(ignored -> stop());
	}

	private static synchronized void start(MinecraftServer server) {
		stop();
		minecraftServer = server;
		try {
			config = loadConfig();
			if (!config.enabled) {
				MapSelect.LOGGER.info("G'Express owner console bridge is disabled in {}.", CONFIG_PATH);
				return;
			}
			InetSocketAddress address = new InetSocketAddress(config.bindAddress, config.port);
			httpServer = HttpServer.create(address, 0);
			httpServer.createContext("/v1/status", GexpressConsoleBridge::handleStatus);
			httpServer.createContext("/v1/shop", GexpressConsoleBridge::handleShop);
			httpServer.createContext("/v1/developer", GexpressConsoleBridge::handleDeveloper);
			httpServer.createContext("/v2/secure", GexpressConsoleBridge::handleSecure);
			executor = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "gexpress-owner-console");
				thread.setDaemon(true);
				return thread;
			});
			httpServer.setExecutor(executor);
			httpServer.start();
			MapSelect.LOGGER.info("G'Express owner console listening on {}:{} (settings: {}).",
				config.bindAddress, config.port, CONFIG_PATH);
		} catch (Exception e) {
			MapSelect.LOGGER.error("Could not start the G'Express owner console bridge.", e);
			stop();
		}
	}

	private static synchronized void stop() {
		if (httpServer != null) {
			httpServer.stop(1);
			httpServer = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		minecraftServer = null;
		synchronized (NONCE_LOCK) {
			RECENT_NONCES.clear();
		}
	}

	private static void handleSecure(HttpExchange exchange) throws IOException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			methodNotAllowed(exchange, "POST");
			return;
		}
		String envelope = readBody(exchange);
		if (envelope == null) return;
		try {
			long now = System.currentTimeMillis() / 1000L;
			GexpressConsoleCrypto.OpenedRequest request = GexpressConsoleCrypto.openRequest(
				config == null ? "" : config.token, envelope, now);
			if (!acceptNonce(request.nonce, now)) {
				throw new GeneralSecurityException("Encrypted request was already used.");
			}
			SecureResult result = routeSecure(request);
			String response = GexpressConsoleCrypto.sealResponse(config.token, request.nonce,
				result.status, result.body, result.message);
			respondSecure(exchange, response);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			respondError(exchange, 401, "The encrypted owner-console request could not be authenticated.");
		} catch (Exception e) {
			MapSelect.LOGGER.warn("Encrypted owner-console request failed.", e);
			respondError(exchange, 500, "The encrypted owner-console request failed.");
		}
	}

	private static SecureResult routeSecure(GexpressConsoleCrypto.OpenedRequest request) {
		String method = request.method.toUpperCase(java.util.Locale.ROOT);
		try {
			if ("GET".equals(method) && "/v1/status".equals(request.path)) {
				return SecureResult.ok(statusJson());
			}
			if ("GET".equals(method) && "/v1/shop".equals(request.path)) {
				return SecureResult.ok(onServer(SkinShopConfig::json));
			}
			if ("PUT".equals(method) && "/v1/shop".equals(request.path)) {
				String accepted = onServer(() -> {
					SkinShopConfig.replaceJson(request.body == null ? "" : request.body, true);
					GexpressConfigSyncHandler.broadcastShopConfig(requireServer());
					return SkinShopConfig.json();
				});
				return SecureResult.ok(accepted);
			}
			if ("GET".equals(method) && "/v1/developer".equals(request.path)) {
				return SecureResult.ok(onServer(GexpressConfig::currentJson));
			}
			if ("PUT".equals(method) && "/v1/developer".equals(request.path)) {
				String accepted = onServer(() -> {
					GexpressConfig.replaceJson(request.body == null ? "" : request.body);
					RoleModifierTuningBridge.applyConfiguredMaxima();
					MinecraftServer server = requireServer();
					for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
						TagCommand.refreshPlayerListName(player);
					}
					GexpressConfigSyncHandler.broadcastConfig(server);
					return GexpressConfig.currentJson();
				});
				return SecureResult.ok(accepted);
			}
			if ("GET".equals(method) || "PUT".equals(method)) {
				return SecureResult.error(404, "Owner-console endpoint not found.");
			}
			return SecureResult.error(405, "Method not allowed.");
		} catch (IOException e) {
			return SecureResult.error(400, e.getMessage());
		} catch (Exception e) {
			MapSelect.LOGGER.warn("Encrypted owner-console operation failed.", e);
			return SecureResult.error(500, e.getMessage());
		}
	}

	private static String statusJson() throws Exception {
		return GSON.toJson(onServer(() -> {
			MinecraftServer server = requireServer();
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("ok", true);
			values.put("server", server.getServerMotd());
			values.put("players", server.getCurrentPlayerCount());
			values.put("maxPlayers", server.getMaxPlayerCount());
			values.put("dedicated", server.isDedicated());
			values.put("shopRotation", dev.mapselect.skin.SkinDailyShop.rotationId());
			values.put("directEncryption", true);
			return values;
		}));
	}

	private static boolean acceptNonce(String nonce, long nowSeconds) {
		synchronized (NONCE_LOCK) {
			Iterator<Map.Entry<String, Long>> iterator = RECENT_NONCES.entrySet().iterator();
			while (iterator.hasNext()) {
				if (iterator.next().getValue() < nowSeconds - NONCE_RETENTION_SECONDS) iterator.remove();
			}
			if (RECENT_NONCES.containsKey(nonce)) return false;
			RECENT_NONCES.put(nonce, nowSeconds);
			while (RECENT_NONCES.size() > MAX_RECENT_NONCES) {
				Iterator<String> oldest = RECENT_NONCES.keySet().iterator();
				if (!oldest.hasNext()) break;
				oldest.next();
				oldest.remove();
			}
			return true;
		}
	}

	private static void handleStatus(HttpExchange exchange) throws IOException {
		if (!authorize(exchange)) return;
		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			methodNotAllowed(exchange, "GET");
			return;
		}
		try {
			respondJson(exchange, 200, statusJson());
		} catch (Exception e) {
			respondError(exchange, 503, e.getMessage());
		}
	}

	private static void handleShop(HttpExchange exchange) throws IOException {
		if (!authorize(exchange)) return;
		String method = exchange.getRequestMethod();
		if ("GET".equalsIgnoreCase(method)) {
			try {
				respondJson(exchange, 200, onServer(SkinShopConfig::json));
			} catch (Exception e) {
				respondError(exchange, 503, e.getMessage());
			}
			return;
		}
		if (!"PUT".equalsIgnoreCase(method)) {
			methodNotAllowed(exchange, "GET, PUT");
			return;
		}
		String body = readBody(exchange);
		if (body == null) return;
		try {
			String accepted = onServer(() -> {
				SkinShopConfig.replaceJson(body, true);
				GexpressConfigSyncHandler.broadcastShopConfig(requireServer());
				return SkinShopConfig.json();
			});
			respondJson(exchange, 200, accepted);
		} catch (IOException e) {
			respondError(exchange, 400, e.getMessage());
		} catch (Exception e) {
			MapSelect.LOGGER.warn("Owner-console shop update failed.", e);
			respondError(exchange, 500, e.getMessage());
		}
	}

	private static void handleDeveloper(HttpExchange exchange) throws IOException {
		if (!authorize(exchange)) return;
		String method = exchange.getRequestMethod();
		if ("GET".equalsIgnoreCase(method)) {
			try {
				respondJson(exchange, 200, onServer(GexpressConfig::currentJson));
			} catch (Exception e) {
				respondError(exchange, 503, e.getMessage());
			}
			return;
		}
		if (!"PUT".equalsIgnoreCase(method)) {
			methodNotAllowed(exchange, "GET, PUT");
			return;
		}
		String body = readBody(exchange);
		if (body == null) return;
		try {
			String accepted = onServer(() -> {
				GexpressConfig.replaceJson(body);
				RoleModifierTuningBridge.applyConfiguredMaxima();
				MinecraftServer server = requireServer();
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					TagCommand.refreshPlayerListName(player);
				}
				GexpressConfigSyncHandler.broadcastConfig(server);
				return GexpressConfig.currentJson();
			});
			respondJson(exchange, 200, accepted);
		} catch (IOException e) {
			respondError(exchange, 400, e.getMessage());
		} catch (Exception e) {
			MapSelect.LOGGER.warn("Owner-console developer update failed.", e);
			respondError(exchange, 500, e.getMessage());
		}
	}

	private static boolean authorize(HttpExchange exchange) throws IOException {
		String supplied = exchange.getRequestHeaders().getFirst("Authorization");
		String expected = config == null ? "" : config.token;
		boolean valid = supplied != null && supplied.startsWith("Bearer ")
			&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				supplied.substring(7).getBytes(StandardCharsets.UTF_8));
		if (valid) return true;
		exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"GExpress Owner Console\"");
		respondError(exchange, 401, "The server profile token is missing or invalid.");
		return false;
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
		if (bytes.length > MAX_BODY_BYTES) {
			respondError(exchange, 413, "Request body is too large.");
			return null;
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static <T> T onServer(CheckedSupplier<T> operation) throws Exception {
		MinecraftServer server = requireServer();
		CompletableFuture<T> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				future.complete(operation.get());
			} catch (Throwable throwable) {
				future.completeExceptionally(throwable);
			}
		});
		try {
			return future.get(10, TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception) throw exception;
			throw e;
		}
	}

	private static MinecraftServer requireServer() {
		MinecraftServer server = minecraftServer;
		if (server == null) throw new IllegalStateException("The Minecraft server is not running.");
		return server;
	}

	private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
		exchange.getResponseHeaders().set("Allow", allow);
		respondError(exchange, 405, "Method not allowed.");
	}

	private static void respondError(HttpExchange exchange, int status, String message) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", false);
		body.put("error", message == null || message.isBlank() ? "Request failed." : message);
		respondJson(exchange, status, GSON.toJson(body));
	}

	private static void respondJson(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private static void respondSecure(HttpExchange exchange, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/vnd.gexpress.secure+json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		exchange.sendResponseHeaders(200, bytes.length);
		try (var output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private static BridgeConfig loadConfig() throws IOException {
		BridgeConfig loaded = null;
		if (Files.exists(CONFIG_PATH)) {
			loaded = GSON.fromJson(Files.readString(CONFIG_PATH), BridgeConfig.class);
		}
		if (loaded == null) loaded = new BridgeConfig();
		loaded.bindAddress = environment("GEXPRESS_CONSOLE_BIND", loaded.bindAddress);
		loaded.token = environment("GEXPRESS_CONSOLE_TOKEN", loaded.token);
		String port = System.getenv("GEXPRESS_CONSOLE_PORT");
		if (port != null && !port.isBlank()) {
			try {
				loaded.port = Integer.parseInt(port.strip());
			} catch (NumberFormatException ignored) {
				MapSelect.LOGGER.warn("Ignoring invalid GEXPRESS_CONSOLE_PORT value.");
			}
		}
		loaded.bindAddress = loaded.bindAddress == null || loaded.bindAddress.isBlank()
			? "127.0.0.1" : loaded.bindAddress.strip();
		loaded.port = Math.max(1024, Math.min(65535, loaded.port));
		if (loaded.token == null || loaded.token.length() < 24) {
			byte[] token = new byte[32];
			new SecureRandom().nextBytes(token);
			loaded.token = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
		}
		writeAtomically(CONFIG_PATH, GSON.toJson(loaded));
		return loaded;
	}

	private static String environment(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value.strip();
	}

	private static void writeAtomically(Path target, String json) throws IOException {
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, json);
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	@FunctionalInterface
	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}

	private static final class BridgeConfig {
		boolean enabled = true;
		String bindAddress = "127.0.0.1";
		int port = 8765;
		String token = "";
	}

	private static final class SecureResult {
		final int status;
		final String body;
		final String message;

		private SecureResult(int status, String body, String message) {
			this.status = status;
			this.body = body == null ? "" : body;
			this.message = message == null ? "" : message;
		}

		static SecureResult ok(String body) {
			return new SecureResult(200, body, "OK");
		}

		static SecureResult error(int status, String message) {
			String safeMessage = message == null || message.isBlank() ? "Request failed." : message;
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("ok", false);
			body.put("error", safeMessage);
			return new SecureResult(status, GSON.toJson(body), safeMessage);
		}
	}
}
