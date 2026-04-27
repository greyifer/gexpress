package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityCooldownPayload(String key, int remainingTicks, int totalTicks, boolean draining)
		implements CustomPayload {
	public static final String PUPPETMASTER_CONTROL = "puppetmaster_control";
	public static final String PELICAN_SWALLOW = "pelican_swallow";

	public static final CustomPayload.Id<AbilityCooldownPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "ability_cooldown"));

	public AbilityCooldownPayload {
		key = key == null ? "" : key;
		remainingTicks = Math.max(0, remainingTicks);
		totalTicks = Math.max(0, totalTicks);
	}

	public static final PacketCodec<PacketByteBuf, AbilityCooldownPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.key(), 64);
			buf.writeInt(payload.remainingTicks());
			buf.writeInt(payload.totalTicks());
			buf.writeBoolean(payload.draining());
		},
		buf -> new AbilityCooldownPayload(buf.readString(64), buf.readInt(), buf.readInt(), buf.readBoolean())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
