package dev.mapselect.game;

import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.role.vulture.VultureManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public final class SpectatorMovementGuard {
	private SpectatorMovementGuard() {}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(SpectatorMovementGuard::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) continue;
			if (VultureManager.isStashed(player) || PuppetmasterManager.isControlled(player)
					|| TimeMasterManager.isFrozen(player)) {
				continue;
			}
			if (!player.getAbilities().allowFlying || !player.getAbilities().flying) {
				player.getAbilities().allowFlying = true;
				player.getAbilities().flying = true;
				player.sendAbilitiesUpdate();
			}
		}
	}
}
