package dev.mapselect.voice;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class VoiceMuteState implements Component {
	public static final ComponentKey<VoiceMuteState> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "voice_mute"),
		VoiceMuteState.class
	);

	private final Set<UUID> muted = Collections.synchronizedSet(new LinkedHashSet<>());

	public boolean isMuted(UUID uuid) {
		return uuid != null && muted.contains(uuid);
	}

	public boolean mute(UUID uuid) {
		return uuid != null && muted.add(uuid);
	}

	public boolean unmute(UUID uuid) {
		return uuid != null && muted.remove(uuid);
	}

	public void clear() {
		muted.clear();
	}

	public Set<UUID> getMuted() {
		synchronized (muted) {
			return Collections.unmodifiableSet(new LinkedHashSet<>(muted));
		}
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		synchronized (muted) {
			muted.clear();
			NbtList list = tag.getList("muted", NbtElement.STRING_TYPE);
			for (int i = 0; i < list.size(); i++) {
				try {
					muted.add(UUID.fromString(list.getString(i)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		synchronized (muted) {
			for (UUID u : muted) list.add(NbtString.of(u.toString()));
		}
		tag.put("muted", list);
	}
}
