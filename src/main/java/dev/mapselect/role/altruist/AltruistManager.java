package dev.mapselect.role.altruist;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AltruistUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public final class AltruistManager {
	private static final double LOOK_RADIUS_SQUARED = 1.25D;

	private AltruistManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(AltruistUsePayload.ID, AltruistUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AltruistUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryRevive(context.player())));
	}

	private static void tryRevive(ServerPlayerEntity altruist) {
		if (altruist == null || !(altruist.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(altruist) || !isAltruist(altruist)
				|| !canUseHere(world, altruist) || !GameFunctions.isPlayerAliveAndSurvival(altruist)) {
			return;
		}
		PlayerBodyEntity body = findBody(altruist);
		if (body == null) {
			altruist.sendMessage(Text.literal("No body close enough to revive."), true);
			return;
		}
		ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(body.getPlayerUuid());
		if (target == null || GameFunctions.isPlayerAliveAndSurvival(target)) {
			altruist.sendMessage(Text.literal("That body cannot be revived."), true);
			return;
		}

		Vec3d revivePos = body.getPos();
		float yaw = body.getYaw();
		body.discard();
		target.changeGameMode(GameMode.ADVENTURE);
		target.teleport(world, revivePos.x, revivePos.y, revivePos.z, yaw, 0.0F);
		target.setHealth(target.getMaxHealth());
		target.setFireTicks(0);
		target.setVelocity(Vec3d.ZERO);
		target.velocityModified = true;
		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
		TrainVoicePlugin.resetPlayer(target.getUuid());
		target.sendMessage(Text.literal("The Altruist revived you."), true);

		AbilitySounds.playTo(java.util.List.of(altruist, target), SoundEvents.BLOCK_BEACON_ACTIVATE,
			SoundCategory.PLAYERS, 0.9F, 1.35F);
		GameFunctions.killPlayer(altruist, true, target, GameConstants.DeathReasons.GENERIC);
		TrainVoicePlugin.addPlayer(altruist.getUuid());
	}

	private static PlayerBodyEntity findBody(ServerPlayerEntity altruist) {
		double range = GexpressConfig.getAltruistRange();
		Vec3d eye = altruist.getEyePos();
		Vec3d look = altruist.getRotationVec(1.0F).normalize();
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (PlayerBodyEntity body : altruist.getServerWorld().getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			Vec3d to = body.getPos().add(0.0D, 0.8D, 0.0D).subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > range || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > LOOK_RADIUS_SQUARED) continue;
			best = body;
			bestAlong = along;
		}
		return best;
	}

	private static boolean isAltruist(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.ALTRUIST_ID.equals(role.identifier());
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}
}
