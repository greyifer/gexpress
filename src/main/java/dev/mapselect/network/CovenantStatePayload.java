package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CovenantStatePayload(boolean active, boolean dracula, int bloodTicks, int maxBloodTicks,
		int batTicks, int maxBatTicks, boolean batForm) implements CustomPayload {
	public static final CustomPayload.Id<CovenantStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_state"));

	public CovenantStatePayload {
		bloodTicks = Math.max(0, bloodTicks);
		maxBloodTicks = Math.max(1, maxBloodTicks);
		batTicks = Math.max(0, batTicks);
		maxBatTicks = Math.max(1, maxBatTicks);
	}

	public static CovenantStatePayload clear() {
		return new CovenantStatePayload(false, false, 0, 1, 0, 1, false);
	}

	public static final PacketCodec<PacketByteBuf, CovenantStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.dracula());
			buf.writeInt(payload.bloodTicks());
			buf.writeInt(payload.maxBloodTicks());
			buf.writeInt(payload.batTicks());
			buf.writeInt(payload.maxBatTicks());
			buf.writeBoolean(payload.batForm());
		},
		buf -> new CovenantStatePayload(buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(),
			buf.readInt(), buf.readInt(), buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
