package dev.mapselect.role.warlock;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.network.WarlockKillPayload;
import dev.mapselect.network.WarlockMarkPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WarlockManager {
	private static final double MARK_RANGE = 4.0D;
	private static final double LOOK_RADIUS_SQUARED = 1.0D;
	public static final double KILL_RANGE = 3.0D;
	private static final double KILL_RANGE_SQUARED = KILL_RANGE * KILL_RANGE;

	private WarlockManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(WarlockMarkPayload.ID, WarlockMarkPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(WarlockKillPayload.ID, WarlockKillPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WarlockMarkPayload.ID,
			(payload, context) -> context.server().execute(() -> tryMark(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(WarlockKillPayload.ID,
			(payload, context) -> context.server().execute(() -> tryHexKill(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(WarlockManager::tick);
	}

	private static void tryMark(ServerPlayerEntity warlock) {
		if (warlock == null || warlock.getWorld().isClient) return;
		if (VultureManager.isStashed(warlock)) return;
		if (!canUseWarlockHere(warlock.getWorld(), warlock) || !isWarlock(warlock)) return;
		ServerPlayerEntity target = findLookTarget(warlock, MARK_RANGE);
		if (target == null) {
			warlock.sendMessage(Text.literal("No living player close enough to mark."), true);
			return;
		}
		if (target == warlock) return;
		if (!isPlayableForWarlock(warlock, warlock) || !isPlayableForWarlock(target, warlock)) {
			return;
		}

		WarlockComponent comp = WarlockComponent.KEY.getNullable(warlock.getWorld());
		if (comp == null) return;

		UUID currentMark = comp.getMarkedTarget(warlock.getUuid());
		if (target.getUuid().equals(currentMark)) {
			warlock.sendMessage(Text.literal("Marked " + target.getName().getString() + "."), true);
			return;
		}

		long remaining = comp.markCooldownRemainingTicks(warlock.getUuid());
		if (remaining > 0L) {
			warlock.sendMessage(Text.literal("Mark ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}

		if (!comp.assignMark(warlock.getUuid(), target.getUuid())) {
			warlock.sendMessage(Text.literal("Could not mark target."), true);
			return;
		}

		warlock.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.7F, 0.55F);
		warlock.sendMessage(Text.literal("Marked " + target.getName().getString() + "."), true);
	}

	private static void tryHexKill(ServerPlayerEntity warlock) {
		if (warlock == null || warlock.getWorld().isClient) return;
		if (VultureManager.isStashed(warlock)) return;
		if (!canUseWarlockHere(warlock.getWorld(), warlock) || !isWarlock(warlock)) return;
		if (!isPlayableForWarlock(warlock, warlock)) return;

		WarlockComponent comp = WarlockComponent.KEY.getNullable(warlock.getWorld());
		if (comp == null) return;

		UUID markId = comp.getMarkedTarget(warlock.getUuid());
		if (markId == null) {
			warlock.sendMessage(Text.literal("No player marked."), true);
			return;
		}

		long cooldown = comp.killCooldownRemainingTicks(warlock.getUuid());
		if (cooldown > 0L) {
			warlock.sendMessage(Text.literal("Hex ready in " + secondsCeil(cooldown) + "s."), true);
			return;
		}

		MinecraftServer server = warlock.getServer();
		ServerPlayerEntity marked = server == null ? null : server.getPlayerManager().getPlayer(markId);
		if (marked == null || marked.getWorld() != warlock.getWorld()
				|| !isPlayableForWarlock(marked, warlock)) {
			comp.removeMark(warlock.getUuid());
			warlock.sendMessage(Text.literal("Your mark is gone."), true);
			return;
		}

		List<ServerPlayerEntity> candidates = killCandidates(marked, warlock);
		if (candidates.isEmpty()) {
			warlock.sendMessage(Text.literal("The mark has no prey nearby."), true);
			return;
		}

		ServerPlayerEntity victim = candidates.get(warlock.getRandom().nextInt(candidates.size()));
		playShot(warlock);
		GameFunctions.killPlayer(victim, true, warlock, GameConstants.DeathReasons.GUN);
		comp.setKillCooldown(warlock.getUuid());
		comp.removeMark(warlock.getUuid());
		warlock.sendMessage(Text.literal("The mark killed " + victim.getName().getString() + "."), true);
	}

	private static List<ServerPlayerEntity> killCandidates(ServerPlayerEntity marked, ServerPlayerEntity warlock) {
		List<ServerPlayerEntity> candidates = new ArrayList<>();
		for (ServerPlayerEntity candidate : marked.getServerWorld().getPlayers(player -> true)) {
			if (candidate == marked || candidate == warlock) continue;
			if (!isPlayableForWarlock(candidate, warlock)) continue;
			if (candidate.squaredDistanceTo(marked) <= KILL_RANGE_SQUARED) {
				candidates.add(candidate);
			}
		}
		return candidates;
	}

	private static ServerPlayerEntity findLookTarget(ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F).normalize();
		ServerPlayerEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (ServerPlayerEntity candidate : player.getServerWorld().getPlayers()) {
			if (candidate == player || !isPlayableForWarlock(candidate, player)) continue;
			Vec3d to = candidate.getEyePos().subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > range || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > LOOK_RADIUS_SQUARED) continue;
			best = candidate;
			bestAlong = along;
		}
		return best;
	}

	private static void playShot(ServerPlayerEntity warlock) {
		ServerWorld world = warlock.getServerWorld();
		world.playSound(null, warlock.getX(), warlock.getEyeY(), warlock.getZ(),
			WatheSounds.ITEM_REVOLVER_SHOOT, SoundCategory.PLAYERS, 5.0F,
			1.0F + (world.getRandom().nextFloat() * 0.1F) - 0.05F);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		WarlockComponent comp = WarlockComponent.KEY.getNullable(world);
		if (comp == null) return;

		if (!isActiveGame(world) && !GexpressTestState.hasRoleTesters()) {
			comp.clearAll();
			return;
		}

		MinecraftServer server = world.getServer();
		for (UUID warlockId : comp.getWarlocks()) {
			ServerPlayerEntity warlock = server.getPlayerManager().getPlayer(warlockId);
			UUID markId = comp.getMarkedTarget(warlockId);
			ServerPlayerEntity marked = markId == null ? null : server.getPlayerManager().getPlayer(markId);
			if (warlock == null || marked == null
					|| marked.getWorld() != world
					|| VultureManager.isStashed(warlock)
					|| !canUseWarlockHere(world, warlock)
					|| !isPlayableForWarlock(warlock, warlock)
					|| !isPlayableForWarlock(marked, warlock)
					|| !isWarlock(warlock)) {
				comp.removeWarlock(warlockId);
			}
		}
	}

	private static boolean isWarlock(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.WARLOCK_ID.equals(role.identifier());
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean canUseWarlockHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayableForWarlock(PlayerEntity player, PlayerEntity warlock) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(warlock);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}
}
