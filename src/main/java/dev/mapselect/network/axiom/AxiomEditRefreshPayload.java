package dev.mapselect.network.axiom;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AxiomEditRefreshPayload() implements CustomPayload {
	public static final Id<AxiomEditRefreshPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "axiom_edit_refresh"));
	public static final PacketCodec<PacketByteBuf, AxiomEditRefreshPayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new AxiomEditRefreshPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
