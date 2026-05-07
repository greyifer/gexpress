package dev.mapselect.client;

import dev.mapselect.config.GexpressConfig;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import net.minecraft.client.MinecraftClient;

public final class ClientVoicePitchBridge {
	private ClientVoicePitchBridge() {}

	public static float currentPitch() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null) return 1.0F;

		float pitch = ClientTricksterState.localVoicePitch();
		if (ModifierUtils.has(client.player, MapSelectModifiers.SQUEAKER_ID)) {
			pitch *= GexpressConfig.getSqueakerPitchPercent() / 100.0F;
		}
		return Math.max(0.5F, Math.min(2.0F, pitch));
	}
}
