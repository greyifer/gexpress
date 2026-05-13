package dev.mapselect.host;

import dev.mapselect.MapSelect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerTagComponent implements AutoSyncedComponent {
	public static final ComponentKey<PlayerTagComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "player_tags"),
		PlayerTagComponent.class
	);

	private final World world;
	private final Map<UUID, LinkedHashSet<PlayerTag>> tags = new LinkedHashMap<>();
	private final Map<PlayerTag, BuiltinTagSettings> builtinTagSettings = new LinkedHashMap<>();
	private final Map<String, CustomTag> customTags = new LinkedHashMap<>();
	private final Map<UUID, LinkedHashSet<String>> customTagAssignments = new LinkedHashMap<>();

	public PlayerTagComponent(World world) {
		this.world = world;
	}

	public PlayerTag getTag(UUID uuid) {
		Set<PlayerTag> current = getPlayerTags(uuid);
		return current.stream().max((a, b) -> Integer.compare(priority(a), priority(b))).orElse(null);
	}

	public Map<UUID, PlayerTag> getTags() {
		Map<UUID, PlayerTag> out = new LinkedHashMap<>();
		for (UUID uuid : tags.keySet()) out.put(uuid, getTag(uuid));
		return Collections.unmodifiableMap(out);
	}

	public Set<PlayerTag> getPlayerTags(UUID uuid) {
		if (uuid == null) return Set.of();
		LinkedHashSet<PlayerTag> current = tags.get(uuid);
		return current == null ? Set.of() : Collections.unmodifiableSet(current);
	}

	public boolean setTag(UUID uuid, PlayerTag tag) {
		if (uuid == null) return false;
		if (tag == null || tag == PlayerTag.PASSENGER || tag == PlayerTag.HOST
				|| tag == PlayerTag.TRUSTED || tag == PlayerTag.DEV) {
			return clearTag(uuid);
		}
		LinkedHashSet<PlayerTag> next = new LinkedHashSet<>();
		next.add(tag);
		LinkedHashSet<PlayerTag> old = tags.put(uuid, next);
		if (old != null && old.size() == 1 && old.contains(tag)) return false;
		KEY.sync(world);
		return true;
	}

	public boolean addTag(UUID uuid, PlayerTag tag) {
		if (uuid == null || tag == null || tag == PlayerTag.PASSENGER
				|| tag == PlayerTag.HOST || tag == PlayerTag.TRUSTED || tag == PlayerTag.DEV) {
			return false;
		}
		boolean changed = tags.computeIfAbsent(uuid, id -> new LinkedHashSet<>()).add(tag);
		if (!changed) return false;
		KEY.sync(world);
		return true;
	}

	public boolean removeTag(UUID uuid, PlayerTag tag) {
		if (uuid == null || tag == null) return false;
		LinkedHashSet<PlayerTag> current = tags.get(uuid);
		if (current == null || !current.remove(tag)) return false;
		if (current.isEmpty()) tags.remove(uuid);
		KEY.sync(world);
		return true;
	}

	public boolean clearTag(UUID uuid) {
		if (uuid == null) return false;
		boolean changed = tags.remove(uuid) != null;
		changed |= customTagAssignments.remove(uuid) != null;
		if (!changed) return false;
		KEY.sync(world);
		return true;
	}

	public Map<String, CustomTag> getCustomTags() {
		return Collections.unmodifiableMap(customTags);
	}

	public BuiltinTagSettings getBuiltinTagSettings(PlayerTag tag) {
		if (tag == null) return null;
		return builtinTagSettings.getOrDefault(tag, BuiltinTagSettings.from(tag));
	}

	public int color(PlayerTag tag) {
		BuiltinTagSettings settings = getBuiltinTagSettings(tag);
		return settings == null ? 0xFFFFFF : settings.color();
	}

	public int priority(PlayerTag tag) {
		BuiltinTagSettings settings = getBuiltinTagSettings(tag);
		return settings == null ? 0 : settings.priority();
	}

	public Set<String> permissions(PlayerTag tag) {
		BuiltinTagSettings settings = getBuiltinTagSettings(tag);
		return settings == null ? Set.of() : settings.permissions();
	}

	public boolean setBuiltinTagColor(String id, int color) {
		PlayerTag tag = PlayerTag.byId(id);
		if (tag == null) return false;
		BuiltinTagSettings current = getBuiltinTagSettings(tag);
		return putBuiltinTagSettings(tag, current.withColor(color & 0xFFFFFF));
	}

	public boolean setBuiltinTagPriority(String id, int priority) {
		PlayerTag tag = PlayerTag.byId(id);
		if (tag == null) return false;
		BuiltinTagSettings current = getBuiltinTagSettings(tag);
		return putBuiltinTagSettings(tag, current.withPriority(priority));
	}

	public boolean setBuiltinTagPermission(String id, String permission, boolean enabled) {
		PlayerTag tag = PlayerTag.byId(id);
		String key = normalizeCustomId(permission);
		if (tag == null || key == null) return false;
		BuiltinTagSettings current = getBuiltinTagSettings(tag);
		LinkedHashSet<String> permissions = new LinkedHashSet<>(current.permissions());
		boolean changed = enabled ? permissions.add(key) : permissions.remove(key);
		if (!changed) return false;
		return putBuiltinTagSettings(tag, current.withPermissions(permissions));
	}

	public boolean resetBuiltinTag(String id) {
		PlayerTag tag = PlayerTag.byId(id);
		if (tag == null || builtinTagSettings.remove(tag) == null) return false;
		KEY.sync(world);
		return true;
	}

	private boolean putBuiltinTagSettings(PlayerTag tag, BuiltinTagSettings next) {
		BuiltinTagSettings defaults = BuiltinTagSettings.from(tag);
		BuiltinTagSettings previous;
		if (next.equals(defaults)) {
			previous = builtinTagSettings.remove(tag);
			if (previous == null) return false;
		} else {
			previous = builtinTagSettings.put(tag, next);
			if (next.equals(previous)) return false;
		}
		KEY.sync(world);
		return true;
	}

	public CustomTag getCustomTag(String id) {
		return customTags.get(normalizeCustomId(id));
	}

	public Set<String> getPlayerCustomTags(UUID uuid) {
		if (uuid == null) return Set.of();
		LinkedHashSet<String> current = customTagAssignments.get(uuid);
		return current == null ? Set.of() : Collections.unmodifiableSet(current);
	}

	public boolean hasCustomPermission(UUID uuid, String permission) {
		return hasPermission(uuid, Set.of(), permission);
	}

	public boolean hasPermission(UUID uuid, Iterable<PlayerTag> builtinTags, String permission) {
		String key = normalizeCustomId(permission);
		if (key == null) return false;
		if (builtinTags != null) {
			for (PlayerTag tag : builtinTags) {
				if (tag != null && permissions(tag).contains(key)) return true;
			}
		}
		for (String tagId : getPlayerCustomTags(uuid)) {
			CustomTag tag = customTags.get(tagId);
			if (tag != null && tag.permissions().contains(key)) return true;
		}
		return false;
	}

	public boolean defineCustomTag(String id, String displayName, int color, int priority) {
		String normalized = normalizeCustomId(id);
		if (normalized == null || PlayerTag.byId(normalized) != null) return false;
		String name = displayName == null || displayName.isBlank() ? normalized : displayName.trim();
		CustomTag next = new CustomTag(normalized, name, color & 0xFFFFFF, priority, Set.of());
		CustomTag previous = customTags.put(normalized, next);
		if (next.equals(previous)) return false;
		KEY.sync(world);
		return true;
	}

	public boolean removeCustomTag(String id) {
		String normalized = normalizeCustomId(id);
		if (normalized == null || customTags.remove(normalized) == null) return false;
		for (LinkedHashSet<String> assigned : customTagAssignments.values()) {
			assigned.remove(normalized);
		}
		customTagAssignments.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		KEY.sync(world);
		return true;
	}

	public boolean setCustomTagColor(String id, int color) {
		CustomTag tag = getCustomTag(id);
		if (tag == null) return false;
		CustomTag next = tag.withColor(color & 0xFFFFFF);
		if (next.equals(tag)) return false;
		customTags.put(tag.id(), next);
		KEY.sync(world);
		return true;
	}

	public boolean setCustomTagName(String id, String displayName) {
		CustomTag tag = getCustomTag(id);
		if (tag == null || displayName == null || displayName.isBlank()) return false;
		CustomTag next = tag.withDisplayName(displayName.trim());
		if (next.equals(tag)) return false;
		customTags.put(tag.id(), next);
		KEY.sync(world);
		return true;
	}

	public boolean setCustomTagPriority(String id, int priority) {
		CustomTag tag = getCustomTag(id);
		if (tag == null) return false;
		CustomTag next = tag.withPriority(priority);
		if (next.equals(tag)) return false;
		customTags.put(tag.id(), next);
		KEY.sync(world);
		return true;
	}

	public boolean setCustomTagPermission(String id, String permission, boolean enabled) {
		CustomTag tag = getCustomTag(id);
		String key = normalizeCustomId(permission);
		if (tag == null || key == null) return false;
		LinkedHashSet<String> permissions = new LinkedHashSet<>(tag.permissions());
		boolean changed = enabled ? permissions.add(key) : permissions.remove(key);
		if (!changed) return false;
		customTags.put(tag.id(), tag.withPermissions(permissions));
		KEY.sync(world);
		return true;
	}

	public boolean addCustomTag(UUID uuid, String id) {
		String normalized = normalizeCustomId(id);
		if (uuid == null || normalized == null || !customTags.containsKey(normalized)) return false;
		boolean changed = customTagAssignments.computeIfAbsent(uuid, key -> new LinkedHashSet<>()).add(normalized);
		if (!changed) return false;
		KEY.sync(world);
		return true;
	}

	public boolean removeCustomTag(UUID uuid, String id) {
		String normalized = normalizeCustomId(id);
		if (uuid == null || normalized == null) return false;
		LinkedHashSet<String> current = customTagAssignments.get(uuid);
		if (current == null || !current.remove(normalized)) return false;
		if (current.isEmpty()) customTagAssignments.remove(uuid);
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tags.clear();
		builtinTagSettings.clear();
		customTags.clear();
		customTagAssignments.clear();
		NbtList builtinDefinitions = tag.getList("builtinTagSettings", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < builtinDefinitions.size(); i++) {
			NbtCompound entry = builtinDefinitions.getCompound(i);
			PlayerTag playerTag = PlayerTag.byId(entry.getString("id"));
			if (playerTag == null) continue;
			int color = entry.contains("color") ? entry.getInt("color") : playerTag.color();
			int priority = entry.contains("priority") ? entry.getInt("priority") : playerTag.priority();
			LinkedHashSet<String> permissions = new LinkedHashSet<>(defaultPermissions(playerTag));
			NbtList permissionList = entry.getList("permissions", NbtElement.STRING_TYPE);
			if (entry.contains("permissions")) {
				permissions.clear();
				for (int j = 0; j < permissionList.size(); j++) {
					String permission = normalizeCustomId(permissionList.getString(j));
					if (permission != null) permissions.add(permission);
				}
			}
			BuiltinTagSettings settings = new BuiltinTagSettings(color, priority, permissions);
			if (!settings.equals(BuiltinTagSettings.from(playerTag))) {
				builtinTagSettings.put(playerTag, settings);
			}
		}
		NbtList customDefinitions = tag.getList("customTags", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < customDefinitions.size(); i++) {
			NbtCompound entry = customDefinitions.getCompound(i);
			String id = normalizeCustomId(entry.getString("id"));
			if (id == null || PlayerTag.byId(id) != null) continue;
			LinkedHashSet<String> permissions = new LinkedHashSet<>();
			NbtList permissionList = entry.getList("permissions", NbtElement.STRING_TYPE);
			for (int j = 0; j < permissionList.size(); j++) {
				String permission = normalizeCustomId(permissionList.getString(j));
				if (permission != null) permissions.add(permission);
			}
			customTags.put(id, new CustomTag(id, entry.getString("name"),
				entry.getInt("color") & 0xFFFFFF, entry.getInt("priority"), permissions));
		}
		NbtList list = tag.getList("tags", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			try {
				UUID uuid = UUID.fromString(entry.getString("player"));
				PlayerTag playerTag = PlayerTag.byId(entry.getString("tag"));
				if (playerTag != null && playerTag.assignable()
						&& playerTag != PlayerTag.PASSENGER
						&& playerTag != PlayerTag.HOST
						&& playerTag != PlayerTag.TRUSTED
						&& playerTag != PlayerTag.DEV) {
					tags.computeIfAbsent(uuid, id -> new LinkedHashSet<>()).add(playerTag);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
		NbtList customAssignments = tag.getList("customAssignments", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < customAssignments.size(); i++) {
			NbtCompound entry = customAssignments.getCompound(i);
			try {
				UUID uuid = UUID.fromString(entry.getString("player"));
				String id = normalizeCustomId(entry.getString("tag"));
				if (id != null && customTags.containsKey(id)) {
					customTagAssignments.computeIfAbsent(uuid, key -> new LinkedHashSet<>()).add(id);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList builtinDefinitions = new NbtList();
		for (Map.Entry<PlayerTag, BuiltinTagSettings> entry : builtinTagSettings.entrySet()) {
			BuiltinTagSettings defaults = BuiltinTagSettings.from(entry.getKey());
			if (entry.getValue().equals(defaults)) continue;
			NbtCompound out = new NbtCompound();
			out.putString("id", entry.getKey().id());
			out.putInt("color", entry.getValue().color());
			out.putInt("priority", entry.getValue().priority());
			NbtList permissions = new NbtList();
			for (String permission : entry.getValue().permissions()) {
				permissions.add(net.minecraft.nbt.NbtString.of(permission));
			}
			out.put("permissions", permissions);
			builtinDefinitions.add(out);
		}
		tag.put("builtinTagSettings", builtinDefinitions);

		NbtList customDefinitions = new NbtList();
		for (CustomTag customTag : customTags.values()) {
			NbtCompound out = new NbtCompound();
			out.putString("id", customTag.id());
			out.putString("name", customTag.displayName());
			out.putInt("color", customTag.color());
			out.putInt("priority", customTag.priority());
			NbtList permissions = new NbtList();
			for (String permission : customTag.permissions()) {
				permissions.add(net.minecraft.nbt.NbtString.of(permission));
			}
			out.put("permissions", permissions);
			customDefinitions.add(out);
		}
		tag.put("customTags", customDefinitions);

		NbtList list = new NbtList();
		for (Map.Entry<UUID, LinkedHashSet<PlayerTag>> entry : tags.entrySet()) {
			for (PlayerTag playerTag : entry.getValue()) {
				NbtCompound out = new NbtCompound();
				out.putString("player", entry.getKey().toString());
				out.putString("tag", playerTag.id());
				list.add(out);
			}
		}
		tag.put("tags", list);

		NbtList customAssignments = new NbtList();
		for (Map.Entry<UUID, LinkedHashSet<String>> entry : customTagAssignments.entrySet()) {
			for (String customTag : entry.getValue()) {
				if (!customTags.containsKey(customTag)) continue;
				NbtCompound out = new NbtCompound();
				out.putString("player", entry.getKey().toString());
				out.putString("tag", customTag);
				customAssignments.add(out);
			}
		}
		tag.put("customAssignments", customAssignments);
	}

	public static PlayerTag getTag(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		PlayerTagComponent c = KEY.getNullable(player.getWorld());
		return c == null ? null : c.getTag(player.getUuid());
	}

	public static String normalizeCustomId(String raw) {
		if (raw == null) return null;
		String cleaned = raw.trim().toLowerCase(Locale.ROOT);
		if (cleaned.isBlank() || cleaned.length() > 24) return null;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_') return null;
		}
		return cleaned;
	}

	private static Set<String> defaultPermissions(PlayerTag tag) {
		if (tag == null) return Set.of();
		return switch (tag) {
			case OWNER -> Set.of("owner", "admin", "host", "setup", "builder", "staff", "trusted");
			case DEV -> Set.of("admin", "host", "setup", "builder", "staff", "trusted");
			case STAFF -> Set.of("admin", "host", "setup", "builder", "staff");
			case HOST -> Set.of("host", "setup", "builder");
			case TRUSTED -> Set.of("trusted");
			default -> Set.of();
		};
	}

	public record BuiltinTagSettings(int color, int priority, Set<String> permissions) {
		public BuiltinTagSettings {
			color &= 0xFFFFFF;
			permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
		}

		public static BuiltinTagSettings from(PlayerTag tag) {
			return new BuiltinTagSettings(tag.color(), tag.priority(), defaultPermissions(tag));
		}

		public BuiltinTagSettings withColor(int color) {
			return new BuiltinTagSettings(color, priority, permissions);
		}

		public BuiltinTagSettings withPriority(int priority) {
			return new BuiltinTagSettings(color, priority, permissions);
		}

		public BuiltinTagSettings withPermissions(Set<String> permissions) {
			return new BuiltinTagSettings(color, priority, permissions);
		}
	}

	public record CustomTag(String id, String displayName, int color, int priority, Set<String> permissions) {
		public CustomTag {
			id = normalizeCustomId(id);
			displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
			color &= 0xFFFFFF;
			permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
		}

		public CustomTag withDisplayName(String displayName) {
			return new CustomTag(id, displayName, color, priority, permissions);
		}

		public CustomTag withColor(int color) {
			return new CustomTag(id, displayName, color, priority, permissions);
		}

		public CustomTag withPriority(int priority) {
			return new CustomTag(id, displayName, color, priority, permissions);
		}

		public CustomTag withPermissions(Set<String> permissions) {
			return new CustomTag(id, displayName, color, priority, permissions);
		}
	}
}
