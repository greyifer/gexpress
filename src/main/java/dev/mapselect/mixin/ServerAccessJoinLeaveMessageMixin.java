package dev.mapselect.mixin;

import dev.mapselect.server.ServerAccessManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class ServerAccessJoinLeaveMessageMixin {
	@Shadow @Final private MinecraftServer server;

	@Unique private ServerPlayerEntity gexpress$joiningPlayer;
	@Unique private ServerPlayerEntity gexpress$leavingPlayer;

	@Inject(
		method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
		at = @At("HEAD")
	)
	private void gexpress$captureJoiningPlayer(ClientConnection connection, ServerPlayerEntity player,
			ConnectedClientData clientData, CallbackInfo ci) {
		gexpress$joiningPlayer = player;
	}

	@Inject(
		method = "onPlayerConnect(Lnet/minecraft/network/ClientConnection;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ConnectedClientData;)V",
		at = @At("RETURN")
	)
	private void gexpress$clearJoiningPlayer(ClientConnection connection, ServerPlayerEntity player,
			ConnectedClientData clientData, CallbackInfo ci) {
		if (gexpress$joiningPlayer == player) gexpress$joiningPlayer = null;
	}

	@Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
	private void gexpress$captureLeavingPlayer(ServerPlayerEntity player, CallbackInfo ci) {
		gexpress$leavingPlayer = player;
	}

	@Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("RETURN"))
	private void gexpress$clearLeavingPlayer(ServerPlayerEntity player, CallbackInfo ci) {
		if (gexpress$leavingPlayer == player) gexpress$leavingPlayer = null;
	}

	@Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
	private void gexpress$suppressClosedJoinLeaveMessages(Text message, boolean overlay, CallbackInfo ci) {
		if (overlay || !ServerAccessManager.shouldSuppressJoinLeaveMessages(server)) return;
		if (message != null && message.getContent() instanceof TranslatableTextContent translatable) {
			String key = translatable.getKey();
			ServerPlayerEntity player = gexpress$announcedPlayer(key);
			if (player != null && ServerAccessManager.canJoinWhenClosed(player)) return;
			if (gexpress$isJoinLeaveMessage(key)) {
				ci.cancel();
			}
		}
	}

	@Unique
	private ServerPlayerEntity gexpress$announcedPlayer(String key) {
		if ("multiplayer.player.left".equals(key)) return gexpress$leavingPlayer;
		if ("multiplayer.player.joined".equals(key) || "multiplayer.player.joined.renamed".equals(key)) {
			return gexpress$joiningPlayer;
		}
		return null;
	}

	@Unique
	private boolean gexpress$isJoinLeaveMessage(String key) {
		return "multiplayer.player.joined".equals(key)
			|| "multiplayer.player.joined.renamed".equals(key)
			|| "multiplayer.player.left".equals(key);
	}
}
