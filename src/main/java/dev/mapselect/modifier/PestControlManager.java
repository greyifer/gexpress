package dev.mapselect.modifier;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.registry.MapSelectRoles;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.events.ModifierAssigned;

public final class PestControlManager {
	public static final int RAT_TRAP_PRICE = 75;

	private PestControlManager() {}

	public static void register() {
		ModifierAssigned.EVENT.register((player, modifier) -> {
			if (player == null || modifier == null
					|| !MapSelectModifiers.PEST_CONTROL_ID.equals(modifier.identifier())
					|| player.getWorld().isClient) {
				return;
			}
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
			if (!hasRatInRound(serverPlayer)) {
				ModifierUtils.removeIfPresent(serverPlayer, modifier);
				return;
			}
			player.sendMessage(Text.literal("Pest Control: Rat Traps are available in your shop for "
				+ RAT_TRAP_PRICE + " gold.").formatted(Formatting.GOLD), false);
		});
	}

	public static boolean has(PlayerEntity player) {
		return ModifierUtils.has(player, MapSelectModifiers.PEST_CONTROL_ID);
	}

	private static boolean hasRatInRound(ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null || game.getRoles() == null) return false;
		for (Role role : game.getRoles().values()) {
			if (role != null && MapSelectRoles.RAT_ID.equals(role.identifier())) return true;
		}
		return false;
	}
}
