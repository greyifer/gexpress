package dev.mapselect.network.role.doomsayer;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DoomsayerStatePayload(boolean active, int correctGuesses, int requiredGuesses,
		List<Target> targets) implements CustomPayload {
	private static final int MAX_TARGETS = 128;
	public static final Id<DoomsayerStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "doomsayer_state"));

	public DoomsayerStatePayload {
		requiredGuesses = Math.max(1, requiredGuesses);
		correctGuesses = Math.max(0, Math.min(requiredGuesses, correctGuesses));
		targets = targets == null ? List.of() : List.copyOf(targets.stream()
			.filter(Objects::nonNull).limit(MAX_TARGETS).toList());
	}

	public static DoomsayerStatePayload clear() {
		return new DoomsayerStatePayload(false, 0, 1, List.of());
	}

	public static final PacketCodec<PacketByteBuf, DoomsayerStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeBoolean(payload.active());
			buf.writeVarInt(payload.correctGuesses());
			buf.writeVarInt(payload.requiredGuesses());
			buf.writeVarInt(payload.targets().size());
			for (Target target : payload.targets()) {
				buf.writeUuid(target.playerId());
				buf.writeString(target.name(), 64);
			}
		},
		buf -> {
			boolean active = buf.readBoolean();
			int correct = buf.readVarInt();
			int required = buf.readVarInt();
			int count = Math.max(0, Math.min(MAX_TARGETS, buf.readVarInt()));
			List<Target> targets = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				targets.add(new Target(buf.readUuid(), buf.readString(64)));
			}
			return new DoomsayerStatePayload(active, correct, required, targets);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Target(UUID playerId, String name) {
		public Target {
			name = name == null ? "" : name;
		}
	}
}
