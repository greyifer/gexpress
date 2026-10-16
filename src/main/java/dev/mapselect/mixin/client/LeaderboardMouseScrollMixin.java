package dev.mapselect.mixin.client;

import dev.mapselect.network.leaderboard.LeaderboardScrollPayload;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class LeaderboardMouseScrollMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void gexpress$scrollLeaderboard(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null || client.currentScreen != null
				|| vertical == 0.0D || !(client.crosshairTarget instanceof BlockHitResult hit)
				|| !client.world.getBlockState(hit.getBlockPos()).isOf(MapSelectBlocks.LEADERBOARD)
				|| !ClientPlayNetworking.canSend(LeaderboardScrollPayload.ID)) {
			return;
		}
		int amount = vertical > 0.0D ? -1 : 1;
		ClientPlayNetworking.send(new LeaderboardScrollPayload(hit.getBlockPos(), amount));
		ci.cancel();
	}
}
