package dev.mapselect.mixin;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.block.HornBlock;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HornBlock.class)
public abstract class HornStartPermissionMixin {
	@Redirect(
		method = "onUse",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getPermissionLevel(Lcom/mojang/authlib/GameProfile;)I")
	)
	private int gexpress$allowStartPermissionOnHorn(MinecraftServer server, GameProfile profile,
			BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		int level = server.getPermissionLevel(profile);
		return level >= 2 || GexpressPermissions.canStartGames(player) ? 2 : level;
	}
}
