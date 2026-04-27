package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Server -> client snapshot of the controlled target's hotbar. */
public record PuppetmasterHotbarPayload(List<ItemStack> hotbar, int selectedSlot) implements CustomPayload {
	public static final CustomPayload.Id<PuppetmasterHotbarPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "puppetmaster_hotbar"));

	public static final PacketCodec<RegistryByteBuf, PuppetmasterHotbarPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			ItemStack.OPTIONAL_LIST_PACKET_CODEC.encode(buf, payload.hotbar());
			buf.writeInt(payload.selectedSlot());
		},
		buf -> new PuppetmasterHotbarPayload(
			ItemStack.OPTIONAL_LIST_PACKET_CODEC.decode(buf),
			buf.readInt()
		)
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
