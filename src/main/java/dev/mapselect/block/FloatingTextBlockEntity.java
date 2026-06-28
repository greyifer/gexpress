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

public final class FloatingTextBlockEntity extends BlockEntity {
	public static final int MAX_TEXT_LENGTH = 256;
	private String text = "Floating Text";

	public FloatingTextBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.FLOATING_TEXT, pos, state);
	}

	public String text() {
		return text;
	}

	public void setText(String value) {
		text = sanitize(value);
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	public static String sanitize(String value) {
		if (value == null) return "";
		String normalized = value.replace("\r", "").strip();
		return normalized.length() > MAX_TEXT_LENGTH ? normalized.substring(0, MAX_TEXT_LENGTH) : normalized;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("Text", text);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		text = nbt.contains("Text") ? sanitize(nbt.getString("Text")) : "Floating Text";
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
