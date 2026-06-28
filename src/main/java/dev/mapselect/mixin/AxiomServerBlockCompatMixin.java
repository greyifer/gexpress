package dev.mapselect.mixin;

import dev.mapselect.server.AxiomServerRefresh;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBlock", remap = false)
public abstract class AxiomServerBlockCompatMixin {
	@Unique
	private static Field gexpress$blocksField;

	@Inject(method = "handle", at = @At("RETURN"), require = 0, remap = false)
	private void gexpress$refreshAxiomEditedBlocks(MinecraftServer server, ServerPlayerEntity player,
			CallbackInfo ci) {
		if (player == null) return;
		AxiomServerRefresh.refreshAxiomBlockMap(gexpress$blocks(), player.getServerWorld());
	}

	@Unique
	private Object gexpress$blocks() {
		try {
			if (gexpress$blocksField == null) {
				gexpress$blocksField = getClass().getDeclaredField("blocks");
				gexpress$blocksField.setAccessible(true);
			}
			return gexpress$blocksField.get(this);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
