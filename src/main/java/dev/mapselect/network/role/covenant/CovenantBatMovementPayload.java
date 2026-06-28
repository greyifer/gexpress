package dev.mapselect.network.role.covenant;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public record CovenantBatMovementPayload(float sideways, float forward, boolean ascending,
		boolean descending, boolean sprinting) implements CustomPayload {
	public static final CustomPayload.Id<CovenantBatMovementPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "covenant_bat_movement"));

	public CovenantBatMovementPayload {
		sideways = Float.isFinite(sideways) ? MathHelper.clamp(sideways, -1.0F, 1.0F) : 0.0F;
		forward = Float.isFinite(forward) ? MathHelper.clamp(forward, -1.0F, 1.0F) : 0.0F;
	}

	public static final PacketCodec<PacketByteBuf, CovenantBatMovementPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeFloat(payload.sideways());
			buf.writeFloat(payload.forward());
			buf.writeBoolean(payload.ascending());
			buf.writeBoolean(payload.descending());
			buf.writeBoolean(payload.sprinting());
		},
		buf -> new CovenantBatMovementPayload(buf.readFloat(), buf.readFloat(), buf.readBoolean(),
			buf.readBoolean(), buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
