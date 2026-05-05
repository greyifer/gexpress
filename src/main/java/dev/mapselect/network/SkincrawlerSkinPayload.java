package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record SkincrawlerSkinPayload(Map<UUID, UUID> swaps) implements CustomPayload {
	public static final Id<SkincrawlerSkinPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "skincrawler_skin"));

	public SkincrawlerSkinPayload {
		swaps = swaps == null ? Map.of() : Map.copyOf(swaps);
	}

	public static final PacketCodec<PacketByteBuf, SkincrawlerSkinPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.swaps().size());
			for (Map.Entry<UUID, UUID> entry : payload.swaps().entrySet()) {
				buf.writeUuid(entry.getKey());
				buf.writeUuid(entry.getValue());
			}
		},
		buf -> {
			int size = Math.max(0, buf.readInt());
			Map<UUID, UUID> swaps = new LinkedHashMap<>();
			for (int i = 0; i < size; i++) {
				swaps.put(buf.readUuid(), buf.readUuid());
			}
			return new SkincrawlerSkinPayload(swaps);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
