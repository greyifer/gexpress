package dev.mapselect.host;

import dev.mapselect.MapSelect;
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class TrustedComponent implements AutoSyncedComponent {
	public static final ComponentKey<TrustedComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "trusted"),
		TrustedComponent.class
	);

	private final World world;
	private final Set<UUID> trusted = new LinkedHashSet<>();

	public TrustedComponent(World world) {
		this.world = world;
	}

	public boolean isTrusted(UUID uuid) {
		return uuid != null && trusted.contains(uuid);
	}

	public Set<UUID> getTrusted() {
		return Collections.unmodifiableSet(trusted);
	}

	public boolean addTrusted(UUID uuid) {
		if (uuid == null || !trusted.add(uuid)) return false;
		KEY.sync(this.world);
		return true;
	}

	public boolean removeTrusted(UUID uuid) {
		if (uuid == null || !trusted.remove(uuid)) return false;
		KEY.sync(this.world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		trusted.clear();
		NbtList list = tag.getList("trusted", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			try {
				trusted.add(UUID.fromString(list.getString(i)));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (UUID u : trusted) list.add(NbtString.of(u.toString()));
		tag.put("trusted", list);
	}

	public static boolean isTrusted(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		TrustedComponent c = KEY.getNullable(player.getWorld());
		return c != null && c.isTrusted(player.getUuid());
	}
}
