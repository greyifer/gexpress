package dev.mapselect.network.bugreport;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BugReportListRequestPayload(String status) implements CustomPayload {
	public static final CustomPayload.Id<BugReportListRequestPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "bug_report_list_request"));

	public BugReportListRequestPayload {
		status = clean(status, 32);
	}

	public static final PacketCodec<PacketByteBuf, BugReportListRequestPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeString(payload.status(), 32),
		buf -> new BugReportListRequestPayload(buf.readString(32))
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static String clean(String raw, int max) {
		String value = raw == null ? "open" : raw.trim().toLowerCase();
		if (value.isBlank()) value = "open";
		return value.length() <= max ? value : value.substring(0, max);
	}
}
