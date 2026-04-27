package dev.mapselect.block;

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
import net.minecraft.world.World;

public class GreyiferPlushBlockEntity extends BlockEntity {
	public double squash;

	public GreyiferPlushBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.GREYIFER_PLUSH, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, GreyiferPlushBlockEntity entity) {
		if (entity.squash <= 0.0D) return;

		entity.squash /= 3.0D;
		if (entity.squash < 0.01D) {
			entity.squash = 0.0D;
			if (world != null) {
				world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
			}
		}
	}

	public void squish(int amount) {
		this.squash += amount;
		if (this.world != null) {
			this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_LISTENERS);
		}
		this.markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putDouble("squash", this.squash);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		this.squash = nbt.getDouble("squash");
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return this.createNbt(registryLookup);
	}
}
