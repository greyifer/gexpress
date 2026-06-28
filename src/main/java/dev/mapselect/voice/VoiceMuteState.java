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
	public static final int MIN_DEAD_VOICE_GROUP_COUNT = 1;
	public static final int DEFAULT_DEAD_VOICE_GROUP_COUNT = 3;
	public static final int MAX_DEAD_VOICE_GROUP_COUNT = 12;
	public static final ComponentKey<VoiceMuteState> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "voice_mute"),
		VoiceMuteState.class
	);

	private final Set<UUID> muted = Collections.synchronizedSet(new LinkedHashSet<>());
	private final Set<UUID> adminDeadVoiceLocked = Collections.synchronizedSet(new LinkedHashSet<>());
	private int deadVoiceGroupCount = DEFAULT_DEAD_VOICE_GROUP_COUNT;

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

	public int getDeadVoiceGroupCount() {
		return deadVoiceGroupCount;
	}

	public int setDeadVoiceGroupCount(int count) {
		deadVoiceGroupCount = clampDeadVoiceGroupCount(count);
		return deadVoiceGroupCount;
	}

	public int addDeadVoiceGroups(int amount) {
		return setDeadVoiceGroupCount(deadVoiceGroupCount + Math.max(0, amount));
	}

	public int removeDeadVoiceGroups(int amount) {
		return setDeadVoiceGroupCount(deadVoiceGroupCount - Math.max(0, amount));
	}

	public boolean isAdminDeadVoiceLocked(UUID uuid) {
		return uuid != null && adminDeadVoiceLocked.contains(uuid);
	}

	public boolean lockAdminDeadVoice(UUID uuid) {
		return uuid != null && adminDeadVoiceLocked.add(uuid);
	}

	public boolean unlockAdminDeadVoice(UUID uuid) {
		return uuid != null && adminDeadVoiceLocked.remove(uuid);
	}

	public int clearAdminDeadVoiceLocks() {
		int count = adminDeadVoiceLocked.size();
		adminDeadVoiceLocked.clear();
		return count;
	}

	public Set<UUID> getAdminDeadVoiceLocked() {
		synchronized (adminDeadVoiceLocked) {
			return Collections.unmodifiableSet(new LinkedHashSet<>(adminDeadVoiceLocked));
		}
	}

	public static int clampDeadVoiceGroupCount(int count) {
		return Math.max(MIN_DEAD_VOICE_GROUP_COUNT, Math.min(MAX_DEAD_VOICE_GROUP_COUNT, count));
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
		deadVoiceGroupCount = tag.contains("deadVoiceGroupCount", NbtElement.INT_TYPE)
			? clampDeadVoiceGroupCount(tag.getInt("deadVoiceGroupCount"))
			: DEFAULT_DEAD_VOICE_GROUP_COUNT;
		synchronized (adminDeadVoiceLocked) {
			adminDeadVoiceLocked.clear();
			NbtList list = tag.getList("adminDeadVoiceLocked", NbtElement.STRING_TYPE);
			for (int i = 0; i < list.size(); i++) {
				try {
					adminDeadVoiceLocked.add(UUID.fromString(list.getString(i)));
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
		tag.putInt("deadVoiceGroupCount", deadVoiceGroupCount);
		NbtList adminList = new NbtList();
		synchronized (adminDeadVoiceLocked) {
			for (UUID u : adminDeadVoiceLocked) adminList.add(NbtString.of(u.toString()));
		}
		tag.put("adminDeadVoiceLocked", adminList);
	}
}
