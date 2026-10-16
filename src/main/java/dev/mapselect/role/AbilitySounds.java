package dev.mapselect.role;

import dev.mapselect.config.GexpressConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class AbilitySounds {
	private AbilitySounds() {}

	public static void playTo(ServerPlayerEntity player, SoundEvent sound, SoundCategory category,
			float volume, float pitch) {
		if (player == null || sound == null) return;
		float adjustedVolume = volume * (GexpressConfig.getAbilitySoundVolumePercent() / 100.0F);
		if (adjustedVolume <= 0.0F) return;
		player.playSoundToPlayer(sound, category, adjustedVolume, pitch);
	}

	public static void playTo(Collection<ServerPlayerEntity> players, SoundEvent sound, SoundCategory category,
			float volume, float pitch) {
		if (players == null || players.isEmpty()) return;
		Set<ServerPlayerEntity> sent = new HashSet<>();
		for (ServerPlayerEntity player : players) {
			if (player != null && sent.add(player)) {
				playTo(player, sound, category, volume, pitch);
			}
		}
	}
}
