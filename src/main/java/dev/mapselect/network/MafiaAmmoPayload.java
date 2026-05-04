package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record MafiaAmmoPayload(int loadedBullets, int maxBullets) implements CustomPayload {
	public static final Id<MafiaAmmoPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "mafia_ammo"));

	public MafiaAmmoPayload {
		loadedBullets = Math.max(0, loadedBullets);
		maxBullets = Math.max(1, maxBullets);
	}

	public static final PacketCodec<PacketByteBuf, MafiaAmmoPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.loadedBullets());
			buf.writeInt(payload.maxBullets());
		},
		buf -> new MafiaAmmoPayload(buf.readInt(), buf.readInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
