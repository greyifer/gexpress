package dev.mapselect.client.render;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.index.WatheBlocks;
import dev.mapselect.network.role.juggernaut.JuggernautDoorDirectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.client.render.entity.animation.Keyframe;
import net.minecraft.client.render.entity.animation.Transformation;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/** Exact four-way Blockbench animations for Wathe's small wooden door. */
public final class ClientJuggernautDoorAnimation {
	private static final long ANIMATION_LENGTH_MILLIS = 1_250L;
	private static final Animation NORTH = build(0.0F, 0.0F, -1.0F);
	private static final Animation SOUTH = build(-180.0F, 0.0F, 1.0F);
	private static final Animation EAST = build(-90.0F, 1.0F, 0.0F);
	private static final Animation WEST = build(90.0F, -1.0F, 0.0F);
	private static final Map<DoorBlockEntity, Integer> START_AGES = new WeakHashMap<>();
	private static final Map<DoorKey, Direction> FALL_DIRECTIONS = new java.util.HashMap<>();

	private ClientJuggernautDoorAnimation() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(JuggernautDoorDirectionPayload.ID, (payload, context) ->
			context.client().execute(() -> applyDirection(context.client(), payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			START_AGES.clear();
			FALL_DIRECTIONS.clear();
		});
	}

	private static Animation build(float yaw, float positionXSign, float positionZSign) {
		return Animation.Builder.create(1.25F)
			.addBoneAnimation("Door", new Transformation(Transformation.Targets.ROTATE,
				new Keyframe(0.0417F, rotation(0.0F, yaw), Transformation.Interpolations.CUBIC),
				new Keyframe(0.3333F, rotation(90.0F, yaw), Transformation.Interpolations.CUBIC),
				new Keyframe(0.4167F, rotation(90.0F, yaw), Transformation.Interpolations.CUBIC),
				new Keyframe(0.5417F, rotation(85.0F, yaw), Transformation.Interpolations.CUBIC),
				new Keyframe(0.75F, rotation(90.0F, yaw), Transformation.Interpolations.CUBIC)))
			.addBoneAnimation("Door", new Transformation(Transformation.Targets.TRANSLATE,
				new Keyframe(0.0F, position(0.0F, 0.0F, 0.0F), Transformation.Interpolations.CUBIC),
				new Keyframe(0.125F, position(4.84F * positionXSign, 2.0F,
					4.84F * positionZSign), Transformation.Interpolations.CUBIC),
				new Keyframe(0.25F, position(12.0F * positionXSign, 1.0F,
					12.0F * positionZSign), Transformation.Interpolations.CUBIC),
				new Keyframe(0.4583F, position(18.0F * positionXSign, 1.0F,
					18.0F * positionZSign), Transformation.Interpolations.CUBIC)))
			.build();
	}

	private static Vector3f rotation(float pitch, float yaw) {
		return AnimationHelper.createRotationalVector(pitch, yaw, 0.0F);
	}

	private static Vector3f position(float x, float y, float z) {
		return AnimationHelper.createTranslationalVector(x, y, z);
	}

	public static boolean supports(DoorBlockEntity door) {
		return door != null && door.getCachedState().isOf(WatheBlocks.SMALL_WOOD_DOOR);
	}

	public static void clear(DoorBlockEntity door) {
		if (door == null) return;
		START_AGES.remove(door);
	}

	/** Applies the server-synced direction constrained to the door's placement axis. */
	public static void applySmallDoor(SinglePartEntityModel<?> model, DoorBlockEntity door,
			float animationProgress) {
		if (model == null || !supports(door) || !door.isBlasted()) return;
		long elapsed = Math.min(ANIMATION_LENGTH_MILLIS, elapsedMillis(door, animationProgress));
		Direction direction = fallDirection(door);
		AnimationHelper.animate(model, animationFor(direction), elapsed,
			1.0F, new Vector3f());
	}

	/** Large/custom doors are deliberately excluded from the Juggernaut animation. */
	public static Pose sample(DoorBlockEntity door, float animationProgress) {
		return null;
	}

	private static Animation animationFor(Direction facing) {
		return switch (facing) {
			case NORTH -> NORTH;
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> SOUTH;
		};
	}

	private static long elapsedMillis(DoorBlockEntity door, float animationProgress) {
		int startAge = START_AGES.computeIfAbsent(door, ignored -> door.getAge());
		return Math.max(0L, Math.round(Math.max(0.0F, animationProgress - startAge) * 50.0F));
	}

	private static void applyDirection(MinecraftClient client, JuggernautDoorDirectionPayload payload) {
		if (client == null || client.world == null || payload == null) return;
		DoorKey key = new DoorKey(client.world.getRegistryKey(), payload.pos().toImmutable());
		Direction direction = payload.direction();
		if (direction == null) {
			FALL_DIRECTIONS.remove(key);
		} else {
			FALL_DIRECTIONS.put(key, direction);
		}
	}

	private static Direction fallDirection(DoorBlockEntity door) {
		DoorKey key = key(door);
		Direction synced = key == null ? null : FALL_DIRECTIONS.get(key);
		return synced == null ? door.getFacing() : synced;
	}

	private static DoorKey key(DoorBlockEntity door) {
		return door == null || door.getWorld() == null ? null
			: new DoorKey(door.getWorld().getRegistryKey(), door.getPos().toImmutable());
	}

	private record DoorKey(RegistryKey<World> world, BlockPos pos) {
	}

	public record Pose(float pitchRadians, float positionY, float positionZ) {}
}
