package dev.mapselect.network.role.twins;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record TwinsFeedPayload(boolean active, int width, int height, int[] colors,
                               List<PlayerMarker> players, String label) implements CustomPayload {
	public static final Id<TwinsFeedPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "twins_feed"));

	public TwinsFeedPayload {
		width = Math.max(0, Math.min(64, width));
		height = Math.max(0, Math.min(64, height));
		int expected = width * height;
		if (colors == null) colors = new int[0];
		if (colors.length != expected) {
			int[] resized = new int[expected];
			System.arraycopy(colors, 0, resized, 0, Math.min(colors.length, resized.length));
			colors = resized;
		}
		players = players == null ? List.of() : List.copyOf(players);
		label = label == null ? "" : label;
	}

	public static final PacketCodec<PacketByteBuf, TwinsFeedPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeVarInt(payload.width());
			buf.writeVarInt(payload.height());
			buf.writeVarInt(payload.colors().length);
			for (int color : payload.colors()) buf.writeInt(color);
			buf.writeVarInt(Math.min(24, payload.players().size()));
			for (PlayerMarker marker : payload.players().subList(0, Math.min(24, payload.players().size()))) {
				buf.writeString(marker.name(), 32);
				buf.writeByte(marker.x());
				buf.writeByte(marker.z());
				buf.writeInt(marker.color());
			}
			buf.writeString(payload.label(), 64);
		},
		buf -> {
			boolean active = buf.readBoolean();
			int width = Math.max(0, Math.min(64, buf.readVarInt()));
			int height = Math.max(0, Math.min(64, buf.readVarInt()));
			int count = Math.max(0, Math.min(width * height, buf.readVarInt()));
			int[] colors = new int[width * height];
			for (int i = 0; i < count; i++) colors[i] = buf.readInt();
			int playerCount = Math.max(0, Math.min(24, buf.readVarInt()));
			List<PlayerMarker> markers = new ArrayList<>(playerCount);
			for (int i = 0; i < playerCount; i++) {
				markers.add(new PlayerMarker(buf.readString(32), buf.readByte(), buf.readByte(), buf.readInt()));
			}
			return new TwinsFeedPayload(active, width, height, colors, markers, buf.readString(64));
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record PlayerMarker(String name, int x, int z, int color) {
		public PlayerMarker {
			name = name == null ? "" : name;
			x = Math.max(-128, Math.min(127, x));
			z = Math.max(-128, Math.min(127, z));
		}
	}
}
