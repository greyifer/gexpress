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

	public static Path file(MinecraftServer server, String name) throws IOException {
		if (!isValidName(name)) throw new IOException("Invalid train preset name.");
		return dir(server).resolve(name + ".json");
	}

	public static void save(MinecraftServer server, String name, TrainPreset preset) throws IOException {
		checkServer(server);
		Path f = file(server, name);
		boolean isNew = !Files.exists(f);
		if (preset == null) throw new IOException("Train preset is null.");
		preset.normalize();
		String json = GSON.toJson(preset);
		writeAtomically(f, json);
		cachedJson.put(name, json);
		if (isNew) cachedList = null;
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
		boolean deleted = Files.deleteIfExists(file(server, name));
		if (deleted) {
			cachedJson.remove(name);
			cachedList = null;
		}
		return deleted;
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
}
