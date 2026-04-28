package dev.mapselect.client.screen;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.mapselect.client.preset.ClientPresetCache;
import dev.mapselect.client.preset.ClientTrainPresetCache;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.train.TrainPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GexpressTrainCartsScreen {
	public static final Map<String, TrainPreset> pendingEdits = new LinkedHashMap<>();

	private GexpressTrainCartsScreen() {}

	public static ConfigCategory buildListCategory(Screen parent) {
		ConfigCategory.Builder cat = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.train_carts"))
			.tooltip(Text.translatable("gui.gexpress.config.category.train_carts.tooltip"));

		OptionGroup.Builder group = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.train_carts.list").formatted(Formatting.GOLD))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.group.train_carts.list.tooltip").formatted(Formatting.GRAY)));

		Map<String, MapPreset> maps = ClientPresetCache.snapshot();
		if (maps.isEmpty()) {
			group.option(LabelOption.create(
				Text.translatable("gui.gexpress.config.group.maps.empty.tooltip").formatted(Formatting.DARK_GRAY)));
		} else {
			for (Map.Entry<String, MapPreset> entry : maps.entrySet()) {
				String mapName = entry.getKey();
				String trainName = trainName(entry.getValue());
				MutableText name = Text.literal(mapName).formatted(Formatting.GOLD);
				if (trainName != null) {
					name.append(Text.literal(" -> " + trainName).formatted(Formatting.GRAY));
				}
				group.option(ButtonOption.createBuilder()
					.name(name)
					.text(Text.translatable("gui.gexpress.config.maps.edit").formatted(Formatting.AQUA))
					.action((scr, opt) -> GexpressOptionsScreen.navigateTrainCarts(parent, mapName))
					.build());
			}
		}

		cat.group(group.build());
		return cat.build();
	}

	public static ConfigCategory buildDetailCategory(Screen parent, String mapName) {
		Map<String, MapPreset> maps = ClientPresetCache.snapshot();
		MapPreset mapPreset = maps.get(mapName);
		if (mapPreset == null) return buildListCategory(parent);

		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.train_carts"))
			.tooltip(Text.translatable("gui.gexpress.config.group.train_carts.map.tooltip", mapName));

		category.group(backGroup(parent, mapName));

		String trainName = trainName(mapPreset);
		if (trainName == null) {
			category.group(messageGroup("gui.gexpress.config.group.train_carts.no_default"));
			return category.build();
		}

		Map<String, TrainPreset> trains = ClientTrainPresetCache.snapshot();
		TrainPreset original = trains.get(trainName);
		if (original == null) {
			category.group(messageGroup("gui.gexpress.config.group.train_carts.missing_train"));
			return category.build();
		}

		TrainPreset edit = mutableTrainPreset(trainName, original);
		category.group(ListOption.<String>createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.train_carts.carts", trainName).formatted(Formatting.YELLOW))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.group.train_carts.carts.tooltip").formatted(Formatting.GRAY)))
			.binding(List.of(), () -> cartRows(edit), rows -> edit.trainCarts = parseCartRows(rows))
			.controller(StringControllerBuilder::create)
			.initial(GexpressTrainCartsScreen::currentPlayerCartString)
			.collapsed(false)
			.build());

		return category.build();
	}

	private static OptionGroup backGroup(Screen parent, String mapName) {
		return OptionGroup.createBuilder()
			.name(Text.literal(mapName).formatted(Formatting.GOLD))
			.description(OptionDescription.of(
				Text.translatable("gui.gexpress.config.maps.back.tooltip").formatted(Formatting.GRAY)))
			.option(ButtonOption.createBuilder()
				.name(Text.literal("< ").formatted(Formatting.AQUA)
					.append(Text.translatable("gui.gexpress.config.maps.back").formatted(Formatting.AQUA)))
				.text(Text.translatable("gui.gexpress.config.maps.back.action").formatted(Formatting.WHITE))
				.action((scr, opt) -> GexpressOptionsScreen.navigateTrainCarts(parent, null))
				.build())
			.build();
	}

	private static OptionGroup messageGroup(String key) {
		return OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.train_carts").formatted(Formatting.YELLOW))
			.option(LabelOption.create(Text.translatable(key).formatted(Formatting.DARK_GRAY)))
			.build();
	}

	private static String trainName(MapPreset preset) {
		if (preset == null || preset.defaultTrainPreset == null || preset.defaultTrainPreset.isBlank()) return null;
		return preset.defaultTrainPreset;
	}

	private static TrainPreset mutableTrainPreset(String name, TrainPreset original) {
		TrainPreset copy = pendingEdits.computeIfAbsent(name, k -> cloneTrainPreset(original));
		if (copy.trainCarts == null) copy.trainCarts = new ArrayList<>();
		return copy;
	}

	private static TrainPreset cloneTrainPreset(TrainPreset src) {
		TrainPreset copy = new TrainPreset();
		copy.resetTemplateArea = cloneBox(src.resetTemplateArea);
		copy.resetPasteOffset = src.resetPasteOffset == null ? null : cloneOffset(src.resetPasteOffset);
		copy.teleportationSlots = new ArrayList<>();
		if (src.teleportationSlots != null) {
			for (TrainPreset.SlotData slot : src.teleportationSlots) {
				if (slot == null) continue;
				TrainPreset.SlotData out = new TrainPreset.SlotData();
				out.id = slot.id;
				out.x = slot.x;
				out.y = slot.y;
				out.z = slot.z;
				out.yaw = slot.yaw;
				out.pitch = slot.pitch;
				copy.teleportationSlots.add(out);
			}
		}
		copy.trainCarts = new ArrayList<>();
		if (src.trainCarts != null) {
			for (TrainPreset.CartData cart : src.trainCarts) {
				TrainPreset.CartData cloned = cloneCart(cart);
				if (cloned != null) copy.trainCarts.add(cloned);
			}
		}
		return copy;
	}

	private static MapPreset.BoxData cloneBox(MapPreset.BoxData box) {
		if (box == null) return null;
		MapPreset.BoxData out = new MapPreset.BoxData();
		out.minX = box.minX;
		out.minY = box.minY;
		out.minZ = box.minZ;
		out.maxX = box.maxX;
		out.maxY = box.maxY;
		out.maxZ = box.maxZ;
		return out;
	}

	private static MapPreset.OffsetData cloneOffset(MapPreset.OffsetData offset) {
		MapPreset.OffsetData out = new MapPreset.OffsetData();
		out.x = offset.x;
		out.y = offset.y;
		out.z = offset.z;
		return out;
	}

	private static TrainPreset.CartData cloneCart(TrainPreset.CartData cart) {
		if (cart == null || cart.area == null) return null;
		TrainPreset.CartData out = new TrainPreset.CartData();
		out.area = cloneBox(cart.area);
		return out;
	}

	private static List<String> cartRows(TrainPreset preset) {
		List<String> out = new ArrayList<>();
		if (preset.trainCarts == null) return out;
		for (TrainPreset.CartData cart : preset.trainCarts) {
			String row = cartToString(cart);
			if (row != null) out.add(row);
		}
		return out;
	}

	private static List<TrainPreset.CartData> parseCartRows(List<String> rows) {
		List<TrainPreset.CartData> out = new ArrayList<>();
		if (rows == null) return out;
		for (String row : rows) {
			TrainPreset.CartData cart = parseCart(row);
			if (cart != null) out.add(cart);
		}
		return out;
	}

	private static String cartToString(TrainPreset.CartData cart) {
		if (cart == null || cart.area == null) return null;
		return fmt(cart.area.minX) + " " + fmt(cart.area.minY) + " " + fmt(cart.area.minZ) + " "
			+ fmt(cart.area.maxX) + " " + fmt(cart.area.maxY) + " " + fmt(cart.area.maxZ);
	}

	private static TrainPreset.CartData parseCart(String raw) {
		if (raw == null) return null;
		String[] parts = raw.trim().split("\\s+");
		if (parts.length != 6) return null;
		try {
			double x1 = Double.parseDouble(parts[0]);
			double y1 = Double.parseDouble(parts[1]);
			double z1 = Double.parseDouble(parts[2]);
			double x2 = Double.parseDouble(parts[3]);
			double y2 = Double.parseDouble(parts[4]);
			double z2 = Double.parseDouble(parts[5]);
			TrainPreset.CartData cart = new TrainPreset.CartData();
			cart.area = new MapPreset.BoxData();
			cart.area.minX = Math.min(x1, x2);
			cart.area.minY = Math.min(y1, y2);
			cart.area.minZ = Math.min(z1, z2);
			cart.area.maxX = Math.max(x1, x2);
			cart.area.maxY = Math.max(y1, y2);
			cart.area.maxZ = Math.max(z1, z2);
			return cart;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String currentPlayerCartString() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return "0 0 0 0 0 0";
		int x = player.getBlockPos().getX();
		int y = player.getBlockPos().getY();
		int z = player.getBlockPos().getZ();
		return x + " " + y + " " + z + " " + x + " " + y + " " + z;
	}

	private static String fmt(double v) {
		String s = new BigDecimal(Double.toString(v)).setScale(2, RoundingMode.DOWN).toPlainString();
		s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}
}
