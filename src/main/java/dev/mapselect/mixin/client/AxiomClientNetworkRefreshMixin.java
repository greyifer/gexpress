package dev.mapselect.mixin.client;

import dev.mapselect.client.render.AxiomClientRefresh;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class AxiomClientNetworkRefreshMixin {
	@Inject(method = "onChunkData", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}

	@Inject(method = "onChunkDeltaUpdate", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterChunkDelta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestLight();
	}

	@Inject(method = "onBlockUpdate", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestLight();
	}

	@Inject(method = "onBlockEntityUpdate", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestLight();
	}

	@Inject(method = "onBlockEvent", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterBlockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestLight();
	}

	@Inject(method = "onLightUpdate", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterLightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestLight();
	}

	@Inject(method = "onUnloadChunk", at = @At("RETURN"))
	private void gexpress$refreshAxiomAfterUnload(UnloadChunkS2CPacket packet, CallbackInfo ci) {
		AxiomClientRefresh.requestFull();
	}
}
