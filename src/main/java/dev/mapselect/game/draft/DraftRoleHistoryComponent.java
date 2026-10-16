package dev.mapselect.game.draft;

import dev.doctor4t.wathe.api.Role;
import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent snapshot of the immediately preceding round's restricted team alignments. */
public final class DraftRoleHistoryComponent implements Component {
	public static final ComponentKey<DraftRoleHistoryComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "draft_role_history"), DraftRoleHistoryComponent.class);

	private final Map<UUID, DraftAlignmentAllocator.Alignment> previous = new LinkedHashMap<>();

	public DraftAlignmentAllocator.Alignment previousAlignment(UUID playerId) {
		return playerId == null
			? DraftAlignmentAllocator.Alignment.INNOCENT
			: previous.getOrDefault(playerId, DraftAlignmentAllocator.Alignment.INNOCENT);
	}

	public void replaceWith(Map<UUID, Role> roles) {
		previous.clear();
		if (roles == null) return;
		for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
			UUID playerId = entry.getKey();
			Role role = entry.getValue();
			if (playerId == null || role == null) continue;
			DraftAlignmentAllocator.Alignment alignment = role.canUseKiller()
				? DraftAlignmentAllocator.Alignment.KILLER
				: !role.isInnocent() ? DraftAlignmentAllocator.Alignment.NEUTRAL
				: DraftAlignmentAllocator.Alignment.INNOCENT;
			if (alignment != DraftAlignmentAllocator.Alignment.INNOCENT) previous.put(playerId, alignment);
		}
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		previous.clear();
		NbtList entries = tag.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < entries.size(); i++) {
			NbtCompound entry = entries.getCompound(i);
			try {
				UUID playerId = UUID.fromString(entry.getString("uuid"));
				DraftAlignmentAllocator.Alignment alignment =
					DraftAlignmentAllocator.Alignment.valueOf(entry.getString("alignment"));
				if (alignment != DraftAlignmentAllocator.Alignment.INNOCENT) previous.put(playerId, alignment);
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList entries = new NbtList();
		for (Map.Entry<UUID, DraftAlignmentAllocator.Alignment> value : previous.entrySet()) {
			NbtCompound entry = new NbtCompound();
			entry.putString("uuid", value.getKey().toString());
			entry.putString("alignment", value.getValue().name());
			entries.add(entry);
		}
		tag.put("players", entries);
	}
}
