package dev.mapselect.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.client.game.ClientSpectatorRoleRevealDelay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wathe replaces the entity team color inside WorldRenderer after Entity#getTeamColorValue
 * returns. Normalize that final expression so addon instinct hooks cannot reintroduce role
 * colors while G'Express is withholding the spectator reveal.
 */
@Mixin(value = WorldRenderer.class, priority = 100)
public abstract class SpectatorInstinctRendererColorMixin {
	@ModifyExpressionValue(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getTeamColorValue()I")
	)
	private int gexpress$maskFinalSpectatorInstinctColor(int original, @Local Entity entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(entity instanceof AbstractClientPlayerEntity player)
				|| GameFunctions.isPlayerSpectatingOrCreative(player)
				|| !ClientSpectatorRoleRevealDelay.shouldUseInstinctReveal(client)) {
			return original;
		}
		return ClientSpectatorRoleRevealDelay.glowColor();
	}
}
