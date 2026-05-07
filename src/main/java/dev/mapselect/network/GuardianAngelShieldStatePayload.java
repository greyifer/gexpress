package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server -> client active guardian angel shield targets, visible to dead players. */
public record GuardianAngelShieldStatePayload(Map<UUID, Integer> shieldedTargets) implements CustomPayload {
	public static final CustomPayload.Id<GuardianAngelShieldStatePayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "guardian_angel_shield_state"));

	public GuardianAngelShieldStatePayload {
		shieldedTargets = shieldedTargets == null ? Map.of() : Map.copyOf(shieldedTargets);
	}

	public static final PacketCodec<PacketByteBuf, GuardianAngelShieldStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeInt(payload.shieldedTargets().size());
			for (Map.Entry<UUID, Integer> entry : payload.shieldedTargets().entrySet()) {
				buf.writeUuid(entry.getKey());
				buf.writeInt(Math.max(0, entry.getValue()));
			}
		},
		buf -> {
			int size = Math.max(0, buf.readInt());
			Map<UUID, Integer> targets = new LinkedHashMap<>();
			for (int i = 0; i < size; i++) {
				targets.put(buf.readUuid(), Math.max(0, buf.readInt()));
			}
			return new GuardianAngelShieldStatePayload(targets);
		}
	);

	public static GuardianAngelShieldStatePayload clear() {
		return new GuardianAngelShieldStatePayload(Map.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
