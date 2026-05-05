package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import dev.mapselect.client.ClientSilentShadowState;
import dev.mapselect.client.ClientVultureState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RoleNameRenderer.class, remap = false)
public abstract class SilentShadowRoleNameRendererMixin {
	@Redirect(
		method = "renderHud",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/util/hit/EntityHitResult;getEntity()Lnet/minecraft/entity/Entity;",
			remap = true
		)
	)
	private static Entity gexpress$hideShadowHoverName(EntityHitResult hitResult) {
		Entity entity = hitResult.getEntity();
		if (entity instanceof PlayerEntity && (ClientSilentShadowState.isShadowed(entity)
				|| ClientVultureState.isLocalStashed(MinecraftClient.getInstance()))) {
			return null;
		}
		return entity;
	}
}
