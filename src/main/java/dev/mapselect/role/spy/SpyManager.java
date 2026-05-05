package dev.mapselect.role.spy;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.SpyFeedPayload;
import dev.mapselect.network.SpyUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpyManager {
	private static final double LOOK_RADIUS_SQUARED = 1.2D;
	private static final Map<UUID, Bug> bugsBySpy = new ConcurrentHashMap<>();
	private static final Map<String, Long> nextLineTick = new ConcurrentHashMap<>();
	private static int tickGate;

	private SpyManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SpyUsePayload.ID, SpyUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpyFeedPayload.ID, SpyFeedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpyUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryBug(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(SpyManager::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity actor) {
				recordInteraction(actor, entity);
			}
			return ActionResult.PASS;
		});
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear());
	}

	private static void tryBug(ServerPlayerEntity spy) {
		if (spy == null || !(spy.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(spy) || !isSpy(spy) || !canUseHere(world, spy)
				|| !GameFunctions.isPlayerAliveAndSurvival(spy)) return;
		ServerPlayerEntity target = findTarget(spy);
		if (target == null) {
			spy.sendMessage(Text.literal("No living player close enough to bug."), true);
			return;
		}
		PlayerShopComponent shop = PlayerShopComponent.KEY.get(spy);
		int cost = GexpressConfig.getSpyBugCost();
		if (cost > 0 && shop.balance < cost) {
			spy.sendMessage(Text.literal("You need " + cost + " coins to plant a bug."), true);
			return;
		}
		if (cost > 0) {
			shop.setBalance(shop.balance - cost);
			PlayerShopComponent.KEY.sync(spy);
		}
		long expires = world.getTime() + (long) GexpressConfig.getSpyBugDurationSeconds() * 20L;
		bugsBySpy.put(spy.getUuid(), new Bug(target.getUuid(), expires, target.getName().getString()));
		send(spy, "Bug planted on " + target.getName().getString() + ".");
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		if (++tickGate < 10) return;
		tickGate = 0;
		long now = world.getTime();
		Iterator<Map.Entry<UUID, Bug>> iterator = bugsBySpy.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Bug> entry = iterator.next();
			ServerPlayerEntity spy = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			Bug bug = entry.getValue();
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(bug.targetId());
			if (spy == null || target == null || spy.getWorld() != world || target.getWorld() != world
					|| bug.expiresAtTick() <= now || !isSpy(spy) || !GameFunctions.isPlayerAliveAndSurvival(spy)
					|| !isPlayable(target, spy)) {
				iterator.remove();
			}
		}
		nextLineTick.entrySet().removeIf(entry -> entry.getValue() <= now - 20L * 10L);
	}

	public static void recordTask(PlayerEntity player, PlayerMoodComponent.Task task) {
		if (!(player instanceof ServerPlayerEntity target) || task == null) return;
		String action = switch (task) {
			case SLEEP -> "slept";
			case OUTSIDE -> "got fresh air";
			case EAT -> "ate";
			case DRINK -> "drank";
		};
		sendToSpies(target, target.getName().getString() + " " + action + ".");
	}

	private static void recordInteraction(ServerPlayerEntity actor, Entity entity) {
		if (!(entity instanceof ServerPlayerEntity other) || actor == other) return;
		if (!isPlayable(actor, actor)) return;
		sendToSpies(actor, actor.getName().getString() + " interacted with " + other.getName().getString() + ".");
	}

	private static void sendToSpies(ServerPlayerEntity target, String line) {
		if (target == null || line == null || line.isBlank()) return;
		long now = target.getWorld().getTime();
		for (Map.Entry<UUID, Bug> entry : new LinkedHashMap<>(bugsBySpy).entrySet()) {
			Bug bug = entry.getValue();
			if (!target.getUuid().equals(bug.targetId()) || bug.expiresAtTick() <= now) continue;
			ServerPlayerEntity spy = target.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (spy == null || spy.getWorld() != target.getWorld() || !isSpy(spy)) continue;
			String throttleKey = spy.getUuid() + ":" + target.getUuid() + ":" + line;
			if (now < nextLineTick.getOrDefault(throttleKey, 0L)) continue;
			nextLineTick.put(throttleKey, now + 20L);
			send(spy, line);
		}
	}

	private static ServerPlayerEntity findTarget(ServerPlayerEntity spy) {
		double range = GexpressConfig.getSpyBugRange();
		Vec3d eye = spy.getEyePos();
		Vec3d look = spy.getRotationVec(1.0F).normalize();
		ServerPlayerEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (ServerPlayerEntity candidate : spy.getServerWorld().getPlayers()) {
			if (candidate == spy || VultureManager.isStashed(candidate) || !isPlayable(candidate, spy)) continue;
			Vec3d to = candidate.getEyePos().subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > range || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > LOOK_RADIUS_SQUARED || !spy.canSee(candidate)) continue;
			best = candidate;
			bestAlong = along;
		}
		return best;
	}

	private static void send(ServerPlayerEntity spy, String line) {
		if (spy != null && ServerPlayNetworking.canSend(spy, SpyFeedPayload.ID)) {
			ServerPlayNetworking.send(spy, new SpyFeedPayload(line));
		}
	}

	private static boolean isSpy(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.SPY_ID.equals(role.identifier());
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(user);
	}

	private static void clear() {
		bugsBySpy.clear();
		nextLineTick.clear();
		tickGate = 0;
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(bugsBySpy), new HashMap<>(nextLineTick));
	}

	public static void restoreForTimeRewind(TimeState state) {
		clear();
		if (state == null) return;
		bugsBySpy.putAll(state.bugsBySpy());
		nextLineTick.putAll(state.nextLineTick());
	}

	public record Bug(UUID targetId, long expiresAtTick, String targetName) {}

	public record TimeState(Map<UUID, Bug> bugsBySpy, Map<String, Long> nextLineTick) {}
}
