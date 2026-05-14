package dev.mapselect.role.covenant;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.CovenantBatPayload;
import dev.mapselect.network.CovenantBitePayload;
import dev.mapselect.network.CovenantStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class CovenantManager {
	private static final int BLOOD_MAX = 20 * 120;
	private static final int BLOOD_FILL_PER_BITE = BLOOD_MAX / 2;
	private static final int DRACULA_DRAIN_PER_TICK = 1;
	private static final int VAMPIRE_DRAIN_PER_TICK = 2;
	private static final int BAT_MAX = 20 * 10;
	private static final double BITE_RANGE = 3.0D;
	private static final Random RANDOM = new Random();
	private static final Map<UUID, Integer> bloodByPlayer = new HashMap<>();
	private static final Map<UUID, Integer> batTicksByPlayer = new HashMap<>();
	private static final Map<UUID, BatState> batStates = new HashMap<>();
	private static int syncTicker;

	private CovenantManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CovenantBitePayload.ID, CovenantBitePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CovenantBatPayload.ID, CovenantBatPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CovenantStatePayload.ID, CovenantStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CovenantBitePayload.ID,
			(payload, context) -> context.server().execute(() -> tryBite(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(CovenantBatPayload.ID,
			(payload, context) -> context.server().execute(() -> toggleBat(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(CovenantManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearRound(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearRound(world));
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearRound(world);
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			RoleKind kind = roleKind(player);
			if (kind == RoleKind.NONE || VultureManager.isStashed(player) || !isPlayable(player, player)) {
				boolean hadState = hasState(player.getUuid());
				clearPlayer(player, true);
				if (hadState) sendState(player, CovenantStatePayload.clear());
				continue;
			}

			UUID id = player.getUuid();
			int blood = bloodByPlayer.getOrDefault(id, BLOOD_MAX);
			blood = Math.max(0, blood - (kind == RoleKind.DRACULA ? DRACULA_DRAIN_PER_TICK : VAMPIRE_DRAIN_PER_TICK));
			bloodByPlayer.put(id, blood);

			if (blood <= 0) {
				clearPlayer(player, true);
				GameFunctions.killPlayer(player, true, null, GameConstants.DeathReasons.GENERIC);
				continue;
			}

			if (kind == RoleKind.DRACULA) {
				tickBat(player);
			} else {
				endBat(player);
			}
		}

		if (++syncTicker >= 5) {
			syncTicker = 0;
			syncAll(world);
		}
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		List<ServerPlayerEntity> covenant = alive.stream().filter(CovenantManager::isCovenant).toList();
		if (covenant.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == covenant.size()) {
			ServerPlayerEntity winner = covenant.stream().filter(CovenantManager::isDracula).findFirst()
				.orElse(covenant.getFirst());
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.covenant",
				MapSelectRoles.DRACULA == null ? 0xB81832 : MapSelectRoles.DRACULA.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static void tryBite(ServerPlayerEntity user) {
		if (user == null || user.getWorld().isClient) return;
		RoleKind kind = roleKind(user);
		if (kind == RoleKind.NONE || VultureManager.isStashed(user) || !canUseHere(user.getWorld(), user)
				|| !isPlayable(user, user)) {
			return;
		}
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(user, user.getServerWorld().getPlayers(),
			BITE_RANGE, 0.0D, true, candidate -> candidate != user
				&& !VultureManager.isStashed(candidate)
				&& !isCovenant(candidate)
				&& isPlayable(candidate, user));
		if (target == null) {
			user.sendMessage(Text.literal("No living player close enough to bite.").formatted(Formatting.RED), true);
			return;
		}

		boolean turned = kind == RoleKind.DRACULA && RANDOM.nextInt(100) < 10;
		if (turned) {
			assignVampire(target);
			bloodByPlayer.put(target.getUuid(), BLOOD_MAX);
			fillBlood(user);
			user.sendMessage(Text.literal("You turned " + target.getName().getString() + " into a Vampire.")
				.formatted(Formatting.DARK_RED), true);
			target.sendMessage(Text.literal("Dracula turned you into a Vampire.").formatted(Formatting.DARK_RED), true);
			SpyManager.recordInteraction(user, target);
			sync(user);
			sync(target);
			return;
		}

		GameFunctions.killPlayer(target, true, user, GameConstants.DeathReasons.KNIFE);
		fillBlood(user);
		SpyManager.recordInteraction(user, target);
		sync(user);
	}

	private static void toggleBat(ServerPlayerEntity player) {
		if (player == null || !isDracula(player) || VultureManager.isStashed(player)
				|| !canUseHere(player.getWorld(), player) || !isPlayable(player, player)) {
			return;
		}
		if (batStates.containsKey(player.getUuid())) {
			endBat(player);
			sync(player);
			return;
		}
		int ticks = batTicksByPlayer.getOrDefault(player.getUuid(), BAT_MAX);
		if (ticks <= 0) {
			player.sendMessage(Text.literal("Bat form is drained.").formatted(Formatting.RED), true);
			return;
		}
		startBat(player);
		sync(player);
	}

	private static void assignVampire(ServerPlayerEntity target) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(target.getWorld());
		if (game == null || MapSelectRoles.VAMPIRE == null) return;
		Role previous = game.getRole(target);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(target, previous);
		}
		game.addRole(target, MapSelectRoles.VAMPIRE);
		if (!Harpymodloader.VANNILA_ROLES.contains(MapSelectRoles.VAMPIRE)) {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(target, MapSelectRoles.VAMPIRE);
		}
		game.sync();
	}

	private static void tickBat(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		BatState state = batStates.get(id);
		int ticks = batTicksByPlayer.getOrDefault(id, BAT_MAX);
		if (state != null) {
			ticks = Math.max(0, ticks - 1);
			batTicksByPlayer.put(id, ticks);
			Entity entity = player.getServerWorld().getEntity(state.batId());
			if (entity instanceof BatEntity bat) {
				bat.setRoosting(false);
				bat.setNoGravity(true);
				bat.refreshPositionAndAngles(player.getX(), player.getY() + 0.6D, player.getZ(),
					player.getYaw(), player.getPitch());
				bat.setVelocity(player.getVelocity());
			}
			if (!player.getAbilities().allowFlying || !player.getAbilities().flying) {
				player.getAbilities().allowFlying = true;
				player.getAbilities().flying = true;
				player.sendAbilitiesUpdate();
			}
			player.fallDistance = 0.0F;
			if (ticks <= 0) endBat(player);
		} else if (ticks < BAT_MAX) {
			batTicksByPlayer.put(id, Math.min(BAT_MAX, ticks + 1));
		}
	}

	private static void startBat(ServerPlayerEntity player) {
		BatEntity bat = EntityType.BAT.create(player.getServerWorld());
		if (bat == null) return;
		bat.refreshPositionAndAngles(player.getX(), player.getY() + 0.6D, player.getZ(),
			player.getYaw(), player.getPitch());
		bat.setInvulnerable(true);
		bat.setSilent(true);
		bat.setRoosting(false);
		bat.setNoGravity(true);
		bat.setAiDisabled(true);
		player.getServerWorld().spawnEntity(bat);

		BatState state = new BatState(player.getAbilities().allowFlying, player.getAbilities().flying,
			player.isInvisible(), bat.getUuid());
		batStates.put(player.getUuid(), state);
		player.getAbilities().allowFlying = true;
		player.getAbilities().flying = true;
		player.setInvisible(true);
		player.sendAbilitiesUpdate();
		player.networkHandler.sendPacket(new SetCameraEntityS2CPacket(bat));
	}

	private static void endBat(ServerPlayerEntity player) {
		if (player == null) return;
		BatState state = batStates.remove(player.getUuid());
		if (state == null) return;
		Entity entity = player.getServerWorld().getEntity(state.batId());
		if (entity != null) entity.discard();
		player.getAbilities().allowFlying = state.allowFlying();
		player.getAbilities().flying = state.flying();
		player.setInvisible(state.invisible());
		player.sendAbilitiesUpdate();
		player.networkHandler.sendPacket(new SetCameraEntityS2CPacket(player));
	}

	private static void fillBlood(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		int blood = bloodByPlayer.getOrDefault(id, BLOOD_MAX);
		bloodByPlayer.put(id, Math.min(BLOOD_MAX, blood + BLOOD_FILL_PER_BITE));
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isPlayable(PlayerEntity player, PlayerEntity user) {
		if (GexpressTestState.isRoleTester(user)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	public static boolean isCovenant(PlayerEntity player) {
		RoleKind kind = roleKind(player);
		return kind == RoleKind.DRACULA || kind == RoleKind.VAMPIRE;
	}

	public static boolean isDracula(PlayerEntity player) {
		return roleKind(player) == RoleKind.DRACULA;
	}

	private static RoleKind roleKind(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return RoleKind.NONE;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return RoleKind.NONE;
		Role role = game.getRole(player);
		if (role == null || role.identifier() == null) return RoleKind.NONE;
		if (MapSelectRoles.DRACULA_ID.equals(role.identifier())) return RoleKind.DRACULA;
		if (MapSelectRoles.VAMPIRE_ID.equals(role.identifier())) return RoleKind.VAMPIRE;
		return RoleKind.NONE;
	}

	private static void syncAll(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) sync(player);
	}

	private static void sync(ServerPlayerEntity player) {
		RoleKind kind = roleKind(player);
		if (kind == RoleKind.NONE || VultureManager.isStashed(player)) {
			sendState(player, CovenantStatePayload.clear());
			return;
		}
		UUID id = player.getUuid();
		sendState(player, new CovenantStatePayload(true, kind == RoleKind.DRACULA,
			bloodByPlayer.getOrDefault(id, BLOOD_MAX), BLOOD_MAX,
			batTicksByPlayer.getOrDefault(id, BAT_MAX), BAT_MAX, batStates.containsKey(id)));
	}

	private static void sendState(ServerPlayerEntity player, CovenantStatePayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, CovenantStatePayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void clearPlayer(ServerPlayerEntity player, boolean restoreBat) {
		if (player == null) return;
		if (restoreBat) endBat(player);
		bloodByPlayer.remove(player.getUuid());
		batTicksByPlayer.remove(player.getUuid());
	}

	private static boolean hasState(UUID playerId) {
		return playerId != null && (bloodByPlayer.containsKey(playerId)
			|| batTicksByPlayer.containsKey(playerId)
			|| batStates.containsKey(playerId));
	}

	private static void clearRound(World world) {
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				clearPlayer(player, true);
				sendState(player, CovenantStatePayload.clear());
			}
		}
		bloodByPlayer.clear();
		batTicksByPlayer.clear();
		batStates.clear();
		syncTicker = 0;
	}

	private enum RoleKind {
		NONE,
		DRACULA,
		VAMPIRE
	}

	private record BatState(boolean allowFlying, boolean flying, boolean invisible, UUID batId) {}
}
