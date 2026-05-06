package dev.mapselect.role.skincrawler;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.SkincrawlerSkinPayload;
import dev.mapselect.network.SkincrawlerUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkincrawlerManager {
	private static final double LOOK_RADIUS_SQUARED = 1.4D;
	private static final Map<UUID, UUID> skinSwaps = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
	private static final Map<UUID, Stun> stunned = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> preservedBodyOwners = new ConcurrentHashMap<>();
	private static int syncTick;

	private SkincrawlerManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(SkincrawlerUsePayload.ID, SkincrawlerUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SkincrawlerSkinPayload.ID, SkincrawlerSkinPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkincrawlerUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryStealSkin(context.player())));
		AllowPlayerDeath.EVENT.register(SkincrawlerManager::allowDeath);
		ServerTickEvents.END_WORLD_TICK.register(SkincrawlerManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sync(handler.player)));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
	}

	private static void tryStealSkin(ServerPlayerEntity skincrawler) {
		if (skincrawler == null || !(skincrawler.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(skincrawler) || !isSkincrawler(skincrawler)
				|| !canUseHere(world, skincrawler) || !GameFunctions.isPlayerAliveAndSurvival(skincrawler)) return;
		long remaining = cooldownRemaining(skincrawler);
		if (remaining > 0L) {
			AbilityCooldownSync.send(skincrawler, AbilityCooldownPayload.SKINCRAWLER_STEAL,
				remaining, (long) GexpressConfig.getSkincrawlerCooldownSeconds() * 20L, false);
			return;
		}
		PlayerBodyEntity body = findBody(skincrawler);
		if (body == null) {
			skincrawler.sendMessage(Text.literal("No fresh body close enough."), true);
			return;
		}
		int maxAge = GexpressConfig.getSkincrawlerBodyMaxAgeSeconds() * 20;
		if (body.age > maxAge) {
			skincrawler.sendMessage(Text.literal("That skin is too far gone."), true);
			return;
		}

		UUID previousSkin = skinSwaps.getOrDefault(skincrawler.getUuid(), skincrawler.getUuid());
		UUID stolenSkin = body.getPlayerUuid();
		body.setPlayerUuid(previousSkin);
		body.age = 0;
		preservedBodyOwners.put(body.getUuid(), previousSkin);
		skinSwaps.put(skincrawler.getUuid(), stolenSkin);
		long cooldown = (long) GexpressConfig.getSkincrawlerCooldownSeconds() * 20L;
		cooldownUntil.put(skincrawler.getUuid(), world.getTime() + cooldown);
		AbilityCooldownSync.send(skincrawler, AbilityCooldownPayload.SKINCRAWLER_STEAL, cooldown, cooldown, false);
		skincrawler.sendMessage(Text.literal("Skin stolen."), true);
		broadcast(world);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (!(victim instanceof ServerPlayerEntity player) || !isSkincrawler(player)) return true;
		if (!GameConstants.DeathReasons.GUN.equals(reason)) return true;
		long now = player.getWorld().getTime();
		Stun current = stunned.get(player.getUuid());
		if (current != null && current.untilTick() > now) return true;
		stunned.put(player.getUuid(), new Stun(now + (long) GexpressConfig.getSkincrawlerStunSeconds() * 20L,
			player.getPos(), player.getYaw(), player.getPitch()));
		player.sendMessage(Text.literal("You are stunned!"), true);
		return false;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			UUID owner = preservedBodyOwners.get(body.getUuid());
			if (owner != null) {
				body.setPlayerUuid(owner);
				body.age = 0;
			}
		}
		long now = world.getTime();
		stunned.entrySet().removeIf(entry -> {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			Stun stun = entry.getValue();
			if (player == null || player.getWorld() != world || stun.untilTick() <= now) return true;
			player.refreshPositionAndAngles(stun.pos().x, stun.pos().y, stun.pos().z, stun.yaw(), stun.pitch());
			player.setVelocity(Vec3d.ZERO);
			player.velocityModified = true;
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 255, false, false, false));
			return false;
		});
		if (++syncTick < 10) return;
		syncTick = 0;
		for (ServerPlayerEntity player : world.getPlayers(SkincrawlerManager::isSkincrawler)) {
			long remaining = cooldownRemaining(player);
			if (remaining > 0L) {
				AbilityCooldownSync.send(player, AbilityCooldownPayload.SKINCRAWLER_STEAL, remaining,
					(long) GexpressConfig.getSkincrawlerCooldownSeconds() * 20L, false);
			}
		}
	}

	private static PlayerBodyEntity findBody(ServerPlayerEntity skincrawler) {
		double range = GexpressConfig.getSkincrawlerRange();
		Vec3d eye = skincrawler.getEyePos();
		Vec3d look = skincrawler.getRotationVec(1.0F).normalize();
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (PlayerBodyEntity body : skincrawler.getServerWorld().getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
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

	private static boolean isSkincrawler(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && MapSelectRoles.SKINCRAWLER_ID.equals(role.identifier());
	}

	public static UUID replacementFor(UUID playerId) {
		return playerId == null ? null : skinSwaps.get(playerId);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static long cooldownRemaining(ServerPlayerEntity player) {
		Long until = cooldownUntil.get(player.getUuid());
		if (until == null) return 0L;
		long remaining = until - player.getWorld().getTime();
		if (remaining <= 0L) {
			cooldownUntil.remove(player.getUuid());
			return 0L;
		}
		return remaining;
	}

	private static void sync(ServerPlayerEntity player) {
		if (player != null && ServerPlayNetworking.canSend(player, SkincrawlerSkinPayload.ID)) {
			ServerPlayNetworking.send(player, new SkincrawlerSkinPayload(new HashMap<>(skinSwaps)));
		}
	}

	private static void broadcast(ServerWorld world) {
		SkincrawlerSkinPayload payload = new SkincrawlerSkinPayload(new HashMap<>(skinSwaps));
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SkincrawlerSkinPayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void clear(World world) {
		skinSwaps.clear();
		cooldownUntil.clear();
		stunned.clear();
		preservedBodyOwners.clear();
		if (world instanceof ServerWorld serverWorld) broadcast(serverWorld);
	}

	public static TimeState snapshotForTimeRewind() {
		return new TimeState(new HashMap<>(skinSwaps), new HashMap<>(cooldownUntil),
			new HashMap<>(stunned), new HashMap<>(preservedBodyOwners));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		skinSwaps.clear();
		cooldownUntil.clear();
		stunned.clear();
		preservedBodyOwners.clear();
		if (state != null) {
			skinSwaps.putAll(state.skinSwaps());
			cooldownUntil.putAll(state.cooldownUntil());
			stunned.putAll(state.stunned());
			preservedBodyOwners.putAll(state.preservedBodyOwners());
		}
		if (world != null) broadcast(world);
	}

	public record TimeState(Map<UUID, UUID> skinSwaps, Map<UUID, Long> cooldownUntil,
			Map<UUID, Stun> stunned, Map<UUID, UUID> preservedBodyOwners) {}

	public record Stun(long untilTick, Vec3d pos, float yaw, float pitch) {}
}
