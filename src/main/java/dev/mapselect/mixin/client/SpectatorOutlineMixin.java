package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientSpectatorRoleRevealDelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class SpectatorOutlineMixin {
	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void gexpress$onlyUseInstinctForDelayedSpectatorOutlines(Entity entity,
			CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (!(entity instanceof PlayerEntity) || !ClientSpectatorRoleRevealDelay.isWaitingForRoleReveal(client)) return;
		if (entity.isGlowing()) return;
		cir.setReturnValue(false);
	}
}
