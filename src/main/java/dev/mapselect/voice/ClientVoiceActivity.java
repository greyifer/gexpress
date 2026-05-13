package dev.mapselect.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVoiceActivity {
	private static final long SPEAKING_GRACE_MS = 450L;
	private static final Map<UUID, Long> speakingUntil = new ConcurrentHashMap<>();

	private ClientVoiceActivity() {}

	public static void markSpeaking(UUID playerId) {
		if (playerId == null) return;
		speakingUntil.put(playerId, System.currentTimeMillis() + SPEAKING_GRACE_MS);
	}

	public static boolean isSpeaking(UUID playerId) {
		if (playerId == null) return false;
		long now = System.currentTimeMillis();
		Long until = speakingUntil.get(playerId);
		if (until == null) return false;
		if (until >= now) return true;
		speakingUntil.remove(playerId);
		return false;
	}
}
