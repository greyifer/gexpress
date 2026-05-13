package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.bountyhunter.BountyHunterManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GameFunctions.class, remap = false)
public abstract class BountyHunterKillRewardMixin {
	@Dynamic("Wathe's GameFunctions class is compiled with intermediary player/identifier descriptors.")
	@Redirect(
		method = "killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",
		at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V"),
		require = 0,
		remap = false
	)
	private static void gexpress$skipBaseKillRewardForBountyHunter(PlayerShopComponent shop, int amount,
			PlayerEntity victim, boolean dead, PlayerEntity killer, Identifier reason) {
		if (BountyHunterManager.isBountyHunter(killer)) return;
		shop.addToBalance(amount);
	}
}
