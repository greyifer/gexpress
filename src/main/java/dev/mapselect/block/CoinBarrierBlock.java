package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.BlockView;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CoinBarrierBlock extends BlockWithEntity {
	private static final MapCodec<CoinBarrierBlock> CODEC = createCodec(CoinBarrierBlock::new);
	private static final VoxelShape COLLISION = createCuboidShape(0, 0, 0, 16, 32, 16);
	public static final int PRICE_PER_BLOCK = 25;
	public static final int MAX_CONNECTED_BLOCKS = 2048;

	public CoinBarrierBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CoinBarrierBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return COLLISION;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player.getMainHandStack().isOf(MapSelectBlocks.COIN_BARRIER_ITEM)
				|| player.getOffHandStack().isOf(MapSelectBlocks.COIN_BARRIER_ITEM)) {
			return ActionResult.PASS;
		}
		if (player.shouldCancelInteraction() && player.getMainHandStack().isEmpty()) {
			if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
				CoinBarrierManager.openEditor(serverPlayer, pos);
			}
			return ActionResult.SUCCESS;
		}
		if (world.isClient) return ActionResult.SUCCESS;
		return player instanceof ServerPlayerEntity serverPlayer && CoinBarrierManager.openPurchase(serverPlayer, pos)
			? ActionResult.SUCCESS
			: ActionResult.PASS;
	}

	public static Cluster scan(World world, BlockPos origin) {
		if (world == null || !isCoinBarrier(world, origin)) return Cluster.EMPTY;
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = origin.toImmutable();
		visited.add(start);
		queue.add(start);

		while (!queue.isEmpty() && visited.size() < MAX_CONNECTED_BLOCKS) {
			BlockPos current = queue.removeFirst();
			for (Direction direction : Direction.values()) {
				BlockPos next = current.offset(direction).toImmutable();
				if (visited.contains(next) || !isCoinBarrier(world, next)) continue;
				visited.add(next);
				queue.addLast(next);
			}
		}

		List<BlockPos> positions = List.copyOf(visited);
		Vec3d center = centerOf(positions);
		int defaultPrice = positions.size() * PRICE_PER_BLOCK;
		Metadata metadata = metadataFor(world, origin, positions);
		return new Cluster(positions, center, metadata.priceOrDefault(defaultPrice), metadata.title());
	}

	private static boolean isCoinBarrier(World world, BlockPos pos) {
		return world.getBlockState(pos).isOf(MapSelectBlocks.COIN_BARRIER);
	}

	private static Metadata metadataFor(World world, BlockPos origin, List<BlockPos> positions) {
		Metadata originMetadata = metadataAt(world, origin);
		if (!originMetadata.isDefault()) return originMetadata;
		for (BlockPos pos : positions) {
			Metadata metadata = metadataAt(world, pos);
			if (!metadata.isDefault()) return metadata;
		}
		return Metadata.DEFAULT;
	}

	private static Metadata metadataAt(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof CoinBarrierBlockEntity barrier) {
			return new Metadata(barrier.price(), barrier.title());
		}
		return Metadata.DEFAULT;
	}

	private static Vec3d centerOf(List<BlockPos> positions) {
		if (positions.isEmpty()) return Vec3d.ZERO;
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		for (BlockPos pos : positions) {
			x += pos.getX() + 0.5D;
			y += pos.getY() + 0.5D;
			z += pos.getZ() + 0.5D;
		}
		double count = positions.size();
		return new Vec3d(x / count, y / count, z / count);
	}

	private record Metadata(int price, String title) {
		private static final Metadata DEFAULT = new Metadata(CoinBarrierBlockEntity.DEFAULT_PRICE, "");

		private int priceOrDefault(int defaultPrice) {
			return price >= 0 ? price : defaultPrice;
		}

		private boolean isDefault() {
			return price < 0 && title.isBlank();
		}
	}

	public record Cluster(List<BlockPos> positions, Vec3d center, int price, String title) {
		public static final Cluster EMPTY = new Cluster(new ArrayList<>(), Vec3d.ZERO, 0, "");
	}
}
