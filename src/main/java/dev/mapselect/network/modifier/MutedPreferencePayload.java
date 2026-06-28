package dev.mapselect.network.modifier;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MutedPreferencePayload(boolean enabled) implements CustomPayload {
	public static final CustomPayload.Id<MutedPreferencePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "muted_preference"));
	public static final PacketCodec<PacketByteBuf, MutedPreferencePayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, MutedPreferencePayload::enabled,
		MutedPreferencePayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
