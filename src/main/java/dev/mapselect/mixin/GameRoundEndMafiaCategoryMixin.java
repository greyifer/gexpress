package dev.mapselect.mixin;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.role.GexpressRoleAnnouncementTexts;
import dev.mapselect.role.mafia.MafiaManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GameRoundEndComponent.class, remap = false)
public abstract class GameRoundEndMafiaCategoryMixin {
	@Shadow
	private World world;

	@Shadow
	private List<GameRoundEndComponent.RoundEndData> players;

	@Shadow
	public abstract void sync();

	@Inject(method = "setRoundEndData", at = @At("RETURN"))
	private void gexpress$markMafiaSection(List<ServerPlayerEntity> players,
			dev.doctor4t.wathe.game.GameFunctions.WinStatus winStatus, CallbackInfo ci) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || this.players.isEmpty()) return;
		boolean changed = false;
		for (int i = 0; i < this.players.size(); i++) {
			GameRoundEndComponent.RoundEndData entry = this.players.get(i);
			GameProfile profile = entry.player();
			ServerPlayerEntity player = null;
			if (world.getServer() != null) {
				player = world.getServer().getPlayerManager().getPlayer(profile.getId());
			}
			if (player != null && MafiaManager.isMafiaRole(game.getRole(player))) {
				this.players.set(i, new GameRoundEndComponent.RoundEndData(profile,
					GexpressRoleAnnouncementTexts.MAFIA, entry.wasDead()));
				changed = true;
			}
		}
		if (changed) sync();
	}
}
