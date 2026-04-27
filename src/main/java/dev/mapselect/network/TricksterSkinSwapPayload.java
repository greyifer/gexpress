package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server -> client skin replacement map for the Trickster's masquerade. */
public record TricksterSkinSwapPayload(Map<UUID, UUID> swaps, int durationTicks) implements CustomPayload {
	public static final CustomPayload.Id<TricksterSkinSwapPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "trickster_skin_swap"));

	public TricksterSkinSwapPayload {
		swaps = swaps == null ? Map.of() : Map.copyOf(swaps);
		durationTicks = Math.max(0, durationTicks);
	}

	public static final PacketCodec<PacketByteBuf, TricksterSkinSwapPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.durationTicks());
			buf.writeInt(payload.swaps().size());
			for (Map.Entry<UUID, UUID> entry : payload.swaps().entrySet()) {
				buf.writeUuid(entry.getKey());
				buf.writeUuid(entry.getValue());
			}
		},
		buf -> {
			int durationTicks = buf.readInt();
			int size = Math.max(0, buf.readInt());
			Map<UUID, UUID> swaps = new LinkedHashMap<>();
			for (int i = 0; i < size; i++) {
				swaps.put(buf.readUuid(), buf.readUuid());
			}
			return new TricksterSkinSwapPayload(swaps, durationTicks);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
