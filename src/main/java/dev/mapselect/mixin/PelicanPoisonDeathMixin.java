package dev.mapselect.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.pelican.PelicanManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GameFunctions.class, remap = false)
public abstract class PelicanPoisonDeathMixin {
	@Dynamic("Wathe's GameFunctions class is compiled with intermediary player/identifier descriptors.")
	@ModifyExpressionValue(
		method = "killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",
		at = @At(
			value = "INVOKE",
			target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerAliveAndSurvival(Lnet/minecraft/class_1657;)Z",
			remap = false
		)
	)
	private static boolean gexpress$allowSwallowedPoisonDeath(boolean original, PlayerEntity victim,
			boolean spawnBody, PlayerEntity killer, Identifier deathReason) {
		if (victim instanceof ServerPlayerEntity swallowed
				&& GameConstants.DeathReasons.POISON.equals(deathReason)) {
			if (PelicanManager.preparePoisonDeath(swallowed)) return true;
		}
		return original;
	}
}
