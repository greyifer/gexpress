package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class PuppetmasterCameraEntityMixin {
	@ModifyVariable(method = "setCameraEntity", at = @At("HEAD"), argsOnly = true)
	private Entity gexpress$keepPuppetCameraOnTarget(Entity requested) {
		return ClientPuppetmasterState.preferredCameraEntity((MinecraftClient) (Object) this, requested);
	}
}
