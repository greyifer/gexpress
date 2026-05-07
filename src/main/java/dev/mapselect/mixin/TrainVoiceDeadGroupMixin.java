package dev.mapselect.mixin;

import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.mapselect.voice.DeadVoiceGroupManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = TrainVoicePlugin.class, remap = false)
public abstract class TrainVoiceDeadGroupMixin {
	@Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
	private static void gexpress$onlyActualDeadPlayersJoinDeadVoice(UUID playerId, CallbackInfo ci) {
		if (!DeadVoiceGroupManager.canJoinDeadVoice(playerId)) {
			ci.cancel();
		}
	}
}
