package dev.mapselect.game;

import dev.mapselect.MapSelect;
import dev.mapselect.level.LevelComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RoundParticipantComponent implements AutoSyncedComponent {
	public static final ComponentKey<RoundParticipantComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "round_participants"),
		RoundParticipantComponent.class
	);

	private final World world;
	private final Set<UUID> participants = new LinkedHashSet<>();
	private final Set<UUID> spectators = new LinkedHashSet<>();
	private final Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
	private boolean roundActive;

	public RoundParticipantComponent(World world) {
		this.world = world;
	}

	public boolean roundActive() {
		return roundActive;
	}

	public boolean hasParticipants() {
		return !participants.isEmpty();
	}

	public Set<UUID> participants() {
		return Collections.unmodifiableSet(participants);
	}

	public boolean isParticipant(UUID playerId) {
		return playerId != null && participants.contains(playerId);
	}

	public boolean isSpectator(UUID playerId) {
		return playerId != null && spectators.contains(playerId);
	}

	public String name(UUID playerId, String fallback) {
		PlayerSnapshot snapshot = playerId == null ? null : snapshots.get(playerId);
		if (snapshot != null && !snapshot.name().isBlank()) return snapshot.name();
		return fallback == null ? "" : fallback;
	}

	public int level(UUID playerId, int fallback) {
		PlayerSnapshot snapshot = playerId == null ? null : snapshots.get(playerId);
		return Math.max(1, snapshot == null ? fallback : snapshot.level());
	}

	public void beginRound(Collection<ServerPlayerEntity> players, Set<UUID> roundPlayers, LevelComponent levels) {
		participants.clear();
		spectators.clear();
		if (roundPlayers != null) participants.addAll(roundPlayers);
		roundActive = true;
		captureSnapshots(players, levels);
		KEY.sync(world);
	}

	public void finishRound(Collection<ServerPlayerEntity> players, LevelComponent levels) {
		roundActive = false;
		captureSnapshots(players, levels);
		KEY.sync(world);
	}

	public boolean markSpectator(ServerPlayerEntity player, LevelComponent levels) {
		if (player == null || participants.contains(player.getUuid())) return false;
		boolean changed = spectators.add(player.getUuid());
		changed |= captureSnapshot(player, levels);
		if (changed) KEY.sync(world);
		return changed;
	}

	public boolean captureSnapshot(ServerPlayerEntity player, LevelComponent levels) {
		if (player == null) return false;
		int level = levels == null ? 1 : levels.level(player.getUuid());
		String name = player.getGameProfile().getName();
		PlayerSnapshot next = new PlayerSnapshot(name == null ? "" : name, Math.max(1, level));
		PlayerSnapshot previous = snapshots.put(player.getUuid(), next);
		return !next.equals(previous);
	}

	private void captureSnapshots(Collection<ServerPlayerEntity> players, LevelComponent levels) {
		if (players == null) return;
		for (ServerPlayerEntity player : players) captureSnapshot(player, levels);
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		participants.clear();
		spectators.clear();
		snapshots.clear();
		roundActive = tag.getBoolean("roundActive");
		readUuidList(tag.getList("participants", NbtElement.STRING_TYPE), participants);
		readUuidList(tag.getList("spectators", NbtElement.STRING_TYPE), spectators);
		NbtList entries = tag.getList("snapshots", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			UUID playerId = parseUuid(entry.getString("uuid"));
			if (playerId == null) continue;
			String name = entry.getString("name");
			int level = Math.max(1, entry.getInt("level"));
			snapshots.put(playerId, new PlayerSnapshot(name == null ? "" : name, level));
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tag.putBoolean("roundActive", roundActive);
		tag.put("participants", writeUuidList(participants));
		tag.put("spectators", writeUuidList(spectators));
		NbtList entries = new NbtList();
		for (Map.Entry<UUID, PlayerSnapshot> entry : snapshots.entrySet()) {
			NbtCompound out = new NbtCompound();
			out.putString("uuid", entry.getKey().toString());
			out.putString("name", entry.getValue().name());
			out.putInt("level", Math.max(1, entry.getValue().level()));
			entries.add(out);
		}
		tag.put("snapshots", entries);
	}

	private static void readUuidList(NbtList list, Set<UUID> out) {
		for (int i = 0; i < list.size(); i++) {
			UUID uuid = parseUuid(list.getString(i));
			if (uuid != null) out.add(uuid);
		}
	}

	private static NbtList writeUuidList(Set<UUID> values) {
		NbtList list = new NbtList();
		for (UUID uuid : values) list.add(NbtString.of(uuid.toString()));
		return list;
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private record PlayerSnapshot(String name, int level) {}
}
