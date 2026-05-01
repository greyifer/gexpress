package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.role.snitch.SnitchManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class KillerRoleInfoManager {
	private static final double RANGE = 8.0D;
	private static final double LOOK_RADIUS_SQUARED = 1.0D;

	private KillerRoleInfoManager() {}

	public static void register() {
		// The Snitch reveal now uses the top-right identity HUD, so this old actionbar lookup stays disabled.
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
		if (!SnitchManager.hasRevealedSnitches()) return;

		for (ServerPlayerEntity viewer : world.getPlayers()) {
			if (!isPlayable(viewer) || !canInspect(game, viewer)) continue;
			ServerPlayerEntity target = findLookTarget(viewer, game);
			if (target == null) continue;
			Role role = game.getRole(target);
			MutableText line = target.getName().copy().formatted(Formatting.RED);
			line.append(Text.literal(" - ").formatted(Formatting.GRAY));
			line.append(roleName(role).formatted(Formatting.RED));
			viewer.sendMessage(line, true);
		}
	}

	private static ServerPlayerEntity findLookTarget(ServerPlayerEntity viewer, GameWorldComponent game) {
		Vec3d eye = viewer.getEyePos();
		Vec3d look = viewer.getRotationVec(1.0F).normalize();
		ServerPlayerEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (ServerPlayerEntity candidate : viewer.getServerWorld().getPlayers()) {
			if (candidate == viewer || !isPlayable(candidate) || !isKillerTeam(game, candidate)) continue;
			if (!viewer.canSee(candidate)) continue;
			Vec3d to = candidate.getEyePos().subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > RANGE || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > LOOK_RADIUS_SQUARED) continue;
			best = candidate;
			bestAlong = along;
		}
		return best;
	}

	private static boolean canInspect(GameWorldComponent game, ServerPlayerEntity viewer) {
		return SnitchManager.isRevealed(viewer.getUuid());
	}

	private static boolean isKillerTeam(GameWorldComponent game, ServerPlayerEntity player) {
		Role role = game.getRole(player);
		return role != null && (role.canUseKiller() || game.canUseKillerFeatures(player));
	}

	private static MutableText roleName(Role role) {
		if (role == null || role.identifier() == null) return Text.literal("Unknown");
		return Text.translatable("announcement.role." + role.identifier().getNamespace()
			+ "." + role.identifier().getPath());
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(player);
	}
}
