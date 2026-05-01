package dev.mapselect.game;

import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class FriendlyFireGuard {
	private FriendlyFireGuard() {}

	public static void register() {
		AllowPlayerDeath.EVENT.register(FriendlyFireGuard::allowDeath);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity target) || !(killer instanceof ServerPlayerEntity attacker)) return true;
		if (!GameConstants.DeathReasons.KNIFE.equals(reason) && !GameConstants.DeathReasons.BAT.equals(reason)) return true;
		if (target == attacker || target.getWorld() != attacker.getWorld()) return true;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(target.getWorld());
		if (game == null || !game.isRunning()) return true;
		if (!game.canUseKillerFeatures(target) || !game.canUseKillerFeatures(attacker)) return true;
		attacker.sendMessage(Text.literal("You cannot melee-kill another killer."), true);
		return false;
	}
}
