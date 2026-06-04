package dev.mapselect.block;

import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GoldBeveragePlateBlockEntity extends BlockEntity {
	private final List<ItemStack> storedItems = new ArrayList<>();
	private String poisoner;
	private boolean drink;

	public GoldBeveragePlateBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.GOLD_BEVERAGE_PLATE, pos, state);
	}

	public List<ItemStack> getStoredItems() {
		return storedItems;
	}

	public void addItem(ItemStack stack) {
		if (stack.isEmpty()) return;
		storedItems.add(stack.copy());
		sync();
	}

	public String getPoisoner() {
		return poisoner;
	}

	public void setPoisoner(String poisoner) {
		this.poisoner = poisoner;
		sync();
	}

	public boolean isDrink() {
		return drink;
	}

	public void setDrink(boolean drink) {
		this.drink = drink;
		sync();
	}

	private void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		NbtCompound itemsNbt = new NbtCompound();
		for (int i = 0; i < storedItems.size(); i++) {
			ItemStack stack = storedItems.get(i);
			if (!stack.isEmpty()) itemsNbt.put("Item" + i, stack.encode(registryLookup));
		}
		nbt.put("Items", itemsNbt);
		if (poisoner != null) nbt.putString("poisoner", poisoner);
		nbt.putBoolean("Drink", drink);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		storedItems.clear();
		if (nbt.contains("Items")) {
			NbtCompound itemsNbt = nbt.getCompound("Items");
			for (String key : itemsNbt.getKeys()) {
				Optional<ItemStack> stack = ItemStack.fromNbt(registryLookup, itemsNbt.getCompound(key));
				stack.ifPresent(storedItems::add);
			}
		}
		poisoner = nbt.contains("poisoner") ? nbt.getString("poisoner") : null;
		drink = nbt.getBoolean("Drink");
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}
