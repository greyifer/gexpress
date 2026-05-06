package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.weather.WeatherType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GexpressMapPresetsCategory {
	private GexpressMapPresetsCategory() {}

	public static final Map<String, MapPreset> pendingEdits = new LinkedHashMap<>();

	public static ConfigCategory emptyCategory() {
		return ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.maps"))
			.tooltip(Text.translatable("gui.gexpress.config.category.maps.tooltip"))
			.group(OptionGroup.createBuilder()
				.name(Text.translatable("gui.gexpress.config.group.maps.empty").formatted(Formatting.GRAY))
				.option(LabelOption.create(
					Text.translatable("gui.gexpress.config.group.maps.empty.tooltip").formatted(Formatting.DARK_GRAY)))
				.build())
			.build();
	}

	public static ConfigCategory buildPresetCategory(String name, MapPreset original) {
		MapPreset edit = mutablePreset(name, original);

		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(Text.literal(name).formatted(Formatting.GOLD))
			.tooltip(Text.translatable("gui.gexpress.config.group.map.tooltip", name));

		OptionGroup.Builder areas = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.areas").formatted(Formatting.YELLOW));
		areas.option(boxOption("wholeMapArea", () -> edit.wholeMapArea, v -> edit.wholeMapArea = v));
		areas.option(boxOption("lobbyArea", () -> edit.lobbyArea, v -> edit.lobbyArea = v));
		areas.option(boxOption("readyArea", () -> edit.readyArea, v -> edit.readyArea = v));
		areas.option(boxOption("playArea", () -> edit.playArea, v -> edit.playArea = v));
		areas.option(boxOption("resetTemplateArea", () -> edit.resetTemplateArea, v -> edit.resetTemplateArea = v));
		category.group(areas.build());
		category.group(freshAirAreasOption(edit));

		OptionGroup.Builder positions = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.positions").formatted(Formatting.YELLOW));
		positions.option(posOption("readyAreaSpawnPos", () -> edit.readyAreaSpawnPos, v -> edit.readyAreaSpawnPos = v));
		positions.option(posOption("spectatorSpawnPos", () -> edit.spectatorSpawnPos, v -> edit.spectatorSpawnPos = v));
		positions.option(offsetOption("playAreaOffset", () -> edit.playAreaOffset, v -> edit.playAreaOffset = v));
		positions.option(offsetOption("lobbyTrainCorner", () -> edit.lobbyTrainCorner, v -> edit.lobbyTrainCorner = v));
		category.group(positions.build());

		category.group(buildRandomSpawnActions(name, edit));
		category.group(buildRandomSpawnsGroup(edit));

		OptionGroup.Builder visuals = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.visuals").formatted(Formatting.YELLOW));
		visuals.option(Option.<WeatherType>createBuilder()
			.name(fieldLabel("weather"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.map.weather.tooltip")))
			.binding(WeatherType.NONE, () -> edit.weather, v -> edit.weather = v)
			.controller(opt -> EnumControllerBuilder.create(opt).enumClass(WeatherType.class))
			.build());
		visuals.option(Option.<String>createBuilder()
			.name(fieldLabel("fogColor"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.map.fogColor.tooltip")))
			.binding("clear", () -> fogColorToString(edit.fogColor), v -> edit.fogColor = parseFogColor(v))
			.controller(StringControllerBuilder::create)
			.build());
		visuals.option(Option.<String>createBuilder()
			.name(fieldLabel("defaultTrainPreset"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.map.defaultTrainPreset.tooltip")))
			.binding("", () -> edit.defaultTrainPreset != null ? edit.defaultTrainPreset : "",
				v -> edit.defaultTrainPreset = (v == null || v.isEmpty()) ? null : v)
			.controller(StringControllerBuilder::create)
			.build());
		visuals.option(Option.<Integer>createBuilder()
			.name(fieldLabel("roomCount"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.map.roomCount.tooltip")))
			.binding(MapPreset.DEFAULT_ROOM_COUNT, () -> MapPreset.normalizeRoomCount(edit.roomCount),
				v -> edit.roomCount = MapPreset.normalizeRoomCount(v))
			.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(MapPreset.MIN_ROOM_COUNT, MapPreset.MAX_ROOM_COUNT))
			.build());
		category.group(visuals.build());
		category.group(buildSnapshotActions(name));

		return category.build();
	}

	private static OptionGroup buildSnapshotActions(String name) {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.imports").formatted(Formatting.YELLOW))
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.maps.snapshotWathe"))
				.text(Text.translatable("gui.gexpress.config.maps.snapshotWathe.action").formatted(Formatting.AQUA))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.maps.snapshotWathe.tooltip")))
				.action((screen, option) -> GexpressOptionsScreen.stageChatCommand("g setup map snapshot " + name))
				.build())
			.build();
	}

	private static ListOption<String> buildRandomSpawnsGroup(MapPreset edit) {
		return ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.randomSpawns").formatted(Formatting.YELLOW))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.group.map.randomSpawns.tooltip").formatted(Formatting.GRAY)))
			.binding(
				List.of(),
				() -> {
					List<String> out = new ArrayList<>();
					if (edit.randomSpawnPositions != null) {
						for (MapPreset.PosData p : edit.randomSpawnPositions) {
							if (p != null) out.add(randomSpawnToString(p));
						}
					}
					return out;
				},
				list -> {
					List<MapPreset.PosData> parsed = new ArrayList<>();
					for (String s : list) {
						MapPreset.PosData p = parsePos(s);
						if (p != null) parsed.add(p);
					}
					edit.randomSpawnPositions = parsed;
			})
			.controller(StringControllerBuilder::create)
			.initial(GexpressMapPresetsCategory::currentPlayerPosString)
			.collapsed(false)
			.build();
	}

	private static OptionGroup buildRandomSpawnActions(String name, MapPreset edit) {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.randomSpawns.actions").formatted(Formatting.YELLOW))
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.maps.randomSpawns.import"))
				.text(Text.translatable("gui.gexpress.config.maps.randomSpawns.import.action").formatted(Formatting.AQUA))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.maps.randomSpawns.import.tooltip")))
				.action((screen, option) -> GexpressOptionsScreen.stageChatCommand("g setup map edit " + name + " randomspawns snapshot"))
				.build())
			.option(ButtonOption.createBuilder()
				.name(Text.translatable("gui.gexpress.config.maps.randomSpawns.clear"))
				.text(Text.translatable("gui.gexpress.config.maps.randomSpawns.clear.action").formatted(Formatting.RED))
				.description(OptionDescription.of(Text.translatable("gui.gexpress.config.maps.randomSpawns.clear.tooltip")))
				.action((screen, option) -> {
					edit.randomSpawnPositions = new ArrayList<>();
					GexpressOptionsScreen.stageChatCommand("g setup map edit " + name + " randomspawns clear");
				})
				.build())
			.build();
	}

	private static MapPreset mutablePreset(String name, MapPreset original) {
		MapPreset copy = pendingEdits.computeIfAbsent(name, k -> cloneShallow(original));
		ensureNonNullShapes(copy);
		return copy;
	}

	private static MapPreset cloneShallow(MapPreset src) {
		MapPreset copy = new MapPreset();
		copy.playArea = cloneBox(src.playArea);
		copy.readyArea = cloneBox(src.readyArea);
		copy.lobbyArea = cloneBox(src.lobbyArea);
		copy.freshAirArea = cloneBox(src.freshAirArea);
		copy.freshAirAreas = cloneFreshAirAreas(src);
		copy.spectatorSpawnPos = clonePos(src.spectatorSpawnPos);
		copy.readyAreaSpawnPos = clonePos(src.readyAreaSpawnPos);
		copy.playAreaOffset = cloneOffset(src.playAreaOffset);
		copy.wholeMapArea = cloneBox(src.wholeMapArea);
		copy.resetTemplateArea = cloneBox(src.resetTemplateArea);
		copy.weather = src.weather != null ? src.weather : WeatherType.NONE;
		copy.fogColor = src.fogColor;
		copy.defaultTrainPreset = src.defaultTrainPreset;
		copy.lobbyTrainCorner = cloneOffset(src.lobbyTrainCorner);
		copy.roomCount = MapPreset.normalizeRoomCount(src.roomCount);
		copy.randomSpawnPositions = new ArrayList<>();
		if (src.randomSpawnPositions != null) {
			for (MapPreset.PosData p : src.randomSpawnPositions) {
				if (p != null) copy.randomSpawnPositions.add(clonePos(p));
			}
		}
		return copy;
	}

	private static MapPreset.BoxData cloneBox(MapPreset.BoxData b) {
		if (b == null) return null;
		MapPreset.BoxData d = new MapPreset.BoxData();
		d.minX = b.minX; d.minY = b.minY; d.minZ = b.minZ;
		d.maxX = b.maxX; d.maxY = b.maxY; d.maxZ = b.maxZ;
		return d;
	}

	private static List<MapPreset.FreshAirAreaData> cloneFreshAirAreas(MapPreset src) {
		List<MapPreset.FreshAirAreaData> out = new ArrayList<>();
		if (src == null) return out;
		if (src.freshAirAreas != null) {
			for (MapPreset.FreshAirAreaData entry : src.freshAirAreas) {
				MapPreset.FreshAirAreaData copy = cloneFreshAirArea(entry);
				if (copy != null) out.add(copy);
			}
		}
		if (out.isEmpty() && src.freshAirArea != null) {
			MapPreset.FreshAirAreaData legacy = new MapPreset.FreshAirAreaData();
			legacy.area = cloneBox(src.freshAirArea);
			legacy.sanityPercent = 100;
			out.add(legacy);
		}
		return out;
	}

	private static MapPreset.FreshAirAreaData cloneFreshAirArea(MapPreset.FreshAirAreaData entry) {
		if (entry == null || entry.area == null) return null;
		MapPreset.FreshAirAreaData copy = new MapPreset.FreshAirAreaData();
		copy.area = cloneBox(entry.area);
		copy.sanityPercent = clampPercent(entry.sanityPercent);
		return copy;
	}

	private static MapPreset.PosData clonePos(MapPreset.PosData p) {
		if (p == null) return null;
		MapPreset.PosData d = new MapPreset.PosData();
		d.x = p.x; d.y = p.y; d.z = p.z;
		d.yaw = p.yaw; d.pitch = p.pitch;
		return d;
	}

	private static MapPreset.OffsetData cloneOffset(MapPreset.OffsetData o) {
		if (o == null) return null;
		MapPreset.OffsetData d = new MapPreset.OffsetData();
		d.x = o.x; d.y = o.y; d.z = o.z;
		return d;
	}

	private static void ensureNonNullShapes(MapPreset p) {
		if (p.weather == null) p.weather = WeatherType.NONE;
		p.roomCount = MapPreset.normalizeRoomCount(p.roomCount);
		if (p.randomSpawnPositions == null) p.randomSpawnPositions = new ArrayList<>();
		if (p.freshAirAreas == null) p.freshAirAreas = new ArrayList<>();
	}

	private static ListOption<String> freshAirAreasOption(MapPreset edit) {
		return ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.map.freshAirAreas").formatted(Formatting.YELLOW))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.group.map.freshAirAreas.tooltip").formatted(Formatting.GRAY)))
			.binding(
				List.of(),
				() -> {
					List<String> rows = new ArrayList<>();
					if (edit.freshAirAreas != null) {
						for (MapPreset.FreshAirAreaData entry : edit.freshAirAreas) {
							if (entry != null && entry.area != null) rows.add(freshAirAreaToString(entry));
						}
					}
					return rows;
				},
				rows -> {
					List<MapPreset.FreshAirAreaData> parsed = new ArrayList<>();
					for (String row : rows) {
						MapPreset.FreshAirAreaData entry = parseFreshAirArea(row);
						if (entry != null) parsed.add(entry);
					}
					edit.freshAirAreas = parsed;
					edit.freshAirArea = parsed.isEmpty() ? null : parsed.getFirst().area;
				})
			.controller(StringControllerBuilder::create)
			.initial(() -> "0 0 0 0 0 0 100")
			.collapsed(false)
			.build();
	}

	private static Option<String> boxOption(String key, Supplier<MapPreset.BoxData> getter, Consumer<MapPreset.BoxData> setter) {
		return Option.<String>createBuilder()
			.name(fieldLabel(key))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.option.map.box.tooltip").formatted(Formatting.GRAY)))
			.binding("0 0 0 0 0 0",
				() -> boxToString(getter.get()),
				v -> setter.accept(parseBox(v)))
			.controller(StringControllerBuilder::create)
			.build();
	}

	private static Option<String> posOption(String key, Supplier<MapPreset.PosData> getter, Consumer<MapPreset.PosData> setter) {
		return Option.<String>createBuilder()
			.name(fieldLabel(key))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.option.map.pos.tooltip").formatted(Formatting.GRAY)))
			.binding("0 0 0 0 0",
				() -> posToString(getter.get()),
				v -> setter.accept(parsePos(v)))
			.controller(StringControllerBuilder::create)
			.build();
	}

	private static Option<String> offsetOption(String key, Supplier<MapPreset.OffsetData> getter, Consumer<MapPreset.OffsetData> setter) {
		return Option.<String>createBuilder()
			.name(fieldLabel(key))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.option.map.offset.tooltip").formatted(Formatting.GRAY)))
			.binding("0 0 0",
				() -> offsetToString(getter.get()),
				v -> setter.accept(parseOffset(v)))
			.controller(StringControllerBuilder::create)
			.build();
	}

	private static String boxToString(MapPreset.BoxData b) {
		if (b == null) return "0 0 0 0 0 0";
		return fmt(b.minX) + " " + fmt(b.minY) + " " + fmt(b.minZ) + " "
			+ fmt(b.maxX) + " " + fmt(b.maxY) + " " + fmt(b.maxZ);
	}

	private static String freshAirAreaToString(MapPreset.FreshAirAreaData entry) {
		if (entry == null || entry.area == null) return "0 0 0 0 0 0 100";
		return boxToString(entry.area) + " " + clampPercent(entry.sanityPercent);
	}

	private static MapPreset.FreshAirAreaData parseFreshAirArea(String s) {
		if (s == null) return null;
		String[] parts = s.trim().split("\\s+");
		if (parts.length != 7) return null;
		MapPreset.BoxData box = parseBox(String.join(" ",
			parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
		if (box == null) return null;
		try {
			MapPreset.FreshAirAreaData entry = new MapPreset.FreshAirAreaData();
			entry.area = box;
			entry.sanityPercent = clampPercent(Integer.parseInt(parts[6]));
			return entry;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static MapPreset.BoxData parseBox(String s) {
		if (s == null) return null;
		String[] parts = s.trim().split("\\s+");
		if (parts.length != 6) return null;
		try {
			MapPreset.BoxData d = new MapPreset.BoxData();
			double x1 = Double.parseDouble(parts[0]);
			double y1 = Double.parseDouble(parts[1]);
			double z1 = Double.parseDouble(parts[2]);
			double x2 = Double.parseDouble(parts[3]);
			double y2 = Double.parseDouble(parts[4]);
			double z2 = Double.parseDouble(parts[5]);
			d.minX = Math.min(x1, x2);
			d.minY = Math.min(y1, y2);
			d.minZ = Math.min(z1, z2);
			d.maxX = Math.max(x1, x2);
			d.maxY = Math.max(y1, y2);
			d.maxZ = Math.max(z1, z2);
			return d;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String posToString(MapPreset.PosData p) {
		if (p == null) return "0 0 0 0 0";
		return fmt(p.x) + " " + fmt(p.y) + " " + fmt(p.z) + " " + fmt(p.yaw) + " " + fmt(p.pitch);
	}

	private static String randomSpawnToString(MapPreset.PosData p) {
		if (p == null) return "0 0 0 0 0";
		return fmt(MapPreset.snapRandomSpawnCoord(p.x)) + " "
			+ fmt(MapPreset.snapRandomSpawnCoord(p.y)) + " "
			+ fmt(MapPreset.snapRandomSpawnCoord(p.z)) + " "
			+ fmt(p.yaw) + " " + fmt(p.pitch);
	}

	private static String currentPlayerPosString() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return "0 0 0 0 0";
		return fmt(MapPreset.snapRandomSpawnCoord(player.getX())) + " "
			+ fmt(MapPreset.snapRandomSpawnCoord(player.getY())) + " "
			+ fmt(MapPreset.snapRandomSpawnCoord(player.getZ())) + " "
			+ fmt(player.getYaw()) + " "
			+ fmt(player.getPitch());
	}

	private static MapPreset.PosData parsePos(String s) {
		if (s == null) return null;
		String[] parts = s.trim().split("\\s+");
		if (parts.length != 5) return null;
		try {
			MapPreset.PosData d = new MapPreset.PosData();
			d.x = Double.parseDouble(parts[0]);
			d.y = Double.parseDouble(parts[1]);
			d.z = Double.parseDouble(parts[2]);
			d.yaw = Float.parseFloat(parts[3]);
			d.pitch = Float.parseFloat(parts[4]);
			return d;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String offsetToString(MapPreset.OffsetData o) {
		if (o == null) return "0 0 0";
		return o.x + " " + o.y + " " + o.z;
	}

	private static MapPreset.OffsetData parseOffset(String s) {
		if (s == null) return null;
		String[] parts = s.trim().split("\\s+");
		if (parts.length != 3) return null;
		try {
			MapPreset.OffsetData d = new MapPreset.OffsetData();
			d.x = Integer.parseInt(parts[0]);
			d.y = Integer.parseInt(parts[1]);
			d.z = Integer.parseInt(parts[2]);
			return d;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String fogColorToString(Integer color) {
		if (color == null) return "clear";
		return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
	}

	private static Integer parseFogColor(String s) {
		if (s == null || s.isBlank()) return null;
		String c = s.trim();
		if ("clear".equalsIgnoreCase(c) || "none".equalsIgnoreCase(c)) return null;
		if (c.startsWith("#")) c = c.substring(1);
		else if (c.startsWith("0x") || c.startsWith("0X")) c = c.substring(2);
		if (c.length() != 6) return null;
		try {
			return Integer.parseInt(c, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String fmt(double v) {
		String s = new BigDecimal(Double.toString(v)).setScale(2, RoundingMode.DOWN).toPlainString();
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	private static String fmt(float v) {
		String s = new BigDecimal(Float.toString(v)).setScale(2, RoundingMode.DOWN).toPlainString();
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	private static int clampPercent(int percent) {
		return Math.max(0, Math.min(100, percent));
	}

	private static Text fieldLabel(String key) {
		return Text.literal(key).formatted(Formatting.WHITE);
	}
}
