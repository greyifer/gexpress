package dev.mapselect.client;

import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public final class ClientModelAttachmentPreview {
	public enum Kind {
		C4,
		SPY_BUG
	}

	private static Kind kind;
	private static C4PlacementPreset preset;

	private ClientModelAttachmentPreview() {}

	public static void activate(Kind nextKind, C4PlacementPreset nextPreset) {
		kind = nextKind;
		preset = nextPreset == null ? C4PlacementPreset.DEFAULT : nextPreset.clamped();
	}

	public static void update(C4PlacementPreset nextPreset) {
		if (kind == null) return;
		preset = nextPreset == null ? C4PlacementPreset.DEFAULT : nextPreset.clamped();
	}

	public static void clear() {
		kind = null;
		preset = null;
	}

	public static C4PlacementPreset c4Preset(AbstractClientPlayerEntity entity) {
		return presetFor(entity, Kind.C4);
	}

	public static C4PlacementPreset spyBugPreset(AbstractClientPlayerEntity entity) {
		return presetFor(entity, Kind.SPY_BUG);
	}

	private static C4PlacementPreset presetFor(AbstractClientPlayerEntity entity, Kind expected) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (kind != expected || preset == null || client == null || entity == null || entity != client.player) {
			return null;
		}
		return preset;
	}
}
