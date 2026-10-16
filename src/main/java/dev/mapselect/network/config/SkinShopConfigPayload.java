package dev.mapselect.network.config;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Live server-to-client skin-store configuration. */
public record SkinShopConfigPayload(String json) implements CustomPayload {
	private static final int MAX_JSON_LENGTH = 262_144;
	public static final CustomPayload.Id<SkinShopConfigPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "skin_shop_config"));
	public static final PacketCodec<PacketByteBuf, SkinShopConfigPayload> CODEC = PacketCodec.of(
		(payload, buf) -> buf.writeString(payload.json(), MAX_JSON_LENGTH),
		buf -> new SkinShopConfigPayload(buf.readString(MAX_JSON_LENGTH))
	);

	public SkinShopConfigPayload {
		json = json == null ? "{}" : json;
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
