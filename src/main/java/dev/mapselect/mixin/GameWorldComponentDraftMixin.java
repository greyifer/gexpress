package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.draft.DraftManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class GameWorldComponentDraftMixin {
	@Redirect(
		method = "tickCommon",
		at = @At(value = "INVOKE",
			target = "Ldev/doctor4t/wathe/game/GameFunctions;initializeGame(Lnet/minecraft/server/world/ServerWorld;)V")
	)
	private void gexpress$postponeInitializationForDraft(ServerWorld world) {
		if (!DraftManager.beginIfDraft(world)) GameFunctions.initializeGame(world);
	}
}
