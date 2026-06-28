package dev.mapselect.block;

import dev.doctor4t.wathe.block.property.OrnamentShape;
import dev.mapselect.registry.MapSelectBlockEntities;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class FusedOrnamentBlockEntity extends BlockEntity {
	private static final Identifier DEFAULT_ORNAMENT_ID = Identifier.of("wathe", "gold_ornament");

	private BlockState baseState = Blocks.STONE.getDefaultState();
	private Identifier ornamentId = DEFAULT_ORNAMENT_ID;
	private final EnumMap<Direction, Decoration> decorations = new EnumMap<>(Direction.class);
	private final Map<Direction, Decoration> decorationsView = Collections.unmodifiableMap(decorations);
	private boolean goldLedgeCollisionDirty = true;
	private boolean hasGoldLedgeCollision;
	private VoxelShape goldLedgeCollisionShape = VoxelShapes.empty();
	private long renderVersion;

	public FusedOrnamentBlockEntity(BlockPos pos, BlockState state) {
		super(MapSelectBlockEntities.FUSED_ORNAMENT, pos, state);
	}

	public record Decoration(Identifier ornamentId, OrnamentShape shape) {}

	public BlockState getBaseState() {
		return baseState;
	}

	public void setBaseState(BlockState baseState) {
		if (baseState == null || baseState.isAir()) return;
		this.baseState = baseState;
		markRenderDataDirty();
		sync();
	}

	public Identifier getOrnamentId() {
		Decoration first = firstDecoration();
		return first == null ? ornamentId : first.ornamentId();
	}

	public void setOrnamentId(Identifier ornamentId) {
		if (ornamentId == null) return;
		this.ornamentId = ornamentId;
		sync();
	}

	public Map<Direction, Decoration> getDecorations() {
		return decorationsView;
	}

	@Nullable
	public Decoration getDecoration(Direction side) {
		return side == null ? null : decorations.get(side);
	}

	public boolean hasDecoration(Identifier id) {
		return decorations.values().stream().anyMatch(decoration -> decoration.ornamentId().equals(id));
	}

	public boolean hasDecorations() {
		return !decorations.isEmpty();
	}

	public boolean setDecoration(Direction side, Identifier id, OrnamentShape shape) {
		if (side == null || id == null || shape == null) return false;
		Decoration next = new Decoration(id, shape);
		Decoration previous = decorations.put(side, next);
		ornamentId = id;
		if (previous == null || !previous.equals(next)) {
			markRenderDataDirty();
			sync();
			return true;
		}
		return false;
	}

	public boolean removeDecoration(Direction side) {
		if (side == null || decorations.remove(side) == null) return false;
		Decoration first = firstDecoration();
		ornamentId = first == null ? DEFAULT_ORNAMENT_ID : first.ornamentId();
		markRenderDataDirty();
		sync();
		return true;
	}

	public long getRenderVersion() {
		return renderVersion;
	}

	public boolean hasGoldLedgeCollision() {
		rebuildGoldLedgeCollisionCache();
		return hasGoldLedgeCollision;
	}

	public VoxelShape getGoldLedgeCollisionShape() {
		rebuildGoldLedgeCollisionCache();
		return goldLedgeCollisionShape;
	}

	private Decoration firstDecoration() {
		return decorations.values().stream().findFirst().orElse(null);
	}

	private void markRenderDataDirty() {
		renderVersion++;
		goldLedgeCollisionDirty = true;
	}

	private void rebuildGoldLedgeCollisionCache() {
		if (!goldLedgeCollisionDirty) return;
		VoxelShape ledges = VoxelShapes.empty();
		boolean hasLedge = false;
		for (Map.Entry<Direction, Decoration> entry : decorations.entrySet()) {
			if (entry.getValue().ornamentId().equals(MapSelectBlocks.WATHE_GOLD_LEDGE_ID)) {
				ledges = VoxelShapes.union(ledges,
					FusedOrnamentGeometry.goldLedgeCollisionShape(entry.getKey(), entry.getValue().shape()));
				hasLedge = true;
			}
		}
		goldLedgeCollisionShape = ledges;
		hasGoldLedgeCollision = hasLedge;
		goldLedgeCollisionDirty = false;
	}

	public void sync() {
		markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.put("BaseState", NbtHelper.fromBlockState(baseState));
		nbt.putString("OrnamentId", getOrnamentId().toString());
		NbtList decorationList = new NbtList();
		for (Map.Entry<Direction, Decoration> entry : decorations.entrySet()) {
			NbtCompound decoration = new NbtCompound();
			decoration.putString("Side", entry.getKey().asString());
			decoration.putString("OrnamentId", entry.getValue().ornamentId().toString());
			decoration.putString("Shape", entry.getValue().shape().name());
			decorationList.add(decoration);
		}
		nbt.put("Decorations", decorationList);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		decorations.clear();
		if (nbt.contains("BaseState")) {
			baseState = NbtHelper.toBlockState(
				registryLookup.getWrapperOrThrow(RegistryKeys.BLOCK),
				nbt.getCompound("BaseState")
			);
		}
		ornamentId = nbt.contains("OrnamentId") ? Identifier.of(nbt.getString("OrnamentId")) : DEFAULT_ORNAMENT_ID;
		if (nbt.contains("Decorations")) {
			NbtList decorationList = nbt.getList("Decorations", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < decorationList.size(); i++) {
				NbtCompound decoration = decorationList.getCompound(i);
				Direction side = parseDirection(decoration.getString("Side"));
				Identifier id = parseIdentifier(decoration.getString("OrnamentId"));
				OrnamentShape shape = parseShape(decoration.getString("Shape"));
				if (side != null && id != null && shape != null) {
					decorations.put(side, new Decoration(id, shape));
					ornamentId = id;
				}
			}
		}
		if (decorations.isEmpty() && nbt.contains("OrnamentId")) {
			decorations.put(legacyFacing(), new Decoration(ornamentId, legacyShape()));
		}
		markRenderDataDirty();
	}

	private Direction legacyFacing() {
		BlockState state = getCachedState();
		return state.contains(FacingBlock.FACING) ? state.get(FacingBlock.FACING) : Direction.NORTH;
	}

	private OrnamentShape legacyShape() {
		BlockState state = getCachedState();
		return state.contains(FusedOrnamentBlock.SHAPE) ? state.get(FusedOrnamentBlock.SHAPE) : OrnamentShape.CENTER;
	}

	@Nullable
	private static Direction parseDirection(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	@Nullable
	private static Identifier parseIdentifier(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return Identifier.of(raw.trim());
		} catch (Exception ignored) {
			return null;
		}
	}

	@Nullable
	private static OrnamentShape parseShape(String raw) {
		if (raw == null || raw.isBlank()) return OrnamentShape.CENTER;
		try {
			return OrnamentShape.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return OrnamentShape.CENTER;
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
