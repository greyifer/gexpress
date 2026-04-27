package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server movement/look input while a Puppetmaster controls a target. */
public record PuppetmasterInputPayload(float sideways, float forward, boolean jumping, boolean sneaking,
		boolean sprinting, boolean using, float yaw, float pitch, int selectedSlot) implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterInputPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_input"));

	public static final PacketCodec<PacketByteBuf, PuppetmasterInputPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeFloat(payload.sideways());
			buf.writeFloat(payload.forward());
			buf.writeBoolean(payload.jumping());
			buf.writeBoolean(payload.sneaking());
			buf.writeBoolean(payload.sprinting());
			buf.writeBoolean(payload.using());
			buf.writeFloat(payload.yaw());
			buf.writeFloat(payload.pitch());
			buf.writeInt(payload.selectedSlot());
		},
		buf -> new PuppetmasterInputPayload(
			buf.readFloat(),
			buf.readFloat(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readInt()
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
