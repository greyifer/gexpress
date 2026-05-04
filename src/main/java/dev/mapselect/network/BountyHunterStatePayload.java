package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record BountyHunterStatePayload(boolean active, UUID targetId, String targetName, int rewardGold,
		long remainingTicks) implements CustomPayload {
	public static final CustomPayload.Id<BountyHunterStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "bounty_hunter_state"));

	public BountyHunterStatePayload {
		targetName = targetName == null ? "" : targetName;
		remainingTicks = Math.max(0L, remainingTicks);
	}

	public static final PacketCodec<PacketByteBuf, BountyHunterStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeBoolean(payload.targetId() != null);
			if (payload.targetId() != null) buf.writeUuid(payload.targetId());
			buf.writeString(payload.targetName());
			buf.writeInt(payload.rewardGold());
			buf.writeLong(payload.remainingTicks());
		},
		buf -> {
			boolean active = buf.readBoolean();
			UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
			return new BountyHunterStatePayload(active, targetId, buf.readString(64), buf.readInt(), buf.readLong());
		}
	);

	public static BountyHunterStatePayload clear() {
		return new BountyHunterStatePayload(false, null, "", 0, 0L);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
