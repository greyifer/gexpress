package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PainterStatePayload(List<Entry> bodyTints, List<Entry> playerTints,
		List<DoorwayEntry> doorways) implements CustomPayload {
	public static final Id<PainterStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_state"));
	private static final int MAX_DOORWAY_BLOCKS = 20000;

	public PainterStatePayload(List<Entry> bodyTints, List<Entry> playerTints) {
		this(bodyTints, playerTints, List.of());
	}

	public PainterStatePayload {
		bodyTints = normalize(bodyTints);
		playerTints = normalize(playerTints);
		doorways = normalizeDoorways(doorways);
	}

	public static PainterStatePayload clear() {
		return new PainterStatePayload(List.of(), List.of(), List.of());
	}

	public static final PacketCodec<PacketByteBuf, PainterStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			writeEntries(buf, payload.bodyTints());
			writeEntries(buf, payload.playerTints());
			writeDoorways(buf, payload.doorways());
		},
		buf -> new PainterStatePayload(readEntries(buf), readEntries(buf), readDoorways(buf))
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static void writeEntries(PacketByteBuf buf, List<Entry> entries) {
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buf.writeUuid(entry.targetId());
			buf.writeInt(entry.color());
			buf.writeVarInt(entry.remainingTicks());
			buf.writeVarInt(entry.totalTicks());
		}
	}

	private static List<Entry> readEntries(PacketByteBuf buf) {
		int size = Math.max(0, Math.min(256, buf.readVarInt()));
		List<Entry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new Entry(buf.readUuid(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));
		}
		return entries;
	}

	private static void writeDoorways(PacketByteBuf buf, List<DoorwayEntry> entries) {
		buf.writeVarInt(entries.size());
		for (DoorwayEntry entry : entries) {
			buf.writeBlockPos(entry.lowerPos());
			buf.writeInt(entry.color());
			buf.writeVarInt(entry.remainingTicks());
			buf.writeVarInt(entry.totalTicks());
			buf.writeEnumConstant(entry.facing());
			buf.writeEnumConstant(entry.through());
			buf.writeBlockPos(entry.destinationLowerPos());
			buf.writeEnumConstant(entry.destinationFacing());
			buf.writeEnumConstant(entry.destinationThrough());
			buf.writeDouble(entry.destination().x);
			buf.writeDouble(entry.destination().y);
			buf.writeDouble(entry.destination().z);
			writeDoorwayPlayers(buf, entry.players());
			writeDoorwayBlocks(buf, entry.blocks());
		}
	}

	private static List<DoorwayEntry> readDoorways(PacketByteBuf buf) {
		int size = Math.max(0, Math.min(64, buf.readVarInt()));
		List<DoorwayEntry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new DoorwayEntry(buf.readBlockPos(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readEnumConstant(Direction.class), buf.readEnumConstant(Direction.class),
				buf.readBlockPos(), buf.readEnumConstant(Direction.class), buf.readEnumConstant(Direction.class),
				new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), readDoorwayPlayers(buf),
				readDoorwayBlocks(buf)));
		}
		return entries;
	}

	private static void writeDoorwayPlayers(PacketByteBuf buf, List<DoorwayPlayerEntry> entries) {
		buf.writeVarInt(entries.size());
		for (DoorwayPlayerEntry entry : entries) {
			buf.writeUuid(entry.playerId());
			buf.writeString(entry.name(), 64);
			buf.writeDouble(entry.x());
			buf.writeDouble(entry.y());
			buf.writeDouble(entry.z());
			buf.writeFloat(entry.yaw());
			buf.writeFloat(entry.pitch());
			buf.writeFloat(entry.bodyYaw());
			buf.writeFloat(entry.headYaw());
			buf.writeBoolean(entry.sneaking());
			buf.writeBoolean(entry.sprinting());
			buf.writeBoolean(entry.invisible());
		}
	}

	private static List<DoorwayPlayerEntry> readDoorwayPlayers(PacketByteBuf buf) {
		int size = Math.max(0, Math.min(32, buf.readVarInt()));
		List<DoorwayPlayerEntry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new DoorwayPlayerEntry(buf.readUuid(), buf.readString(64),
				buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
		}
		return entries;
	}

	private static void writeDoorwayBlocks(PacketByteBuf buf, List<DoorwayBlockEntry> entries) {
		buf.writeVarInt(entries.size());
		for (DoorwayBlockEntry entry : entries) {
			buf.writeByte(entry.dx());
			buf.writeByte(entry.dy());
			buf.writeByte(entry.dz());
			buf.writeVarInt(entry.stateId());
		}
	}

	private static List<DoorwayBlockEntry> readDoorwayBlocks(PacketByteBuf buf) {
		int size = Math.max(0, Math.min(MAX_DOORWAY_BLOCKS, buf.readVarInt()));
		List<DoorwayBlockEntry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new DoorwayBlockEntry(buf.readByte(), buf.readByte(), buf.readByte(), buf.readVarInt()));
		}
		return entries;
	}

	private static List<Entry> normalize(List<Entry> entries) {
		if (entries == null || entries.isEmpty()) return List.of();
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry entry : entries) {
			if (entry != null && entry.targetId() != null && entry.totalTicks() > 0) out.add(entry);
		}
		return List.copyOf(out);
	}

	private static List<DoorwayEntry> normalizeDoorways(List<DoorwayEntry> entries) {
		if (entries == null || entries.isEmpty()) return List.of();
		List<DoorwayEntry> out = new ArrayList<>(entries.size());
		for (DoorwayEntry entry : entries) {
			if (entry != null && entry.lowerPos() != null && entry.facing() != null && entry.through() != null
					&& entry.destinationLowerPos() != null && entry.destinationFacing() != null
					&& entry.destinationThrough() != null && entry.destination() != null
					&& entry.totalTicks() > 0) {
				out.add(entry);
			}
		}
		return List.copyOf(out);
	}

	public record Entry(UUID targetId, int color, int remainingTicks, int totalTicks) {
		public Entry {
			color &= 0xFFFFFF;
			remainingTicks = Math.max(0, remainingTicks);
			totalTicks = Math.max(1, totalTicks);
		}
	}

	public record DoorwayEntry(BlockPos lowerPos, int color, int remainingTicks, int totalTicks,
			Direction facing, Direction through, BlockPos destinationLowerPos,
			Direction destinationFacing, Direction destinationThrough, Vec3d destination,
			List<DoorwayPlayerEntry> players, List<DoorwayBlockEntry> blocks) {
		public DoorwayEntry(BlockPos lowerPos, int color, int remainingTicks, int totalTicks,
				Direction facing, Direction through, BlockPos destinationLowerPos,
				Direction destinationFacing, Direction destinationThrough, Vec3d destination) {
			this(lowerPos, color, remainingTicks, totalTicks, facing, through,
				destinationLowerPos, destinationFacing, destinationThrough, destination, List.of(), List.of());
		}

		public DoorwayEntry(BlockPos lowerPos, int color, int remainingTicks, int totalTicks,
				Direction facing, Direction through, BlockPos destinationLowerPos,
				Direction destinationFacing, Direction destinationThrough, Vec3d destination,
				List<DoorwayPlayerEntry> players) {
			this(lowerPos, color, remainingTicks, totalTicks, facing, through,
				destinationLowerPos, destinationFacing, destinationThrough, destination, players, List.of());
		}

		public DoorwayEntry {
			lowerPos = lowerPos == null ? BlockPos.ORIGIN : lowerPos.toImmutable();
			color &= 0xFFFFFF;
			remainingTicks = Math.max(0, remainingTicks);
			totalTicks = Math.max(1, totalTicks);
			facing = facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
			through = through == null || through.getAxis().isVertical() ? facing : through;
			destinationLowerPos = destinationLowerPos == null ? BlockPos.ORIGIN : destinationLowerPos.toImmutable();
			destinationFacing = destinationFacing == null || destinationFacing.getAxis().isVertical()
				? Direction.NORTH
				: destinationFacing;
			destinationThrough = destinationThrough == null || destinationThrough.getAxis().isVertical()
				? destinationFacing
				: destinationThrough;
			destination = destination == null ? Vec3d.ZERO : destination;
			players = players == null || players.isEmpty()
				? List.of()
				: List.copyOf(players.stream()
					.filter(entry -> entry != null && entry.playerId() != null)
					.limit(32)
					.toList());
			blocks = blocks == null || blocks.isEmpty()
				? List.of()
				: List.copyOf(blocks.stream()
					.filter(entry -> entry != null && entry.stateId() > 0)
					.limit(MAX_DOORWAY_BLOCKS)
					.toList());
		}
	}

	public record DoorwayPlayerEntry(UUID playerId, String name, double x, double y, double z,
			float yaw, float pitch, float bodyYaw, float headYaw,
			boolean sneaking, boolean sprinting, boolean invisible) {
		public DoorwayPlayerEntry {
			name = name == null || name.isBlank() ? "Player" : name.strip();
			if (name.length() > 64) name = name.substring(0, 64);
		}
	}

	public record DoorwayBlockEntry(int dx, int dy, int dz, int stateId) {
		public DoorwayBlockEntry {
			dx = clampByte(dx);
			dy = clampByte(dy);
			dz = clampByte(dz);
			stateId = Math.max(0, stateId);
		}

		public static DoorwayBlockEntry of(BlockPos origin, BlockPos pos, BlockState state) {
			return new DoorwayBlockEntry(pos.getX() - origin.getX(), pos.getY() - origin.getY(),
				pos.getZ() - origin.getZ(), Block.STATE_IDS.getRawId(state));
		}

		public BlockState state() {
			BlockState state = Block.STATE_IDS.get(stateId);
			return state == null ? Blocks.AIR.getDefaultState() : state;
		}

		private static int clampByte(int value) {
			return Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, value));
		}
	}
}
