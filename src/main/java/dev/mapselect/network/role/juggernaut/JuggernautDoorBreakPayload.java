package dev.mapselect.network.role.juggernaut;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record JuggernautDoorBreakPayload() implements CustomPayload {
	public static final Id<JuggernautDoorBreakPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "juggernaut_door_break"));
	public static final PacketCodec<PacketByteBuf, JuggernautDoorBreakPayload> CODEC =
		PacketCodec.of((payload, buf) -> {}, buf -> new JuggernautDoorBreakPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
