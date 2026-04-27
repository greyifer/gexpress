package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.WatheOptionsScreen;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WatheOptionsScreen.class)
public abstract class WatheExtendedOpMixin {
	@Inject(method = "isOp", at = @At("RETURN"), cancellable = true, remap = false)
	private static void mapselect$hostBypass(CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null) return;
		if (GexpressPermissions.isHostOrDev(mc.player)) {
			cir.setReturnValue(Boolean.TRUE);
		}
	}
}
