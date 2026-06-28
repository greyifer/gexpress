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
import net.minecraft.util.math.Vec3d;

public class CoinBarrierBlockEntity extends BlockEntity {
	public static final int DEFAULT_PRICE = -1;
	public static final int MAX_PRICE = 999_999;
	public static final int MAX_TITLE_LENGTH = 64;

	private int price = DEFAULT_PRICE;
	private String title = "";
	private Vec3d ribbonStart;
	private Vec3d ribbonEnd;
	private boolean fixedRibbonAnchors;

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

	public boolean hasRibbonSpan() {
		return fixedRibbonAnchors && ribbonStart != null && ribbonEnd != null;
	}

	public Vec3d ribbonStart() {
		return ribbonStart;
	}

	public Vec3d ribbonEnd() {
		return ribbonEnd;
	}

	public void setRibbonSpan(Vec3d start, Vec3d end) {
		ribbonStart = start;
		ribbonEnd = end;
		fixedRibbonAnchors = start != null && end != null;
		sync();
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
		if (hasRibbonSpan()) {
			nbt.putDouble("RibbonStartX", ribbonStart.x);
			nbt.putDouble("RibbonStartY", ribbonStart.y);
			nbt.putDouble("RibbonStartZ", ribbonStart.z);
			nbt.putDouble("RibbonEndX", ribbonEnd.x);
			nbt.putDouble("RibbonEndY", ribbonEnd.y);
			nbt.putDouble("RibbonEndZ", ribbonEnd.z);
			nbt.putBoolean("FixedRibbonAnchors", true);
		}
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
		fixedRibbonAnchors = nbt.getBoolean("FixedRibbonAnchors");
		if (fixedRibbonAnchors && nbt.contains("RibbonStartX") && nbt.contains("RibbonStartY") && nbt.contains("RibbonStartZ")
				&& nbt.contains("RibbonEndX") && nbt.contains("RibbonEndY") && nbt.contains("RibbonEndZ")) {
			ribbonStart = new Vec3d(nbt.getDouble("RibbonStartX"), nbt.getDouble("RibbonStartY"), nbt.getDouble("RibbonStartZ"));
			ribbonEnd = new Vec3d(nbt.getDouble("RibbonEndX"), nbt.getDouble("RibbonEndY"), nbt.getDouble("RibbonEndZ"));
		} else {
			ribbonStart = null;
			ribbonEnd = null;
			fixedRibbonAnchors = false;
		}
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
