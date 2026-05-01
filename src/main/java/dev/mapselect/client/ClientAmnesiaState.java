package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.GexpressGameModes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public final class ClientAmnesiaState {
	private ClientAmnesiaState() {}

	public static boolean shouldHideKillerIdentity(PlayerEntity target) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null || target == null) return false;
		if (target == client.player || target.getWorld() != client.world) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		if (!GexpressGameModes.isAmnesia(game) || !game.isRunning()) return false;
		Role localRole = game.getRole(client.player);
		Role targetRole = game.getRole(target);
		return localRole != null && localRole.canUseKiller()
			&& targetRole != null && targetRole.canUseKiller()
			&& !GameFunctions.isPlayerSpectatingOrCreative(target);
	}

	public static int civilianInstinctColor(PlayerEntity target) {
		float mood = PlayerMoodComponent.KEY.get(target).getMood();
		if (mood < GameConstants.DEPRESSIVE_MOOD_THRESHOLD) return 0x171DC6;
		if (mood < GameConstants.MID_MOOD_THRESHOLD) return 0x1FAFAF;
		return 0x4EDD35;
	}
}
