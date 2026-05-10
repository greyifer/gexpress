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

	public PlayerTagComponent(World world) {
		this.world = world;
	}

	public PlayerTag getTag(UUID uuid) {
		Set<PlayerTag> current = getPlayerTags(uuid);
		return current.stream().max((a, b) -> Integer.compare(a.priority(), b.priority())).orElse(null);
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
		if (uuid == null || tags.remove(uuid) == null) return false;
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tags.clear();
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
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
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
	}

	public static PlayerTag getTag(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		PlayerTagComponent c = KEY.getNullable(player.getWorld());
		return c == null ? null : c.getTag(player.getUuid());
	}
}
