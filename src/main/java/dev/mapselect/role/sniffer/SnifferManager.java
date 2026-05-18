package dev.mapselect.role.sniffer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.entity.player.PlayerEntity;

public final class SnifferManager {
	private SnifferManager() {}

	public static void register() {
		CanSeePoison.EVENT.register(SnifferManager::canSeePoison);
	}

	private static boolean canSeePoison(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.SNIFFER_ID.equals(role.identifier());
	}
}
