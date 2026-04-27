package dev.mapselect.role.bombspecialist;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectSounds;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class C4Detonation {
	private C4Detonation() {}

	private static final double BLAST_RADIUS = 3.0D;
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
		thrownCharges.put(entity.getUuid(), new ThrownCharge(owner, -1L, -1L));
	}

	public static void triggerRemoteDetonation(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		int armed = 0;
		long now = world.getTime();
		long detonationAt = now
			+ (long) GexpressConfig.getC4FirstBeepSeconds() * 20L
			+ (long) GexpressConfig.getC4FuseSeconds() * 20L;
		for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
			ThrownCharge charge = entry.getValue();
			if (!player.getUuid().equals(charge.owner())) continue;
			ItemEntity entity = thrownChargeEntity(world, entry.getKey());
			if (entity == null) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			if (charge.isArmed()) continue;
			thrownCharges.put(entry.getKey(), new ThrownCharge(charge.owner(), now, detonationAt));
			armed++;
		}
		if (armed <= 0) {
			player.sendMessage(Text.literal("No thrown C4 charges to detonate."), true);
			return;
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK,
			SoundCategory.PLAYERS, 0.8F, 1.4F);
		player.sendMessage(Text.literal("Armed " + armed + " C4 charge" + (armed == 1 ? "." : "s.")), true);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		C4BackComponent comp = C4BackComponent.KEY.getNullable(world);
		if (comp == null) return;
		Map<UUID, Long> carriers = comp.getCarriers();
		boolean hasThrown = !thrownCharges.isEmpty();
		if (carriers.isEmpty() && !hasThrown) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if ((game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE)
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
		world.spawnParticles(
			new ItemStackParticleEffect(ParticleTypes.ITEM, WatheItems.THROWN_GRENADE.getDefaultStack()),
			x, y, z, 100, 0.0, 0.0, 0.0, 1.0);

		List<ServerPlayerEntity> victims = world.getPlayers(p -> {
			if (!GameFunctions.isPlayerAliveAndSurvival(p)) return false;
			return p.getPos().isInRange(blastCenter, BLAST_RADIUS);
		});
		for (ServerPlayerEntity victim : victims) {
			GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.GRENADE);
		}
	}

	private static void tickThrownCharges(ServerWorld world, long now) {
		for (Map.Entry<UUID, ThrownCharge> entry : List.copyOf(thrownCharges.entrySet())) {
			ItemEntity entity = thrownChargeEntity(world, entry.getKey());
			if (entity == null) {
				thrownCharges.remove(entry.getKey());
				continue;
			}
			ThrownCharge charge = entry.getValue();
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
		world.spawnParticles(
			new ItemStackParticleEffect(ParticleTypes.ITEM, MapSelectItems.C4.getDefaultStack()),
			entity.getX(), entity.getY() + 0.1D, entity.getZ(),
			4, 0.05D, 0.05D, 0.05D, 0.02D);
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

	private record ThrownCharge(UUID owner, long armedAt, long detonationAt) {
		private boolean isArmed() {
			return armedAt >= 0L && detonationAt >= 0L;
		}
	}
}
