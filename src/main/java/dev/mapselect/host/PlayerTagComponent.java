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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTagComponent implements AutoSyncedComponent {
	public static final ComponentKey<PlayerTagComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "player_tags"),
		PlayerTagComponent.class
	);

	private final World world;
	private final Map<UUID, PlayerTag> tags = new LinkedHashMap<>();

	public PlayerTagComponent(World world) {
		this.world = world;
	}

	public PlayerTag getTag(UUID uuid) {
		return uuid == null ? null : tags.get(uuid);
	}

	public Map<UUID, PlayerTag> getTags() {
		return Collections.unmodifiableMap(tags);
	}

	public boolean setTag(UUID uuid, PlayerTag tag) {
		if (uuid == null) return false;
		if (tag == null || tag == PlayerTag.PASSENGER || tag == PlayerTag.HOST
				|| tag == PlayerTag.TRUSTED || tag == PlayerTag.DEV) {
			return clearTag(uuid);
		}
		PlayerTag old = tags.put(uuid, tag);
		if (old == tag) return false;
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
						&& playerTag != PlayerTag.TRUSTED) {
					tags.put(uuid, playerTag);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, PlayerTag> entry : tags.entrySet()) {
			NbtCompound out = new NbtCompound();
			out.putString("player", entry.getKey().toString());
			out.putString("tag", entry.getValue().id());
			list.add(out);
		}
		tag.put("tags", list);
	}

	public static PlayerTag getTag(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		PlayerTagComponent c = KEY.getNullable(player.getWorld());
		return c == null ? null : c.getTag(player.getUuid());
	}
}
