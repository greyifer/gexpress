package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.role.GexpressRoleAssignment;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ModdedMurderGameMode.class, remap = false)
public abstract class HmlRoleModifierTuningMixin {
	@Inject(method = "initializeGame", at = @At("HEAD"), cancellable = true)
	private void gexpress$assignRolesWithoutDividends(ServerWorld world, GameWorldComponent gameWorld,
			List<ServerPlayerEntity> players, CallbackInfo ci) {
		if (GexpressRoleAssignment.initializeGame(world, gameWorld, players)) {
			ci.cancel();
		}
	}
}
