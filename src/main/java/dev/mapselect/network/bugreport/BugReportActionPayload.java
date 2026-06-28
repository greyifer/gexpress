package dev.mapselect.network.bugreport;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BugReportActionPayload(String id, String action, String statusFilter) implements CustomPayload {
	public static final CustomPayload.Id<BugReportActionPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "bug_report_action"));

	public BugReportActionPayload {
		id = cleanId(id, 48);
		action = clean(action, 32);
		statusFilter = clean(statusFilter, 32);
	}

	public static final PacketCodec<PacketByteBuf, BugReportActionPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.id(), 48);
			buf.writeString(payload.action(), 32);
			buf.writeString(payload.statusFilter(), 32);
		},
		buf -> new BugReportActionPayload(buf.readString(48), buf.readString(32), buf.readString(32))
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static String clean(String raw, int max) {
		String value = raw == null ? "" : raw.trim().toLowerCase();
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String cleanId(String raw, int max) {
		String value = raw == null ? "" : raw.trim();
		return value.length() <= max ? value : value.substring(0, max);
	}
}
