package dev.mapselect.mixin;

import dev.mapselect.server.AxiomServerRefresh;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBuffer", remap = false)
public abstract class AxiomServerBufferCompatMixin {
	@Inject(method = "applyBlockBufferServer", at = @At("RETURN"), require = 0, remap = false)
	private static void gexpress$refreshAxiomEditedBlocks(Object buffer, ServerWorld world, Object cutout,
			ServerPlayerEntity player, CallbackInfo ci) {
		AxiomServerRefresh.refreshAxiomBuffer(buffer, world);
	}
}
