package dev.mapselect.network.modifier;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record LoversStatePayload(boolean active, UUID loverId, String loverName) implements CustomPayload {
	public static final CustomPayload.Id<LoversStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "lovers_state"));

	public LoversStatePayload {
		loverName = loverName == null ? "" : loverName;
	}

	public static LoversStatePayload clear() {
		return new LoversStatePayload(false, null, "");
	}

	public static final PacketCodec<PacketByteBuf, LoversStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.loverId() != null);
			if (payload.loverId() != null) buf.writeUuid(payload.loverId());
			buf.writeString(payload.loverName(), 64);
		},
		buf -> {
			boolean active = buf.readBoolean();
			UUID loverId = buf.readBoolean() ? buf.readUuid() : null;
			return new LoversStatePayload(active, loverId, buf.readString(64));
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
