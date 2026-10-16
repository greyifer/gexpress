package dev.mapselect.block;

import dev.mapselect.leaderboard.LeaderboardCategory;
import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

public final class LeaderboardBlockEntity extends BlockEntity {
	private static final int MAX_SCROLL_OFFSET = 10_000;
	private LeaderboardCategory category = LeaderboardCategory.KILLS;
	private int scrollOffset;

	public LeaderboardBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.LEADERBOARD, pos, state);
	}

	public LeaderboardCategory category() {
		return category;
	}

	public int scrollOffset() {
		return scrollOffset;
	}

	public void setDisplay(LeaderboardCategory category, int scrollOffset) {
		LeaderboardCategory nextCategory = category == null ? LeaderboardCategory.KILLS : category;
		int nextOffset = Math.max(0, Math.min(MAX_SCROLL_OFFSET, scrollOffset));
		if (this.category == nextCategory && this.scrollOffset == nextOffset) return;
		this.category = nextCategory;
		this.scrollOffset = nextOffset;
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Category", category.ordinal());
		nbt.putInt("ScrollOffset", scrollOffset);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		category = LeaderboardCategory.byOrdinal(nbt.getInt("Category"));
		scrollOffset = Math.max(0, Math.min(MAX_SCROLL_OFFSET, nbt.getInt("ScrollOffset")));
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}
}
