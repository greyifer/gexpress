package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.cca.TrainWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrainWorldComponent.class)
public abstract class WatheTrainSnowMixin {

	@Shadow @Final
	private World world;

	@Inject(method = "isSnowing", at = @At("HEAD"), cancellable = true)
	private void mapselect$suppressSnowInPlayArea(CallbackInfoReturnable<Boolean> cir) {
		if (!(world instanceof ClientWorld)) return;
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;
		MapVariablesWorldComponent mapVars = MapVariablesWorldComponent.KEY.getNullable(world);
		if (mapVars == null) return;
		Box playArea = mapVars.getPlayArea();
		if (playArea != null && playArea.contains(player.getPos())) {
			cir.setReturnValue(false);
		}
	}
}
