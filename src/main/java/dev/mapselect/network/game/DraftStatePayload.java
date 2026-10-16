package dev.mapselect.network.game;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record DraftStatePayload(Phase phase, int playerCount, int selfPosition, int activePosition,
		int remainingTicks, List<RoleChoice> choices, List<PickResult> picks) implements CustomPayload {
	public static final Id<DraftStatePayload> ID =
		new Id<>(Identifier.of(MapSelect.MOD_ID, "draft_state"));

	public DraftStatePayload {
		phase = phase == null ? Phase.CLOSED : phase;
		playerCount = Math.max(0, playerCount);
		selfPosition = Math.max(-1, Math.min(playerCount - 1, selfPosition));
		activePosition = Math.max(-1, Math.min(playerCount - 1, activePosition));
		remainingTicks = Math.max(0, remainingTicks);
		if (choices == null || choices.isEmpty()) {
			choices = List.of();
		} else {
			List<RoleChoice> normalized = new ArrayList<>(Math.min(3, choices.size()));
			for (RoleChoice choice : choices) {
				if (choice != null && choice.roleId() != null) normalized.add(choice);
				if (normalized.size() == 3) break;
			}
			choices = List.copyOf(normalized);
		}
		if (picks == null || picks.isEmpty()) {
			picks = List.of();
		} else {
			List<PickResult> normalized = new ArrayList<>(Math.min(playerCount, picks.size()));
			boolean[] occupied = new boolean[playerCount];
			for (PickResult pick : picks) {
				if (pick == null || pick.position() < 0 || pick.position() >= playerCount
						|| occupied[pick.position()]) continue;
				occupied[pick.position()] = true;
				normalized.add(pick);
			}
			picks = List.copyOf(normalized);
		}
	}

	public static final PacketCodec<PacketByteBuf, DraftStatePayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeEnumConstant(payload.phase());
			buf.writeVarInt(payload.playerCount());
			buf.writeVarInt(payload.selfPosition() + 1);
			buf.writeVarInt(payload.activePosition() + 1);
			buf.writeVarInt(payload.remainingTicks());
			buf.writeVarInt(payload.choices().size());
			for (RoleChoice choice : payload.choices()) {
				buf.writeIdentifier(choice.roleId());
				buf.writeString(choice.displayName(), 96);
				buf.writeInt(choice.color());
				buf.writeEnumConstant(choice.alignment());
			}
			buf.writeVarInt(payload.picks().size());
			for (PickResult pick : payload.picks()) {
				buf.writeVarInt(pick.position());
				buf.writeString(pick.displayName(), 96);
				buf.writeInt(pick.color());
				buf.writeBoolean(pick.randomSelection());
			}
		},
		buf -> {
			Phase phase = buf.readEnumConstant(Phase.class);
			int playerCount = Math.max(0, Math.min(128, buf.readVarInt()));
			int selfPosition = buf.readVarInt() - 1;
			int activePosition = buf.readVarInt() - 1;
			int remainingTicks = Math.max(0, Math.min(20 * 60, buf.readVarInt()));
			int choiceCount = Math.max(0, Math.min(3, buf.readVarInt()));
			List<RoleChoice> choices = new ArrayList<>(choiceCount);
			for (int i = 0; i < choiceCount; i++) {
				choices.add(new RoleChoice(buf.readIdentifier(), buf.readString(96), buf.readInt(),
					buf.readEnumConstant(RoleAlignment.class)));
			}
			int pickCount = Math.max(0, Math.min(playerCount, buf.readVarInt()));
			List<PickResult> picks = new ArrayList<>(pickCount);
			for (int i = 0; i < pickCount; i++) {
				picks.add(new PickResult(buf.readVarInt(), buf.readString(96), buf.readInt(), buf.readBoolean()));
			}
			return new DraftStatePayload(phase, playerCount, selfPosition, activePosition,
				remainingTicks, choices, picks);
		}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Phase {
		ORDER,
		PICK,
		FINISHING,
		CLOSED
	}

	public record RoleChoice(Identifier roleId, String displayName, int color, RoleAlignment alignment) {
		public RoleChoice {
			displayName = displayName == null || displayName.isBlank()
				? roleId == null ? "Unknown Role" : title(roleId.getPath())
				: displayName;
			color &= 0xFFFFFF;
			alignment = alignment == null ? RoleAlignment.CIVILIAN : alignment;
		}

		private static String title(String path) {
			if (path == null || path.isBlank()) return "Unknown Role";
			StringBuilder out = new StringBuilder(path.length());
			boolean upper = true;
			for (int i = 0; i < path.length(); i++) {
				char c = path.charAt(i);
				if (c == '_' || c == '-') {
					out.append(' ');
					upper = true;
				} else {
					out.append(upper ? Character.toUpperCase(c) : c);
					upper = false;
				}
			}
			return out.toString();
		}
	}

	public enum RoleAlignment {
		CIVILIAN("Civilian"),
		NEUTRAL("Neutral"),
		KILLER("Killer");

		private final String label;

		RoleAlignment(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public record PickResult(int position, String displayName, int color, boolean randomSelection) {
		public PickResult {
			displayName = displayName == null || displayName.isBlank() ? "Unknown Role" : displayName;
			color &= 0xFFFFFF;
		}
	}
}
