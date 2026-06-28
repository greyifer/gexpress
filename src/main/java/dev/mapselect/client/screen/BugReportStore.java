package dev.mapselect.client.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mapselect.MapSelect;
import dev.mapselect.network.bugreport.BugReportActionPayload;
import dev.mapselect.network.bugreport.BugReportListPayload;
import dev.mapselect.network.bugreport.BugReportListRequestPayload;
import dev.mapselect.network.bugreport.BugReportSubmitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BugReportStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path STORE_PATH = Path.of(System.getProperty("user.home"), ".gexpress", "bug_reports.json");
	private static final String REPOSITORY = "greyifer/gexpress";
	private static final String ISSUE_MARKER = "<!-- gexpress-bug-report -->";
	private static final String ISSUE_LABEL = "bug-report";
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(6))
		.build();
	private static volatile List<GitHubIssue> githubIssues = List.of();
	private static volatile String githubStatus = "Not loaded.";
	private static volatile boolean githubRefreshing;
	private static volatile List<RemoteReport> remoteReports = List.of();
	private static volatile String remoteStatus = "Not loaded.";
	private static volatile String remoteFilter = "all";
	private static volatile boolean remoteRefreshing;
	private static volatile boolean canModerateRemoteReports;

	private BugReportStore() {}

	public static Path path() {
		return STORE_PATH;
	}

	public static String repository() {
		return REPOSITORY;
	}

	public static List<GitHubIssue> githubIssues() {
		return githubIssues;
	}

	public static String githubStatus() {
		return githubRefreshing ? "Refreshing..." : githubStatus;
	}

	public static boolean isGithubRefreshing() {
		return githubRefreshing;
	}

	public static List<RemoteReport> remoteReports() {
		return remoteReports;
	}

	public static String remoteStatus() {
		return remoteRefreshing ? "Refreshing..." : remoteStatus;
	}

	public static boolean isRemoteRefreshing() {
		return remoteRefreshing;
	}

	public static String remoteFilter() {
		return remoteFilter;
	}

	public static boolean canModerateRemoteReports() {
		return canModerateRemoteReports;
	}

	public static List<BugReport> load() {
		try {
			if (!Files.exists(STORE_PATH)) return new ArrayList<>();
			Store store = GSON.fromJson(Files.readString(STORE_PATH), Store.class);
			if (store == null || store.reports == null) return new ArrayList<>();
			return new ArrayList<>(store.reports);
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to load bug reports from {}", STORE_PATH, t);
			return new ArrayList<>();
		}
	}

	public static boolean add(Category category, String message) {
		String clean = message == null ? "" : message.trim();
		if (clean.isEmpty()) return false;
		MinecraftClient client = MinecraftClient.getInstance();
		String author = client.player == null ? "Unknown" : client.player.getGameProfile().getName();
		UUID authorId = client.player == null ? null : client.player.getUuid();
		List<BugReport> reports = load();
		reports.add(new BugReport(Instant.now().toString(), author, authorId,
			category == null ? Category.MISCELLANEOUS : category, clean));
		return save(reports);
	}

	public static Submission submitToGithub(Category category, String message) {
		String clean = message == null ? "" : message.trim();
		if (clean.isEmpty()) return null;
		boolean saved = add(category, clean);
		URI uri = openIssueUri(category, clean);
		String body = reportBody(category, clean);
		copyToClipboard("Open this GitHub draft:\n" + uri + "\n\nIf GitHub is unavailable, send this report to staff:\n\n" + body);
		boolean opened = open(uri);
		notifyPlayer(saved
			? "Bug report saved locally and copied to clipboard."
			: "Bug report copied, but local saving failed. Check the log.", saved ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GOLD);
		return new Submission(opened, uri.toString(), body, saved, STORE_PATH.toString());
	}

	public static Submission submitToDiscord(Category category, String message) {
		String clean = message == null ? "" : message.trim();
		if (clean.isEmpty()) return null;
		Category safeCategory = category == null ? Category.MISCELLANEOUS : category;
		boolean saved = add(safeCategory, clean);
		String body = reportBody(safeCategory, clean);
		boolean sent = false;
		if (ClientPlayNetworking.canSend(BugReportSubmitPayload.ID)) {
			ClientPlayNetworking.send(new BugReportSubmitPayload(safeCategory.apiValue(), clean));
			sent = true;
			remoteStatus = "Report sent. Refresh to see the newest Discord copy.";
		}
		if (!sent) copyToClipboard("Send this report to staff:\n\n" + body);
		notifyPlayer(sent
			? "Bug report sent to Discord."
			: "Discord reporting is unavailable here. Copied the report text.",
			saved ? (sent ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GOLD) : net.minecraft.util.Formatting.RED);
		return new Submission(sent, "", body, saved, STORE_PATH.toString());
	}

	public static void refreshRemoteReports(String status) {
		String safeStatus = normalizeStatus(status);
		remoteFilter = safeStatus;
		canModerateRemoteReports = false;
		if (!ClientPlayNetworking.canSend(BugReportListRequestPayload.ID)) {
			remoteRefreshing = false;
			remoteStatus = "Join a server with the G'Express bot bridge to load Discord reports.";
			return;
		}
		remoteRefreshing = true;
		remoteStatus = "Refreshing...";
		ClientPlayNetworking.send(new BugReportListRequestPayload(safeStatus));
	}

	public static void applyRemoteReports(BugReportListPayload payload) {
		if (payload == null) return;
		remoteFilter = normalizeStatus(payload.status());
		canModerateRemoteReports = payload.canModerate();
		List<RemoteReport> reports = new ArrayList<>();
		for (BugReportListPayload.Entry entry : payload.reports()) {
			reports.add(new RemoteReport(
				entry.id(),
				entry.status(),
				categoryLabel(entry.category()),
				entry.title(),
				entry.description(),
				entry.reporterName(),
				entry.serverName(),
				entry.modVersion(),
				entry.minecraftVersion(),
				entry.createdAt(),
				entry.updatedAt(),
				entry.discordUrl()
			));
		}
		remoteReports = List.copyOf(reports);
		remoteStatus = payload.statusMessage().isBlank()
			? "Loaded " + reports.size() + " Discord report(s)."
			: payload.statusMessage();
		remoteRefreshing = false;
	}

	public static void requestRemoteAction(RemoteReport report, String action, String statusFilter) {
		if (report == null || report.id().isBlank()) return;
		if (!canModerateRemoteReports) {
			notifyPlayer("You do not have permission to moderate bug reports.", net.minecraft.util.Formatting.RED);
			return;
		}
		String safeAction = action == null ? "" : action.trim().toLowerCase();
		if (!ClientPlayNetworking.canSend(BugReportActionPayload.ID)) {
			notifyPlayer("This server cannot update Discord reports from the game.", net.minecraft.util.Formatting.GOLD);
			return;
		}
		remoteStatus = "Updating " + report.id() + "...";
		ClientPlayNetworking.send(new BugReportActionPayload(report.id(), safeAction, normalizeStatus(statusFilter)));
	}

	public static void openRemoteReport(RemoteReport report) {
		if (report == null) return;
		if (report.discordUrl() != null && !report.discordUrl().isBlank()) {
			open(URI.create(report.discordUrl()));
			return;
		}
		copyToClipboard(report.id() + " - " + report.title());
		notifyPlayer("That report has no Discord URL yet, so its ID was copied.", net.minecraft.util.Formatting.GRAY);
	}

	public static Submission copyReportToClipboard(Category category, String message) {
		String clean = message == null ? "" : message.trim();
		if (clean.isEmpty()) return null;
		boolean saved = add(category, clean);
		String body = reportBody(category, clean);
		copyToClipboard("Send this report to staff:\n\n" + body);
		notifyPlayer(saved
			? "Bug report text copied."
			: "Bug report text copied. Local fallback failed.", saved ? net.minecraft.util.Formatting.BLUE : net.minecraft.util.Formatting.GOLD);
		return new Submission(false, "", body, saved, STORE_PATH.toString());
	}

	public static CompletableFuture<Boolean> refreshGithubIssues() {
		if (githubRefreshing) return CompletableFuture.completedFuture(false);
		githubRefreshing = true;
		githubStatus = "Refreshing...";
		return CompletableFuture.supplyAsync(() -> {
			try {
				List<GitHubIssue> issues = fetchGithubIssues();
				githubIssues = issues;
				githubStatus = "Loaded " + issues.size() + " GitHub report(s).";
				return true;
			} catch (Throwable t) {
				githubStatus = "Failed to load GitHub reports: " + t.getMessage();
				MapSelect.LOGGER.warn("Failed to fetch GitHub bug reports.", t);
				return false;
			} finally {
				githubRefreshing = false;
			}
		});
	}

	public static void openIssue(GitHubIssue issue) {
		if (issue == null || issue.url() == null || issue.url().isBlank()) return;
		open(URI.create(issue.url()));
	}

	public static void prepareFixed(GitHubIssue issue) {
		copyAndOpen(issue, "Fixed this one. Closing as fixed.");
	}

	public static void prepareReopen(GitHubIssue issue) {
		copyAndOpen(issue, "Reopening this because it still happens.");
	}

	public static void prepareDeleteOrClose(GitHubIssue issue) {
		copyAndOpen(issue, "Closing this report as invalid/duplicate.");
	}

	private static void copyAndOpen(GitHubIssue issue, String clipboard) {
		copyToClipboard(clipboard);
		openIssue(issue);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(net.minecraft.text.Text.literal(
				"Opened GitHub issue and copied the action text.").formatted(net.minecraft.util.Formatting.GRAY), true);
		}
	}

	private static URI openIssueUri(Category category, String message) {
		Category safeCategory = category == null ? Category.MISCELLANEOUS : category;
		String author = authorName();
		String title = "[Bug Report] " + safeCategory + " - " + author;
		String body = reportBody(safeCategory, message);
		String query = "title=" + enc(title)
			+ "&body=" + enc(body)
			+ "&labels=" + enc(ISSUE_LABEL);
		String full = "https://github.com/" + REPOSITORY + "/issues/new?" + query;
		if (full.length() <= 7000) return URI.create(full);
		return URI.create("https://github.com/" + REPOSITORY + "/issues/new?title="
			+ enc(title) + "&labels=" + enc(ISSUE_LABEL));
	}

	public static String reportBody(Category category, String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		UUID authorId = client.player == null ? null : client.player.getUuid();
		Category safeCategory = category == null ? Category.MISCELLANEOUS : category;
		return ISSUE_MARKER + "\n"
			+ "Reporter: " + authorName() + "\n"
			+ "UUID: " + (authorId == null ? "Unknown" : authorId) + "\n"
			+ "Category: " + safeCategory + "\n\n"
			+ "Bug:\n" + (message == null ? "" : message.trim()) + "\n";
	}

	private static String authorName() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null || client.player == null ? "Unknown" : client.player.getGameProfile().getName();
	}

	private static String normalizeStatus(String raw) {
		String status = raw == null ? "" : raw.trim().toLowerCase();
		return switch (status) {
			case "open", "fixed", "duplicate", "deleted", "all" -> status;
			default -> "all";
		};
	}

	private static String categoryLabel(String raw) {
		String value = raw == null ? "" : raw.trim();
		return switch (value.toLowerCase()) {
			case "role/modifier", "role_modifier", "role" -> "Role/Modifier";
			case "map" -> "Map";
			case "miscellaneous", "misc" -> "Miscellaneous";
			default -> value.isBlank() ? "Miscellaneous" : value;
		};
	}

	private static List<GitHubIssue> fetchGithubIssues() throws Exception {
		URI uri = URI.create("https://api.github.com/repos/" + REPOSITORY + "/issues?state=all&per_page=50");
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "GExpress-Mod")
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("GitHub returned HTTP " + response.statusCode());
		}
		JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
		List<GitHubIssue> out = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) continue;
			JsonObject obj = element.getAsJsonObject();
			if (obj.has("pull_request")) continue;
			String title = string(obj, "title");
			String body = string(obj, "body");
			List<String> labels = labels(obj);
			if (!isBugReportIssue(title, body, labels)) continue;
			out.add(new GitHubIssue(
				obj.has("number") ? obj.get("number").getAsInt() : 0,
				title,
				body,
				string(obj, "state"),
				string(obj, "html_url"),
				string(obj, "updated_at"),
				labels
			));
		}
		return out;
	}

	private static boolean isBugReportIssue(String title, String body, List<String> labels) {
		if (body != null && body.contains(ISSUE_MARKER)) return true;
		if (title != null && title.startsWith("[Bug Report]")) return true;
		for (String label : labels) {
			if (ISSUE_LABEL.equalsIgnoreCase(label)) return true;
		}
		return false;
	}

	private static List<String> labels(JsonObject obj) {
		List<String> out = new ArrayList<>();
		if (!obj.has("labels") || !obj.get("labels").isJsonArray()) return out;
		for (JsonElement element : obj.getAsJsonArray("labels")) {
			if (!element.isJsonObject()) continue;
			String name = string(element.getAsJsonObject(), "name");
			if (!name.isBlank()) out.add(name);
		}
		return out;
	}

	private static String string(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
		return obj.get(key).getAsString();
	}

	private static boolean open(URI uri) {
		try {
			Util.getOperatingSystem().open(uri);
			return true;
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("Failed to open {}", uri, t);
			return false;
		}
	}

	private static void copyToClipboard(String value) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.keyboard != null) client.keyboard.setClipboard(value == null ? "" : value);
	}

	private static void notifyPlayer(String message, net.minecraft.util.Formatting color) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(net.minecraft.text.Text.literal(message).formatted(color), true);
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static boolean save(List<BugReport> reports) {
		try {
			Files.createDirectories(STORE_PATH.getParent());
			Files.writeString(STORE_PATH, GSON.toJson(new Store(reports == null ? List.of() : reports)));
			return true;
		} catch (IOException e) {
			MapSelect.LOGGER.warn("Failed to save bug reports to {}", STORE_PATH, e);
			return false;
		}
	}

	public enum Category {
		ROLE_MODIFIER("Role/Modifier", "role/modifier"),
		MAP("Map", "map"),
		MISCELLANEOUS("Miscellaneous", "miscellaneous");

		private final String label;
		private final String apiValue;

		Category(String label, String apiValue) {
			this.label = label;
			this.apiValue = apiValue;
		}

		public String apiValue() {
			return apiValue;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public record BugReport(String createdAt, String author, UUID authorId, Category category, String message) {}

	public record RemoteReport(String id, String status, String category, String title, String description,
			String reporterName, String serverName, String modVersion, String minecraftVersion,
			String createdAt, String updatedAt, String discordUrl) {}

	public record GitHubIssue(int number, String title, String body, String state, String url,
			String updatedAt, List<String> labels) {}

	public record Submission(boolean opened, String url, String body, boolean saved, String localPath) {}

	private record Store(List<BugReport> reports) {}
}
