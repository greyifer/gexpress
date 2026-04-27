package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client, per-player: "you currently have the Short-sighted modifier". One bit per
 * packet. Sent whenever the server-side modifier state for this player changes.
 *
 * <h2>Why this exists instead of the client reading HML directly</h2>
 * The Short-sighted render mixin would otherwise call
 * {@code WorldModifierComponent.KEY.getNullable(world).isModifier(...)} from inside the
 * client-side render path. That ties the client mixin's class load to HML's presence on
 * the client JVM, which is not guaranteed. Keeping HML access strictly server-side and
 * sending a one-bit boolean keeps the render path clean.
 */
public record ShortSightedSyncPayload(boolean shortSighted) implements CustomPayload {

	public static final CustomPayload.Id<ShortSightedSyncPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "short_sighted_sync"));

	public static final PacketCodec<PacketByteBuf, ShortSightedSyncPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, ShortSightedSyncPayload::shortSighted,
		ShortSightedSyncPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
