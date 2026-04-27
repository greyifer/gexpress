package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bidirectional snapshot of live G'Express settings. */
public record GexpressConfigSyncPayload(int c4Price, int c4FuseSeconds, int c4FirstBeepSeconds, int wrongWirePercent,
		int grenadePrice, int medicShieldCooldownSeconds, boolean medicShieldKnifeBreaks,
		int silentShadowDurationSeconds, int silentShadowCooldownSeconds,
		int warlockMarkCooldownSeconds, int warlockKillCooldownSeconds,
		int juggernautInitialCooldownSeconds, int juggernautCooldownReductionSeconds,
		int juggernautMinimumCooldownSeconds, int tricksterSwapDurationSeconds,
		int puppetmasterControlDurationSeconds, int puppetmasterControlCooldownSeconds,
		boolean puppetmasterRandomTarget, int pelicanEatCooldownSeconds, int snitchTasksRequired,
		int timeMasterRewindSeconds, int timeMasterCooldownSeconds, int timeMasterMaxUses, int maxKillerAmount,
		float c4BackOffsetX, float c4BackOffsetY, float c4BackOffsetZ,
		float c4BackRotationX, float c4BackRotationY, float c4BackRotationZ,
		float c4BackSlant, float c4BackScale, String c4PlacementPresets,
		String roleDescriptionOverrides,
		float shortSightedFogRange,
		int medicShieldBlockFlashTicks, int medicShieldBreakFlashTicks,
		int medicShieldBlockFlashAlpha, int medicShieldBreakFlashAlpha,
		float silentShadowAlpha) implements CustomPayload {

	public static final CustomPayload.Id<GexpressConfigSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "config_sync"));

	public static final PacketCodec<PacketByteBuf, GexpressConfigSyncPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.c4Price());
			buf.writeInt(payload.c4FuseSeconds());
			buf.writeInt(payload.c4FirstBeepSeconds());
			buf.writeInt(payload.wrongWirePercent());
			buf.writeInt(payload.grenadePrice());
			buf.writeInt(payload.medicShieldCooldownSeconds());
			buf.writeBoolean(payload.medicShieldKnifeBreaks());
			buf.writeInt(payload.silentShadowDurationSeconds());
			buf.writeInt(payload.silentShadowCooldownSeconds());
			buf.writeInt(payload.warlockMarkCooldownSeconds());
			buf.writeInt(payload.warlockKillCooldownSeconds());
			buf.writeInt(payload.juggernautInitialCooldownSeconds());
			buf.writeInt(payload.juggernautCooldownReductionSeconds());
			buf.writeInt(payload.juggernautMinimumCooldownSeconds());
			buf.writeInt(payload.tricksterSwapDurationSeconds());
			buf.writeInt(payload.puppetmasterControlDurationSeconds());
			buf.writeInt(payload.puppetmasterControlCooldownSeconds());
			buf.writeBoolean(payload.puppetmasterRandomTarget());
			buf.writeInt(payload.pelicanEatCooldownSeconds());
			buf.writeInt(payload.snitchTasksRequired());
			buf.writeInt(payload.timeMasterRewindSeconds());
			buf.writeInt(payload.timeMasterCooldownSeconds());
			buf.writeInt(payload.timeMasterMaxUses());
			buf.writeInt(payload.maxKillerAmount());
			buf.writeFloat(payload.c4BackOffsetX());
			buf.writeFloat(payload.c4BackOffsetY());
			buf.writeFloat(payload.c4BackOffsetZ());
			buf.writeFloat(payload.c4BackRotationX());
			buf.writeFloat(payload.c4BackRotationY());
			buf.writeFloat(payload.c4BackRotationZ());
			buf.writeFloat(payload.c4BackSlant());
			buf.writeFloat(payload.c4BackScale());
			buf.writeString(payload.c4PlacementPresets());
			buf.writeString(payload.roleDescriptionOverrides());
			buf.writeFloat(payload.shortSightedFogRange());
			buf.writeInt(payload.medicShieldBlockFlashTicks());
			buf.writeInt(payload.medicShieldBreakFlashTicks());
			buf.writeInt(payload.medicShieldBlockFlashAlpha());
			buf.writeInt(payload.medicShieldBreakFlashAlpha());
			buf.writeFloat(payload.silentShadowAlpha());
		},
		buf -> new GexpressConfigSyncPayload(
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readBoolean(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readBoolean(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readString(),
			buf.readString(),
			buf.readFloat(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readInt(),
			buf.readFloat()
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
