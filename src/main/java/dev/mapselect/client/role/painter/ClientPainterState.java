package dev.mapselect.client.role.painter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.mapselect.client.ability.ClientAbilityTargetState;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.client.screen.PainterChoiceScreen;
import dev.mapselect.network.role.painter.PainterBodyUsePayload;
import dev.mapselect.network.role.painter.PainterChoiceOpenPayload;
import dev.mapselect.network.role.painter.PainterDoorwayUsePayload;
import dev.mapselect.network.role.painter.PainterDoorwayTransitionPayload;
import dev.mapselect.network.role.painter.PainterReturnParticlesPayload;
import dev.mapselect.network.role.painter.PainterStatePayload;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ClientPainterState {
	private static final Map<UUID, PaintTint> BODY_TINTS = new HashMap<>();
	private static final Map<UUID, PaintTint> PLAYER_TINTS = new HashMap<>();
	private static final Map<BlockPos, DoorwayTint> DOORWAY_TINTS = new HashMap<>();
	private static final List<ReturnParticleTrail> RETURN_TRAILS = new ArrayList<>();
	private static final int RETURN_TRAIL_TICKS = 40;
	private static final int RETURN_CLUMPS = 5;
	private static final int RETURN_PARTICLES_PER_CLUMP = 9;
	private static boolean wasBodyKeyDown;
	private static boolean wasDoorwayKeyDown;
	private static Object syncedWorld;

	private ClientPainterState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PainterStatePayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(PainterChoiceOpenPayload.ID, (payload, context) ->
			context.client().execute(() -> context.client().setScreen(new PainterChoiceScreen(payload))));
		ClientPlayNetworking.registerGlobalReceiver(PainterReturnParticlesPayload.ID, (payload, context) ->
			context.client().execute(() -> spawnReturnParticles(context.client(), payload)));
		ClientPlayNetworking.registerGlobalReceiver(PainterDoorwayTransitionPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientPainterDoorwayTransition.start(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientPainterState::tick);
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		return player != null && active(PLAYER_TINTS.get(player.getUuid()));
	}

	public static boolean shouldGlow(PlayerBodyEntity body) {
		return body != null && active(BODY_TINTS.get(body.getUuid()));
	}

	public static boolean isPlayerPainted(AbstractClientPlayerEntity player) {
		return shouldGlow(player);
	}

	public static boolean isBodyPainted(PlayerBodyEntity body) {
		return shouldGlow(body);
	}

	public static int glowColor(AbstractClientPlayerEntity player) {
		PaintTint tint = player == null ? null : PLAYER_TINTS.get(player.getUuid());
		return glowColor(tint);
	}

	public static int glowColor(PlayerBodyEntity body) {
		PaintTint tint = body == null ? null : BODY_TINTS.get(body.getUuid());
		return glowColor(tint);
	}

	public static int playerColor(AbstractClientPlayerEntity player, int originalColor) {
		PaintTint tint = player == null ? null : PLAYER_TINTS.get(player.getUuid());
		return blend(originalColor, tint, 0.78F, 0.62F);
	}

	public static int bodyColor(PlayerBodyEntity body, int originalColor) {
		PaintTint tint = body == null ? null : BODY_TINTS.get(body.getUuid());
		int color = blend(originalColor, tint, 1.0F, 0.42F);
		return !isBodyPainted(body) && ClientAbilityTargetState.shouldGlow(body)
			? blend(color, new PaintTint(ClientAbilityTargetState.glowColor(body), Long.MAX_VALUE, 1), 0.50F)
			: color;
	}

	public static boolean isDoorwayActive(BlockPos pos) {
		return active(doorway(pos));
	}

	public static int doorwayColor(BlockPos pos) {
		DoorwayTint tint = doorway(pos);
		return glowColor(tint == null ? null : tint.asPaintTint());
	}

	public static Collection<DoorwayTint> doorways() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			long now = client.world.getTime();
			DOORWAY_TINTS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= now);
		}
		return List.copyOf(DOORWAY_TINTS.values());
	}

	public static boolean canShowDoorwayTarget(MinecraftClient client) {
		return client != null && client.currentScreen == null && client.player != null && client.world != null
			&& !ClientVultureState.isLocalStashed(client)
			&& ClientRoleRevealState.canUseRoleAbility(client)
			&& isLocalPainter(client);
	}

	private static void apply(MinecraftClient client, PainterStatePayload payload) {
		checkWorld(client);
		BODY_TINTS.clear();
		PLAYER_TINTS.clear();
		DOORWAY_TINTS.clear();
		if (client == null || client.world == null || payload == null) return;
		long now = client.world.getTime();
		for (PainterStatePayload.Entry entry : payload.bodyTints()) {
			BODY_TINTS.put(entry.targetId(), PaintTint.from(entry, now));
		}
		for (PainterStatePayload.Entry entry : payload.playerTints()) {
			PLAYER_TINTS.put(entry.targetId(), PaintTint.from(entry, now));
		}
		for (PainterStatePayload.DoorwayEntry entry : payload.doorways()) {
			DoorwayTint tint = DoorwayTint.from(entry, now);
			DOORWAY_TINTS.put(tint.lowerPos(), tint);
		}
	}

	private static void tick(MinecraftClient client) {
		checkWorld(client);
		ClientPainterDoorwayTransition.tick(client);
		if (client == null || client.player == null || client.world == null) return;
		long now = client.world.getTime();
		BODY_TINTS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= now);
		PLAYER_TINTS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= now);
		DOORWAY_TINTS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= now);
		tickReturnTrails(client, now);

		if (client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client) || !isLocalPainter(client)) {
			wasBodyKeyDown = false;
			wasDoorwayKeyDown = false;
			return;
		}

		KeyBinding paintKey = ClientAbilityKeys.primaryBinding();
		boolean paintDown = paintKey != null && ClientAbilityKeys.isDown(client, paintKey);
		if (paintDown && !wasBodyKeyDown && ClientPlayNetworking.canSend(PainterBodyUsePayload.ID)) {
			ClientPlayNetworking.send(new PainterBodyUsePayload());
		}
		wasBodyKeyDown = paintDown;

		KeyBinding doorwayKey = ClientAbilityKeys.secondaryBinding();
		boolean doorwayDown = doorwayKey != null && ClientAbilityKeys.isDown(client, doorwayKey);
		if (doorwayDown && !wasDoorwayKeyDown && ClientPlayNetworking.canSend(PainterDoorwayUsePayload.ID)) {
			ClientPlayNetworking.send(new PainterDoorwayUsePayload());
		}
		wasDoorwayKeyDown = doorwayDown;
	}

	private static boolean isLocalPainter(MinecraftClient client) {
		return MapSelectRoles.PAINTER_ID.equals(ClientCopycatState.effectiveRoleId(client, localRoleId(client)));
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void checkWorld(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (syncedWorld == world) return;
		syncedWorld = world;
		BODY_TINTS.clear();
		PLAYER_TINTS.clear();
		DOORWAY_TINTS.clear();
		RETURN_TRAILS.clear();
		wasBodyKeyDown = false;
		wasDoorwayKeyDown = false;
	}

	private static boolean active(PaintTint tint) {
		MinecraftClient client = MinecraftClient.getInstance();
		return tint != null && client.world != null && tint.expiresAtTick() > client.world.getTime();
	}

	private static boolean active(DoorwayTint tint) {
		MinecraftClient client = MinecraftClient.getInstance();
		return tint != null && client.world != null && tint.expiresAtTick() > client.world.getTime();
	}

	private static DoorwayTint doorway(BlockPos pos) {
		if (pos == null) return null;
		DoorwayTint tint = DOORWAY_TINTS.get(pos);
		if (tint != null) return tint;
		return DOORWAY_TINTS.get(pos.down());
	}

	private static void spawnReturnParticles(MinecraftClient client, PainterReturnParticlesPayload payload) {
		if (client == null || client.world == null || client.player == null || payload == null) return;
		int color = payload.color() & 0xFFFFFF;
		Vec3d origin = new Vec3d(payload.x(), payload.y(), payload.z());
		long now = client.world.getTime();
		RETURN_TRAILS.add(new ReturnParticleTrail(origin, color, now, now + RETURN_TRAIL_TICKS));
		while (RETURN_TRAILS.size() > 24) {
			RETURN_TRAILS.removeFirst();
		}
	}

	private static void tickReturnTrails(MinecraftClient client, long now) {
		if (client == null || client.world == null || client.player == null || RETURN_TRAILS.isEmpty()) return;
		Vec3d destination = client.player.getPos().add(0.0D, client.player.getHeight() * 0.52D, 0.0D);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Iterator<ReturnParticleTrail> iterator = RETURN_TRAILS.iterator();
		while (iterator.hasNext()) {
			ReturnParticleTrail trail = iterator.next();
			if (now > trail.endTick()) {
				for (int i = 0; i < 16; i++) {
					spawnPaintParticle(client, trail.color(), destination.add(random.nextDouble(-0.22D, 0.22D),
						random.nextDouble(-0.16D, 0.22D), random.nextDouble(-0.22D, 0.22D)), Vec3d.ZERO, 1.45F);
				}
				iterator.remove();
				continue;
			}
			float duration = Math.max(1.0F, trail.endTick() - trail.startTick());
			float baseProgress = MathHelper.clamp((now - trail.startTick()) / duration, 0.0F, 1.0F);
			for (int clump = 0; clump < RETURN_CLUMPS; clump++) {
				float t = baseProgress - clump * 0.13F + random.nextFloat(-0.01F, 0.01F);
				if (t <= 0.0F || t >= 1.0F) continue;
				float eased = 1.0F - (1.0F - t) * (1.0F - t);
				Vec3d center = trail.origin().lerp(destination, eased)
					.add(random.nextDouble(-0.035D, 0.035D), random.nextDouble(-0.025D, 0.045D),
						random.nextDouble(-0.035D, 0.035D));
				for (int particle = 0; particle < RETURN_PARTICLES_PER_CLUMP; particle++) {
					Vec3d jitter = new Vec3d(random.nextDouble(-0.045D, 0.045D),
						random.nextDouble(-0.025D, 0.065D), random.nextDouble(-0.045D, 0.045D));
					Vec3d start = center.add(jitter);
					Vec3d direction = destination.subtract(start);
					Vec3d velocity = direction.lengthSquared() < 0.0001D
						? Vec3d.ZERO
						: direction.normalize().multiply(random.nextDouble(0.012D, 0.032D));
					spawnPaintParticle(client, trail.color(), start, velocity, 1.55F);
				}
			}
		}
	}

	private static void spawnPaintParticle(MinecraftClient client, int color, Vec3d position, Vec3d velocity,
			float scale) {
		float red = ((color >>> 16) & 0xFF) / 255.0F;
		float green = ((color >>> 8) & 0xFF) / 255.0F;
		float blue = (color & 0xFF) / 255.0F;
		DustParticleEffect effect = new DustParticleEffect(new Vector3f(red, green, blue), scale);
		client.world.addParticle(effect, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
	}

	private static int glowColor(PaintTint tint) {
		if (!active(tint)) return 0xFFFFFF;
		float fade = remaining(tint);
		float minimum = 0.22F;
		float weight = minimum + (1.0F - minimum) * fade;
		int red = Math.round(((tint.color() >>> 16) & 0xFF) * weight);
		int green = Math.round(((tint.color() >>> 8) & 0xFF) * weight);
		int blue = Math.round((tint.color() & 0xFF) * weight);
		return (red << 16) | (green << 8) | blue;
	}

	private static int blend(int originalColor, PaintTint tint, float strength) {
		return blend(originalColor, tint, strength, 1.0F);
	}

	private static int blend(int originalColor, PaintTint tint, float strength, float paintBrightness) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (tint == null || client.world == null) return originalColor;
		long now = client.world.getTime();
		if (tint.expiresAtTick() <= now) return originalColor;
		float remaining = remaining(tint);
		float weight = Math.max(0.0F, Math.min(1.0F, remaining * strength));
		int alpha = (originalColor >>> 24) & 0xFF;
		int originalRed = (originalColor >>> 16) & 0xFF;
		int originalGreen = (originalColor >>> 8) & 0xFF;
		int originalBlue = originalColor & 0xFF;
		int paintRed = Math.round(((tint.color() >>> 16) & 0xFF) * paintBrightness);
		int paintGreen = Math.round(((tint.color() >>> 8) & 0xFF) * paintBrightness);
		int paintBlue = Math.round((tint.color() & 0xFF) * paintBrightness);
		int red = Math.round(originalRed + (paintRed - originalRed) * weight);
		int green = Math.round(originalGreen + (paintGreen - originalGreen) * weight);
		int blue = Math.round(originalBlue + (paintBlue - originalBlue) * weight);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static float remaining(PaintTint tint) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (tint == null || client.world == null) return 0.0F;
		return Math.max(0.0F, Math.min(1.0F,
			(tint.expiresAtTick() - client.world.getTime()) / (float) Math.max(1, tint.totalTicks())));
	}

	private record PaintTint(int color, long expiresAtTick, int totalTicks) {
		private static PaintTint from(PainterStatePayload.Entry entry, long now) {
			return new PaintTint(entry.color(), now + entry.remainingTicks(), entry.totalTicks());
		}
	}

	public record DoorwayTint(BlockPos lowerPos, int color, long expiresAtTick, int totalTicks,
			Direction facing, Direction through, BlockPos destinationLowerPos,
			Direction destinationFacing, Direction destinationThrough, Vec3d destination,
			List<DoorwayPlayer> players, List<DoorwayBlock> blocks) {
		private static DoorwayTint from(PainterStatePayload.DoorwayEntry entry, long now) {
			return new DoorwayTint(entry.lowerPos(), entry.color(), now + entry.remainingTicks(),
				entry.totalTicks(), entry.facing(), entry.through(), entry.destinationLowerPos(),
				entry.destinationFacing(), entry.destinationThrough(), entry.destination(),
				entry.players().stream().map(DoorwayPlayer::from).toList(),
				entry.blocks().stream()
					.map(block -> DoorwayBlock.from(entry.destinationLowerPos(), block))
					.toList());
		}

		private PaintTint asPaintTint() {
			return new PaintTint(color, expiresAtTick, totalTicks);
		}

		public float remaining() {
			return ClientPainterState.remaining(asPaintTint());
		}
	}

	public record DoorwayPlayer(UUID playerId, String name, Vec3d pos, float yaw, float pitch,
			float bodyYaw, float headYaw, boolean sneaking, boolean sprinting, boolean invisible) {
		private static DoorwayPlayer from(PainterStatePayload.DoorwayPlayerEntry entry) {
			return new DoorwayPlayer(entry.playerId(), entry.name(), new Vec3d(entry.x(), entry.y(), entry.z()),
				entry.yaw(), entry.pitch(), entry.bodyYaw(), entry.headYaw(),
				entry.sneaking(), entry.sprinting(), entry.invisible());
		}
	}

	public record DoorwayBlock(BlockPos pos, BlockState state) {
		private static DoorwayBlock from(BlockPos origin, PainterStatePayload.DoorwayBlockEntry entry) {
			BlockPos base = origin == null ? BlockPos.ORIGIN : origin;
			return new DoorwayBlock(base.add(entry.dx(), entry.dy(), entry.dz()), entry.state());
		}
	}

	private record ReturnParticleTrail(Vec3d origin, int color, long startTick, long endTick) {}
}
