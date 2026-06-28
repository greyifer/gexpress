package dev.mapselect.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.vengeful.VengefulSpiritManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameFunctions.class, remap = false)
public abstract class VengefulSpiritCommittedDeathMixin {
	@Unique
	private static final ThreadLocal<java.util.ArrayDeque<Boolean>> GEXPRESS_ALIVE_BEFORE =
		ThreadLocal.withInitial(java.util.ArrayDeque::new);

	@Inject(
		method = "killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",
		at = @At("HEAD"),
		remap = false
	)
	private static void gexpress$captureAliveBefore(PlayerEntity victim, boolean spawnBody,
			PlayerEntity killer, Identifier reason, CallbackInfo ci) {
		GEXPRESS_ALIVE_BEFORE.get().push(GameFunctions.isPlayerAliveAndSurvival(victim));
	}

	@Inject(
		method = "killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",
		at = @At("RETURN"),
		remap = false
	)
	private static void gexpress$afterCommittedDeath(PlayerEntity victim, boolean spawnBody,
			PlayerEntity killer, Identifier reason, CallbackInfo ci) {
		java.util.ArrayDeque<Boolean> states = GEXPRESS_ALIVE_BEFORE.get();
		boolean wasAlive = !states.isEmpty() && states.pop();
		if (states.isEmpty()) GEXPRESS_ALIVE_BEFORE.remove();
		if (wasAlive) VengefulSpiritManager.afterKillAttempt(victim, killer);
	}
}
