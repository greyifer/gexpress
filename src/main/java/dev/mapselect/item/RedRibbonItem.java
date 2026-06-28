package dev.mapselect.item;

import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import dev.mapselect.block.CoinBarrierBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RedRibbonItem extends Item {
	private static final int MAX_LENGTH = 64;
	private static final Map<UUID, Endpoint> FIRST_POINTS = new ConcurrentHashMap<>();

	public RedRibbonItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)
				|| !(context.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;
		if (!GexpressPermissions.canEditSetupOptions(player)) {
			player.sendMessage(Text.literal("You need setup permission to place red ribbons.")
				.formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}

		BlockPos point = placementPoint(context);
		Vec3d attachment = attachmentPoint(context);
		Endpoint first = FIRST_POINTS.get(player.getUuid());
		if (first == null || !first.world().equals(world.getRegistryKey())) {
			FIRST_POINTS.put(player.getUuid(), new Endpoint(world.getRegistryKey(), point, attachment));
			player.sendMessage(Text.literal("Red Ribbon start set. Click the other end.")
				.formatted(Formatting.RED), true);
			return ActionResult.SUCCESS;
		}

		FIRST_POINTS.remove(player.getUuid());
		if (axisDistance(first.pos(), point) > MAX_LENGTH) {
			player.sendMessage(Text.literal("Red Ribbon can be at most " + MAX_LENGTH + " blocks long.")
				.formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}

		List<BlockPos> path = connectedLine(first.pos(), point);
		int placed = 0;
		List<BlockPos> ribbonBlocks = new ArrayList<>();
		for (BlockPos pos : path) {
			if (!world.isInBuildLimit(pos)) continue;
			BlockState state = world.getBlockState(pos);
			if (state.isOf(MapSelectBlocks.COIN_BARRIER)) {
				ribbonBlocks.add(pos);
				continue;
			}
			if (!state.isReplaceable()) continue;
			if (world.setBlockState(pos, MapSelectBlocks.COIN_BARRIER.getDefaultState(), Block.NOTIFY_ALL)) {
				placed++;
				ribbonBlocks.add(pos);
			}
		}
		if (ribbonBlocks.isEmpty()) {
			player.sendMessage(Text.literal("The ribbon path is blocked; no sections were placed.")
				.formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		for (BlockPos pos : ribbonBlocks) {
			if (world.getBlockEntity(pos) instanceof CoinBarrierBlockEntity barrier) {
				barrier.setRibbonSpan(first.attachment(), attachment);
			}
		}
		player.sendMessage(Text.literal("Placed Red Ribbon across " + placed + " blocks.")
			.formatted(Formatting.RED), true);
		return ActionResult.SUCCESS;
	}

	private static Vec3d attachmentPoint(ItemUsageContext context) {
		Vec3i vector = context.getSide().getVector();
		Vec3d base = Vec3d.ofCenter(context.getBlockPos());
		if (vector.getY() == 0) base = base.add(0.0D, -0.30D, 0.0D);
		return base.add(
			vector.getX() * 0.53D,
			vector.getY() * 0.53D,
			vector.getZ() * 0.53D);
	}

	private static BlockPos placementPoint(ItemUsageContext context) {
		BlockPos clicked = context.getBlockPos();
		return context.getWorld().getBlockState(clicked).isReplaceable()
			? clicked.toImmutable()
			: clicked.offset(context.getSide()).toImmutable();
	}

	private static int axisDistance(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()),
			Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	private static List<BlockPos> connectedLine(BlockPos start, BlockPos end) {
		LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
		int steps = Math.max(1, axisDistance(start, end));
		BlockPos previous = start;
		positions.add(previous.toImmutable());
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			BlockPos target = new BlockPos(
				(int) Math.round(start.getX() + (end.getX() - start.getX()) * t),
				(int) Math.round(start.getY() + (end.getY() - start.getY()) * t),
				(int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * t));
			int x = previous.getX();
			int y = previous.getY();
			int z = previous.getZ();
			while (x != target.getX()) {
				x += Integer.signum(target.getX() - x);
				positions.add(new BlockPos(x, y, z));
			}
			while (z != target.getZ()) {
				z += Integer.signum(target.getZ() - z);
				positions.add(new BlockPos(x, y, z));
			}
			while (y != target.getY()) {
				y += Integer.signum(target.getY() - y);
				positions.add(new BlockPos(x, y, z));
			}
			previous = target;
		}
		return new ArrayList<>(positions);
	}

	private record Endpoint(RegistryKey<World> world, BlockPos pos, Vec3d attachment) {}
}
