package dev.mapselect.preset.train;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import cat.rezelyn.watheextended.game.TeleportationSlot;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.mapselect.preset.map.MapPreset;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrainPreset {
	private static final int MAX_TELEPORTATION_SLOTS = 256;

	public MapPreset.BoxData resetTemplateArea;
	public MapPreset.OffsetData resetPasteOffset;
	public List<SlotData> teleportationSlots;

	public static TrainPreset from(ServerWorld world) {
		TrainPreset p = new TrainPreset();
		MapVariablesWorldComponent mv = MapVariablesWorldComponent.KEY.get(world);
		Box tmpl = mv.getResetTemplateArea();
		if (tmpl != null) p.resetTemplateArea = MapPreset.BoxData.from(tmpl);
		Vec3i off = mv.getResetPasteOffset();
		if (off != null) p.resetPasteOffset = MapPreset.OffsetData.from(off);

		WatheExtendedWorldComponent we = WatheExtendedWorldComponent.KEY.get(world);
		Map<Integer, TeleportationSlot> slots = we.getTeleportationSlots();
		List<SlotData> out = new ArrayList<>();
		if (slots != null) {
			for (Map.Entry<Integer, TeleportationSlot> e : slots.entrySet()) {
				if (e.getValue() == null) continue;
				out.add(SlotData.from(e.getKey(), e.getValue()));
			}
		}
		p.teleportationSlots = out;
		return p;
	}

	public void applyTo(ServerWorld world) {
		normalize();
		MapVariablesWorldComponent mv = MapVariablesWorldComponent.KEY.get(world);
		if (resetTemplateArea != null) mv.setResetTemplateArea(resetTemplateArea.toBox());
		if (resetPasteOffset != null) mv.setResetPasteOffset(resetPasteOffset.toVec3i());

		WatheExtendedWorldComponent we = WatheExtendedWorldComponent.KEY.get(world);
		Map<Integer, TeleportationSlot> rebuilt = new LinkedHashMap<>();
		if (teleportationSlots != null) {
			for (SlotData s : teleportationSlots) {
				rebuilt.put(s.id, new TeleportationSlot(s.x, s.y, s.z, s.yaw, s.pitch));
			}
		}
		we.setTeleportationSlots(rebuilt);
	}

	public int slotCount() {
		return teleportationSlots == null ? 0 : teleportationSlots.size();
	}

	public void normalize() {
		MapPreset scratch = new MapPreset();
		scratch.resetTemplateArea = resetTemplateArea;
		scratch.normalize();
		resetTemplateArea = scratch.resetTemplateArea;

		List<SlotData> normalized = new ArrayList<>();
		if (teleportationSlots != null) {
			for (SlotData s : teleportationSlots) {
				if (s == null || !finite(s.x) || !finite(s.y) || !finite(s.z) || !finite(s.yaw) || !finite(s.pitch)) {
					continue;
				}
				s.pitch = Math.max(-90.0F, Math.min(90.0F, s.pitch));
				normalized.add(s);
				if (normalized.size() >= MAX_TELEPORTATION_SLOTS) break;
			}
		}
		teleportationSlots = normalized;
	}

	private static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}

	public static class SlotData {
		public int id;
		public double x, y, z;
		public float yaw, pitch;

		public static SlotData from(int id, TeleportationSlot s) {
			SlotData d = new SlotData();
			d.id = id;
			d.x = s.x;
			d.y = s.y;
			d.z = s.z;
			d.yaw = s.yaw;
			d.pitch = s.pitch;
			return d;
		}
	}
}
