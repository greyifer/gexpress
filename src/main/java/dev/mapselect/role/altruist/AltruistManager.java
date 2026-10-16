package dev.mapselect.role.altruist;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.role.altruist.AltruistUsePayload;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.testing.GexpressTestState;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.altruist.AltruistFeedPayload;
import dev.mapselect.network.role.altruist.AltruistStatePayload;
import dev.mapselect.network.role.altruist.AltruistTerminatePayload;
import dev.mapselect.role.AbilityTargeting;
import net.minecraft.entity.LivingEntity;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AltruistManager {
	private static final double LOOK_RADIUS_SQUARED = 1.25D;
	private static final int MAX_REVIVES = 2;
	private static final Map<UUID, LinkedHashSet<UUID>> CONTROLLED_BY_ALTRUIST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> REVIVES_USED = new ConcurrentHashMap<>();
	private static final Map<String, Long> NEXT_FEED_TICK = new ConcurrentHashMap<>();
	private static int tickGate;

	private AltruistManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(AltruistUsePayload.ID, AltruistUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AltruistTerminatePayload.ID, AltruistTerminatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AltruistStatePayload.ID, AltruistStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AltruistFeedPayload.ID, AltruistFeedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AltruistUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryRevive(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(AltruistTerminatePayload.ID,
			(payload, context) -> context.server().execute(() -> tryTerminate(context.player())));
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearRound(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearRound(world));
		ServerTickEvents.END_WORLD_TICK.register(AltruistManager::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(AltruistManager::afterDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.player != null) removeControlledPlayer(handler.player);
		});
	}

	private static void tryRevive(ServerPlayerEntity altruist) {
		if (altruist == null || !(altruist.getWorld() instanceof ServerWorld world)) return;
		boolean roleTester = GexpressTestState.isRoleTester(altruist);
		if (PelicanManager.isStashed(altruist) || !isAltruist(altruist)
				|| !canUseHere(world, altruist)
				|| (!roleTester && !GameFunctions.isPlayerAliveAndSurvival(altruist))) {
			return;
		}
		int used = REVIVES_USED.getOrDefault(altruist.getUuid(), 0);
		if (used >= MAX_REVIVES && !GexpressTestState.hasCreativeAbilityBypass(altruist)) {
			altruist.sendMessage(Text.literal("You have already used both revives."), true);
			return;
		}
		PlayerBodyEntity body = findBody(altruist);
		if (body == null) {
			altruist.sendMessage(Text.literal("No body close enough to revive."), true);
			return;
		}
		if (!AltruistRevivePolicy.canReviveBodyAge(body.age)) {
			altruist.sendMessage(Text.literal("That body has been dead too long to revive."), true);
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

		List<ServerPlayerEntity> soundTargets = new ArrayList<>();
		soundTargets.add(altruist);
		if (revivePlayer(world, target, revivePos, yaw, Text.literal("The Altruist revived you."))) {
			soundTargets.add(target);
			addControlled(altruist, target);
		}
		ServerPlayerEntity linkedLover = remainingRevives(altruist) > 0
			? reviveLinkedLover(world, target) : null;
		if (linkedLover != null) soundTargets.add(linkedLover);
		if (linkedLover != null) addControlled(altruist, linkedLover);

		AbilitySounds.playTo(soundTargets, SoundEvents.BLOCK_BEACON_ACTIVATE,
			SoundCategory.PLAYERS, 0.9F, 1.35F);
		altruist.sendMessage(Text.literal("Revives used: "
			+ REVIVES_USED.getOrDefault(altruist.getUuid(), 0) + "/" + MAX_REVIVES + "."), true);
	}

	private static void tryTerminate(ServerPlayerEntity altruist) {
		if (altruist == null || !(altruist.getWorld() instanceof ServerWorld world)
				|| !isAltruist(altruist) || PelicanManager.isStashed(altruist)
				|| !canUseHere(world, altruist)
				|| (!GexpressTestState.isRoleTester(altruist)
					&& !GameFunctions.isPlayerAliveAndSurvival(altruist))) return;
		LinkedHashSet<UUID> controlled = CONTROLLED_BY_ALTRUIST.get(altruist.getUuid());
		if (controlled == null || controlled.isEmpty()) {
			altruist.sendMessage(Text.literal("You are not controlling any revived players."), true);
			return;
		}
		List<ServerPlayerEntity> candidates = controlled.stream()
			.map(id -> world.getServer().getPlayerManager().getPlayer(id))
			.filter(player -> player != null && player.getWorld() == world
				&& GameFunctions.isPlayerAliveAndSurvival(player))
			.toList();
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(altruist, candidates,
			Math.max(3.0D, GexpressConfig.getAltruistRange()), 0.0D, true, player -> true);
		if (target == null && candidates.size() == 1) target = candidates.getFirst();
		if (target == null) {
			altruist.sendMessage(Text.literal("Look at one of your revived players to end their life."), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(altruist, target)) return;
		altruist.sendMessage(Text.literal("You ended " + target.getName().getString() + "'s revived life."), true);
		GameFunctions.killPlayer(target, true, altruist, GameConstants.DeathReasons.GENERIC);
	}

	private static int remainingRevives(ServerPlayerEntity altruist) {
		if (GexpressTestState.hasCreativeAbilityBypass(altruist)) return MAX_REVIVES;
		return Math.max(0, MAX_REVIVES - REVIVES_USED.getOrDefault(altruist.getUuid(), 0));
	}

	private static void addControlled(ServerPlayerEntity altruist, ServerPlayerEntity revived) {
		if (altruist == null || revived == null) return;
		CONTROLLED_BY_ALTRUIST.computeIfAbsent(altruist.getUuid(), ignored -> new LinkedHashSet<>())
			.add(revived.getUuid());
		if (!GexpressTestState.hasCreativeAbilityBypass(altruist)) {
			REVIVES_USED.merge(altruist.getUuid(), 1, Integer::sum);
		}
		sendState(altruist);
	}

	private static boolean revivePlayer(ServerWorld world, ServerPlayerEntity target, Vec3d revivePos, float yaw,
			Text message) {
		if (world == null || target == null || revivePos == null || GameFunctions.isPlayerAliveAndSurvival(target)) {
			return false;
		}
		target.changeGameMode(GameMode.ADVENTURE);
		target.teleport(world, revivePos.x, revivePos.y, revivePos.z, yaw, 0.0F);
		target.setHealth(target.getMaxHealth());
		target.setFireTicks(0);
		target.setVelocity(Vec3d.ZERO);
		target.velocityModified = true;
		target.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
		TrainVoicePlugin.resetPlayer(target.getUuid());
		ModifierUtils.addEvenIfPresent(target, MapSelectModifiers.MUTED);
		if (message != null) target.sendMessage(message, true);
		return true;
	}

	private static ServerPlayerEntity reviveLinkedLover(ServerWorld world, ServerPlayerEntity revived) {
		if (world == null || revived == null) return null;
		UUID partnerId = LoversManager.partnerId(revived.getUuid());
		if (partnerId == null) return null;
		ServerPlayerEntity partner = world.getServer().getPlayerManager().getPlayer(partnerId);
		if (partner == null || GameFunctions.isPlayerAliveAndSurvival(partner)) return null;
		PlayerBodyEntity partnerBody = findBodyFor(world, partnerId);
		if (partnerBody == null || !AltruistRevivePolicy.canReviveBodyAge(partnerBody.age)) return null;
		Vec3d revivePos = partnerBody.getPos();
		float yaw = partnerBody.getYaw();
		partnerBody.discard();
		return revivePlayer(world, partner, revivePos, yaw,
			Text.literal("The Altruist revived your lover, pulling you back too.")) ? partner : null;
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

	private static PlayerBodyEntity findBodyFor(ServerWorld world, UUID playerId) {
		if (world == null || playerId == null) return null;
		for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY,
				entity -> playerId.equals(entity.getPlayerUuid()))) {
			return body;
		}
		return null;
	}

	private static boolean isAltruist(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.ALTRUIST_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.ALTRUIST_ID));
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	public static void recordInteraction(ServerPlayerEntity actor, ServerPlayerEntity other) {
		if (actor == null || other == null || actor == other) return;
		for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : CONTROLLED_BY_ALTRUIST.entrySet()) {
			if (!entry.getValue().contains(actor.getUuid()) && !entry.getValue().contains(other.getUuid())) continue;
			ServerPlayerEntity altruist = actor.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (altruist != null && isAltruist(altruist) && isPlayable(altruist)) {
				sendFeed(altruist, actor.getName().getString() + " interacted with "
					+ other.getName().getString() + ".");
			}
		}
	}

	public static void recordTask(PlayerEntity player, PlayerMoodComponent.TrainTask task) {
		if (!(player instanceof ServerPlayerEntity target) || task == null) return;
		String action = switch (task.getType()) {
			case SLEEP -> "slept";
			case OUTSIDE -> "got fresh air";
			case EAT -> "ate";
			case DRINK -> "drank";
		};
		for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : CONTROLLED_BY_ALTRUIST.entrySet()) {
			if (!entry.getValue().contains(target.getUuid())) continue;
			ServerPlayerEntity altruist = target.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (altruist != null && isAltruist(altruist) && isPlayable(altruist)) {
				sendFeed(altruist, target.getName().getString() + " " + action + ".");
			}
		}
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
		if (entity instanceof ServerPlayerEntity player) removeControlledPlayer(player);
	}

	private static void removeControlledPlayer(ServerPlayerEntity player) {
		if (player == null) return;
		for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : new ArrayList<>(CONTROLLED_BY_ALTRUIST.entrySet())) {
			if (!entry.getValue().remove(player.getUuid())) continue;
			ServerPlayerEntity altruist = player.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (entry.getValue().isEmpty()) CONTROLLED_BY_ALTRUIST.remove(entry.getKey());
			if (altruist != null) {
				sendFeed(altruist, player.getName().getString() + " died.");
				sendState(altruist);
			}
		}
	}

	private static void tick(ServerWorld world) {
		if (!dev.mapselect.game.GexpressWorlds.isGameWorld(world) || ++tickGate < 10) return;
		tickGate = 0;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isAltruist(player)) sendState(player);
			else if (CONTROLLED_BY_ALTRUIST.remove(player.getUuid()) != null) sendClear(player);
		}
		NEXT_FEED_TICK.entrySet().removeIf(entry -> entry.getValue() < world.getTime() - 200L);
	}

	private static void sendState(ServerPlayerEntity altruist) {
		if (altruist == null) return;
		LinkedHashSet<UUID> controlled = CONTROLLED_BY_ALTRUIST.getOrDefault(altruist.getUuid(), new LinkedHashSet<>());
		List<AltruistStatePayload.Entry> entries = new ArrayList<>();
		for (UUID targetId : controlled) {
			ServerPlayerEntity target = altruist.getServer().getPlayerManager().getPlayer(targetId);
			if (target != null && GameFunctions.isPlayerAliveAndSurvival(target)) {
				entries.add(new AltruistStatePayload.Entry(targetId, target.getName().getString()));
			}
		}
		PayloadSync.sendIfChanged(altruist, AltruistStatePayload.ID,
			new AltruistStatePayload(entries, REVIVES_USED.getOrDefault(altruist.getUuid(), 0), MAX_REVIVES));
	}

	private static void sendClear(ServerPlayerEntity player) {
		PayloadSync.sendIfChanged(player, AltruistStatePayload.ID, AltruistStatePayload.clear());
	}

	private static void sendFeed(ServerPlayerEntity altruist, String line) {
		if (altruist == null || line == null || line.isBlank()
				|| !ServerPlayNetworking.canSend(altruist, AltruistFeedPayload.ID)) return;
		long now = altruist.getWorld().getTime();
		String key = altruist.getUuid() + ":" + line;
		if (now < NEXT_FEED_TICK.getOrDefault(key, 0L)) return;
		NEXT_FEED_TICK.put(key, now + 20L);
		ServerPlayNetworking.send(altruist, new AltruistFeedPayload(line));
	}

	private static boolean isPlayable(ServerPlayerEntity player) {
		return GexpressTestState.isRoleTester(player) || DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static void clearRound(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) sendClear(player);
		}
		CONTROLLED_BY_ALTRUIST.clear();
		REVIVES_USED.clear();
		NEXT_FEED_TICK.clear();
		tickGate = 0;
		PayloadSync.forgetAll(AltruistStatePayload.ID);
	}

	public static TimeState snapshotForTimeRewind() {
		Map<UUID, List<UUID>> controlled = new ConcurrentHashMap<>();
		for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : CONTROLLED_BY_ALTRUIST.entrySet()) {
			controlled.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return new TimeState(Map.copyOf(controlled), Map.copyOf(REVIVES_USED));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		CONTROLLED_BY_ALTRUIST.clear();
		REVIVES_USED.clear();
		if (state != null) {
			for (Map.Entry<UUID, List<UUID>> entry : state.controlledPlayers().entrySet()) {
				CONTROLLED_BY_ALTRUIST.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
			}
			REVIVES_USED.putAll(state.revivesUsed());
		}
		if (world != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (isAltruist(player)) sendState(player);
				else sendClear(player);
			}
		}
	}

	public record TimeState(Map<UUID, List<UUID>> controlledPlayers, Map<UUID, Integer> revivesUsed) {}
}
