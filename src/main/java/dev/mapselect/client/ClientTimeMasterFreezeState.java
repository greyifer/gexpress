package dev.mapselect.client;

import dev.mapselect.MapSelect;
import dev.mapselect.network.TimeMasterFreezeStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientTimeMasterFreezeState {
	private static final Map<UUID, FrozenState> FROZEN = new ConcurrentHashMap<>();
	private static Field limbPrevSpeedField;
	private static Field limbPosField;
	private static boolean lookedUpLimbFields;

	private ClientTimeMasterFreezeState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TimeMasterFreezeStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(payload)));
		ClientTickEvents.END_WORLD_TICK.register(world -> tick(MinecraftClient.getInstance()));
	}

	public static boolean isLocalFrozen(MinecraftClient client) {
		return client != null && client.player != null && FROZEN.containsKey(client.player.getUuid());
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || player == null) return false;
		FrozenState state = FROZEN.get(player.getUuid());
		return state != null && client.player.getUuid().equals(state.timeMasterId);
	}

	public static int glowColor() {
		return 0x66D9FF;
	}

	private static void apply(TimeMasterFreezeStatePayload payload) {
		if (payload.targetId() == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (!payload.frozen()) {
			FROZEN.remove(payload.targetId());
			return;
		}
		if (client == null || client.world == null || payload.durationTicks() <= 0) return;
		long expiresAt = client.world.getTime() + payload.durationTicks();
		AbstractClientPlayerEntity player = findPlayer(client, payload.targetId());
		FROZEN.put(payload.targetId(), new FrozenState(payload.timeMasterId(), expiresAt,
			player == null ? null : FrozenPose.capture(player)));
	}

	private static void tick(MinecraftClient client) {
		if (client == null || client.world == null) {
			FROZEN.clear();
			return;
		}

		long now = client.world.getTime();
		for (Iterator<Map.Entry<UUID, FrozenState>> it = FROZEN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, FrozenState> entry = it.next();
			FrozenState state = entry.getValue();
			if (now >= state.expiresAt) {
				it.remove();
				continue;
			}

			AbstractClientPlayerEntity player = findPlayer(client, entry.getKey());
			if (player == null) continue;
			if (state.pose == null) state.pose = FrozenPose.capture(player);
			state.pose.apply(player);
		}

		if (isLocalFrozen(client)) {
			lockLocalInput(client);
		}
	}

	private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID playerId) {
		if (client == null || client.world == null || playerId == null) return null;
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (playerId.equals(player.getUuid())) return player;
		}
		return null;
	}

	private static void lockLocalInput(MinecraftClient client) {
		if (client.player == null) return;
		Input input = client.player.input;
		if (input != null) {
			input.movementSideways = 0.0F;
			input.movementForward = 0.0F;
			input.pressingForward = false;
			input.pressingBack = false;
			input.pressingLeft = false;
			input.pressingRight = false;
			input.jumping = false;
			input.sneaking = false;
		}
		client.player.setVelocity(Vec3d.ZERO);
	}

	private static void setLimbPose(LimbAnimator limbAnimator, float prevSpeed, float speed, float pos) {
		if (limbAnimator == null) return;
		try {
			lookupLimbFields();
			if (limbPrevSpeedField != null) limbPrevSpeedField.setFloat(limbAnimator, prevSpeed);
			limbAnimator.setSpeed(speed);
			if (limbPosField != null) limbPosField.setFloat(limbAnimator, pos);
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to freeze Time Master target limb pose: {}", t.toString());
		}
	}

	private static float limbPrevSpeed(LimbAnimator limbAnimator) {
		try {
			lookupLimbFields();
			return limbPrevSpeedField == null ? limbAnimator.getSpeed() : limbPrevSpeedField.getFloat(limbAnimator);
		} catch (Throwable ignored) {
			return limbAnimator.getSpeed();
		}
	}

	private static float limbPos(LimbAnimator limbAnimator) {
		try {
			lookupLimbFields();
			return limbPosField == null ? limbAnimator.getPos() : limbPosField.getFloat(limbAnimator);
		} catch (Throwable ignored) {
			return limbAnimator.getPos();
		}
	}

	private static void lookupLimbFields() throws ReflectiveOperationException {
		if (lookedUpLimbFields) return;
		lookedUpLimbFields = true;
		limbPrevSpeedField = LimbAnimator.class.getDeclaredField("prevSpeed");
		limbPrevSpeedField.setAccessible(true);
		limbPosField = LimbAnimator.class.getDeclaredField("pos");
		limbPosField.setAccessible(true);
	}

	private static final class FrozenState {
		private final UUID timeMasterId;
		private final long expiresAt;
		private FrozenPose pose;

		private FrozenState(UUID timeMasterId, long expiresAt, FrozenPose pose) {
			this.timeMasterId = timeMasterId;
			this.expiresAt = expiresAt;
			this.pose = pose;
		}
	}

	private record FrozenPose(double x, double y, double z, float yaw, float pitch,
			float bodyYaw, float prevBodyYaw, float headYaw, float prevHeadYaw,
			boolean handSwinging, Hand preferredHand, int handSwingTicks,
			float handSwingProgress, float lastHandSwingProgress,
			float limbPrevSpeed, float limbSpeed, float limbPos,
			float sidewaysSpeed, float upwardSpeed, float forwardSpeed,
			float strideDistance, float prevStrideDistance) {
		private static FrozenPose capture(AbstractClientPlayerEntity player) {
			return new FrozenPose(
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				player.bodyYaw,
				player.prevBodyYaw,
				player.headYaw,
				player.prevHeadYaw,
				player.handSwinging,
				player.preferredHand,
				player.handSwingTicks,
				player.handSwingProgress,
				player.lastHandSwingProgress,
				ClientTimeMasterFreezeState.limbPrevSpeed(player.limbAnimator),
				player.limbAnimator.getSpeed(),
				ClientTimeMasterFreezeState.limbPos(player.limbAnimator),
				player.sidewaysSpeed,
				player.upwardSpeed,
				player.forwardSpeed,
				player.strideDistance,
				player.prevStrideDistance
			);
		}

		private void apply(AbstractClientPlayerEntity player) {
			player.refreshPositionAndAngles(x, y, z, yaw, pitch);
			player.prevX = x;
			player.prevY = y;
			player.prevZ = z;
			player.lastRenderX = x;
			player.lastRenderY = y;
			player.lastRenderZ = z;
			player.prevYaw = yaw;
			player.prevPitch = pitch;
			player.setVelocity(Vec3d.ZERO);
			player.bodyYaw = bodyYaw;
			player.prevBodyYaw = prevBodyYaw;
			player.headYaw = headYaw;
			player.prevHeadYaw = prevHeadYaw;
			player.handSwinging = handSwinging;
			player.preferredHand = preferredHand;
			player.handSwingTicks = handSwingTicks;
			player.handSwingProgress = handSwingProgress;
			player.lastHandSwingProgress = lastHandSwingProgress;
			player.sidewaysSpeed = sidewaysSpeed;
			player.upwardSpeed = upwardSpeed;
			player.forwardSpeed = forwardSpeed;
			player.strideDistance = strideDistance;
			player.prevStrideDistance = prevStrideDistance;
			setLimbPose(player.limbAnimator, limbPrevSpeed, limbSpeed, limbPos);
		}
	}
}
