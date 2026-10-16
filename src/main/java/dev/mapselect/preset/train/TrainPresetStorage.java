package dev.mapselect.preset.train;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TrainPresetStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");
	private static final Pattern BACKUP_ID = Pattern.compile("[0-9]{8}-[0-9]{6}(?:-[0-9]{1,3})?");
	private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static WeakReference<MinecraftServer> cachedServerRef = new WeakReference<>(null);
	private static List<String> cachedList;
	private static final Map<String, String> cachedJson = new HashMap<>();

	private static void checkServer(MinecraftServer server) {
		if (cachedServerRef.get() != server) {
			cachedServerRef = new WeakReference<>(server);
			cachedList = null;
			cachedJson.clear();
		}
	}

	public static boolean isValidName(String name) {
		return name != null && VALID_NAME.matcher(name).matches();
	}

	public static Path dir(MinecraftServer server) throws IOException {
		Path d = server.getSavePath(WorldSavePath.ROOT).resolve("gexpress").resolve("trains");
		Files.createDirectories(d);
		return d;
	}

	public static Path backupDir(MinecraftServer server) throws IOException {
		Path d = server.getSavePath(WorldSavePath.ROOT).resolve("gexpress").resolve("backups").resolve("trains");
		Files.createDirectories(d);
		return d;
	}

	public static Path file(MinecraftServer server, String name) throws IOException {
		if (!isValidName(name)) throw new IOException("Invalid train preset name.");
		return dir(server).resolve(name + ".json");
	}

	public static void save(MinecraftServer server, String name, TrainPreset preset) throws IOException {
		checkServer(server);
		Path f = file(server, name);
		if (preset == null) throw new IOException("Train preset is null.");
		preset.normalize();
		String json = GSON.toJson(preset);
		backupExisting(server, f, name);
		writeAtomically(f, json);
		cachedJson.put(name, json);
		cachedList = null;
	}

	public static TrainPreset load(MinecraftServer server, String name) throws IOException {
		checkServer(server);
		String json = cachedJson.get(name);
		if (json == null) {
			Path f = file(server, name);
			if (!Files.exists(f)) return null;
			json = Files.readString(f);
			cachedJson.put(name, json);
		}
		try {
			TrainPreset preset = GSON.fromJson(json, TrainPreset.class);
			if (preset != null) preset.normalize();
			return preset;
		} catch (JsonSyntaxException e) {
			cachedJson.remove(name);
			throw new IOException("Malformed train preset JSON: " + e.getMessage(), e);
		}
	}

	public static boolean delete(MinecraftServer server, String name) throws IOException {
		checkServer(server);
		Path f = file(server, name);
		backupExisting(server, f, name);
		boolean deleted = Files.deleteIfExists(f);
		if (deleted) {
			cachedJson.remove(name);
			cachedList = null;
		}
		return deleted;
	}

	public static void rename(MinecraftServer server, String oldName, String newName) throws IOException {
		checkServer(server);
		Path oldFile = file(server, oldName);
		Path newFile = file(server, newName);
		if (!Files.exists(oldFile)) {
			throw new IOException("Train preset '" + oldName + "' does not exist.");
		}
		if (Files.exists(newFile)) {
			throw new IOException("Train preset '" + newName + "' already exists.");
		}
		backupExisting(server, oldFile, oldName);
		Files.move(oldFile, newFile);
		String carried = cachedJson.remove(oldName);
		if (carried != null) cachedJson.put(newName, carried);
		cachedList = null;
	}

	public static void duplicate(MinecraftServer server, String sourceName, String targetName) throws IOException {
		checkServer(server);
		if (!isValidName(sourceName) || !isValidName(targetName)) {
			throw new IOException("Invalid train preset name.");
		}
		if (sourceName.equals(targetName)) {
			throw new IOException("Source and target train preset names must be different.");
		}
		Path sourceFile = file(server, sourceName);
		Path targetFile = file(server, targetName);
		if (!Files.exists(sourceFile)) {
			throw new IOException("Train preset '" + sourceName + "' does not exist.");
		}
		if (Files.exists(targetFile)) {
			throw new IOException("Train preset '" + targetName + "' already exists.");
		}
		String json = Files.readString(sourceFile);
		try {
			TrainPreset preset = GSON.fromJson(json, TrainPreset.class);
			if (preset == null) throw new JsonSyntaxException("Train preset is null.");
			preset.normalize();
			json = GSON.toJson(preset);
		} catch (JsonSyntaxException e) {
			throw new IOException("Malformed train preset JSON: " + e.getMessage(), e);
		}
		writeAtomically(targetFile, json);
		cachedJson.put(targetName, json);
		cachedList = null;
	}

	public static List<String> listBackups(MinecraftServer server, String name) throws IOException {
		if (!isValidName(name)) throw new IOException("Invalid train preset name.");
		Path d = backupDir(server);
		List<String> backups = new ArrayList<>();
		String prefix = name + ".";
		try (Stream<Path> stream = Files.list(d)) {
			stream.filter(Files::isRegularFile)
				.map(p -> p.getFileName().toString())
				.filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
				.map(n -> n.substring(prefix.length(), n.length() - 5))
				.filter(id -> BACKUP_ID.matcher(id).matches())
				.sorted(Comparator.reverseOrder())
				.forEach(backups::add);
		}
		return List.copyOf(backups);
	}

	public static void restoreBackup(MinecraftServer server, String name, String backupId) throws IOException {
		checkServer(server);
		if (!isValidName(name)) throw new IOException("Invalid train preset name.");
		if (backupId == null || !BACKUP_ID.matcher(backupId).matches()) {
			throw new IOException("Invalid backup id.");
		}
		Path backup = backupDir(server).resolve(name + "." + backupId + ".json");
		if (!Files.isRegularFile(backup)) {
			throw new IOException("Backup '" + backupId + "' for train preset '" + name + "' does not exist.");
		}
		String json = Files.readString(backup);
		try {
			TrainPreset preset = GSON.fromJson(json, TrainPreset.class);
			if (preset == null) throw new JsonSyntaxException("Train preset is null.");
			preset.normalize();
			json = GSON.toJson(preset);
		} catch (JsonSyntaxException e) {
			throw new IOException("Malformed train backup JSON: " + e.getMessage(), e);
		}
		Path target = file(server, name);
		backupExisting(server, target, name);
		writeAtomically(target, json);
		cachedJson.put(name, json);
		cachedList = null;
	}

	public static boolean exists(MinecraftServer server, String name) throws IOException {
		checkServer(server);
		if (cachedList != null) return cachedList.contains(name);
		if (cachedJson.containsKey(name)) return true;
		return Files.exists(file(server, name));
	}

	public static List<String> list(MinecraftServer server) throws IOException {
		checkServer(server);
		if (cachedList != null) return cachedList;
		Path d = dir(server);
		List<String> names = new ArrayList<>();
		try (Stream<Path> stream = Files.list(d)) {
			stream.filter(Files::isRegularFile)
				.map(p -> p.getFileName().toString())
				.filter(n -> n.endsWith(".json"))
				.map(n -> n.substring(0, n.length() - 5))
				.filter(TrainPresetStorage::isValidName)
				.sorted(Comparator.naturalOrder())
				.forEach(names::add);
		}
		cachedList = Collections.unmodifiableList(names);
		return cachedList;
	}

	private static void writeAtomically(Path target, String json) throws IOException {
		Files.createDirectories(target.getParent());
		Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(tmp, json);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static void backupExisting(MinecraftServer server, Path source, String name) throws IOException {
		if (!Files.isRegularFile(source)) return;
		Path backup = nextBackupPath(backupDir(server), name);
		Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
	}

	private static Path nextBackupPath(Path backupDir, String name) throws IOException {
		String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
		for (int attempt = 0; attempt < 1000; attempt++) {
			String suffix = attempt == 0 ? "" : "-" + attempt;
			Path candidate = backupDir.resolve(name + "." + timestamp + suffix + ".json");
			if (!Files.exists(candidate)) return candidate;
		}
		throw new IOException("Could not allocate backup filename for train preset '" + name + "'.");
	}
}
