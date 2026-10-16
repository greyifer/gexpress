package dev.mapselect.network.role.pelican;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server -> client state for the deliberately intrusive chat cards created by players inside a
 * Pelican. The Pelican receives closable cards; swallowed viewers receive the same cards read-only.
 */
public record PelicanChatPopupPayload(Action action, UUID popupId, UUID pelicanId,
		UUID senderId, String senderName, String message, boolean canClose) implements CustomPayload {
	private static final UUID EMPTY_UUID = new UUID(0L, 0L);
	public static final CustomPayload.Id<PelicanChatPopupPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "pelican_chat_popup"));

	public PelicanChatPopupPayload {
		action = action == null ? Action.CLEAR : action;
		popupId = popupId == null ? EMPTY_UUID : popupId;
		pelicanId = pelicanId == null ? EMPTY_UUID : pelicanId;
		senderId = senderId == null ? EMPTY_UUID : senderId;
		senderName = senderName == null ? "" : senderName;
		message = message == null ? "" : message;
	}

	public static PelicanChatPopupPayload add(UUID popupId, UUID pelicanId, UUID senderId, String senderName,
			String message, boolean canClose) {
		return new PelicanChatPopupPayload(Action.ADD, popupId, pelicanId, senderId,
			senderName, message, canClose);
	}

	public static PelicanChatPopupPayload remove(UUID popupId, UUID pelicanId) {
		return new PelicanChatPopupPayload(Action.REMOVE, popupId, pelicanId, EMPTY_UUID,
			"", "", false);
	}

	public static PelicanChatPopupPayload clear(UUID pelicanId) {
		return new PelicanChatPopupPayload(Action.CLEAR, EMPTY_UUID, pelicanId, EMPTY_UUID,
			"", "", false);
	}

	public static final PacketCodec<PacketByteBuf, PelicanChatPopupPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeByte(payload.action().ordinal());
			buf.writeUuid(payload.popupId());
			buf.writeUuid(payload.pelicanId());
			buf.writeUuid(payload.senderId());
			buf.writeString(payload.senderName(), 64);
			buf.writeString(payload.message(), 256);
			buf.writeBoolean(payload.canClose());
		},
		buf -> {
			int actionIndex = Math.max(0, Math.min(Action.values().length - 1, buf.readUnsignedByte()));
			return new PelicanChatPopupPayload(
				Action.values()[actionIndex],
				buf.readUuid(),
				buf.readUuid(),
				buf.readUuid(),
				buf.readString(64),
				buf.readString(256),
				buf.readBoolean()
			);
		}
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Action {
		ADD,
		REMOVE,
		CLEAR
	}
}
