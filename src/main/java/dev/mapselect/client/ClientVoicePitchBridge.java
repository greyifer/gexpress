package dev.mapselect.client;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

public final class ClientVoicePitchBridge {
	private ClientVoicePitchBridge() {}

	public static float pitchFor(UUID playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || playerId == null) return 1.0F;

		float pitch = ClientTricksterState.voicePitchFor(playerId);
		if (ModifierUtils.has(client.world, playerId, MapSelectModifiers.SQUEAKER_ID)) {
			pitch *= GexpressConfig.getSqueakerPitchPercent() / 100.0F;
		}
		return Math.max(0.5F, Math.min(2.0F, pitch));
	}
}
