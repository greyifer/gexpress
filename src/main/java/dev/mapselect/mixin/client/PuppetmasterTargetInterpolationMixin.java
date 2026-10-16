package dev.mapselect.mixin.client;

import dev.mapselect.client.role.puppetmaster.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class PuppetmasterTargetInterpolationMixin {
	@ModifyVariable(method = "updateTrackedPositionAndAngles", at = @At("HEAD"), argsOnly = true)
	private int gexpress$reduceControlledTargetInterpolation(int interpolationSteps) {
		Entity self = (Entity) (Object) this;
		return ClientPuppetmasterState.getTargetPlayer(MinecraftClient.getInstance()) == self
			? 1
			: interpolationSteps;
	}
}
