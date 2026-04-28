package dev.mapselect.role.trickster;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPreset;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DancingCartsManager {
	private static final int MAX_BLOCKS_PER_CART = 32768;
	private static final Map<RegistryKey<World>, Map<UUID, Long>> nextUseTicks = new ConcurrentHashMap<>();

	private DancingCartsManager() {}

	public static void tryActivate(ServerPlayerEntity harlequin) {
		if (harlequin == null || harlequin.getWorld().isClient) return;
		if (VultureManager.isStashed(harlequin)) return;
		if (!canUseHarlequinHere(harlequin.getWorld(), harlequin) || !isHarlequin(harlequin)) return;
		if (!isPlayableForHarlequin(harlequin, harlequin)) return;

		ServerWorld world = harlequin.getServerWorld();
		long remaining = cooldownRemainingTicks(world, harlequin.getUuid());
		if (remaining > 0L) {
			harlequin.sendMessage(Text.literal("Dancing Carts ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}

		TrainPreset trainPreset = activeTrainPreset(world);
		if (trainPreset == null) {
			harlequin.sendMessage(Text.literal("Dancing Carts needs an active map with a train preset."), true);
			return;
		}

		List<CartRegion> regions = cartRegions(trainPreset);
		if (regions.size() < 4) {
			harlequin.sendMessage(Text.literal("Dancing Carts needs at least four configured carts."), true);
			return;
		}

		List<Integer> movable = interiorIndexes(regions);
		if (movable.size() < 2) {
			harlequin.sendMessage(Text.literal("Dancing Carts needs at least two middle carts."), true);
			return;
		}

		String validation = validateMovableRegions(regions, movable);
		if (validation != null) {
			harlequin.sendMessage(Text.literal(validation), true);
			return;
		}

		Map<Integer, Integer> moves = buildCartMoves(movable);
		try {
			performMove(world, regions, moves);
		} catch (RuntimeException e) {
			harlequin.sendMessage(Text.literal("Dancing Carts failed: " + e.getMessage()), true);
			return;
		}

		setCooldown(world, harlequin.getUuid());
		world.playSound(null, harlequin.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundCategory.PLAYERS, 0.9F, 0.85F);
		harlequin.sendMessage(Text.literal("Dancing Carts."), true);
	}

	public static void clearForTimeRewind(ServerWorld world) {
		if (world != null) nextUseTicks.remove(world.getRegistryKey());
	}

	private static TrainPreset activeTrainPreset(ServerWorld world) {
		String currentMap = MapWeatherComponent.KEY.get(world).getCurrentMapName();
		if (currentMap == null || currentMap.isBlank()) return null;
		try {
			MapPreset mapPreset = PresetStorage.load(world.getServer(), currentMap);
			if (mapPreset == null || mapPreset.defaultTrainPreset == null || mapPreset.defaultTrainPreset.isBlank()) {
				return null;
			}
			return TrainPresetStorage.load(world.getServer(), mapPreset.defaultTrainPreset);
		} catch (IOException ignored) {
			return null;
		}
	}

	private static List<CartRegion> cartRegions(TrainPreset preset) {
		preset.normalize();
		List<CartRegion> out = new ArrayList<>();
		if (preset.trainCarts == null) return out;
		for (TrainPreset.CartData cart : preset.trainCarts) {
			CartRegion region = CartRegion.from(cart);
			if (region != null) out.add(region);
		}
		return out;
	}

	private static List<Integer> interiorIndexes(List<CartRegion> regions) {
		List<Integer> out = new ArrayList<>();
		for (int i = 1; i < regions.size() - 1; i++) {
			out.add(i);
		}
		return out;
	}

	private static String validateMovableRegions(List<CartRegion> regions, List<Integer> movable) {
		CartRegion first = regions.get(movable.getFirst());
		for (int index : movable) {
			CartRegion region = regions.get(index);
			if (region.blockCount() > MAX_BLOCKS_PER_CART) {
				return "Dancing Carts cart #" + (index + 1) + " is too large.";
			}
			if (!region.sameSize(first)) {
				return "Dancing Carts needs all middle carts to be the same size.";
			}
		}
		return null;
	}

	private static Map<Integer, Integer> buildCartMoves(List<Integer> movable) {
		List<Integer> destinations = new ArrayList<>(movable);
		if (destinations.size() == 2) {
			Collections.swap(destinations, 0, 1);
		} else {
			for (int attempt = 0; attempt < 32; attempt++) {
				Collections.shuffle(destinations);
				boolean valid = true;
				for (int i = 0; i < movable.size(); i++) {
					if (movable.get(i).equals(destinations.get(i))) {
						valid = false;
						break;
					}
				}
				if (valid) break;
			}
			for (int i = 0; i < movable.size(); i++) {
				if (!movable.get(i).equals(destinations.get(i))) continue;
				Collections.rotate(destinations, 1);
				break;
			}
		}

		Map<Integer, Integer> moves = new LinkedHashMap<>();
		for (int i = 0; i < movable.size(); i++) {
			moves.put(movable.get(i), destinations.get(i));
		}
		return moves;
	}

	private static void performMove(ServerWorld world, List<CartRegion> regions, Map<Integer, Integer> moves) {
		RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();
		List<CartSnapshot> snapshots = new ArrayList<>();
		for (Map.Entry<Integer, Integer> move : moves.entrySet()) {
			CartRegion source = regions.get(move.getKey());
			CartRegion destination = regions.get(move.getValue());
			snapshots.add(CartSnapshot.capture(world, source, destination, lookup));
		}

		Map<UUID, EntityMove> entityMoves = captureEntityMoves(world, regions, moves);
		for (int sourceIndex : moves.keySet()) {
			regions.get(sourceIndex).clear(world);
		}
		for (CartSnapshot snapshot : snapshots) {
			snapshot.paste(world, lookup);
		}
		for (EntityMove move : entityMoves.values()) {
			move.apply(world);
		}
	}

	private static Map<UUID, EntityMove> captureEntityMoves(ServerWorld world,
			List<CartRegion> regions, Map<Integer, Integer> moves) {
		Map<UUID, EntityMove> out = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : moves.entrySet()) {
			CartRegion source = regions.get(entry.getKey());
			CartRegion destination = regions.get(entry.getValue());
			Vec3i delta = destination.min().subtract(source.min());
			for (Entity entity : world.getOtherEntities(null, source.entityBox(), entity -> !entity.isRemoved())) {
				out.putIfAbsent(entity.getUuid(), new EntityMove(entity, delta));
			}
			for (ServerPlayerEntity player : world.getPlayers(player -> !player.isRemoved())) {
				if (source.entityBox().intersects(player.getBoundingBox())) {
					out.putIfAbsent(player.getUuid(), new EntityMove(player, delta));
				}
			}
		}
		return out;
	}

	private static long cooldownRemainingTicks(ServerWorld world, UUID playerId) {
		Map<UUID, Long> worldCooldowns = nextUseTicks.get(world.getRegistryKey());
		if (worldCooldowns == null) return 0L;
		return Math.max(0L, worldCooldowns.getOrDefault(playerId, 0L) - world.getTime());
	}

	private static void setCooldown(ServerWorld world, UUID playerId) {
		nextUseTicks.computeIfAbsent(world.getRegistryKey(), key -> new ConcurrentHashMap<>())
			.put(playerId, world.getTime() + GexpressConfig.getTricksterSwapDurationSeconds() * 20L);
	}

	private static boolean isHarlequin(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.TRICKSTER_ID.equals(role.identifier());
	}

	private static boolean canUseHarlequinHere(World world, PlayerEntity player) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(player);
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean isPlayableForHarlequin(PlayerEntity player, PlayerEntity harlequin) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(harlequin);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private record CartRegion(BlockPos min, BlockPos max) {
		private static CartRegion from(TrainPreset.CartData cart) {
			if (cart == null || cart.area == null) return null;
			int minX = (int) Math.floor(Math.min(cart.area.minX, cart.area.maxX));
			int minY = (int) Math.floor(Math.min(cart.area.minY, cart.area.maxY));
			int minZ = (int) Math.floor(Math.min(cart.area.minZ, cart.area.maxZ));
			int maxX = (int) Math.floor(Math.max(cart.area.minX, cart.area.maxX));
			int maxY = (int) Math.floor(Math.max(cart.area.minY, cart.area.maxY));
			int maxZ = (int) Math.floor(Math.max(cart.area.minZ, cart.area.maxZ));
			return new CartRegion(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
		}

		private int sizeX() {
			return max.getX() - min.getX() + 1;
		}

		private int sizeY() {
			return max.getY() - min.getY() + 1;
		}

		private int sizeZ() {
			return max.getZ() - min.getZ() + 1;
		}

		private long blockCount() {
			return (long) sizeX() * (long) sizeY() * (long) sizeZ();
		}

		private boolean sameSize(CartRegion other) {
			return other != null && sizeX() == other.sizeX() && sizeY() == other.sizeY() && sizeZ() == other.sizeZ();
		}

		private Box entityBox() {
			return new Box(min.getX(), min.getY(), min.getZ(),
				max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
		}

		private void clear(ServerWorld world) {
			BlockPos.Mutable mutable = new BlockPos.Mutable();
			for (int x = min.getX(); x <= max.getX(); x++) {
				for (int y = min.getY(); y <= max.getY(); y++) {
					for (int z = min.getZ(); z <= max.getZ(); z++) {
						mutable.set(x, y, z);
						world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 3);
					}
				}
			}
		}
	}

	private record CartSnapshot(CartRegion source, CartRegion destination, List<BlockEntry> blocks) {
		private static CartSnapshot capture(ServerWorld world, CartRegion source, CartRegion destination,
				RegistryWrapper.WrapperLookup lookup) {
			List<BlockEntry> blocks = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, source.blockCount()));
			BlockPos.Mutable mutable = new BlockPos.Mutable();
			for (int x = source.min().getX(); x <= source.max().getX(); x++) {
				for (int y = source.min().getY(); y <= source.max().getY(); y++) {
					for (int z = source.min().getZ(); z <= source.max().getZ(); z++) {
						mutable.set(x, y, z);
						BlockState state = world.getBlockState(mutable);
						BlockEntity blockEntity = world.getBlockEntity(mutable);
						NbtCompound tag = blockEntity == null ? null : blockEntity.createNbtWithId(lookup).copy();
						blocks.add(new BlockEntry(
							x - source.min().getX(),
							y - source.min().getY(),
							z - source.min().getZ(),
							state,
							tag
						));
					}
				}
			}
			return new CartSnapshot(source, destination, blocks);
		}

		private void paste(ServerWorld world, RegistryWrapper.WrapperLookup lookup) {
			BlockPos.Mutable mutable = new BlockPos.Mutable();
			for (BlockEntry entry : blocks) {
				mutable.set(
					destination.min().getX() + entry.dx(),
					destination.min().getY() + entry.dy(),
					destination.min().getZ() + entry.dz()
				);
				BlockPos target = mutable.toImmutable();
				world.setBlockState(target, entry.state(), 3);
				if (entry.blockEntityNbt() == null) continue;
				BlockEntity blockEntity = world.getBlockEntity(target);
				if (blockEntity == null) continue;
				NbtCompound tag = entry.blockEntityNbt().copy();
				tag.putInt("x", target.getX());
				tag.putInt("y", target.getY());
				tag.putInt("z", target.getZ());
				blockEntity.read(tag, lookup);
				blockEntity.markDirty();
			}
		}
	}

	private record BlockEntry(int dx, int dy, int dz, BlockState state, NbtCompound blockEntityNbt) {}

	private record EntityMove(Entity entity, Vec3i delta) {
		private void apply(ServerWorld world) {
			if (entity.isRemoved() || entity.getWorld() != world) return;
			double x = entity.getX() + delta.getX();
			double y = entity.getY() + delta.getY();
			double z = entity.getZ() + delta.getZ();
			if (entity instanceof ServerPlayerEntity player) {
				player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
			} else {
				entity.refreshPositionAndAngles(x, y, z, entity.getYaw(), entity.getPitch());
				entity.velocityModified = true;
			}
		}
	}
}
