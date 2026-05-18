package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Small sync packet for dev-owned systems that change often while testing. */
public record GexpressDevTuningPayload(
		int levelRoundXp,
		int levelWinXp,
		int levelNeutralWinBonusXp,
		int levelKillXp,
		int levelCivilianTaskXp,
		int levelBaseXp,
		int levelXpIncrease,
		int levelRoadmapDisplayLevels,
		String levelXpOverrides,
		String levelRewardRoadmap,
		String grenadeLineOfSightPassThroughBlocks) implements CustomPayload {
	public static final CustomPayload.Id<GexpressDevTuningPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "dev_tuning"));

	public GexpressDevTuningPayload {
		levelXpOverrides = levelXpOverrides == null ? "" : levelXpOverrides;
		levelRewardRoadmap = levelRewardRoadmap == null ? "" : levelRewardRoadmap;
		grenadeLineOfSightPassThroughBlocks = grenadeLineOfSightPassThroughBlocks == null
			? "" : grenadeLineOfSightPassThroughBlocks;
	}

	public static final PacketCodec<PacketByteBuf, GexpressDevTuningPayload> CODEC = PacketCodec.of(
		GexpressDevTuningPayload::encode,
		GexpressDevTuningPayload::decode
	);

	private static void encode(GexpressDevTuningPayload payload, PacketByteBuf buf) {
		buf.writeInt(payload.levelRoundXp());
		buf.writeInt(payload.levelWinXp());
		buf.writeInt(payload.levelNeutralWinBonusXp());
		buf.writeInt(payload.levelKillXp());
		buf.writeInt(payload.levelCivilianTaskXp());
		buf.writeInt(payload.levelBaseXp());
		buf.writeInt(payload.levelXpIncrease());
		buf.writeInt(payload.levelRoadmapDisplayLevels());
		buf.writeString(payload.levelXpOverrides());
		buf.writeString(payload.levelRewardRoadmap());
		buf.writeString(payload.grenadeLineOfSightPassThroughBlocks());
	}

	private static GexpressDevTuningPayload decode(PacketByteBuf buf) {
		return new GexpressDevTuningPayload(
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readString(),
			buf.readString(),
			buf.readString()
		);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
