package dev.mapselect.host;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class HostComponent implements AutoSyncedComponent {
	public static final ComponentKey<HostComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "hosts"),
		HostComponent.class
	);

	private final World world;
	private final Set<UUID> hosts = new LinkedHashSet<>();

	public HostComponent(World world) {
		this.world = world;
	}

	public boolean isHost(UUID uuid) {
		return uuid != null && hosts.contains(uuid);
	}

	public Set<UUID> getHosts() {
		return Collections.unmodifiableSet(hosts);
	}

	public boolean addHost(UUID uuid) {
		if (uuid == null || !hosts.add(uuid)) return false;
		KEY.sync(this.world);
		return true;
	}

	public boolean removeHost(UUID uuid) {
		if (uuid == null || !hosts.remove(uuid)) return false;
		KEY.sync(this.world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		hosts.clear();
		NbtList list = tag.getList("hosts", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			try {
				hosts.add(UUID.fromString(list.getString(i)));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (UUID u : hosts) list.add(NbtString.of(u.toString()));
		tag.put("hosts", list);
	}

	public static boolean isHost(net.minecraft.entity.player.PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		HostComponent c = KEY.getNullable(player.getWorld());
		return c != null && c.isHost(player.getUuid());
	}
}
