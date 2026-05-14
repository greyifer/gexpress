package dev.mapselect.role.bombspecialist;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectSounds;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class C4Detonation {
	private C4Detonation() {}

	private static final double BLAST_RADIUS = 3.0D;
	private static final double SURFACE_OFFSET = 0.001D;
	private static final Map<UUID, ThrownCharge> thrownCharges = new ConcurrentHashMap<>();

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(C4Detonation::tick);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
			C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
			if (comp != null) comp.clearAll();
			clearThrownCharges();
		});
	}

	public static void registerThrownCharge(ItemEntity entity, UUID owner) {
		if (entity == null || owner == null) return;
		thrownCharges.put(entity.getUuid(), new ThrownCharge(owner, -1L, -1L, entity.getPos(), false, entity.getWorld().getTime()));
	}

	public static boolean isDefusableBlockCharge(ItemEntity entity) {
		return entity != null
			&& !entity.isRemoved()
			&& entity.getStack().isOf(MapSelectItems.C4)
			&& (entity.hasNoGravity() || thrownCharges.containsKey(entity.getUuid()));
	}

	public static ItemEntity findLookedAtCharge(ServerPlayerEntity player, double range) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return null;
		Vec3d start = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0F).normalize();
		Vec3d end = start.add(direction.multiply(range));
		double maxDistanceSq = range * range;

		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end,
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			maxDistanceSq = Math.min(maxDistanceSq, start.squaredDistanceTo(blockHit.getPos()) + 0.08D);
		}

		Box searchBox = player.getBoundingBox().stretch(direction.multiply(range)).expand(0.45D);
		EntityHitResult hit = ProjectileUtil.raycast(player, start, end, searchBox,
			entity -> entity instanceof ItemEntity item && isDefusableBlockCharge(item), maxDistanceSq);
		return hit != null && hit.getEntity() instanceof ItemEntity item ? item : null;
	}

	public static boolean defuseBlockCharge(ServerPlayerEntity defuser, ItemEntity entity) {
		if (defuser == null || entity == null || entity.isRemoved() || !entity.getStack().isOf(MapSelectItems.C4)) return false;
		thrownCharges.remove(entity.getUuid());
		World world = entity.getWorld();
		Vec3d pos = entity.getPos();
		entity.discard();
		world.playSound(null, pos.x, pos.y, pos.z,
			SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, SoundCategory.PLAYERS, 0.9F, 1.2F);
		world.playSound(null, pos.x, pos.y, pos.z,
			SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	public static boolean misfireBlockCharge(ServerWorld world, ItemEntity entity, PlayerEntity attacker) {
		if (world == null || entity == null || entity.isRemoved() || !entity.getStack().isOf(MapSelectItems.C4)) return false;
		thrownCharges.remove(entity.getUuid());
		Vec3d pos = entity.getPos();
		entity.discard();
		detonateAt(world, pos, attacker);
		return true;
	}

	private static void registerVisibleThrownCharges(ServerWorld world, ServerPlayerEntity owner) {
		for (ItemEntity entity : world.getEntitiesByType(EntityType.ITEM, entity ->
				entity.getStack().isOf(MapSelectItems.C4) && isOwnedBy(entity, owner.getUuid()))) {
			thrownCharges.putIfAbsent(entity.getUuid(),
				new ThrownCharge(owner.getUuid(), -1L, -1L, entity.getPos(), entity.hasNoGravity(), placedAt(world, entity)));
		}
	}

	private static Map.Entry<UUID, ThrownCharge> newestUnarmedCharge(ServerWorld world, ServerPlayerEntity player) {
		Map.Entry<UUID, ThrownCharge> newest = null;
		for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
			ThrownCharge charge = entry.getValue();
			if (!player.getUuid().equals(charge.owner())) continue;
			ItemEntity entity = thrownChargeEntity(world, entry.getKey());
			if (entity == null) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			if (charge.isArmed()) continue;
			if (newest == null || charge.placedAt() > newest.getValue().placedAt()) {
				newest = entry;
			}
		}
		return newest;
	}

	private static long placedAt(ServerWorld world, ItemEntity entity) {
		return Math.max(0L, world.getTime() - Math.max(0, entity.getItemAge()));
	}

	public static void triggerRemoteDetonation(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		registerVisibleThrownCharges(world, player);
		if (hasArmedCharge(world, player)) {
			player.sendMessage(Text.literal("A C4 charge is already armed."), true);
			return;
		}
		long now = world.getTime();
		long detonationAt = now
			+ (long) GexpressConfig.getC4FirstBeepSeconds() * 20L
			+ (long) GexpressConfig.getC4FuseSeconds() * 20L;
		Map.Entry<UUID, ThrownCharge> target = newestUnarmedCharge(world, player);
		if (target == null) {
			player.sendMessage(Text.literal("No thrown C4 charges to detonate."), true);
			return;
		}
		ThrownCharge charge = target.getValue();
		thrownCharges.put(target.getKey(), charge.armed(now, detonationAt));
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK,
			SoundCategory.PLAYERS, 0.8F, 1.4F);
		player.sendMessage(Text.literal("Armed 1 C4 charge."), true);
	}

	private static boolean hasArmedCharge(ServerWorld world, ServerPlayerEntity player) {
		for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
			ThrownCharge charge = entry.getValue();
			if (!player.getUuid().equals(charge.owner())) continue;
			ItemEntity entity = thrownChargeEntity(world, entry.getKey());
			if (entity == null) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			if (charge.isArmed()) return true;
		}
		return false;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null) return;
		Map<UUID, Long> carriers = comp.getCarriers();
		boolean hasThrown = !thrownCharges.isEmpty();
		if (carriers.isEmpty() && !hasThrown) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.STOPPING
				&& !GexpressTestState.hasRoleTesters()) {
			comp.clearAll();
			clearThrownCharges();
			return;
		}

		long now = world.getTime();
		MinecraftServer server = world.getServer();
		List<UUID> expired = null;

		for (Map.Entry<UUID, Long> e : carriers.entrySet()) {
			UUID id = e.getKey();
			long detonationAt = e.getValue();
			long remaining = detonationAt - now;

			if (remaining <= 0L) {
				if (expired == null) expired = new ArrayList<>();
				expired.add(id);
				continue;
			}

			ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(id);
			if (carrier == null || carrier.isRemoved()) continue;
			maybeBeep(comp, carrier, remaining);
		}

		if (expired != null) {
			for (UUID carrierId : expired) {
				ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(carrierId);
				comp.removeC4(carrierId);
				if (carrier == null || carrier.isRemoved()) continue;
				if (!(carrier.getWorld() instanceof ServerWorld currentWorld)) continue;
				detonateAt(currentWorld, carrier, carrier);
			}
		}

		tickThrownCharges(world, now);
	}

	private static long beepInterval(double progress) {
		if (progress < 0.50D) return 20;
		if (progress < 0.75D) return 10;
		if (progress < 0.90D) return 5;
		return 2;
	}

	private static void maybeBeep(C4BackComponent comp, ServerPlayerEntity carrier, long remaining) {
		long ticksSincePlant = comp.ticksSincePlant(carrier.getUuid());
		long fuseTicks = ticksSincePlant + remaining;
		long configuredDelay = (long) GexpressConfig.getC4FirstBeepSeconds() * 20L;
		long firstBeepDelay = Math.min(configuredDelay, Math.max(0L, fuseTicks - 1L));
		long audibleTicks = Math.max(1L, fuseTicks - firstBeepDelay);
		long ticksSinceFirstBeep = ticksSincePlant - firstBeepDelay;
		if (ticksSinceFirstBeep < 0L) return;

		double progress = Math.min(1.0D, Math.max(0.0D, ticksSinceFirstBeep / (double) audibleTicks));
		long interval = beepInterval(progress);
		if (ticksSinceFirstBeep % interval != 0L) return;
		if (!(carrier.getWorld() instanceof ServerWorld world)) return;

		float urgency = (float) progress;
		float pitch = 1.5F + urgency * 0.5F;
		float volume = 0.5F + urgency * 0.3F;

		world.playSound(
			null,
			carrier.getBlockPos(),
			MapSelectSounds.C4_BEEP,
			SoundCategory.PLAYERS,
			volume,
			pitch
		);
	}

	public static void detonateAt(ServerWorld world, PlayerEntity carrier, PlayerEntity attacker) {
		detonateAt(world, carrier.getPos(), attacker);
	}

	public static void detonateAt(ServerWorld world, Vec3d blastCenter, PlayerEntity attacker) {
		double x = blastCenter.x;
		double y = blastCenter.y + 0.1D;
		double z = blastCenter.z;
		BlockPos pos = BlockPos.ofFloored(blastCenter);

		world.playSound(null, pos, WatheSounds.ITEM_GRENADE_EXPLODE, SoundCategory.BLOCKS,
			5.0F, 1.0F + (world.getRandom().nextFloat() * 0.1F) - 0.05F);

		world.spawnParticles(WatheParticles.BIG_EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 100, 0.0, 0.0, 0.0, 0.2);

		List<ServerPlayerEntity> victims = world.getPlayers(p -> {
			if (!GameFunctions.isPlayerAliveAndSurvival(p)) return false;
			return p.getPos().isInRange(blastCenter, BLAST_RADIUS) && hasExplosionLineOfSight(world, blastCenter, p);
		});
		for (ServerPlayerEntity victim : victims) {
			GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.GRENADE);
		}
	}

	private static boolean hasExplosionLineOfSight(ServerWorld world, Vec3d blastCenter, ServerPlayerEntity victim) {
		Vec3d center = blastCenter.add(0.0D, 0.35D, 0.0D);
		Vec3d eye = victim.getEyePos();
		Vec3d body = victim.getPos().add(0.0D, victim.getHeight() * 0.5D, 0.0D);
		return unobstructed(world, center, eye, victim) || unobstructed(world, center, body, victim);
	}

	private static boolean unobstructed(ServerWorld world, Vec3d from, Vec3d to, ServerPlayerEntity victim) {
		BlockHitResult hit = world.raycast(new RaycastContext(from, to,
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, victim));
		if (hit.getType() == HitResult.Type.MISS) return true;
		return hit.getPos().squaredDistanceTo(from) + 0.05D >= to.squaredDistanceTo(from);
	}

	private static void tickThrownCharges(ServerWorld world, long now) {
		for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
			ItemEntity entity = thrownChargeEntity(world, entry.getKey());
			if (entity == null) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			ThrownCharge charge = entry.getValue();
			if (tryAttachThrownToPlayer(world, entity, charge)) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			charge = updateStickyState(world, entity, charge);
			thrownCharges.put(entry.getKey(), charge);
			if (!charge.isArmed()) continue;
			long remaining = charge.detonationAt() - now;
			if (remaining <= 0L) {
				thrownCharges.remove(entry.getKey());
				Vec3d pos = entity.getPos();
				entity.discard();
				ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(charge.owner());
				detonateAt(world, pos, owner);
			} else {
				maybeBeepThrown(world, entity, charge, remaining);
			}
		}
	}

	private static boolean tryAttachThrownToPlayer(ServerWorld world, ItemEntity entity, ThrownCharge charge) {
		if (charge.stuck()) return false;
		Vec3d previous = charge.previousPos() != null ? charge.previousPos() : entity.getPos();
		Vec3d current = entity.getPos();
		Vec3d delta = current.subtract(previous);
		if (delta.lengthSquared() <= 1.0E-7D) return false;
		EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
			entity,
			previous,
			current,
			entity.getBoundingBox().stretch(delta).expand(0.7D),
			target -> canThrownC4AttachTo(charge, target),
			delta.lengthSquared() + 1.0D
		);
		if (hit == null || !(hit.getEntity() instanceof ServerPlayerEntity target)) return false;

		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null || comp.hasC4(target.getUuid())) return false;
		if (!comp.addC4(target.getUuid())) return false;
		entity.discard();
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
			SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.8F, 1.3F);
		return true;
	}

	private static boolean canThrownC4AttachTo(ThrownCharge charge, Entity target) {
		return target instanceof ServerPlayerEntity player
			&& !player.getUuid().equals(charge.owner())
			&& !player.isSpectator()
			&& player.canHit();
	}

	private static boolean isOwnedBy(ItemEntity entity, UUID ownerId) {
		if (entity == null || ownerId == null) return false;
		Entity owner = entity.getOwner();
		return owner != null && ownerId.equals(owner.getUuid());
	}

	private static ThrownCharge updateStickyState(ServerWorld world, ItemEntity entity, ThrownCharge charge) {
		if (charge.stuck()) {
			keepStuck(entity);
			return charge.withPreviousPos(entity.getPos());
		}

		Vec3d previous = charge.previousPos() != null ? charge.previousPos() : entity.getPos();
		Vec3d current = entity.getPos();
		BlockHitResult hit = findSurfaceHit(world, entity, previous, current);
		if (hit != null) {
			stickToSurface(entity, hit.getPos(), hit.getSide());
			return charge.stuck(entity.getPos());
		}

		Direction fallbackSide = fallbackCollisionSide(entity, previous, current);
		if (fallbackSide != null) {
			stickToSurface(entity, current, fallbackSide);
			return charge.stuck(entity.getPos());
		}

		return charge.withPreviousPos(current);
	}

	private static BlockHitResult findSurfaceHit(ServerWorld world, ItemEntity entity, Vec3d previous, Vec3d current) {
		if (previous.squaredDistanceTo(current) <= 1.0E-7D) return null;
		BlockHitResult hit = world.raycast(new RaycastContext(previous, current,
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
		if (hit.getType() != HitResult.Type.BLOCK) return null;
		if (world.getBlockState(hit.getBlockPos()).isAir()) return null;
		return hit;
	}

	private static Direction fallbackCollisionSide(ItemEntity entity, Vec3d previous, Vec3d current) {
		Vec3d delta = current.subtract(previous);
		if (entity.isOnGround()) return Direction.UP;
		if (entity.verticalCollision) {
			return delta.y > 0.0D ? Direction.DOWN : Direction.UP;
		}
		if (!entity.horizontalCollision) return null;
		if (Math.abs(delta.x) > Math.abs(delta.z)) {
			return delta.x > 0.0D ? Direction.WEST : Direction.EAST;
		}
		return delta.z > 0.0D ? Direction.NORTH : Direction.SOUTH;
	}

	private static void stickToSurface(ItemEntity entity, Vec3d surfacePos, Direction side) {
		Vec3d normal = Vec3d.of(side.getVector());
		Vec3d plantedPos = surfacePos.add(normal.multiply(SURFACE_OFFSET));
		entity.setPosition(plantedPos);
		entity.setVelocity(Vec3d.ZERO);
		entity.setNoGravity(true);
		entity.velocityModified = true;
		entity.setYaw(yawForSide(side));
		entity.setPitch(pitchForSide(side));
		entity.setPickupDelayInfinite();
		entity.setNeverDespawn();
	}

	private static void keepStuck(ItemEntity entity) {
		entity.setVelocity(Vec3d.ZERO);
		entity.setNoGravity(true);
		entity.velocityModified = true;
		entity.setPickupDelayInfinite();
		entity.setNeverDespawn();
	}

	private static float yawForSide(Direction side) {
		return switch (side) {
			case NORTH -> 180.0F;
			case EAST -> -90.0F;
			case WEST -> 90.0F;
			default -> 0.0F;
		};
	}

	private static float pitchForSide(Direction side) {
		return switch (side) {
			case UP -> -90.0F;
			case DOWN -> 90.0F;
			default -> 0.0F;
		};
	}

	private static void maybeBeepThrown(ServerWorld world, ItemEntity entity, ThrownCharge charge, long remaining) {
		long fuseTicks = Math.max(1L, charge.detonationAt() - charge.armedAt());
		long configuredDelay = (long) GexpressConfig.getC4FirstBeepSeconds() * 20L;
		long firstBeepDelay = Math.min(configuredDelay, Math.max(0L, fuseTicks - 1L));
		long ticksSinceFirstBeep = world.getTime() - charge.armedAt() - firstBeepDelay;
		if (ticksSinceFirstBeep < 0L) return;

		long audibleTicks = Math.max(1L, fuseTicks - firstBeepDelay);
		double progress = Math.min(1.0D, Math.max(0.0D, ticksSinceFirstBeep / (double) audibleTicks));
		long interval = beepInterval(progress);
		if (ticksSinceFirstBeep % interval != 0L) return;

		float urgency = (float) progress;
		world.playSound(null, entity.getBlockPos(), MapSelectSounds.C4_BEEP,
			SoundCategory.PLAYERS, 0.5F + urgency * 0.3F, 1.5F + urgency * 0.5F);
	}

	private static ItemEntity thrownChargeEntity(ServerWorld world, UUID entityId) {
		if (world == null || entityId == null) return null;
		Entity entity = world.getEntity(entityId);
		if (!(entity instanceof ItemEntity itemEntity) || itemEntity.isRemoved()
				|| !itemEntity.getStack().isOf(MapSelectItems.C4)) {
			return null;
		}
		return itemEntity;
	}

	private static void clearThrownCharges() {
		thrownCharges.clear();
	}

	private record ThrownCharge(UUID owner, long armedAt, long detonationAt, Vec3d previousPos, boolean stuck, long placedAt) {
		private boolean isArmed() {
			return armedAt >= 0L && detonationAt >= 0L;
		}

		private ThrownCharge armed(long armedAt, long detonationAt) {
			return new ThrownCharge(owner, armedAt, detonationAt, previousPos, stuck, placedAt);
		}

		private ThrownCharge withPreviousPos(Vec3d previousPos) {
			return new ThrownCharge(owner, armedAt, detonationAt, previousPos, stuck, placedAt);
		}

		private ThrownCharge stuck(Vec3d previousPos) {
			return new ThrownCharge(owner, armedAt, detonationAt, previousPos, true, placedAt);
		}
	}
}
