package dev.mapselect.network.bugreport;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BugReportListPayload(String status, String statusMessage, boolean canModerate,
		List<Entry> reports) implements CustomPayload {
	public static final CustomPayload.Id<BugReportListPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "bug_report_list"));

	public BugReportListPayload {
		status = clean(status, 32);
		statusMessage = clean(statusMessage, 256);
		reports = reports == null ? List.of() : List.copyOf(reports);
	}

	public static final PacketCodec<PacketByteBuf, BugReportListPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.status(), 32);
			buf.writeString(payload.statusMessage(), 256);
			buf.writeBoolean(payload.canModerate());
			buf.writeInt(payload.reports().size());
			for (Entry report : payload.reports()) {
				buf.writeString(report.id(), 48);
				buf.writeString(report.status(), 32);
				buf.writeString(report.category(), 64);
				buf.writeString(report.title(), 160);
				buf.writeString(report.description(), 600);
				buf.writeString(report.reporterName(), 64);
				buf.writeString(report.serverName(), 64);
				buf.writeString(report.modVersion(), 64);
				buf.writeString(report.minecraftVersion(), 32);
				buf.writeString(report.createdAt(), 64);
				buf.writeString(report.updatedAt(), 64);
				buf.writeString(report.discordUrl(), 256);
			}
		},
		buf -> {
			String status = buf.readString(32);
			String statusMessage = buf.readString(256);
			boolean canModerate = buf.readBoolean();
			int size = Math.max(0, buf.readInt());
			List<Entry> reports = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				reports.add(new Entry(
					buf.readString(48),
					buf.readString(32),
					buf.readString(64),
					buf.readString(160),
					buf.readString(600),
					buf.readString(64),
					buf.readString(64),
					buf.readString(64),
					buf.readString(32),
					buf.readString(64),
					buf.readString(64),
					buf.readString(256)
				));
			}
			return new BugReportListPayload(status, statusMessage, canModerate, reports);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static String clean(String raw, int max) {
		String value = raw == null ? "" : raw.trim();
		return value.length() <= max ? value : value.substring(0, max);
	}

	public record Entry(
		String id,
		String status,
		String category,
		String title,
		String description,
		String reporterName,
		String serverName,
		String modVersion,
		String minecraftVersion,
		String createdAt,
		String updatedAt,
		String discordUrl
	) {
		public Entry {
			id = clean(id, 48);
			status = clean(status, 32);
			category = clean(category, 64);
			title = clean(title, 160);
			description = clean(description, 600);
			reporterName = clean(reporterName, 64);
			serverName = clean(serverName, 64);
			modVersion = clean(modVersion, 64);
			minecraftVersion = clean(minecraftVersion, 32);
			createdAt = clean(createdAt, 64);
			updatedAt = clean(updatedAt, 64);
			discordUrl = clean(discordUrl, 256);
		}
	}
}
