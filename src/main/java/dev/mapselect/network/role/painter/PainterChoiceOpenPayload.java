package dev.mapselect.network.role.painter;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PainterChoiceOpenPayload(Mode mode, UUID targetId, String targetName,
		List<DeathChoice> deathChoices, List<RoleChoice> roleChoices) implements CustomPayload {
	public static final Id<PainterChoiceOpenPayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "painter_choice_open"));

	public PainterChoiceOpenPayload {
		mode = mode == null ? Mode.PLAYER : mode;
		targetName = targetName == null ? "" : targetName;
		deathChoices = normalizeDeathChoices(deathChoices);
		roleChoices = normalizeRoleChoices(roleChoices);
	}

	public static final PacketCodec<PacketByteBuf, PainterChoiceOpenPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeEnumConstant(payload.mode());
			buf.writeUuid(payload.targetId());
			buf.writeString(payload.targetName(), 64);
			buf.writeVarInt(payload.deathChoices().size());
			for (DeathChoice choice : payload.deathChoices()) {
				buf.writeIdentifier(choice.deathReasonId());
				buf.writeString(choice.displayName(), 64);
				buf.writeIdentifier(choice.itemId());
			}
			buf.writeVarInt(payload.roleChoices().size());
			for (RoleChoice choice : payload.roleChoices()) {
				buf.writeIdentifier(choice.roleId());
				buf.writeString(choice.displayName(), 64);
				buf.writeInt(choice.color());
			}
		},
		buf -> {
			Mode mode = buf.readEnumConstant(Mode.class);
			UUID targetId = buf.readUuid();
			String targetName = buf.readString(64);
			int deathCount = Math.max(0, Math.min(64, buf.readVarInt()));
			List<DeathChoice> deathChoices = new ArrayList<>(deathCount);
			for (int i = 0; i < deathCount; i++) {
				deathChoices.add(new DeathChoice(buf.readIdentifier(), buf.readString(64), buf.readIdentifier()));
			}
			int roleCount = Math.max(0, Math.min(256, buf.readVarInt()));
			List<RoleChoice> roleChoices = new ArrayList<>(roleCount);
			for (int i = 0; i < roleCount; i++) {
				roleChoices.add(new RoleChoice(buf.readIdentifier(), buf.readString(64), buf.readInt()));
			}
			return new PainterChoiceOpenPayload(mode, targetId, targetName, deathChoices, roleChoices);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	private static List<DeathChoice> normalizeDeathChoices(List<DeathChoice> choices) {
		if (choices == null || choices.isEmpty()) return List.of();
		List<DeathChoice> out = new ArrayList<>(choices.size());
		for (DeathChoice choice : choices) {
			if (choice != null && choice.deathReasonId() != null && choice.itemId() != null) out.add(choice);
		}
		return List.copyOf(out);
	}

	private static List<RoleChoice> normalizeRoleChoices(List<RoleChoice> choices) {
		if (choices == null || choices.isEmpty()) return List.of();
		List<RoleChoice> out = new ArrayList<>(choices.size());
		for (RoleChoice choice : choices) {
			if (choice != null && choice.roleId() != null) out.add(choice);
		}
		return List.copyOf(out);
	}

	public enum Mode {
		BODY,
		PLAYER
	}

	public record DeathChoice(Identifier deathReasonId, String displayName, Identifier itemId) {
		public DeathChoice {
			displayName = displayName == null ? "" : displayName;
		}
	}

	public record RoleChoice(Identifier roleId, String displayName, int color) {
		public RoleChoice {
			displayName = displayName == null ? "" : displayName;
			color &= 0xFFFFFF;
		}
	}
}
