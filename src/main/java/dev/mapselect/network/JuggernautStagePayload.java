package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record JuggernautStagePayload(boolean active, int stage, int maxStage, int cooldownReductionSeconds,
		boolean knifeShield, boolean gunShield) implements CustomPayload {
	public static final CustomPayload.Id<JuggernautStagePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "juggernaut_stage"));

	public JuggernautStagePayload {
		stage = Math.max(0, stage);
		maxStage = Math.max(1, maxStage);
		cooldownReductionSeconds = Math.max(0, cooldownReductionSeconds);
	}

	public static final PacketCodec<PacketByteBuf, JuggernautStagePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeInt(payload.stage());
			buf.writeInt(payload.maxStage());
			buf.writeInt(payload.cooldownReductionSeconds());
			buf.writeBoolean(payload.knifeShield());
			buf.writeBoolean(payload.gunShield());
		},
		buf -> new JuggernautStagePayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
			buf.readBoolean(), buf.readBoolean())
	);

	public static JuggernautStagePayload clear() {
		return new JuggernautStagePayload(false, 0, 5, 0, false, false);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
