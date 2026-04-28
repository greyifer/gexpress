package dev.mapselect.preset.train;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import cat.rezelyn.watheextended.game.TeleportationSlot;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.mapselect.preset.map.MapPreset;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrainPreset {
	private static final int MAX_TELEPORTATION_SLOTS = 256;
	private static final int MAX_TRAIN_CARTS = 64;

	public MapPreset.BoxData resetTemplateArea;
	public MapPreset.OffsetData resetPasteOffset;
	public List<SlotData> teleportationSlots;
	public List<CartData> trainCarts = new ArrayList<>();

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

	public int cartCount() {
		return trainCarts == null ? 0 : trainCarts.size();
	}

	public int addTrainCart(BlockPos corner1, BlockPos corner2) {
		if (trainCarts == null) trainCarts = new ArrayList<>();
		trainCarts.add(CartData.fromCorners(corner1, corner2));
		normalize();
		return trainCarts.size();
	}

	public boolean removeTrainCart(int oneBasedIndex) {
		if (trainCarts == null || oneBasedIndex < 1 || oneBasedIndex > trainCarts.size()) return false;
		trainCarts.remove(oneBasedIndex - 1);
		normalize();
		return true;
	}

	public void clearTrainCarts() {
		if (trainCarts != null) trainCarts.clear();
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

		List<CartData> normalizedCarts = new ArrayList<>();
		if (trainCarts != null) {
			for (CartData cart : trainCarts) {
				CartData normalizedCart = normalizeCart(cart);
				if (normalizedCart != null) normalizedCarts.add(normalizedCart);
				if (normalizedCarts.size() >= MAX_TRAIN_CARTS) break;
			}
		}
		trainCarts = normalizedCarts;
	}

	private static CartData normalizeCart(CartData cart) {
		if (cart == null || cart.area == null) return null;
		MapPreset.BoxData area = cart.area;
		if (!finite(area.minX) || !finite(area.minY) || !finite(area.minZ)
				|| !finite(area.maxX) || !finite(area.maxY) || !finite(area.maxZ)) {
			return null;
		}

		CartData out = new CartData();
		out.area = new MapPreset.BoxData();
		out.area.minX = Math.floor(Math.min(area.minX, area.maxX));
		out.area.minY = Math.floor(Math.min(area.minY, area.maxY));
		out.area.minZ = Math.floor(Math.min(area.minZ, area.maxZ));
		out.area.maxX = Math.floor(Math.max(area.minX, area.maxX));
		out.area.maxY = Math.floor(Math.max(area.minY, area.maxY));
		out.area.maxZ = Math.floor(Math.max(area.minZ, area.maxZ));
		return out;
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

	public static class CartData {
		public MapPreset.BoxData area;

		public static CartData fromCorners(BlockPos corner1, BlockPos corner2) {
			CartData d = new CartData();
			d.area = new MapPreset.BoxData();
			d.area.minX = Math.min(corner1.getX(), corner2.getX());
			d.area.minY = Math.min(corner1.getY(), corner2.getY());
			d.area.minZ = Math.min(corner1.getZ(), corner2.getZ());
			d.area.maxX = Math.max(corner1.getX(), corner2.getX());
			d.area.maxY = Math.max(corner1.getY(), corner2.getY());
			d.area.maxZ = Math.max(corner1.getZ(), corner2.getZ());
			return d;
		}
	}
}
