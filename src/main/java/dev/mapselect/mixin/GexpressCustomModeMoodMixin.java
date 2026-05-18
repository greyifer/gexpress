package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.mapselect.game.GexpressGameModes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class GexpressCustomModeMoodMixin {
	@Final
	@Shadow
	private PlayerEntity player;

	@Shadow
	private float mood;

	@Inject(method = "getMood", at = @At("HEAD"), cancellable = true)
	private void gexpress$customModesUseRealMood(CallbackInfoReturnable<Float> cir) {
		if (usesRealMoodInCustomMode()) cir.setReturnValue(mood);
	}

	@Inject(method = "setMood", at = @At("HEAD"), cancellable = true)
	private void gexpress$customModesStoreRealMood(float mood, CallbackInfo ci) {
		if (!usesRealMoodInCustomMode()) return;
		this.mood = MathHelper.clamp(mood, 0.0F, 1.0F);
		PlayerMoodComponent.KEY.sync(player);
		ci.cancel();
	}

	private boolean usesRealMoodInCustomMode() {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		return game != null && game.isRunning()
			&& (GexpressGameModes.isAmnesia(game) || GexpressGameModes.isTakeover(game));
	}
}
