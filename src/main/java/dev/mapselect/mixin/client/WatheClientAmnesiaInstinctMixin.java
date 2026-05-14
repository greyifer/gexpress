package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.client.ClientAmnesiaState;
import dev.mapselect.client.ClientVultureState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientAmnesiaInstinctMixin {
	@Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
	private static void gexpress$disableInstinctInPelicanBelly(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientVultureState.isLocalStashed(client)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
	private static void gexpress$renderOtherKillersAsCivilians(Entity target, CallbackInfoReturnable<Integer> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientVultureState.isLocalStashed(client)) {
			cir.setReturnValue(-1);
			return;
		}
		if (target instanceof PlayerEntity player && ClientAmnesiaState.shouldHideKillerIdentity(player)) {
			cir.setReturnValue(-1);
		}
	}
}
