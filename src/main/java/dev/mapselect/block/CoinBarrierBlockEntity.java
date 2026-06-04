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

public class CoinBarrierBlockEntity extends BlockEntity {
	public static final int DEFAULT_PRICE = -1;
	public static final int MAX_PRICE = 999_999;
	public static final int MAX_TITLE_LENGTH = 64;

	private int price = DEFAULT_PRICE;
	private String title = "";

	public CoinBarrierBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.COIN_BARRIER, pos, state);
	}

	public int price() {
		return price;
	}

	public String title() {
		return title;
	}

	public int priceOrDefault(int defaultPrice) {
		return price >= 0 ? price : defaultPrice;
	}

	public void setMetadata(int price, String title) {
		this.price = clampPrice(price);
		this.title = sanitizeTitle(title);
		sync();
	}

	private void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	public static int clampPrice(int price) {
		return Math.max(0, Math.min(MAX_PRICE, price));
	}

	public static String sanitizeTitle(String title) {
		if (title == null) return "";
		String trimmed = title.strip();
		return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putInt("Price", price);
		if (!title.isBlank()) nbt.putString("Title", title);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (nbt.contains("Price")) {
			int storedPrice = nbt.getInt("Price");
			price = storedPrice < 0 ? DEFAULT_PRICE : clampPrice(storedPrice);
		} else {
			price = DEFAULT_PRICE;
		}
		title = nbt.contains("Title") ? sanitizeTitle(nbt.getString("Title")) : "";
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
