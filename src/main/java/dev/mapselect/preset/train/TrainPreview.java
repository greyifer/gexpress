package dev.mapselect.preset.train;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

public class TrainPreview {
	private static final Identifier PRESERVE_BLOCK_ID = Identifier.of("wathe", "metal_sheet_stairs");
	private static final long MAX_PREVIEW_BLOCKS = 150000L;
	private static final int PRESERVE_Y = 0;
	private static final int PRESERVE_Z = -357;
	private static final Identifier[] ALWAYS_PRESERVE_IDS = {
		Identifier.of("minecraft", "barrier"),
		Identifier.of("wathe", "barrier_panel"),
		Identifier.of("wathe", "rail_beam"),
		Identifier.of("minecraft", "stone"),
		Identifier.of("minecraft", "cracked_stone_bricks"),
		Identifier.of("minecraft", "stone_bricks"),
		Identifier.of("minecraft", "bricks"),
		Identifier.of("minecraft", "granite"),
		Identifier.of("minecraft", "brick_slab"),
		Identifier.of("minecraft", "brick_stairs"),
		Identifier.of("wathe", "trimmed_lantern")
	};

	// Lazily populated on first paste call. Stays valid for the server's lifetime.
	private static Block cachedPreserveBlock;
	private static Set<Block> cachedAlwaysPreserve;

	private static void ensureBlockCache() {
		if (cachedAlwaysPreserve == null) {
			Set<Block> set = new HashSet<>(ALWAYS_PRESERVE_IDS.length * 2);
			for (Identifier id : ALWAYS_PRESERVE_IDS) {
				set.add(Registries.BLOCK.get(id));
			}
			cachedPreserveBlock = Registries.BLOCK.get(PRESERVE_BLOCK_ID);
			cachedAlwaysPreserve = set;
		}
	}

	public static Result apply(ServerWorld world, Box tmpl, int cornerX, int cornerY, int cornerZ) {
		if (tmpl == null) return Result.NO_TEMPLATE;

		int minX = (int) Math.floor(tmpl.minX);
		int minY = (int) Math.floor(tmpl.minY);
		int minZ = (int) Math.floor(tmpl.minZ);
		int maxX = (int) Math.floor(tmpl.maxX);
		int maxY = (int) Math.floor(tmpl.maxY);
		int maxZ = (int) Math.floor(tmpl.maxZ);

		long volume = (long) Math.max(0, maxX - minX)
			* (long) Math.max(0, maxY - minY)
			* (long) Math.max(0, maxZ - minZ);
		if (volume <= 0L || volume > MAX_PREVIEW_BLOCKS) return Result.PASTE_FAILED;

		int dx = cornerX - minX;
		int dy = cornerY - minY;
		int dz = cornerZ - minZ;

		RegistryWrapper.WrapperLookup registries = world.getRegistryManager();
		int flags = Block.NOTIFY_ALL | Block.FORCE_STATE | Block.SKIP_DROPS;
		ensureBlockCache();
		Block preserveBlock = cachedPreserveBlock;
		Set<Block> alwaysPreserve = cachedAlwaysPreserve;

		BlockPos.Mutable src = new BlockPos.Mutable();
		BlockPos.Mutable dest = new BlockPos.Mutable();
		for (int x = minX; x < maxX; x++) {
			for (int y = minY; y < maxY; y++) {
				for (int z = minZ; z < maxZ; z++) {
					src.set(x, y, z);
					int destX = x + dx, destY = y + dy, destZ = z + dz;
					dest.set(destX, destY, destZ);

					BlockState destState = world.getBlockState(dest);

					if (destY == PRESERVE_Y && destZ == PRESERVE_Z && destState.isOf(preserveBlock)) {
						continue;
					}
					if (alwaysPreserve.contains(destState.getBlock())) {
						continue;
					}

					BlockState state = world.getBlockState(src);
					BlockEntity be = world.getBlockEntity(src);
					if (state == destState && be == null) {
						continue;
					}

					NbtCompound beNbt = be == null ? null : be.createNbtWithIdentifyingData(registries);
					world.setBlockState(dest, state, flags);
					if (beNbt != null) {
						BlockEntity destBe = world.getBlockEntity(dest);
						if (destBe != null) destBe.read(beNbt, registries);
					}
				}
			}
		}

		return Result.OK;
	}

	public enum Result {
		OK,
		NO_TEMPLATE,
		PASTE_FAILED
	}
}
