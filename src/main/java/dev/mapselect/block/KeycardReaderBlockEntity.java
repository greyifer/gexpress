package dev.mapselect.block;

import dev.mapselect.item.KeycardItem;
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
import org.jetbrains.annotations.Nullable;

public final class KeycardReaderBlockEntity extends BlockEntity {
	private @Nullable BlockPos linkedDoorPos;
	private String keyName = "";
	private @Nullable KeycardItem.Tier keycardTier;

	public KeycardReaderBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.KEYCARD_READER, pos, state);
	}

	public @Nullable BlockPos linkedDoorPos() {
		return linkedDoorPos;
	}

	public String keyName() {
		return keyName;
	}

	public @Nullable KeycardItem.Tier keycardTier() {
		return keycardTier;
	}

	public void link(BlockPos doorPos, String keyName, KeycardItem.Tier keycardTier) {
		this.linkedDoorPos = doorPos == null ? null : doorPos.toImmutable();
		this.keyName = KeycardItem.sanitizeKeyName(keyName);
		this.keycardTier = keycardTier;
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (linkedDoorPos != null) nbt.putLong("LinkedDoorPos", linkedDoorPos.asLong());
		nbt.putString("KeyName", keyName);
		if (keycardTier != null) nbt.putString("KeycardTier", keycardTier.id());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		linkedDoorPos = nbt.contains("LinkedDoorPos") ? BlockPos.fromLong(nbt.getLong("LinkedDoorPos")) : null;
		keyName = nbt.contains("KeyName") ? KeycardItem.sanitizeKeyName(nbt.getString("KeyName")) : "";
		keycardTier = nbt.contains("KeycardTier")
			? KeycardItem.Tier.byId(nbt.getString("KeycardTier"))
			: (keyName.isBlank() ? null : KeycardItem.Tier.BASIC);
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
