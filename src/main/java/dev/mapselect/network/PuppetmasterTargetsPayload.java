package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client list of alive players the Puppetmaster may control. */
public record PuppetmasterTargetsPayload(List<Entry> targets) implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterTargetsPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_targets"));

	public PuppetmasterTargetsPayload {
		targets = targets == null ? List.of() : List.copyOf(targets);
	}

	public static final PacketCodec<PacketByteBuf, PuppetmasterTargetsPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.targets().size());
			for (Entry entry : payload.targets()) {
				buf.writeUuid(entry.id());
				buf.writeString(entry.name(), 64);
			}
		},
		buf -> {
			int size = Math.max(0, buf.readInt());
			List<Entry> entries = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				entries.add(new Entry(buf.readUuid(), buf.readString(64)));
			}
			return new PuppetmasterTargetsPayload(entries);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Entry(UUID id, String name) {}
}
