package dev.mapselect.preset.map;

import cat.rezelyn.watheextended.cca.WatheExtendedWorldComponent;
import cat.rezelyn.watheextended.game.TeleportationSlot;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent.PosWithOrientation;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.mapselect.weather.WeatherType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapPreset {
	private static final int MAX_RANDOM_SPAWNS = 256;
	private static final int MAX_KEY_DOORS = 512;
	private static final double MAX_BOX_SPAN = 10000.0D;

	public BoxData playArea;
	public BoxData readyArea;
	public BoxData lobbyArea;
	public PosData spectatorSpawnPos;
	public PosData readyAreaSpawnPos;
	public OffsetData playAreaOffset;
	public BoxData wholeMapArea;
	public BoxData resetTemplateArea;
	public WeatherType weather = WeatherType.NONE;
	public Integer fogColor;
	public String defaultTrainPreset;
	public OffsetData lobbyTrainCorner;
	public List<PosData> randomSpawnPositions = new ArrayList<>();
	public List<KeyDoorData> keyDoors = new ArrayList<>();

	public static MapPreset from(MapVariablesWorldComponent c) {
		MapPreset p = new MapPreset();
		p.playArea = BoxData.from(c.getPlayArea());
		p.readyArea = BoxData.from(c.getReadyArea());
		p.spectatorSpawnPos = PosData.from(c.getSpectatorSpawnPos());
		p.playAreaOffset = OffsetData.from(c.getPlayAreaOffset());
		p.resetTemplateArea = BoxData.from(c.getResetTemplateArea());
		return p;
	}

	public static MapPreset from(ServerWorld world) {
		MapPreset p = from(MapVariablesWorldComponent.KEY.get(world));
		WatheExtendedWorldComponent ext = WatheExtendedWorldComponent.KEY.get(world);
		p.lobbyArea = BoxData.from(ext.getLobbyArea());
		p.readyAreaSpawnPos = PosData.from(ext.getReadyAreaSpawnPos());
		p.randomSpawnPositions = randomSpawnsFrom(world);
		p.keyDoors = keyDoorsFrom(world, null);
		return p;
	}

	public static List<PosData> randomSpawnsFrom(ServerWorld world) {
		List<PosData> out = new ArrayList<>();
		Map<Integer, TeleportationSlot> slots = WatheExtendedWorldComponent.KEY.get(world).getTeleportationSlots();
		if (slots == null || slots.isEmpty()) return out;
		for (TeleportationSlot slot : slots.values()) {
			if (slot == null) continue;
			PosData p = new PosData();
			p.x = slot.x;
			p.y = slot.y;
			p.z = slot.z;
			p.yaw = slot.yaw;
			p.pitch = slot.pitch;
			out.add(p);
			if (out.size() >= MAX_RANDOM_SPAWNS) break;
		}
		return out;
	}

	public static List<KeyDoorData> keyDoorsFrom(ServerWorld world, BoxData area) {
		List<KeyDoorData> out = new ArrayList<>();
		if (world == null) return out;
		BoxData scanArea = normalizeBox(copyBox(area));
		if (scanArea == null) {
			return out;
		}
		int minX = (int) Math.floor(scanArea.minX);
		int minY = (int) Math.floor(scanArea.minY);
		int minZ = (int) Math.floor(scanArea.minZ);
		int maxX = (int) Math.floor(scanArea.maxX);
		int maxY = (int) Math.floor(scanArea.maxY);
		int maxZ = (int) Math.floor(scanArea.maxZ);
		Iterable<BlockPos> positions = BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ);
		for (BlockPos pos : positions) {
			if (world.getBlockEntity(pos) instanceof DoorBlockEntity door) {
				String keyName = door.getKeyName();
				if (keyName != null && !keyName.isBlank()) {
					KeyDoorData data = new KeyDoorData();
					data.keyName = keyName;
					data.x = pos.getX();
					data.y = pos.getY();
					data.z = pos.getZ();
					out.add(data);
					if (out.size() >= MAX_KEY_DOORS) break;
				}
			}
		}
		return out;
	}

	public void applyTo(MapVariablesWorldComponent c) {
		normalize();
		if (playArea != null) c.setPlayArea(playArea.toBox());
		if (readyArea != null) c.setReadyArea(readyArea.toBox());
		if (spectatorSpawnPos != null) c.setSpectatorSpawnPos(spectatorSpawnPos.toPosWithOrientation());
		if (playAreaOffset != null) c.setPlayAreaOffset(playAreaOffset.toVec3i());
		if (resetTemplateArea != null) c.setResetTemplateArea(resetTemplateArea.toBox());
	}

	public void applyTo(ServerWorld world) {
		applyTo(MapVariablesWorldComponent.KEY.get(world));
		WatheExtendedWorldComponent ext = WatheExtendedWorldComponent.KEY.get(world);
		if (lobbyArea != null) ext.setLobbyArea(lobbyArea.toBox());
		if (readyAreaSpawnPos != null) ext.setReadyAreaSpawnPos(readyAreaSpawnPos.toPosWithOrientation());
		applyKeyDoorsTo(world);
	}

	public boolean applyRandomSpawnsTo(ServerWorld world) {
		normalize();
		if (randomSpawnPositions == null || randomSpawnPositions.isEmpty()) return false;

		Map<Integer, TeleportationSlot> rebuilt = new LinkedHashMap<>();
		int id = 0;
		for (PosData p : randomSpawnPositions) {
			rebuilt.put(id++, new TeleportationSlot(p.x, p.y, p.z, p.yaw, p.pitch));
		}
		WatheExtendedWorldComponent.KEY.get(world).setTeleportationSlots(rebuilt);
		return true;
	}

	public void applyKeyDoorsTo(ServerWorld world) {
		normalize();
		if (world == null || keyDoors == null || keyDoors.isEmpty()) return;
		for (KeyDoorData keyDoor : keyDoors) {
			if (keyDoor == null || keyDoor.keyName == null) continue;
			BlockPos pos = new BlockPos(keyDoor.x, keyDoor.y, keyDoor.z);
			if (world.getBlockEntity(pos) instanceof DoorBlockEntity door) {
				door.setKeyName(keyDoor.keyName);
				door.markDirty();
			}
		}
	}

	public void normalize() {
		playArea = normalizeBox(playArea);
		readyArea = normalizeBox(readyArea);
		lobbyArea = normalizeBox(lobbyArea);
		wholeMapArea = normalizeBox(wholeMapArea);
		resetTemplateArea = normalizeBox(resetTemplateArea);
		spectatorSpawnPos = normalizePos(spectatorSpawnPos);
		readyAreaSpawnPos = normalizePos(readyAreaSpawnPos);

		if (weather == null) weather = WeatherType.NONE;
		if (fogColor != null) fogColor = fogColor & 0xFFFFFF;
		if (defaultTrainPreset != null) {
			defaultTrainPreset = defaultTrainPreset.trim();
			if (defaultTrainPreset.isEmpty()) defaultTrainPreset = null;
		}

		List<PosData> normalizedSpawns = new ArrayList<>();
		if (randomSpawnPositions != null) {
			for (PosData p : randomSpawnPositions) {
				PosData normalized = normalizeRandomSpawn(p);
				if (normalized != null) normalizedSpawns.add(normalized);
				if (normalizedSpawns.size() >= MAX_RANDOM_SPAWNS) break;
			}
		}
		randomSpawnPositions = normalizedSpawns;

		List<KeyDoorData> normalizedKeys = new ArrayList<>();
		if (keyDoors != null) {
			for (KeyDoorData keyDoor : keyDoors) {
				KeyDoorData normalized = normalizeKeyDoor(keyDoor);
				if (normalized != null) normalizedKeys.add(normalized);
				if (normalizedKeys.size() >= MAX_KEY_DOORS) break;
			}
		}
		keyDoors = normalizedKeys;
	}

	private static BoxData copyBox(BoxData box) {
		if (box == null) return null;
		BoxData out = new BoxData();
		out.minX = box.minX;
		out.minY = box.minY;
		out.minZ = box.minZ;
		out.maxX = box.maxX;
		out.maxY = box.maxY;
		out.maxZ = box.maxZ;
		return out;
	}

	private static BoxData normalizeBox(BoxData b) {
		if (b == null) return null;
		if (!finite(b.minX) || !finite(b.minY) || !finite(b.minZ)
			|| !finite(b.maxX) || !finite(b.maxY) || !finite(b.maxZ)) {
			return null;
		}

		double minX = Math.min(b.minX, b.maxX);
		double minY = Math.min(b.minY, b.maxY);
		double minZ = Math.min(b.minZ, b.maxZ);
		double maxX = Math.max(b.minX, b.maxX);
		double maxY = Math.max(b.minY, b.maxY);
		double maxZ = Math.max(b.minZ, b.maxZ);
		if (maxX - minX > MAX_BOX_SPAN || maxY - minY > MAX_BOX_SPAN || maxZ - minZ > MAX_BOX_SPAN) {
			return null;
		}

		b.minX = minX;
		b.minY = minY;
		b.minZ = minZ;
		b.maxX = maxX;
		b.maxY = maxY;
		b.maxZ = maxZ;
		return b;
	}

	private static PosData normalizePos(PosData p) {
		if (p == null) return null;
		if (!finite(p.x) || !finite(p.y) || !finite(p.z) || !finite(p.yaw) || !finite(p.pitch)) {
			return null;
		}
		p.pitch = Math.max(-90.0F, Math.min(90.0F, p.pitch));
		return p;
	}

	private static PosData normalizeRandomSpawn(PosData p) {
		p = normalizePos(p);
		if (p == null) return null;
		p.x = snapRandomSpawnCoord(p.x);
		p.y = snapRandomSpawnCoord(p.y);
		p.z = snapRandomSpawnCoord(p.z);
		return p;
	}

	private static KeyDoorData normalizeKeyDoor(KeyDoorData keyDoor) {
		if (keyDoor == null || keyDoor.keyName == null) return null;
		String name = keyDoor.keyName.trim();
		if (name.isEmpty()) return null;
		KeyDoorData out = new KeyDoorData();
		out.keyName = name.length() > 64 ? name.substring(0, 64) : name;
		out.x = keyDoor.x;
		out.y = keyDoor.y;
		out.z = keyDoor.z;
		return out;
	}

	public static double snapRandomSpawnCoord(double v) {
		if (Math.abs(v - Math.rint(v)) < 1.0E-6D) return Math.rint(v);
		return Math.floor(v) + 0.5D;
	}

	private static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}

	public static class PosData {
		public double x, y, z;
		public float yaw, pitch;

		public static PosData from(PosWithOrientation p) {
			if (p == null) return null;
			PosData d = new PosData();
			d.x = p.pos.x;
			d.y = p.pos.y;
			d.z = p.pos.z;
			d.yaw = p.yaw;
			d.pitch = p.pitch;
			return d;
		}

		public PosWithOrientation toPosWithOrientation() {
			return new PosWithOrientation(new Vec3d(x, y, z), yaw, pitch);
		}
	}

	public static class BoxData {
		public double minX, minY, minZ, maxX, maxY, maxZ;

		public static BoxData from(Box b) {
			if (b == null) return null;
			BoxData d = new BoxData();
			d.minX = b.minX;
			d.minY = b.minY;
			d.minZ = b.minZ;
			d.maxX = b.maxX;
			d.maxY = b.maxY;
			d.maxZ = b.maxZ;
			return d;
		}

		public static BoxData fromCorners(Vec3d a, Vec3d b) {
			return from(new Box(a, b));
		}

		public Box toBox() {
			return new Box(minX, minY, minZ, maxX, maxY, maxZ);
		}

		public double sizeX() { return maxX - minX; }
		public double sizeY() { return maxY - minY; }
		public double sizeZ() { return maxZ - minZ; }
	}

	public static class OffsetData {
		public int x, y, z;

		public static OffsetData from(Vec3i v) {
			if (v == null) return null;
			OffsetData d = new OffsetData();
			d.x = v.getX();
			d.y = v.getY();
			d.z = v.getZ();
			return d;
		}

		public Vec3i toVec3i() {
			return new Vec3i(x, y, z);
		}
	}

	public static class KeyDoorData {
		public String keyName;
		public int x, y, z;
	}
}
