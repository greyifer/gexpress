package dev.mapselect.network.bugreport;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BugReportSubmitPayload(String category, String message) implements CustomPayload {
	public static final CustomPayload.Id<BugReportSubmitPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "bug_report_submit"));

	public BugReportSubmitPayload {
		category = clean(category, 64);
		message = clean(message, 4000);
	}

	public static final PacketCodec<PacketByteBuf, BugReportSubmitPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.category(), 64);
			buf.writeString(payload.message(), 4000);
		},
		buf -> new BugReportSubmitPayload(buf.readString(64), buf.readString(4000))
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static String clean(String raw, int max) {
		String value = raw == null ? "" : raw.trim();
		return value.length() <= max ? value : value.substring(0, max);
	}
}
