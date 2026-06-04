package dev.mapselect.currency;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.MapSelect;
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

public class GcoinComponent implements AutoSyncedComponent {
	public static final ComponentKey<GcoinComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "gcoins"),
		GcoinComponent.class
	);

	private final World world;
	private final Map<UUID, Integer> balances = new LinkedHashMap<>();

	public GcoinComponent(World world) {
		this.world = world;
	}

	public Map<UUID, Integer> entries() {
		return Collections.unmodifiableMap(balances);
	}

	public int balance(UUID playerId) {
		return playerId == null ? 0 : Math.max(0, balances.getOrDefault(playerId, 0));
	}

	public boolean setBalance(UUID playerId, int amount) {
		if (playerId == null) return false;
		balances.put(playerId, Math.max(0, amount));
		KEY.sync(world);
		return true;
	}

	public boolean add(UUID playerId, int amount) {
		if (playerId == null || amount <= 0) return false;
		return setBalance(playerId, balance(playerId) + amount);
	}

	public boolean remove(UUID playerId, int amount) {
		if (playerId == null || amount <= 0) return false;
		return setBalance(playerId, Math.max(0, balance(playerId) - amount));
	}

	public static boolean isOutOfGame(World world) {
		GameWorldComponent game = world == null ? null : GameWorldComponent.KEY.getNullable(world);
		if (game == null) return true;
		return !game.isRunning()
			&& game.getGameStatus() != GameWorldComponent.GameStatus.STARTING
			&& game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
			&& game.getGameStatus() != GameWorldComponent.GameStatus.STOPPING;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		balances.clear();
		NbtList players = tag.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < players.size(); i++) {
			NbtCompound entry = players.getCompound(i);
			UUID playerId = parseUuid(entry.getString("uuid"));
			if (playerId != null) balances.put(playerId, Math.max(0, entry.getInt("gcoins")));
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList players = new NbtList();
		for (Map.Entry<UUID, Integer> entry : balances.entrySet()) {
			NbtCompound out = new NbtCompound();
			out.putString("uuid", entry.getKey().toString());
			out.putInt("gcoins", Math.max(0, entry.getValue()));
			players.add(out);
		}
		tag.put("players", players);
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
