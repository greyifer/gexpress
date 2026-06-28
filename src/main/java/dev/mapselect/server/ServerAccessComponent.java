package dev.mapselect.server;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

public final class ServerAccessComponent implements AutoSyncedComponent {
	public static final ComponentKey<ServerAccessComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "server_access"),
		ServerAccessComponent.class
	);

	private final World world;
	private boolean open = true;

	public ServerAccessComponent(World world) {
		this.world = world;
	}

	public boolean isOpen() {
		return open;
	}

	public boolean setOpen(boolean open) {
		if (this.open == open) return false;
		this.open = open;
		KEY.sync(world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		open = !tag.contains("open") || tag.getBoolean("open");
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tag.putBoolean("open", open);
	}
}
