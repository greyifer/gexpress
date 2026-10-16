package dev.mapselect.network.role.pelican;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Client -> server request from the owning Pelican to close one belly-chat popup. */
public record PelicanChatPopupDismissPayload(UUID popupId) implements CustomPayload {
	public static final CustomPayload.Id<PelicanChatPopupDismissPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "pelican_chat_popup_dismiss"));

	public static final PacketCodec<PacketByteBuf, PelicanChatPopupDismissPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeUuid(payload.popupId()),
		buf -> new PelicanChatPopupDismissPayload(buf.readUuid())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
