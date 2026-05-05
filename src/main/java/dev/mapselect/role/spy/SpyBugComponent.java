package dev.mapselect.role.spy;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SpyBugComponent implements AutoSyncedComponent {
	public static final ComponentKey<SpyBugComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "spy_bug"),
		SpyBugComponent.class
	);

	private final World world;
	private final Map<UUID, Long> targets = new LinkedHashMap<>();

	public SpyBugComponent(World world) {
		this.world = world;
	}

	public boolean hasBug(UUID uuid) {
		return uuid != null && targets.containsKey(uuid);
	}

	public void replaceAll(Map<UUID, Long> activeTargets) {
		Map<UUID, Long> next = new LinkedHashMap<>();
		if (activeTargets != null) {
			for (Map.Entry<UUID, Long> entry : activeTargets.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > world.getTime()) {
					next.put(entry.getKey(), entry.getValue());
				}
			}
		}
		if (targets.equals(next)) return;
		targets.clear();
		targets.putAll(next);
		KEY.sync(world);
	}

	public void clearAll() {
		if (targets.isEmpty()) return;
		targets.clear();
		KEY.sync(world);
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		targets.clear();
		NbtList list = tag.getList("targets", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			String uuidString = entry.getString("uuid");
			if (uuidString == null || uuidString.isEmpty()) continue;
			try {
				UUID uuid = UUID.fromString(uuidString);
				targets.put(uuid, entry.getLong("expires_at"));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, Long> entry : targets.entrySet()) {
			NbtCompound nbt = new NbtCompound();
			nbt.putString("uuid", entry.getKey().toString());
			nbt.putLong("expires_at", entry.getValue());
			list.add(nbt);
		}
		tag.put("targets", list);
	}

	public static boolean hasBug(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		SpyBugComponent component = KEY.getNullable(player.getWorld());
		return component != null && component.hasBug(player.getUuid());
	}
}
